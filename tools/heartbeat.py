"""Pleos Virtual Console(127.0.0.1:8088) 세션을 켜 둔다. 세션이 없으면 가상 차량(기어·속도·내비 상태)이 멈춘다.
사용: python -I heartbeat.py [초]
"""
import sys
import time
import urllib.request
import uuid

CLIENT = "dejaroad-" + uuid.uuid4().hex
end = time.time() + float(sys.argv[1] if len(sys.argv) > 1 else 600)
while time.time() < end:
    try:
        urllib.request.urlopen(urllib.request.Request(f"http://127.0.0.1:8088/browser/session?client={CLIENT}", method="POST", data=b""), timeout=3).read()
    except Exception as e:
        print("heartbeat error", e, flush=True)
    time.sleep(1)
