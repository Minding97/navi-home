"""Additional settings/permission checks on the disposable emulator only."""
from emulator_mirror_smoke import adb,nodes,R,ROOT
import re,time,json
checks=[]
def text():return '\n'.join(n.get('text','') for n in nodes(R))
def click(label):
 ns=nodes(R);ns=[n for n in ns if n.get('clickable')=='true' and (n.get('text')==label)] or [n for n in ns if n.get('clickable')=='true' and label in n.get('text','')]
 assert ns,label
 x,y,z,w=map(int,re.findall(r'\d+',ns[0].get('bounds')));adb(R,'shell','input','tap',str((x+z)//2),str((y+w)//2))
def check(b,name):
 assert b,name
 checks.append(name);print('PASS',name,flush=True)
def home():adb(R,'shell','am','start','-n','local.navi.diagnostic/.MainActivity')
home();click('설정')
ns=nodes(R);sliders=[n for n in ns if n.get('class')=='android.widget.SeekBar'];check(len(sliders)==2,'brightness and volume controls visible')
initial=text();oldbr=re.search(r'홈 밝기 · (\d+)%',initial).group(1);oldvol=re.search(r'미디어 음량 · (\d+) /',initial).group(1)
for index,n in enumerate(sliders):
 x,y,z,w=map(int,re.findall(r'\d+',n.get('bounds')));adb(R,'shell','input','tap',str(x+(z-x)*(2 if (int(oldbr) if index==0 else int(oldvol)*100/15)<60 else 1)//3),str((y+w)//2))
s=text();br=re.search(r'홈 밝기 · (\d+)%',s).group(1);vol=re.search(r'미디어 음량 · (\d+) /',s).group(1)
check(br!=oldbr,'brightness changes from initial value');check(vol!=oldvol,'media volume changes from initial value')
adb(R,'shell','am','force-stop','local.navi.diagnostic');home();click('설정');s=text()
check('홈 밝기 · '+br+'%' in s and '미디어 음량 · '+vol+' /' in s,'brightness preference and system volume survive restart')
adb(R,'shell','pm','revoke','local.navi.diagnostic','android.permission.ACCESS_FINE_LOCATION');home()
denials=[n.get('text') for n in nodes(R) if n.get('text','').lower().startswith('don') and 'allow' in n.get('text','').lower()]
if denials:click(denials[0])
check('GPS 권한 필요' in text(),'location permission denial degrades without crash')
adb(R,'shell','pm','grant','local.navi.diagnostic','android.permission.ACCESS_FINE_LOCATION')
# Explicit non-default startup preference is the meaningful upgrade test.
adb(R,'uninstall','local.navi.diagnostic')
adb(R,'install','--no-incremental',str(ROOT/'staging/NaviDiagnostic-0.5.apk'));home()
click('전원을 켠 뒤 앱 자동 실행 (완전 부팅 시)')
check(any(n.get('class')=='android.widget.CheckBox' and n.get('checked')=='false' for n in nodes(R)),'0.5 boot launch explicitly disabled')
adb(R,'install','--no-incremental','-r',str(ROOT/'staging/NaviDiagnostic-0.6.apk'));adb(R,'shell','pm','grant','local.navi.diagnostic','android.permission.ACCESS_FINE_LOCATION');home();click('설정')
adb(R,'shell','input','swipe','735','400','735','220','700')
check(any('완전 부팅' in n.get('text','') and n.get('checked')=='false' for n in nodes(R)),'0.6 upgrade preserves disabled boot preference')
check('FATAL EXCEPTION' not in adb(R,'logcat','-b','crash','-d').decode(errors='replace'),'no permission/upgrade Java crash')
(ROOT/'reports/offline-settings-checks.json').write_text(json.dumps({'checks':checks,'platform':'isolated API36 emulator'},ensure_ascii=False,indent=2))
print(len(checks),'checks passed')
