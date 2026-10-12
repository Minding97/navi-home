"""Checks our launcher/notebook and an upstream TCP attempt, not real Android Auto projection."""
from pathlib import Path
import json,time,socket,threading,hashlib,re,subprocess
from emulator_mirror_smoke import adb,nodes,click,field,R,ROOT
checks=[]
def state():return '\n'.join(n.get('text','') for n in nodes(R))
def check(value,label):
    assert value,label
    checks.append(label);print('PASS',label,flush=True)
def start():adb(R,'shell','am','start','-n','local.navi.diagnostic/.MainActivity')
def permissions():
    # Only the throwaway emulator's standard permission dialogs; no overlay/settings grants.
    for _ in range(8):
        page=nodes(R)
        if not any('permissioncontroller' in n.get('package','') for n in page):return
        button=next((n for n in page if n.get('class')=='android.widget.Button' and n.get('text') in ['While using the app','Allow']),None)
        if button is None:raise RuntimeError('Unexpected permission screen')
        x,y,z,w=map(int,re.findall(r'\d+',button.get('bounds')))
        adb(R,'shell','input','tap',str((x+z)//2),str((y+w)//2));time.sleep(.3)
    raise RuntimeError('Permission prompts did not end')
def fake_server():
    server=socket.socket();server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);server.bind(('127.0.0.1',5277));server.listen(1);server.settimeout(20)
    connected=threading.Event()
    def accept():
        try:
            client,_=server.accept();connected.set();client.settimeout(2)
            try:client.recv(128)
            except OSError:pass
            client.close()
        except OSError:pass
        finally:server.close()
    thread=threading.Thread(target=accept,daemon=True);thread.start()
    return connected,thread

adb(R,'shell','input','keyevent','224');adb(R,'shell','input','keyevent','82')
try:adb(R,'uninstall','local.navi.diagnostic')
except subprocess.CalledProcessError:pass
for version in ['0.4','0.5']:
    adb(R,'install','--no-incremental','-r',str(ROOT/'staging'/f'NaviDiagnostic-{version}.apk'));start()
    check('Navi 진단 · '+version in state(),'launch '+version)
    check(any(n.get('class')=='android.widget.CheckBox' and n.get('checked')=='true' for n in nodes(R)),'boot preference '+version)
click(R,'오늘 시험')
check('오늘 저녁 iQ 3 시험' in state(),'offline guide bundled in APK')
click(R,'② AA 연결')
check('Open Headunit 3.3.0 설치됨' in state(),'upstream package recognized')
(ROOT/'reports/evening-connect-screen.png').write_bytes(adb(R,'exec-out','screencap','-p'))
field(R,0,'999.1.1.1');click(R,'AA 연결 요청')
check('로컬 IPv4' in state(),'invalid address rejected before launching')
click(R,'확인');field(R,0,'10.0.2.2')
event,thread=fake_server();click(R,'서버 확인');check(event.wait(5),'probe reaches selected host only');thread.join(5)
check('TCP 5277 응답 있음' in state(),'TCP response labelled without claiming AA success')
click(R,'서버 확인');time.sleep(1);check('서버 응답 없음' in state(),'unavailable server reported')
click(R,'③ 결과 기록');click(R,'앱 업데이트 / 부팅 자동 실행');click(R,'통과')
check('앱 업데이트 / 부팅 자동 실행 · 통과' in state(),'manual test result saved')
adb(R,'shell','am','force-stop','local.navi.diagnostic');start();click(R,'오늘 시험');click(R,'③ 결과 기록')
check('앱 업데이트 / 부팅 자동 실행 · 통과' in state(),'result survives process restart')
click(R,'② AA 연결');check('10.0.2.2' in state(),'phone address survives restart')
click(R,'Open Headunit');time.sleep(1)
permissions()
if 'Got it' in state():click(R,'Got it')
check(any(n.get('package')=='com.andrerinas.headunitrevived' for n in nodes(R)),'Open Headunit launches from our app')
start();click(R,'오늘 시험');click(R,'② AA 연결')
event,thread=fake_server();click(R,'AA 연결 요청')
attempt=event.wait(8);thread.join(3)
check(attempt,'exported Open Headunit MainActivity makes TCP connection attempt')
start();click(R,'오늘 시험');click(R,'② AA 연결')
adb(R,'shell','input','swipe','650','408','650','170','250');click(R,'AA 종료 요청');time.sleep(1)
start();click(R,'오늘 시험');click(R,'③ 결과 기록')
check('앱 업데이트 / 부팅 자동 실행 · 통과' in state(),'notebook still available after third-party exit')
log=adb(R,'logcat','-b','crash','-d').decode(errors='replace')
check('FATAL EXCEPTION' not in log,'no Java crash in test session')
data={'platform':'isolated Android API 36 emulator; 800x480','checks':checks,
    'receiver_sha256':hashlib.sha256((ROOT/'staging/NaviDiagnostic-0.5.apk').read_bytes()).hexdigest(),
    'openhu_sha256':hashlib.sha256((ROOT/'staging/OpenHeadunit-3.3.0.apk').read_bytes()).hexdigest(),
    'limitations':['A TCP accept stub is not a phone or Android Auto server','Real projection, USB, Wi-Fi dongle, Android 5 and camera coexistence remain untested']}
(ROOT/'reports/evening-emulator-checks.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
