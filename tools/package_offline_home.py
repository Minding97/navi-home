"""Package the offline home without vendor files or network requirements."""
from pathlib import Path
import hashlib,zipfile
root=Path(__file__).resolve().parents[1];stage=root/'staging'
apk=stage/'NaviDiagnostic-0.6.apk'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(apk)==apk.with_suffix('.sha256').read_text().split()[0]
files=[(stage/'app-menu/SHOW_APPLIST','SHOW_APPLIST'),(apk,'NaviTest/'+apk.name),(stage/'OFFLINE-HOME-KO.txt','NaviTest/OFFLINE-HOME-0.6-KO.txt')]
output=stage/'Navi-offline-home-0.6.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
 for p,n in files:z.write(p,n)
 z.writestr('SHA256SUMS.txt',''.join(sha(p)+'  '+n+'\n' for p,n in files))
with zipfile.ZipFile(output) as z:assert z.testzip() is None
output.with_suffix('.sha256').write_text(sha(output)+'  '+output.name+'\n')
print(output,output.stat().st_size,sha(output))
