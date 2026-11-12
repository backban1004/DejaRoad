"""데자로드 시연 영상 만들기 (에뮬레이터 실제 화면 녹화 + 자막 + 앱 안내 문장 음성).

- 화면: record_demo.py가 찍은 rec_*.mp4 (2560x1440) → 1920x1080
- 주행 구간은 안내 음성·급제동 앞뒤만 원래 속도, 나머지는 4배속
- 소리: logcat 'TTS 요청' 문장을 그 시각에 Windows 한국어 음성으로 다시 읽어 넣는다 (에뮬레이터 녹화에는 소리가 없다)
사용: python -I make_video.py <raw 폴더> <출력 mp4>
"""
import json
import pathlib
import subprocess
import sys
import wave

RAW = pathlib.Path(sys.argv[1])
OUTPUT = pathlib.Path(sys.argv[2])
WORK = RAW / "work"
WORK.mkdir(exist_ok=True)
FF = r"C:\Program Files\CPFD\Barracuda\25.0.0\tecplot\bin\ffmpeg.exe"
FONT_B = "C\\:/Windows/Fonts/malgunbd.ttf"
FONT_R = "C\\:/Windows/Fonts/malgun.ttf"
STILL_NEXT = pathlib.Path("D:/Pleos/DejaRoad/captures/01_예습.png")
REPO = "github.com/backban1004/DejaRoad"
FAST = 4.0

tl = json.loads((RAW / "timeline.json").read_text(encoding="utf-8"))
segs = tl["segments"]
ev = {e["what"]: e["t"] for e in tl["events"]}
logs = json.loads((RAW / "session.json").read_text(encoding="utf-8"))  # {"jumps":[{t,m}], "brakes":[t]}


def probe_duration(path):
    r = subprocess.run([FF, "-i", str(path)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    for line in r.stderr.splitlines():
        if "Duration:" in line:
            h, m, s = line.split("Duration:")[1].split(",")[0].strip().split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    raise RuntimeError(path)


for s in segs:
    s["dur"] = probe_duration(RAW / s["file"])

# ---- 소리: 앱이 읽은 문장 → wav
lines = [{"id": f"t{i}", "text": t["text"], "t": t["t"]} for i, t in enumerate(tl["tts"])]
(WORK / "lines.json").write_text(json.dumps([{"id": l["id"], "text": l["text"]} for l in lines], ensure_ascii=False), encoding="utf-8")
subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(RAW.parent / "tts.ps1"), str(WORK / "lines.json"), str(WORK / "tts")], check=True, capture_output=True)
for l in lines:
    with wave.open(str(WORK / "tts" / f"{l['id']}.wav")) as w:
        l["dur"] = w.getnframes() / w.getframerate()

# ---- 원본 구간 (시각은 에뮬레이터 시계, 초)
P, L, R, D, V, E = ev["preview"], ev["listen"], ev["ready"], ev["drive"], ev["review"], ev["end"]
E = min(E, segs[-1]["t"] + segs[-1]["dur"] - 0.2)  # 녹화가 조금 먼저 끝날 수 있다
print("마지막 녹화 끝까지", round(E - V, 1), "초 복습")
PREVIEW_LISTEN = 19.0  # 예습 음성은 앞 19초만 들려주고 넘어간다

pieces = []  # (src_start, src_end, speed)
pieces.append((P, L + 0.6, 1.0))
pieces.append((L + 0.6, L + 0.6 + PREVIEW_LISTEN, 1.0))
pieces.append((R - 0.5, R + 5.0, 1.0))
# 주행: 중요한 구간(안내 음성, 급제동, 도착 직전)은 1배속
important = []
for l in lines:
    if D <= l["t"] <= V:
        important.append((l["t"] - 2.0, l["t"] + l["dur"] + 1.5))
for b in logs["brakes"]:
    important.append((b - 6.0, b + 5.0))
important.append((V - 3.0, V))
important.sort()
merged = []
for a, b in important:
    a, b = max(a, D), min(b, V)
    if merged and a <= merged[-1][1] + 1.0:
        merged[-1] = (merged[-1][0], max(b, merged[-1][1]))
    else:
        merged.append((a, b))
cur = D
for a, b in merged:
    if a > cur + 0.5:
        pieces.append((cur, a, FAST))
    pieces.append((max(a, cur), b, 1.0))
    cur = b
if V > cur + 0.5:
    pieces.append((cur, V, FAST))
pieces.append((V, E, 1.0))


def locate(t):
    """원본 시각 → (조각 번호, 조각 안 시각)"""
    best = None
    for i, s in enumerate(segs):
        off = t - s["t"]
        if -0.05 <= off <= s["dur"] + 0.05:
            best = (i, max(0.0, min(off, s["dur"] - 0.04)))
    if best is None:
        raise RuntimeError(f"녹화 밖 시각 {t}")
    return best


