"""Create isolated throwaway AVD configs; never change a saved user AVD."""
from pathlib import Path
import os
base=Path('/tmp/navi-mirror-avd')
if base.exists():raise SystemExit('Temporary AVD directory already exists; inspect it before reusing or removing it.')
base.mkdir()
for name,w,h,density,port in [('navi_receiver',800,480,160,5580),('navi_sender',720,1280,240,5582)]:
    p=base/(name+'.avd');p.mkdir()
    (base/(name+'.ini')).write_text(f'avd.ini.encoding=UTF-8\npath={p}\ntarget=android-36\n')
    (p/'config.ini').write_text(f'''avd.ini.encoding=UTF-8
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=2
hw.ramSize=1536
hw.lcd.width={w}
hw.lcd.height={h}
hw.lcd.density={density}
hw.keyboard=yes
hw.mainKeys=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
hw.gps=yes
disk.dataPartition.size=2G
image.sysdir.1=system-images/android-36/google_apis/arm64-v8a/
tag.id=google_apis
tag.display=Google APIs
target=android-36
showDeviceFrame=no
''')
    print(f'ANDROID_AVD_HOME={base} emulator -avd {name} -port {port} -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader')
print('After both boot: python3 tools/emulator_mirror_smoke.py')
print('Then: python3 tools/emulator_mirror_soak.py')
