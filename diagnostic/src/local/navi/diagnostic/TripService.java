package local.navi.diagnostic;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

/** Keeps the current session across camera/app switches without opening any window. */
public final class TripService extends Service implements LocationListener {
    private TripMetrics metrics;
    private LocationManager gps;
    private boolean listening;
    private String issue = "GPS 신호 대기";
    private final IBinder binder = new LocalBinder();
    public final class LocalBinder extends Binder { TripService service() { return TripService.this; } }
    public static boolean permitted(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onCreate() {
        super.onCreate();
        metrics=new TripMetrics(SystemClock.elapsedRealtime());
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel("trip","현재 GPS 세션",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"trip"):new Notification.Builder(this);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Navi 홈")
            .setContentText("현재 세션 · GPS 설정과 종료는 홈에서").setContentIntent(open).setOngoing(true);
        startForeground(60,b.build());
    }
    @Override public int onStartCommand(Intent i,int flags,int id) { configure(); return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return binder; }
    public TripMetrics.Snapshot snapshot() { return metrics.snapshot(SystemClock.elapsedRealtime()); }
    public String issue() { return issue; }
    public void reset() { metrics.reset(SystemClock.elapsedRealtime()); }
    public void configure() {
        boolean enabled=getSharedPreferences("offline_home",0).getBoolean("gps_enabled",true);
        if(!enabled || !permitted(this)) {
            disconnect();issue=enabled?"GPS 권한 필요":"GPS 수신 일시 중지"; return;
        }
        if(listening) return;
        try {
            if(gps==null || !gps.getAllProviders().contains(LocationManager.GPS_PROVIDER)) { issue="GPS 제공자 없음";return; }
            gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);
            listening=true;issue=gps.isProviderEnabled(LocationManager.GPS_PROVIDER)?"GPS 신호 대기":"기기 위치 설정 꺼짐";
        } catch(RuntimeException e) { disconnect(); issue="GPS 접근 실패 · "+e.getClass().getSimpleName(); }
    }
    private void disconnect() {
        if(gps!=null) try { gps.removeUpdates(this); } catch(RuntimeException ignored) {}
        listening=false;metrics.unavailable();
    }
    @Override public void onLocationChanged(Location l) {
        if(!listening || !LocationManager.GPS_PROVIDER.equals(l.getProvider())) return;
        metrics.accept(SystemClock.elapsedRealtime(),l.getElapsedRealtimeNanos()/1000000L,l.getLatitude(),l.getLongitude(),
            l.hasAccuracy()?l.getAccuracy():Double.NaN,l.hasSpeed()?l.getSpeed():Double.NaN);
        issue="";
    }
    @Override public void onProviderDisabled(String p) { if(LocationManager.GPS_PROVIDER.equals(p)) {metrics.unavailable();issue="기기 위치 설정 꺼짐";} }
    @Override public void onProviderEnabled(String p) { if(LocationManager.GPS_PROVIDER.equals(p)) issue="GPS 신호 대기"; }
    @Override public void onStatusChanged(String p,int s,Bundle b) {}
    @Override public void onDestroy() { disconnect();stopForeground(true);super.onDestroy(); }
}
