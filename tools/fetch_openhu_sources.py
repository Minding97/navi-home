"""Read pinned upstream sources for compatibility review; never execute upstream code."""
from pathlib import Path
import subprocess,concurrent.futures
base=Path(__file__).resolve().parents[1]/'analysis/openhu-3.3.0'
base.mkdir(parents=True,exist_ok=True)
paths=['app/src/main/AndroidManifest.xml','app/src/main/java/com/andrerinas/openheadunit/main/MainActivity.kt','app/src/main/java/com/andrerinas/openheadunit/main/OnboardingActivity.kt','app/src/main/java/com/andrerinas/openheadunit/utils/Settings.kt','app/src/main/res/values/strings.xml','app/src/main/res/values-ko/strings.xml','contract/src/main/java/com/andrerinas/openheadunit/contract/HeadUnitIntent.kt','LICENSE']
def fetch(path):
    name=path.replace('/','__');url='https://raw.githubusercontent.com/andreknieriem/open-headunit/v.3.3.0/'+path
    subprocess.run(['curl','-LsSf','--max-time','25',url,'-o',str(base/name)],check=True,capture_output=True)
    return path
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for name in pool.map(fetch,paths):print(name)
