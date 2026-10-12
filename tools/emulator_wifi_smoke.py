"""UI/update checks on the isolated API 36 AVD; this does NOT test Android 5 Wi-Fi control."""
from pathlib import Path
import json,time,subprocess
from emulator_mirror_smoke import adb,nodes,click,R,ROOT
for _ in range(90):
    try:
        if adb(R,'shell','getprop','sys.boot_completed').strip()==b'1':break
    except subprocess.CalledProcessError:pass
    time.sleep(1)
else:raise RuntimeError('Temporary AVD did not boot')
checks=[]
try:adb(R,'uninstall','local.navi.diagnostic')
except subprocess.CalledProcessError:pass
def state():return '\n'.join(n.get('text','') for n in nodes(R))
def check(ok,label):
    assert ok,label
    checks.append(label);print('PASS',label,flush=True)
for version in ['0.3','0.4']:
    adb(R,'install','--no-incremental','-r',str(ROOT/'staging'/f'NaviDiagnostic-{version}.apk'))
    adb(R,'shell','input','keyevent','224');adb(R,'shell','input','keyevent','82')
    adb(R,'shell','am','start','-n','local.navi.diagnostic/.MainActivity')
    time.sleep(1)
    check(f'Navi 진단 · {version}' in state(),'launch '+version)
    boot=[n for n in nodes(R) if n.get('class')=='android.widget.CheckBox']
    check(any(n.get('checked')=='true' for n in boot),'boot preference preserved '+version)
click(R,'Wi-Fi 연결 관리')
check('기억한 Wi-Fi: 아직 없음' in state(),'management opens without saved target')
buttons=nodes(R)
check(any(n.get('text')=='다시 연결' and n.get('enabled')=='false' for n in buttons),'modern Android control disabled honestly')
click(R,'Wi-Fi 설정 열기');time.sleep(1)
check(any(n.get('package')=='com.android.settings' for n in nodes(R)),'system Wi-Fi settings opens')
adb(R,'shell','input','keyevent','4');time.sleep(1)
check('Wi-Fi 연결 관리' in state(),'returns from settings')
click(R,'파인 연결 설정')
check('파인 연결 설정을 열 수 없습니다' in state(),'missing vendor app fallback')
click(R,'확인')
time.sleep(1)
(ROOT/'reports/wifi-management-emulator.png').write_bytes(adb(R,'exec-out','screencap','-p'))
click(R,'돌아가기');click(R,'휴대폰 지도 연결');click(R,'연결 대기')
check('연결 코드:' in state(),'receiver listener still starts')
click(R,'Wi-Fi 관리')
check('Wi-Fi 연결 관리' in state(),'mirror screen opens management')
click(R,'돌아가기')
check('연결 코드:' in state(),'receiver session retained after settings')
log=adb(R,'logcat','-b','crash','-d').decode(errors='replace')
check('FATAL EXCEPTION' not in log,'no Java crash recorded')
report={'platform':'Android API 36 emulator, 800x480','checks':checks,
        'limitations':['Android 5 control path not executed','real dongle/hotspot and vendor settings entry not verified']}
(ROOT/'reports/wifi-emulator-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
