package local.navi.diagnostic;

import android.app.Service;
import android.content.*;
import android.os.*;

/** A single, finite attempt. It never opens an activity or takes camera focus. */
public class WifiConnectionService extends Service {
    static volatile boolean active;
    static volatile String status="아직 연결 시도 없음";
    private HandlerThread thread;
    private Handler worker;
    private WifiReconnect session;
    private WifiAccess access;
    private volatile boolean destroyed;
    static void start(Context c,boolean automatic) {
        if(!WifiAccess.supportsControl()) return;
        if(automatic && !WifiAccess.prefs(c).getBoolean("auto_connect",false)) return;
        try { c.startService(new Intent(c,WifiConnectionService.class).putExtra("automatic",automatic)); }
        catch(RuntimeException e) {
            status="연결 시도를 시작할 수 없습니다 · "+e.getClass().getSimpleName();
            WifiAccess.prefs(c).edit().putString("last_result",status).apply();
        }
    }
    @Override public void onCreate() {
        super.onCreate(); access=new WifiAccess(this);
        thread=new HandlerThread("wifi-connection"); thread.start(); worker=new Handler(thread.getLooper());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        final boolean automatic=intent!=null && intent.getBooleanExtra("automatic",false);
        worker.post(()->{
            if(destroyed || session!=null) return;
            session=new WifiReconnect(WifiAccess.target(this),automatic,SystemClock.elapsedRealtime());
            active=true; tick.run();
        });
        return START_NOT_STICKY;
    }
    private final Runnable tick=()->advance();
    private void advance() {
        if(destroyed || session==null) return;
        boolean allowed=WifiAccess.supportsControl() && session.target.equals(WifiAccess.target(this))
            && (!session.automatic || WifiAccess.prefs(this).getBoolean("auto_connect",false));
        session.tick(access,SystemClock.elapsedRealtime(),allowed);
        status=session.status;
        if(session.finished) {
            WifiAccess.prefs(this).edit().putString("last_result",status).putLong("last_time",System.currentTimeMillis()).apply();
            stopSelf();
        } else worker.postDelayed(tick,2000);
    }
    @Override public void onDestroy() {
        destroyed=true; active=false;
        worker.removeCallbacksAndMessages(null); thread.quitSafely();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
