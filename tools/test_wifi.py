from pathlib import Path
import subprocess
from build_env import java_bin
root=Path(__file__).resolve().parents[1]
build=root/'tests/build';build.mkdir(exist_ok=True)
jdk=java_bin()
sources=[root/'diagnostic/src/local/navi/diagnostic/WifiReconnect.java',root/'tests/java/local/navi/diagnostic/WifiReconnectTest.java']
subprocess.run([str(jdk/'javac'),'--release','8','-d',str(build),*[str(s) for s in sources]],check=True)
subprocess.run([str(jdk/'java'),'-cp',str(build),'local.navi.diagnostic.WifiReconnectTest'],check=True,timeout=20)
