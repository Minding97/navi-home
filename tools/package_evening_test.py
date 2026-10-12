"""Package verified APKs and offline instructions for copying onto the existing card."""
from pathlib import Path
import hashlib,json,zipfile
root=Path(__file__).resolve().parents[1];stage=root/'staging'
apks=['NaviDiagnostic-0.5.apk','OpenHeadunit-3.3.0.apk','NaviSender-0.1.apk']
for name in apks:
    path=stage/name
    assert hashlib.sha256(path.read_bytes()).hexdigest()==path.with_suffix('.sha256').read_text().split()[0],name
items={name:stage/name for name in apks+['OPENHEADUNIT-NOTICES.txt','OPENHEADUNIT-LICENSE.txt','EVENING-RESULT-TEMPLATE.txt']}
items['EVENING-TEST-0.5-KO.txt']=stage/'EVENING-TEST-KO.txt'
archive=stage/'Navi-evening-test-0.5.zip'
checksums=''.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name+'\n' for name,path in items.items())
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    z.write(stage/'app-menu/SHOW_APPLIST','SHOW_APPLIST')
    for name,path in items.items():z.write(path,'NaviTest/'+name)
    z.writestr('NaviTest/SHA256SUMS-0.5.txt',checksums)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
sha=hashlib.sha256(archive.read_bytes()).hexdigest();archive.with_suffix('.sha256').write_text(sha+'  '+archive.name+'\n')
print(json.dumps({'file':str(archive),'size':archive.stat().st_size,'sha256':sha,'files':len(items)+2},indent=2))
