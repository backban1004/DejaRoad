"""데자로드 시연 주행: Pleos Virtual Console(127.0.0.1:8088)로 속도·브레이크를 보내고, 앱에 위치 이동(jump) 명령을 보낸다.

장면
1. 출발해서 70 km/h까지
2. 신갈JC 3.2km 전으로 이동 → "3킬로미터 앞 신갈JC, 지난 화요일 급히 줄였던 곳" → 1.5km 전 안내
3. 분기점 앞에서 74 → 41 km/h 급제동 (이번에도 어려웠던 곳)
4. 휴게소처럼 중간에 P로 세웠다가 다시 출발 (주행 화면이 그대로인지)
5. 동수원IC 출구·연속 회전을 무난히 지나고 정차, P → 복습 화면

사용: adb forward tcp:8088 tcp:8088 뒤
      python -I demo_drive.py <adb> <serial> [캡처 폴더]
캡처 폴더를 주면 장면마다 화면을 PNG로 남긴다.
"""
import pathlib
import subprocess
import sys
import time
import urllib.request
import uuid

BASE = "http://127.0.0.1:8088"
CLIENT = "dejaroad-demo-" + uuid.uuid4().hex
APP = "com.dejaroad.app/.MainActivity"


def send(method, path):
    req = urllib.request.Request(BASE + path, method=method, data=b"" if method in ("POST", "PUT") else None)
    try:
        urllib.request.urlopen(req, timeout=3).read()
    except Exception as e:  # 콘솔 응답 오류는 다음 주기에 다시 보낸다
        print("console error", path, e, flush=True)


def heartbeat(closing=False):
    send("POST", f"/browser/session?client={CLIENT}" + ("&closing=1" if closing else ""))


def controls(speed, brake):
    # 콘솔은 소수 속도를 거절한다
    send("PUT", f"/vehicle/controls?speed={int(round(speed))}&accelerator={0 if brake else 20}&brake={brake}&steering=0")


def app(adb, serial, *extras):
    subprocess.run([adb, "-s", serial, "shell", "am", "start", "--user", "10", "--activity-single-top", "-n", APP, *extras], capture_output=True)


def shot(adb, serial, out, name):
    if out is None:
        return
    png = subprocess.run([adb, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True).stdout
    (out / f"{name}.png").write_bytes(png)
    print(time.strftime("%H:%M:%S"), "shot", name, flush=True)


def ramp(seconds, v0, v1, brake=0):
    start = time.time()
    while (t := time.time() - start) < seconds:
        heartbeat()
        controls(v0 + (v1 - v0) * t / seconds, brake)
        time.sleep(0.2)


def main():
    adb, serial = sys.argv[1], sys.argv[2]
    out = pathlib.Path(sys.argv[3]) if len(sys.argv) > 3 else None
    if out:
        out.mkdir(parents=True, exist_ok=True)
    heartbeat()
    send("PUT", "/vehicle/gear?gear=Drive")
    try:
        ramp(6, 0, 70)
        shot(adb, serial, out, "10_주행_시작")
        # 신갈JC(9.2km) 3.2km 전으로: 3km 전에 지난 기록 안내
        app(adb, serial, "--ef", "jump", "6000")
        ramp(4, 70, 74)
        ramp(10, 74, 74)
        shot(adb, serial, out, "11_주행_3km전_지난기록")
        # 1.6km 전으로: 1.5km 전 안내
        app(adb, serial, "--ef", "jump", "7600")
        ramp(9, 74, 74)
        shot(adb, serial, out, "12_주행_1.5km전")
        # 분기점 700m 전으로 가서, 끼어드는 차에 놀라 세게 밟는다
        app(adb, serial, "--ef", "jump", "8500")
        ramp(6, 74, 74)
        ramp(3, 74, 41, brake=60)
        ramp(5, 41, 60)
        ramp(6, 60, 70)
        # 휴게소(분기점을 지나 10.5km): 천천히 서서 P로 두었다가 다시 출발 (주행 기록은 이어진다)
        app(adb, serial, "--ef", "jump", "10500")
        ramp(14, 70, 0, brake=10)
        controls(0, 0)
        send("PUT", "/vehicle/gear?gear=Park")
        for _ in range(12):
            heartbeat()
            time.sleep(0.5)
        shot(adb, serial, out, "15_중간정차_휴게소")
        send("PUT", "/vehicle/gear?gear=Drive")
        ramp(6, 0, 70)
        # 동수원IC 출구(15.8km) 1.6km 전으로
        app(adb, serial, "--ef", "jump", "14200")
        ramp(14, 70, 70)
        shot(adb, serial, out, "13_주행_출구_1.5km전")
        # 출구와 연속 회전을 지나 도착 (16.0km + 150m 넘게)
        app(adb, serial, "--ef", "jump", "16400")
        ramp(5, 50, 0)
    finally:
        controls(0, 0)
        send("PUT", "/vehicle/gear?gear=Park")
        for _ in range(8):
            heartbeat()
            time.sleep(0.5)
        shot(adb, serial, out, "14_도착_복습")
        heartbeat(closing=True)
        print("done", flush=True)


if __name__ == "__main__":
    main()
