"""600-second sample of the running temporary receiver, not a hardware certification."""
from pathlib import Path
import subprocess, time, re, json, xml.etree.ElementTree as ET, hashlib
ROOT=Path(__file__).resolve().parents[1]
from build_env import sdk_root
ADB=str(sdk_root()/'platform-tools/adb');DEVICE='emulator-5580'
def adb(*a):return subprocess.check_output([ADB,'-s',DEVICE,*a],timeout=20)
def sample():
    adb('shell','uiautomator','dump','/sdcard/navi-ui.xml')
    values=[n.get('text','') for n in ET.fromstring(adb('shell','cat','/sdcard/navi-ui.xml')).iter('node')]
    status=next((v for v in values if 'fps' in v),'')
    match=re.search(r'([0-9.]+) fps',status)
    return {'elapsed_seconds':round(time.monotonic()-start,1),'fps':float(match.group(1)) if match else None,'state':status}
start=time.monotonic();rows=[]
mem_start=adb('shell','dumpsys','meminfo','local.navi.diagnostic').decode()
for index in range(21):
    remaining=start+index*30-time.monotonic()
    if remaining>0:time.sleep(remaining)
    item=sample();rows.append(item);print(json.dumps(item,ensure_ascii=False),flush=True)
mem_end=adb('shell','dumpsys','meminfo','local.navi.diagnostic').decode()
result={'environment':'Two isolated Android 16/API 36 arm64 emulators, receiver 800x480, sender test clock; ADB TCP forwarding on Mac',
        'receiver_sha256':hashlib.sha256((ROOT/'staging/NaviDiagnostic-0.3.apk').read_bytes()).hexdigest(),
        'sender_sha256':hashlib.sha256((ROOT/'staging/NaviSender-0.1.apk').read_bytes()).hexdigest(),
        'duration_seconds':round(time.monotonic()-start,1),'samples':rows,
        'all_samples_streaming':all('영상 수신 중' in r['state'] for r in rows),
        'fps_min':min(r['fps'] for r in rows if r['fps'] is not None),
        'fps_mean':round(sum(r['fps'] or 0 for r in rows)/len(rows),2),
        'hardware_navigation_camera_latency_verified':False}
(ROOT/'reports/mirror-soak-test.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
(ROOT/'reports/mirror-memory-start.txt').write_text(mem_start)
(ROOT/'reports/mirror-memory-end.txt').write_text(mem_end)
print(json.dumps({k:v for k,v in result.items() if k!='samples'},ensure_ascii=False),flush=True)
assert result['all_samples_streaming'] and result['fps_min']>=10
