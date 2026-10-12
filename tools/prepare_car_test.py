from pathlib import Path
import hashlib,json,os,subprocess,plistlib,datetime,argparse
parser=argparse.ArgumentParser()
parser.add_argument('--version', choices=['0.1','0.2','0.3','0.4','0.5','0.6'], default='0.6')
version=parser.parse_args().version
base=Path(__file__).resolve().parents[1]
card=Path('/Volumes/FD_UMS_SD')
backup=base/'backups/FD_UMS_SD-20260915-220836'
verified=json.loads((backup/'verification.json').read_text())
assert verified['status']=='verified' and (backup/'sd-card.zip').is_file(), 'Verified backup required'
info=plistlib.loads(subprocess.check_output(['diskutil','info','-plist',str(card)]))
assert info.get('MountPoint')==str(card) and info.get('VolumeName')=='FD_UMS_SD', 'Unexpected volume'
assert 15_000_000_000 < info.get('TotalSize',0) < 17_000_000_000, 'Unexpected size'
manifest={x['path']:x['sha256'] for x in json.loads((backup/'sha256.json').read_text())}
sha=lambda b:hashlib.sha256(b).hexdigest()
for rel in ['System/HomeScreen.dat','Finesn.bin']:
 assert sha((card/rel).read_bytes())==manifest[rel], 'Card identity or home app differs from backup'
apk=base/'staging'/('NaviDiagnostic-'+version+'.apk')
assert sha(apk.read_bytes())==apk.with_suffix('.sha256').read_text().split()[0], 'APK changed'
guide={'0.1':'CAR-TEST-KO.txt','0.2':'BOOT-TEST-KO.txt','0.3':'MIRROR-TEST-KO.txt','0.4':'WIFI-TEST-KO.txt','0.5':'EVENING-TEST-KO.txt','0.6':'OFFLINE-HOME-KO.txt'}[version]
card_guide={'0.1':'README-KO.txt','0.2':'BOOT-TEST-0.2-KO.txt','0.3':'MIRROR-TEST-0.3-KO.txt','0.4':'WIFI-TEST-0.4-KO.txt','0.5':'EVENING-TEST-0.5-KO.txt','0.6':'OFFLINE-HOME-0.6-KO.txt'}[version]
items=[(base/'staging/app-menu/SHOW_APPLIST',card/'SHOW_APPLIST'),(apk,card/'NaviTest'/apk.name),(base/'staging'/guide,card/'NaviTest'/card_guide)]
if version=='0.3':
 sender=base/'staging/NaviSender-0.1.apk'
 assert sha(sender.read_bytes())==sender.with_suffix('.sha256').read_text().split()[0], 'Sender APK changed'
 items.append((sender,card/'NaviTest'/sender.name))
if version=='0.5':
 for name in ['OpenHeadunit-3.3.0.apk','NaviSender-0.1.apk']:
  src=base/'staging'/name
  assert sha(src.read_bytes())==src.with_suffix('.sha256').read_text().split()[0], 'APK changed: '+name
  items.append((src,card/'NaviTest'/name))
 for name in ['OPENHEADUNIT-NOTICES.txt','OPENHEADUNIT-LICENSE.txt','EVENING-RESULT-TEMPLATE.txt']:
  items.append((base/'staging'/name,card/'NaviTest'/name))
for src,dst in items:
 assert not dst.is_symlink()
 if dst.exists(): assert dst.read_bytes()==src.read_bytes(), 'Refusing to overwrite different file: '+str(dst)
report=[]
for src,dst in items:
 data=src.read_bytes();dst.parent.mkdir(exist_ok=True)
 action='already-identical'
 if not dst.exists():
  with dst.open('xb') as f:f.write(data);f.flush();os.fsync(f.fileno())
  action='created'
 assert sha(dst.read_bytes())==sha(data), 'Copy verification failed'
 report.append({'path':str(dst),'size':len(data),'sha256':sha(data),'action':action})
result={'time':datetime.datetime.now().isoformat(),'status':'copied-and-verified','card':str(card),'files':report,'existing_vendor_files_modified':False,'device_installation':'not performed','device_execution':'not tested'}
(base/'reports'/('car-test-deployment-'+version+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps(result,ensure_ascii=False,indent=2))