# 조각 경계를 넘는 구간은 나눈다
split = []
for a, b, sp in pieces:
    ia, _ = locate(a)
    ib, _ = locate(b)
    if ia == ib:
        split.append((a, b, sp))
    else:
        edge = segs[ia]["t"] + segs[ia]["dur"]
        split.append((a, edge - 0.04, sp))
        split.append((segs[ib]["t"], b, sp))
pieces = [p for p in split if p[1] - p[0] > 0.3]

# 원본 시각 → 영상 시각
TITLE = 6.0
out_t = TITLE
mapping = []
for a, b, sp in pieces:
    mapping.append((a, b, sp, out_t))
    out_t += (b - a) / sp
BODY_END = out_t
NEXT = 5.0
END = 7.0
TOTAL = BODY_END + NEXT + END


def out_of(t):
    for a, b, sp, o in mapping:
        if a - 0.01 <= t <= b + 0.01:
            return o + (t - a) / sp
    return None


# ---- 자막 (영상 시각 범위, 글)
def j(m):
    hits = [x["t"] for x in logs["jumps"] if abs(x["m"] - m) < 1]
    return hits[0] if hits else None


caps = []
def cap(a, b, text):
    if a is not None and b is not None and b > a:
        caps.append((a, b, text))

cap(out_of(P), out_of(L + 0.6), "① 출발 전 예습 — 경로에서 합류·분기 지점만 골라 노선도처럼 (지난번 급제동한 곳은 채운 네모)")
cap(out_of(L + 0.6), out_of(L + 0.6 + PREVIEW_LISTEN), "'미리 듣기 · 1분' — 지점 순서와 할 일을 소리로 예습")
cap(out_of(R - 0.5), out_of(R + 5.0), "② 다 들으면 출발 대기 안내판 — 첫 지점, 남은 거리, 지난 기록")
drive_caps = [
    (D, j(6000), "③ 주행 중에는 화면 전체가 검은 안내판 — 누를 곳 없이 남은 거리와 할 일만"),
    (j(6000), j(7600), "④ 지난번 급제동한 신갈JC는 3km 앞에서 먼저 — 지난 실패 대신 지금 할 준비를"),
    (j(7600), j(8500), "⑤ 1.5km 앞 안내 — 일반적인 내비 1km 안내보다 먼저"),
    (j(8500), j(10500), "⑥ 끼어드는 차에 놀라 74→41km/h — 브레이크 페달 신호와 속도로 급제동 감지"),
    (j(10500), j(14200), "휴게소에서 잠시 P — 주행 기록은 그대로 이어짐"),
    (j(14200), V, "동수원IC 출구 1.5km 앞 안내"),
]
for a, b, text in drive_caps:
    cap(out_of(a), out_of(b), text)
cap(out_of(V), BODY_END, "⑦ 도착 후 복습 — 지난번(점선)과 오늘(실선) 속도를 같은 자리끼리 겹쳐 봄")
cap(BODY_END, BODY_END + NEXT, "⑧ 다음 번 예습 — 급제동한 곳은 채운 네모와 그날 기록으로 먼저 챙김")
fast_ranges = [(o, o + (b - a) / sp) for a, b, sp, o in mapping if sp > 1]


def esc_file(text, name):
    p = WORK / name
    p.write_text(text, encoding="utf-8")
    return str(p).replace("\\", "/").replace(":", "\\:")


# ---- ffmpeg 필터
inputs = []
for s in segs:
    inputs += ["-i", str(RAW / s["file"])]
still_idx = len(segs)
inputs += ["-loop", "1", "-t", str(NEXT), "-i", str(STILL_NEXT)]
fc = []
vl = []
for k, (a, b, sp, o) in enumerate(mapping):
    i, x = locate(a)
    _, y = locate(b)
    fc.append(f"[{i}:v]trim=start={x:.3f}:end={y:.3f},setpts=(PTS-STARTPTS)/{sp},scale=1920:1080,fps=30,format=yuv420p[p{k}]")
    vl.append(f"[p{k}]")
fc.append(f"[{still_idx}:v]scale=1920:1080,fps=30,format=yuv420p,trim=duration={NEXT},setpts=PTS-STARTPTS[pn]")


def card(label, dur, lines_spec):
    parts = [f"color=c=0x0e0f10:s=1920x1080:r=30:d={dur}"]
    for k, (text, size, y, font, color) in enumerate(lines_spec):
        f = esc_file(text, f"{label}_{k}.txt")
        parts.append(f"drawtext=fontfile='{font}':textfile='{f}':fontsize={size}:fontcolor={color}:x=(w-text_w)/2:y={y}")
    fc.append(",".join(parts) + f",format=yuv420p[{label}]")


