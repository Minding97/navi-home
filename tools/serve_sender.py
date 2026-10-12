"""Serve only the sender APK + checksum, with an unguessable URL and a 30 minute lifetime."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse, secrets, threading, subprocess
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8767);args=parser.parse_args()
files={name:root/'staging'/name for name in ['NaviSender-0.1.apk','NaviSender-0.1.sha256']}
for p in files.values():
    if not p.is_file():raise SystemExit('Build sender APK first')
token=secrets.token_urlsafe(18)
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        prefix='/'+token+'/'
        if not self.path.startswith(prefix):self.send_error(404);return
        name=self.path[len(prefix):]
        if name not in files:self.send_error(404);return
        data=files[name].read_bytes();self.send_response(200)
        self.send_header('Content-Type','application/vnd.android.package-archive' if name.endswith('.apk') else 'text/plain')
        self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(data)
    def log_message(self,*args):pass
server=ThreadingHTTPServer(('0.0.0.0',args.port),Handler)
ip='MAC_LOCAL_IP'
for interface in ['en0','en1']:
    r=subprocess.run(['ipconfig','getifaddr',interface],capture_output=True,text=True)
    if r.returncode==0:ip=r.stdout.strip();break
print(f'Sender APK: http://{ip}:{args.port}/{token}/NaviSender-0.1.apk',flush=True)
print('Only sender APK/checksum are exposed. Stops in 30 minutes; Ctrl-C stops now.',flush=True)
timer=threading.Timer(1800,server.shutdown);timer.daemon=True;timer.start()
try:server.serve_forever()
except KeyboardInterrupt:pass
finally:server.server_close();timer.cancel()
