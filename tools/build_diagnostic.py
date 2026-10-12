"""Build framework-only Android APKs using the existing local development identity."""
from pathlib import Path
import argparse, subprocess, zipfile, shutil, hashlib, xml.etree.ElementTree as ET, os
from build_env import sdk_root, java_bin, BUILD_TOOLS_VERSION, COMPILE_API
ROOT=Path(__file__).resolve().parents[1]
SDK=sdk_root()
BT=SDK/'build-tools'/BUILD_TOOLS_VERSION; PLATFORM=SDK/'platforms'/('android-'+COMPILE_API)/'android.jar'
JDK=java_bin()
KEY=Path(os.environ.get('NAVI_KEYSTORE',str(ROOT/'diagnostic/keys/local-development.p12'))).expanduser()
def validate_environment():
    if not KEY.is_file():
        raise RuntimeError('Original signing key required. Set NAVI_KEYSTORE; do not replace an existing app identity.')
    if not os.environ.get('NAVI_KEYSTORE_PASSWORD'):
        raise RuntimeError('Set NAVI_KEYSTORE_PASSWORD in the environment; signing passwords are not stored in source.')
    if not os.environ.get('NAVI_KEY_PASSWORD'):
        os.environ['NAVI_KEY_PASSWORD']=os.environ['NAVI_KEYSTORE_PASSWORD']
    for path in [JDK/'javac',JDK/'java',PLATFORM,BT/'aapt',BT/'zipalign',BT/'lib/d8.jar',BT/'lib/apksigner.jar']:
        if not path.is_file(): raise RuntimeError('Missing build dependency: '+str(path))
def run(args):
    subprocess.run([str(a) for a in args],check=True,cwd=ROOT)
def build(directory,stem):
    validate_environment()
    app=ROOT/directory; dest=app/'build'; dest.mkdir(exist_ok=True)
    if directory=='diagnostic':
        raw=app/'res/raw';raw.mkdir(exist_ok=True)
        shutil.copyfile(ROOT/'staging/EVENING-TEST-KO.txt',raw/'evening_guide.txt')
        shutil.copyfile(ROOT/'staging/OFFLINE-HOME-KO.txt',raw/'offline_guide.txt')
    for folder in ('classes','dex'):
        shutil.rmtree(dest/folder,ignore_errors=True); (dest/folder).mkdir()
    manifest=ET.parse(app/'AndroidManifest.xml').getroot(); ns='{http://schemas.android.com/apk/res/android}'
    version=manifest.get(ns+'versionName'); minimum=manifest.find('uses-sdk').get(ns+'minSdkVersion')
    sources=sorted(app.glob('src/**/*.java'))+sorted((ROOT/'shared').glob('src/**/*.java'))
    run([JDK/'javac','--release','8','-encoding','UTF-8','-classpath',PLATFORM,'-d',dest/'classes',*sources])
    run([JDK/'java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api',minimum,'--lib',PLATFORM,'--output',dest/'dex',*sorted((dest/'classes').glob('**/*.class'))])
    run([BT/'aapt','package','-f','-M',app/'AndroidManifest.xml','-S',app/'res','-I',PLATFORM,'-F',dest/'unsigned.apk'])
    with zipfile.ZipFile(dest/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        z.write(dest/'dex/classes.dex','classes.dex')
    run([BT/'zipalign','-f','4',dest/'unsigned.apk',dest/'aligned.apk'])
    (ROOT/'staging').mkdir(exist_ok=True)
    final=ROOT/'staging'/f'{stem}-{version}.apk'
    run([JDK/'java','-jar',BT/'lib/apksigner.jar','sign','--ks',KEY,'--ks-pass','env:NAVI_KEYSTORE_PASSWORD','--key-pass','env:NAVI_KEY_PASSWORD','--min-sdk-version',minimum,'--v1-signing-enabled','true','--v2-signing-enabled','true','--out',final,dest/'aligned.apk'])
    run([JDK/'java','-jar',BT/'lib/apksigner.jar','verify','--verbose','--print-certs','--min-sdk-version',minimum,final])
    run([BT/'aapt','dump','badging',final])
    final.with_suffix('.sha256').write_text(hashlib.sha256(final.read_bytes()).hexdigest()+'  '+final.name+'\n')
    print('Built',final)
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--app',choices=['receiver','sender','all'],default='all');args=parser.parse_args()
    if args.app in ('receiver','all'):build('diagnostic','NaviDiagnostic')
    if args.app in ('sender','all'):build('sender','NaviSender')
