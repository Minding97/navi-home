from pathlib import Path
import json, re, struct, subprocess, zipfile
out=Path('analysis/metadata');out.mkdir(exist_ok=True)
from build_env import sdk_root, BUILD_TOOLS_VERSION
aapt=str(sdk_root()/'build-tools'/BUILD_TOOLS_VERSION/'aapt')
for apk in sorted(Path('analysis/apks').glob('*.apk')):
 for what,args in [('badging',['dump','badging']),('manifest',['dump','xmltree'])]:
  cmd=[aapt,*args,str(apk)]+(['AndroidManifest.xml'] if what=='manifest' else [])
  r=subprocess.run(cmd,capture_output=True,text=True,check=True)
  (out/(apk.stem+'-'+what+'.txt')).write_text(r.stdout)
 with zipfile.ZipFile(apk) as z:
  b=z.read('classes.dex');n,offset=struct.unpack_from('<II',b,56);strings=[]
  for i in range(n):
   p=struct.unpack_from('<I',b,offset+i*4)[0]
   while b[p]&128:p+=1
   p+=1;end=b.index(0,p);s=b[p:end].decode('utf-8','replace');strings.append(s)
  (out/(apk.stem+'-strings.json')).write_text(json.dumps(strings,ensure_ascii=False,indent=2))
  selected=[s for s in strings if re.search(r'(?i)adb|debugging|5555|\.apk|installpackage|packageinstaller|unknown.sources|requestLocationUpdates|LocationManager|GPS_PROVIDER|/dev/tty|/system/bin|/mnt/|/sdcard/|usb.config|startAdb',s)]
  print(apk.stem,json.dumps(selected,ensure_ascii=False))
