"""시연 영상용 녹화: 예습 → 미리 듣기 → 출발 대기 → 주행(demo_drive.py) → 복습을 에뮬레이터 화면 그대로 녹화한다.

- adb screenrecord는 한 번에 3분까지라 170초씩 이어서 찍는다. 조각마다 시작 시각(에뮬레이터 시계)을 남긴다.
- 앱이 말한 문장과 시각은 logcat의 'TTS 요청' 줄에서 따로 뽑는다(영상에 같은 문장을 소리로 넣으려고).
사용: adb forward tcp:8088 tcp:8088 뒤
      python -I record_demo.py <adb> <serial> <출력 폴더>
"""
import json
import pathlib
import subprocess
import sys
import threading
import time

ADB, SERIAL, OUT = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)
APP = "com.dejaroad.app/.MainActivity"
HERE = pathlib.Path(__file__).parent


def adb(*args, **kw):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", **kw)


def now_dev():
    return float(adb("shell", "date +%s.%N").stdout.strip())


def app(*extras):
    adb("shell", "am", "start", "--user", "10", "--activity-single-top", "-n", APP, *extras)


events = []


def mark(name):
    events.append({"t": now_dev(), "what": name})
    print(time.strftime("%H:%M:%S"), name, flush=True)


stop = threading.Event()
segments = []


def recorder():
    i = 0
    while not stop.is_set():
        i += 1
        remote = f"/sdcard/dejaroad_rec_{i}.mp4"
        segments.append({"file": f"rec_{i}.mp4", "remote": remote, "t": now_dev()})
        adb("shell", "screenrecord", "--time-limit", "170", "--bit-rate", "12000000", remote)


adb("logcat", "-c")
app("--ez", "clear", "true", "--ez", "demo_steps", "true", "--ez", "seed_past", "true")
time.sleep(4)
rec = threading.Thread(target=recorder, daemon=True)
rec.start()
time.sleep(1.5)
mark("preview")
time.sleep(7)
mark("listen")
adb("shell", "input", "tap", "2255", "1103")  # 미리 듣기
# 예습이 끝나면 앱이 출발 대기 안내판으로 넘어간다
for _ in range(120):
    time.sleep(1)
    if "TTS 재생 끝" in adb("logcat", "-d", "-s", "VoiceGuide").stdout:
        break
mark("ready")
time.sleep(6)
mark("drive")
subprocess.run([sys.executable, "-I", str(HERE / "demo_drive.py"), ADB, SERIAL], check=False)
mark("review")
time.sleep(14)
mark("end")
stop.set()
# 지금 찍고 있는 조각을 끝낸다 (screenrecord는 SIGINT로 멈추면 파일을 마무리한다)
adb("shell", "pkill", "-INT", "screenrecord")
rec.join(timeout=20)
time.sleep(2)
for s in segments:
    adb("pull", s["remote"], str(OUT / s["file"]))
    adb("shell", "rm", s["remote"])
tts = []
for line in adb("logcat", "-d", "-v", "epoch", "-s", "VoiceGuide").stdout.splitlines():
    if "TTS 요청:" in line:
        t = float(line.split()[0])
        tts.append({"t": t, "text": line.split("TTS 요청:", 1)[1].strip()})
(OUT / "timeline.json").write_text(json.dumps({"segments": segments, "events": events, "tts": tts}, ensure_ascii=False, indent=1), encoding="utf-8")
print("saved", len(segments), "segments,", len(tts), "tts lines", flush=True)
