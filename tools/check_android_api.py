"""Static SDK API inventory. Newer calls still require manual control-flow review."""
from pathlib import Path
import subprocess,re,json,xml.etree.ElementTree as E
from build_env import sdk_root, java_bin, COMPILE_API
ROOT=Path(__file__).resolve().parents[1]
API=E.parse(sdk_root()/'platforms'/('android-'+COMPILE_API)/'data/api-versions.xml').getroot()
classes={c.get('name'):c for c in API.findall('class')}
javap=str(java_bin()/'javap')
def since(owner,signature,seen=None):
    seen=set() if seen is None else seen
    if owner in seen or owner not in classes:return None
    seen.add(owner);c=classes[owner];base=int(c.get('since','1'))
    for m in c.findall('method'):
        if m.get('name')==signature:return max(base,int(m.get('since',str(base))))
    for parent in list(c.findall('extends'))+list(c.findall('implements')):
        result=since(parent.get('name'),signature,seen)
        if result is not None:return max(base,result)
    return None
result={}
for app,minimum in [('diagnostic',21),('sender',29)]:
    build=ROOT/app/'build/classes';newer=[];checked=set()
    if not build.is_dir() or not any(build.rglob('*.class')):
        raise RuntimeError('Build both apps before API inventory: '+app)
    for f in build.rglob('*.class'):
        name=str(f.relative_to(build).with_suffix('')).replace('/','.')
        output=subprocess.check_output([javap,'-classpath',str(build),'-c','-p',name],text=True)
        for owner,method,desc in re.findall(r'// (?:InterfaceMethod|Method) ([\w/$]+)\.([\w<>"$]+):(\S+)',output):
            signature=method.strip('"')+desc
            level=since(owner,signature)
            if level is not None:
                checked.add((owner,signature))
                if level>minimum:newer.append({'caller':name,'api':level,'method':owner+'.'+signature})
    result[app]={'minimum':minimum,'resolved_platform_calls':len(checked),'newer_calls':newer}
(ROOT/'reports').mkdir(exist_ok=True)
(ROOT/'reports/mirror-api-inventory.json').write_text(json.dumps(result,indent=2))
for app,entry in result.items():
    print(app,entry['resolved_platform_calls'],'resolved calls;',len(entry['newer_calls']),'newer calls to review')
    for call in entry['newer_calls']:print(call)
