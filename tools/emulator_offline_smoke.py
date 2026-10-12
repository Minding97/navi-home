"""Isolated emulator only. Synthetic GPS; does not certify real camera or hardware."""
from emulator_mirror_smoke import adb,nodes,click,R,ROOT
import json,re,time,hashlib
checks=[]
def click(device,label):
 page=nodes(device)
 candidates=[n for n in page if n.get('text','')==label and n.get('clickable')=='true']
 if not candidates:candidates=[n for n in page if label in n.get('text','') and n.get('clickable')=='true']
 if not candidates:raise AssertionError('clickable item not found: '+label)
 x,y,z,w=map(int,re.findall(r'\d+',candidates[0].get('bounds')))
 adb(device,'shell','input','tap',str((x+z)//2),str((y+w)//2))
def state():return '\n'.join(n.get('text','') for n in nodes(R))
def check(ok,name):
 assert ok,name
 checks.append(name);print('PASS',name,flush=True)
def home():adb(R,'shell','am','start','-n','local.navi.diagnostic/.MainActivity')
def scroll_to(label):
 if label in state():return
 for _ in range(5):adb(R,'shell','input','swipe','735','150','735','390','700')
 for _ in range(9):
  if label in state():return
  adb(R,'shell','input','swipe','735','400','735','220','700')
 raise AssertionError('not found: '+label)
def screen(name):(ROOT/'reports'/name).write_bytes(adb(R,'exec-out','screencap','-p'))
# Seed an older installation only on this disposable Navi emulator.
try:adb(R,'uninstall','local.navi.diagnostic')
except Exception:pass
adb(R,'install','--no-incremental',str(ROOT/'staging/NaviDiagnostic-0.5.apk'));home()
state()
adb(R,'install','--no-incremental','-r',str(ROOT/'staging/NaviDiagnostic-0.6.apk'))
adb(R,'shell','pm','grant','local.navi.diagnostic','android.permission.ACCESS_FINE_LOCATION')
adb(R,'shell','cmd','location','set-location-enabled','true');home()
if 'Got it' in state():click(R,'Got it')
adb(R,'shell','svc','wifi','disable');adb(R,'shell','svc','data','disable')
check('NAVI  /  HOME' in state(),'home launches without Wi-Fi or mobile data')
screen('offline-home-800x480.png')
click(R,'설정');scroll_to('완전 부팅')
check(any('완전 부팅' in n.get('text','') and n.get('checked')=='true' for n in nodes(R)),'boot preference retained from 0.5')
click(R,'GPS 수신 ·')
click(R,'홈');s=state();check('GPS 수신 일시 중지' in s and '—' in s,'paused GPS shows unavailable speed')
screen('offline-home-no-gps.png')
click(R,'설정');scroll_to('GPS 수신 ·');click(R,'GPS 수신 ·');click(R,'홈')
# Emulator reports velocity in knots (19.4384 ~= 36 km/h).
for i in range(6):
 adb(R,'emu','geo','fix','127',str(37+i*.00009),'50','8','19.4384');time.sleep(1.1)
s=state();check('36' in s and 'GPS 수신 중' in s,'fresh provider speed displays 36 km/h')
km=re.search(r'(\d+\.\d+) km',s);check(km is not None and float(km.group(1))>0,'moving fixes accumulate GPS distance')
screen('offline-home-gps-simulated.png')
# A different foreground app stands in for an OEM camera Activity; real camera still needs a car test.
adb(R,'shell','am','start','-a','android.settings.SETTINGS')
for i in range(6,9):
 adb(R,'emu','geo','fix','127',str(37+i*.00009),'50','8','19.4384');time.sleep(1.1)
check('NAVI  /  HOME' not in state(),'home does not pull itself over another app')
home();s=state();km2=re.search(r'(\d+\.\d+) km',s)
check(km2 and float(km2.group(1))>float(km.group(1)),'GPS session continues during app switch')
# Disable the provider to check immediate clearing, then stop updates at source to test stale expiry.
adb(R,'shell','cmd','location','set-location-enabled','false');time.sleep(.4)
if 'No location access' in state():click(R,'Close')
s=state();check('기기 위치 설정 꺼짐' in s and '—' in s,'provider-off hides old speed')
adb(R,'shell','cmd','location','set-location-enabled','true')
click(R,'＋ 앱 추가');click(R,'Clock');check('Clock' in state(),'favorite assigned')
click(R,'Clock');check('NAVI  /  HOME' not in state(),'favorite launches installed app')
home();click(R,'앱 목록');check('Clock' in state(),'installed app list available');screen('offline-apps-800x480.png')
click(R,'홈');click(R,'설정');screen('offline-settings-800x480.png')
click(R,'사용 안내');check('Navi 홈 0.6' in state(),'offline guide available inside app');click(R,'닫기')
scroll_to('기기 상태');click(R,'기기 상태');check('연결된 USB 장치' in state(),'USB identity and storage diagnostic available');click(R,'확인')
scroll_to('파인 홈');click(R,'파인 홈');check('제조사 앱을 찾지 못했습니다' in state(),'missing vendor launcher handled');click(R,'확인')
click(R,'홈');click(R,'설정');scroll_to('세션 초기화');click(R,'세션 초기화');click(R,'초기화');click(R,'홈')
check('0.00 km' in state(),'explicit reset clears aggregate')
# Service persists no trip data on disk. Force-stop is a new session, favorites persist.
adb(R,'shell','am','force-stop','local.navi.diagnostic');home()
s=state();check('Clock' in s and '0.00 km' in s,'favorites retained; trip discarded on process restart')
click(R,'설정');scroll_to('세션 종료');click(R,'세션 종료');click(R,'종료');time.sleep(.4)
service=adb(R,'shell','dumpsys','activity','services','local.navi.diagnostic').decode(errors='replace')
check('ServiceRecord' not in service,'exit stops GPS service')
home();check('NAVI  /  HOME' in state(),'new session can restart')
log=adb(R,'logcat','-b','crash','-d').decode(errors='replace');check('FATAL EXCEPTION' not in log,'no Java crash')
(ROOT/'reports/offline-emulator-checks.json').write_text(json.dumps({'checks':checks,'apk_sha256':hashlib.sha256((ROOT/'staging/NaviDiagnostic-0.6.apk').read_bytes()).hexdigest(),'platform':'API 36 / isolated 800x480 emulator','limits':['GPS inputs synthetic','Android 5 runtime, real camera coexistence and real driving distance not verified']},ensure_ascii=False,indent=2))
print(len(checks),'checks passed')
