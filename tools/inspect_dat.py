from pathlib import Path
import json,zipfile
root=Path('/Volumes/FD_UMS_SD'); out=Path('analysis/apks');report=[]
for rel in ['App/TurboGPS.dat','App/RearCam.dat','System/HomeScreen.dat','System/Setting.dat','System/USBUpdate.dat','System/FAL.dat','System/FineUpdate.dat']:
 b=(root/rel).read_bytes();key=b[:4];payload=b[12:];dec=bytearray(len(payload))
 for j,k in enumerate(key): dec[j::4]=payload[j::4].translate(bytes(x^k for x in range(256)))
 if len(payload)%4: dec[-(len(payload)%4):]=payload[-(len(payload)%4):]
 target=out/(Path(rel).stem+'.apk');target.write_bytes(dec)
 good=False;entries=[]
 if zipfile.is_zipfile(target):
  with zipfile.ZipFile(target) as z:
   bad=z.testzip();good=bad is None;entries=z.namelist()
 print(json.dumps({'source':rel,'decoded':str(target),'zip_crc_ok':good,'head':bytes(dec[:4]).hex(),'entries':len(entries)},ensure_ascii=False),flush=True)
 report.append({'source':rel,'output':str(target),'xor_header_bytes':key.hex(),'payload_offset':12,'zip_crc_ok':good,'entries':entries})
Path('analysis/dat-format.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
