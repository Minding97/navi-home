from pathlib import Path
import datetime, hashlib, json, os, shutil, time, zipfile
src=Path('/Volumes/FD_UMS_SD')
out=Path('backups')/datetime.datetime.now().strftime('FD_UMS_SD-%Y%m%d-%H%M%S')
out.mkdir()
skip={'.Spotlight-V100','.fseventsd','.Trashes'}
files=[]
dirs=[]
for root, subdirs, names in os.walk(src):
    if Path(root)==src:
        subdirs[:]=sorted(x for x in subdirs if x not in skip)
    for d in subdirs: dirs.append((Path(root)/d).relative_to(src).as_posix())
    for n in names:
        p=Path(root)/n
        files.append((p,p.stat()))
files.sort(key=lambda x:(x[0].relative_to(src).parts[0]=='ATLAN',str(x[0])))
total=sum(s.st_size for p,s in files)
if shutil.disk_usage(out).free < total+2*1024**3: raise RuntimeError('Insufficient free space')
print(json.dumps({'backup_dir':str(out.resolve()),'files':len(files),'bytes':total,'excluded_os_metadata':sorted(skip)},ensure_ascii=False),flush=True)
(out/'inventory.json').write_text(json.dumps({'source':str(src),'directories':dirs,'files':[{'path':p.relative_to(src).as_posix(),'size':s.st_size,'mtime':s.st_mtime} for p,s in files],'excluded_os_metadata':sorted(skip)},ensure_ascii=False,indent=2))
manifest=[]; done=0; last=time.monotonic()
partial=out/'sd-card.zip.partial'
with zipfile.ZipFile(partial,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
    for d in dirs: z.writestr(d+'/',b'')
    for p,st in files:
        rel=p.relative_to(src).as_posix(); h=hashlib.sha256(); count=0
        info=zipfile.ZipInfo.from_file(p,arcname=rel); info.compress_type=zipfile.ZIP_DEFLATED; info._compresslevel=1
        with p.open('rb') as f,z.open(info,'w',force_zip64=True) as dst:
            while chunk:=f.read(4*1024**2):
                h.update(chunk); dst.write(chunk); count+=len(chunk);done+=len(chunk)
                if time.monotonic()-last>25:
                    print(json.dumps({'stage':'copy','percent':round(done/total*100,1),'bytes':done}),flush=True);last=time.monotonic()
        current=p.stat()
        if count!=st.st_size or current.st_size!=st.st_size or current.st_mtime!=st.st_mtime: raise RuntimeError('Source changed: '+rel)
        manifest.append({'path':rel,'size':count,'sha256':h.hexdigest()})
(out/'sha256.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
print('Verifying every archived file against source-read SHA-256 hashes',flush=True)
verified=0;last=time.monotonic()
with zipfile.ZipFile(partial) as z:
    for item in manifest:
        h=hashlib.sha256()
        with z.open(item['path']) as f:
            while chunk:=f.read(4*1024**2): h.update(chunk);verified+=len(chunk)
        if h.hexdigest()!=item['sha256']: raise RuntimeError('Verification failed: '+item['path'])
        if time.monotonic()-last>25:
            print(json.dumps({'stage':'verify','percent':round(verified/total*100,1)}),flush=True);last=time.monotonic()
final=out/'sd-card.zip';partial.rename(final)
result={'status':'verified','file_count':len(manifest),'source_bytes':total,'archive_bytes':final.stat().st_size,'archive':str(final.resolve()),'note':'File-level backup, not raw disk image or internal eMMC backup. Excludes macOS volatile indexing/trash directories only.'}
(out/'verification.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result),flush=True)
