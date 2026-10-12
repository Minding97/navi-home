"""Resolve the local Android SDK and JDK without machine-specific paths."""
import os
from pathlib import Path
import shutil


def sdk_root():
    configured = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if configured:
        return Path(configured).expanduser()
    candidates = [Path.home() / 'Library/Android/sdk', Path.home() / 'Android/Sdk']
    return next((path for path in candidates if path.is_dir()), candidates[0])


def java_bin():
    if os.environ.get('JAVA_HOME'):
        return Path(os.environ['JAVA_HOME']).expanduser() / 'bin'
    homebrew = Path('/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin')
    if (homebrew / 'javac').is_file():
        return homebrew
    compiler = shutil.which('javac')
    if compiler:
        return Path(compiler).resolve().parent
    raise RuntimeError('JDK required: install JDK 21 and set JAVA_HOME.')


BUILD_TOOLS_VERSION = os.environ.get('NAVI_BUILD_TOOLS_VERSION', '35.0.0')
COMPILE_API = os.environ.get('NAVI_COMPILE_API', '36')
