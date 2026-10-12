"""Exercise only the two temporary Navi AVDs created for this task."""
from pathlib import Path
import subprocess, time, re, json, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
from build_env import sdk_root
ADB=str(sdk_root()/'platform-tools/adb')
R='emulator-5580';S='emulator-5582'
def adb(device,*args):
    return subprocess.check_output([ADB,'-s',device,*args],timeout=30)
def nodes(device):
    adb(device,'shell','uiautomator','dump','/sdcard/navi-ui.xml')
    return list(ET.fromstring(adb(device,'shell','cat','/sdcard/navi-ui.xml')).iter('node'))
def click(device,text):
    for n in nodes(device):
        if text.lower() in n.get('text','').lower():
            x,y,z,w=map(int,re.findall(r'\d+',n.get('bounds')))
            adb(device,'shell','input','tap',str((x+z)//2),str((y+w)//2));return
    raise RuntimeError('Button not found: '+text)
def field(device,index,value):
    for i,n in enumerate(n for n in nodes(device) if n.get('class')=='android.widget.EditText'):
        if i==index:
            x,y,z,w=map(int,re.findall(r'\d+',n.get('bounds')))
            adb(device,'shell','input','tap',str((x+z)//2),str((y+w)//2))
            adb(device,'shell','input','keycombination','113','29')
            adb(device,'shell','input','text',value)
            adb(device,'shell','input','keyevent','4');return
    raise RuntimeError('Input not found: '+str(index))
if __name__=='__main__':
    for serial,apk,component in [(R,'NaviDiagnostic-0.3.apk','local.navi.diagnostic/.MainActivity'),(S,'NaviSender-0.1.apk','local.navi.sender/.MainActivity')]:
        print(adb(serial,'install','--no-incremental','-r',str(ROOT/'staging'/apk)).decode(),flush=True)
        adb(serial,'shell','input','keyevent','224')
        adb(serial,'shell','input','keyevent','82')
        adb(serial,'shell','settings','put','system','accelerometer_rotation','0')
        adb(serial,'shell','settings','put','system','user_rotation','0')
        adb(serial,'shell','am','start','-n',component)
    click(R,'휴대폰 지도 연결')
    click(R,'연결 대기')
    state='\n'.join(n.get('text','') for n in nodes(R))
    code=re.search('연결 코드: ([A-F0-9-]+)',state).group(1).replace('-','')
    adb(R,'forward','tcp:52741','tcp:52741')
    phone=nodes(S)
    for n in phone:
        if n.get('class')=='android.widget.EditText':print('input id',n.get('resource-id'),flush=True)
    field(S,0,'10.0.2.2')
    field(S,1,code)
    click(S,'화면 공유 시작')
    if any(n.get('text')=='Share one app' for n in nodes(S)):
        click(S,'Share one app');click(S,'Share entire screen')
    click(S,'Share screen')
    time.sleep(2)
    click(S,'지연 시험 화면')
    adb(S,'shell','settings','put','system','user_rotation','1')
    time.sleep(2)
    state='\n'.join(n.get('text','') for n in nodes(R))
    print(state,flush=True)
    if '영상 수신 중' not in state:raise RuntimeError('Stream did not start')