card("title", TITLE, [
    ("데자로드  Déjà Road", 96, 330, FONT_B, "white"),
    ("처음 가는 길의 합류·분기점을 출발 전에 미리 듣고,", 44, 490, FONT_R, "0xe8e9eb"),
    ("내가 급제동한 곳을 다음에 먼저 알려 주는 차량용 앱", 44, 556, FONT_R, "0xe8e9eb"),
    ("Pleos Connect 에뮬레이터 실제 화면 녹화 · 판교역 → 광교 갤러리아 시연 경로", 30, 760, FONT_R, "0xa7aab0"),
    ("소리: 앱이 실제로 낸 음성 안내 문장(앱 로그)을 PC 한국어 음성으로 다시 읽음 (에뮬레이터 화면 녹화에는 소리가 담기지 않음)", 24, 820, FONT_R, "0x8a8d93"),
])
card("end", END, [
    ("처음 가는 길도, 와 본 길처럼.", 72, 360, FONT_B, "white"),
    ("NaviHelper · Vehicle · TextToSpeech · FusedLocation SDK  +  AAOS Car API(속도)", 34, 520, FONT_R, "0xe8e9eb"),
    (REPO, 40, 620, FONT_B, "white"),
    ("소스 코드 · release APK · README", 30, 690, FONT_R, "0xa7aab0"),
])
fc.append("[title]" + "".join(vl) + "[pn][end]" + f"concat=n={len(vl) + 3}:v=1:a=0[body]")

# 자막: 아래 검은 독(dock) 자리에 흰 글씨, 4배속 구간은 오른쪽 위에 표시
chain = "[body]"
filters = []
for k, (a, b, text) in enumerate(caps):
    f = esc_file(text, f"cap_{k}.txt")
    filters.append(f"drawbox=x=0:y=975:w=1920:h=105:color=0x0e0f10@0.92:t=fill:enable='between(t,{a:.2f},{b:.2f})'")
    filters.append(f"drawtext=fontfile='{FONT_B}':textfile='{f}':fontsize=38:fontcolor=white:x=(w-text_w)/2:y=1008:enable='between(t,{a:.2f},{b:.2f})'")
for k, (a, b) in enumerate(fast_ranges):
    f = esc_file("4배속", f"fast_{k}.txt")
    filters.append(f"drawtext=fontfile='{FONT_B}':textfile='{f}':fontsize=30:fontcolor=white:box=1:boxcolor=0x0e0f10@0.8:boxborderw=12:x=w-text_w-40:y=96:enable='between(t,{a:.2f},{b:.2f})'")
fc.append(chain + ",".join(filters) + "[vout]")

# 소리: 문장 wav를 영상 시각에 맞춰 한 파일로 직접 섞는다 (겹치지 않으므로 그냥 더한다)
import array
with wave.open(str(WORK / "tts" / f"{lines[0]['id']}.wav")) as w0:
    rate, ch, width = w0.getframerate(), w0.getnchannels(), w0.getsampwidth()
assert width == 2
mix = array.array("h", bytes(int(TOTAL * rate) * ch * 2))
placed = 0
for l in lines:
    o = out_of(l["t"])
    if o is None:
        continue
    with wave.open(str(WORK / "tts" / f"{l['id']}.wav")) as w:
        data = array.array("h", w.readframes(w.getnframes()))
    limit = l["dur"]
    later = [x["t"] for x in lines if x["t"] > l["t"]]
    if later:  # 앱은 새 안내를 시작하면 말하던 것을 끊는다
        limit = min(limit, later[0] - l["t"])
    if L <= l["t"] <= R:  # 예습 음성은 영상에 남긴 길이만, 끝을 줄여 끊는다
        limit = min(limit, PREVIEW_LISTEN - 0.3)
    n = int(limit * rate) * ch
    fade = int(0.4 * rate) * ch
    start = int(o * rate) * ch
    for k in range(min(n, len(data), len(mix) - start)):
        v = data[k]
        if k > n - fade:
            v = int(v * (n - k) / fade)
        mix[start + k] = max(-32768, min(32767, mix[start + k] + v))
    placed += 1
with wave.open(str(WORK / "mix.wav"), "wb") as w:
    w.setnchannels(ch); w.setsampwidth(2); w.setframerate(rate); w.writeframes(mix.tobytes())
a_inputs = ["-i", str(WORK / "mix.wav")]
a_labels = ["x"] * placed
audio_idx = len(segs) + 1

(WORK / "filter.txt").write_text(";\n".join(fc), encoding="utf-8")
cmd = [FF, "-y", *inputs, *a_inputs, "-filter_complex_script", str(WORK / "filter.txt"), "-map", "[vout]", "-map", f"{audio_idx}:a",
       "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", "-t", f"{TOTAL:.2f}", str(OUTPUT)]
print("길이", round(TOTAL, 1), "초, 조각", len(mapping), ", 자막", len(caps), ", 음성", len(a_labels))
r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
if r.returncode != 0:
    print(r.stderr[-3000:])
    sys.exit(1)
print("saved", OUTPUT)
