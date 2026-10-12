package local.navi.diagnostic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** Framework-only diagnostic app with optional boot entry and HOME selection. */
public class DiagnosticActivity extends Activity implements LocationListener {
    private LocationManager manager;
    private TextView info, gps;
    private TextView startup;
    private CheckBox bootEnabled;
    private boolean listening;
    private long lastFix;
    private String lastFixText = "";
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!listening) return;
            if (lastFix > 0) {
                long age = (SystemClock.elapsedRealtime() - lastFix) / 1000;
                gps.setText(lastFixText + "\n마지막 수신: " + age + "초 전" + (age > 10 ? " · 현재 값으로 사용하지 마세요" : ""));
            }
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 10, 16, 10);
        root.setBackgroundColor(0xff101923);
        TextView title = text(22);
        title.setText("Navi 진단 · 0.6");
        root.addView(title);
        LinearLayout actions = new LinearLayout(this);
        button(actions, "진단 시작", new View.OnClickListener() {
            public void onClick(View v) { startGps(); }
        });
        button(actions, "진단 중지", new View.OnClickListener() {
            public void onClick(View v) { stopGps(); gps.setText("이 화면의 GPS 진단을 중지했습니다. 홈 GPS는 홈 설정에서 중지합니다."); }
        });
        button(actions, "Android 설정", new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Exception e) { gps.setText("일반 Android 설정을 열 수 없습니다: " + e.getClass().getSimpleName()); }
            }
        });
        button(actions, "종료", new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        root.addView(actions);
        LinearLayout homeActions = new LinearLayout(this);
        button(homeActions, "파인 홈", new View.OnClickListener() {
            public void onClick(View v) { openPackage("com.finedigital.homescreen"); }
        });
        button(homeActions, "파인 설정", new View.OnClickListener() {
            public void onClick(View v) { openPackage("com.finedigital.setting"); }
        });
        button(homeActions, "기본 홈 설정", new View.OnClickListener() {
            public void onClick(View v) { openHomeSettings(); }
        });
        button(homeActions, "자동 실행 해제", new View.OnClickListener() {
            public void onClick(View v) { restoreHome(); }
        });
        root.addView(homeActions);
        LinearLayout mirrorActions = new LinearLayout(this);
        button(mirrorActions, "휴대폰 지도 연결", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(DiagnosticActivity.this, MirrorActivity.class)); }
        });
        button(mirrorActions, "Wi-Fi 연결 관리", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(DiagnosticActivity.this, WifiActivity.class)); }
        });
        button(mirrorActions, "오늘 시험 / Android Auto", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(DiagnosticActivity.this, CarTestActivity.class)); }
        });
        root.addView(mirrorActions);
        // Opening the update once enables the user-requested boot launch.
        if (!BootReceiver.preferences(this).contains("boot_enabled")) {
            BootReceiver.preferences(this).edit().putBoolean("boot_enabled", true).commit();
        }
        bootEnabled = new CheckBox(this);
        bootEnabled.setText("전원을 켠 뒤 앱 자동 실행 (완전 부팅 시)");
        bootEnabled.setTextColor(0xffecf1f5);
        bootEnabled.setTextSize(16);
        bootEnabled.setChecked(BootReceiver.preferences(this).getBoolean("boot_enabled", false));
        bootEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                BootReceiver.preferences(DiagnosticActivity.this).edit().putBoolean("boot_enabled", checked).commit();
                showStartup();
            }
        });
        root.addView(bootEnabled);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        info = text(15); gps = text(20); startup = text(15);
        gps.setText("진단 시작을 누르면 수신을 시험합니다.\n화면을 벗어나면 이 진단만 중지합니다. 홈 GPS 세션은 계속됩니다.");
        body.addView(startup); body.addView(gps); body.addView(info);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showInfo();
    }
    private void openPackage(String name) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(name);
            if (launch == null) { message("이 기기에서 해당 앱의 실행 화면을 찾지 못했습니다."); return; }
            startActivity(launch);
        } catch (RuntimeException e) { message("앱을 열 수 없습니다: " + e.getClass().getSimpleName()); }
    }
    private void message(String value) {
        new AlertDialog.Builder(this).setMessage(value).setPositiveButton("확인", null).show();
    }
    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (RuntimeException e) {
            message("이 기기에서는 기본 홈 설정 화면을 열 수 없습니다.\n체크된 부팅 자동 실행으로 먼저 시험하세요. 제조사 홈이 다시 나타나면 결과를 알려주세요.");
        }
    }
    private void restoreHome() {
        new AlertDialog.Builder(this).setTitle("자동 실행 해제")
            .setMessage("부팅 자동 실행을 끄고 Navi 진단의 기본 홈 지정을 해제합니다. 홈 선택 화면이 나오면 파인드라이브 홈을 선택하세요.")
            .setNegativeButton("취소", null)
            .setPositiveButton("해제", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    bootEnabled.setChecked(false);
                    try { getPackageManager().clearPackagePreferredActivities(getPackageName()); }
                    catch (RuntimeException e) { message("기본 홈 해제 실패. 기본 홈 설정에서 파인드라이브 홈을 선택하세요."); }
                    showStartup();
                    openHomeSettings();
                }
            }).show();
    }
    private void showStartup() {
        if (startup == null) return;
        String s = "기본 홈: " + (BootReceiver.isDefaultHome(this) ? "Navi 홈" : "다른 앱 또는 미지정");
        s += "\n기본 홈으로 지정하면 체크를 꺼도 홈 화면으로 실행됩니다. 복구는 ‘자동 실행 해제’.";
        long boot = BootReceiver.preferences(this).getLong("last_boot", 0);
        s += "\n최근 부팅 알림: " + (boot == 0 ? "아직 없음" :
            new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date(boot))
            + " · " + BootReceiver.preferences(this).getString("boot_result", ""));
        startup.setText(s);
    }
    @Override protected void onResume() {
        super.onResume();
        showStartup();
    }
    private TextView text(int size) {
        TextView t = new TextView(this); t.setTextColor(0xffecf1f5);
        t.setTextSize(size); t.setPadding(0, 5, 0, 8); return t;
    }
    private void button(LinearLayout row, String label, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); b.setTextSize(15);
        b.setOnClickListener(listener);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void showInfo() {
        StringBuilder s = new StringBuilder("\n기기 정보\n");
        s.append("제조사 / 모델: ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL);
        s.append("\nAndroid: ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT);
        s.append("\n빌드: ").append(Build.DISPLAY);
        s.append("\nCPU ABI: ").append(java.util.Arrays.toString(Build.SUPPORTED_ABIS));
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        s.append("\n화면: ").append(dm.widthPixels).append(" × ").append(dm.heightPixels).append(" px");
        try { s.append("\n위치 제공자: ").append(manager == null ? "서비스 없음" : manager.getAllProviders()); }
        catch (Exception e) { s.append("\n위치 제공자 조회 실패: ").append(e.getClass().getSimpleName()); }
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        s.append("\n\n공개된 센서\n");
        List<Sensor> sensors = sm == null ? java.util.Collections.<Sensor>emptyList() : sm.getSensorList(Sensor.TYPE_ALL);
        if (sensors.isEmpty()) s.append("없음 — 별도 제조사 전용 센서의 존재 여부는 알 수 없습니다.");
        for (Sensor sensor : sensors) s.append(sensor.getName()).append(" [type ").append(sensor.getType()).append("]\n");
        s.append("\n\n이 앱은 현재 화면에서만 진단합니다. 후방카메라와 OBD는 별도로 검증해야 합니다.");
        info.setText(s.toString());
    }
    private void startGps() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1); return;
        }
        if (manager == null) { gps.setText("위치 서비스가 없습니다."); return; }
        stopGps();
        try {
            if (!manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                gps.setText("Android에 GPS 제공자가 공개되어 있지 않습니다."); return;
            }
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps.setText("GPS 제공자가 꺼져 있습니다. 기기 위치 설정을 확인하세요."); return;
            }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            listening = true;
            gps.setText("GPS 신호 대기 중… 실외에서도 첫 수신에는 시간이 걸릴 수 있습니다.");
            handler.post(refresh);
        } catch (Exception e) { stopGps(); gps.setText("GPS 시작 실패: " + e.getClass().getSimpleName() + "\n" + e.getMessage()); }
    }
    private void stopGps() {
        if (manager != null) try { manager.removeUpdates(this); } catch (Exception ignored) {}
        listening = false; lastFix = 0; lastFixText = ""; handler.removeCallbacks(refresh);
    }
    @Override public void onLocationChanged(Location l) {
        if (!listening) return;
        lastFix = l.getElapsedRealtimeNanos() / 1000000L;
        String speed = l.hasSpeed() ? String.format(Locale.US,"%.1f km/h",l.getSpeed()*3.6) : "미제공";
        String accuracy = l.hasAccuracy() ? String.format(Locale.US,"±%.0f m",l.getAccuracy()) : "미제공";
        lastFixText = String.format(Locale.US,"GPS 수신 성공\n속도: %s · 정확도: %s\n위도 %.6f / 경도 %.6f",speed,accuracy,l.getLatitude(),l.getLongitude());
        gps.setText(lastFixText);
    }
    @Override public void onStatusChanged(String p,int status,Bundle extras) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {
        if (LocationManager.GPS_PROVIDER.equals(p)) { stopGps(); gps.setText("GPS 제공자가 꺼져 수신을 중지했습니다."); }
    }
    @Override protected void onPause() {
        boolean wasListening = listening; stopGps();
        if (wasListening) gps.setText("화면을 벗어나 이 화면의 GPS 진단을 중지했습니다. 홈 GPS는 홈 설정에서 중지합니다. 다시 시작하려면 GPS 시작을 누르세요.");
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission dialogs may return before onResume; a second explicit tap is predictable.
                gps.setText("위치 권한이 허용됐습니다. GPS 시작을 눌러주세요.");
            } else gps.setText("위치 권한이 없어 GPS를 시작하지 않았습니다.");
        }
    }
}
