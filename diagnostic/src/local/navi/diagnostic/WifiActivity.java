package local.navi.diagnostic;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class WifiActivity extends Activity {
    private WifiAccess access;
    private TextView connection,target,result;
    private CheckBox automatic;
    private final Handler ui=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){showStatus();ui.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); access=new WifiAccess(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,8,16,8);root.setBackgroundColor(0xff141b21);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(23);title.setText("Wi-Fi 연결 관리");title.setTextColor(0xff69d7c6);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button back=new Button(this);back.setText("돌아가기");back.setOnClickListener(v->finish());header.addView(back);root.addView(header);
        connection=text(17);root.addView(connection);
        target=text(16);root.addView(target);
        LinearLayout settings=new LinearLayout(this);
        button(settings,"Wi-Fi 설정 열기",v->openSettings(false),true);
        button(settings,"파인 연결 설정",v->openSettings(true),true);
        button(settings,"현재 Wi-Fi 기억",v->rememberCurrent(),WifiAccess.supportsControl());root.addView(settings);
        LinearLayout actions=new LinearLayout(this);
        button(actions,"저장 목록에서 선택",v->chooseSaved(),WifiAccess.supportsControl());
        button(actions,"다시 연결",v->{
            if(WifiAccess.target(this).isEmpty()) {message("먼저 설정에서 Wi-Fi에 연결한 뒤 ‘현재 Wi-Fi 기억’을 눌러 주세요.");return;}
            WifiConnectionService.start(this,false);result.setText("연결 시도를 시작합니다…");
        },WifiAccess.supportsControl());
        button(actions,"시도 중지",v->stopAttempt(),true);root.addView(actions);
        automatic=new CheckBox(this);automatic.setTextColor(0xffecf1f5);automatic.setTextSize(16);
        automatic.setText("부팅 시 기억한 Wi-Fi 찾기 · 꺼져 있으면 Wi-Fi 켜기");
        automatic.setEnabled(WifiAccess.supportsControl());automatic.setChecked(WifiAccess.prefs(this).getBoolean("auto_connect",false));
        automatic.setOnCheckedChangeListener((button,checked)->{
            if(checked && WifiAccess.target(this).isEmpty()) {automatic.setChecked(false);message("먼저 연결할 Wi-Fi를 기억해 주세요.");return;}
            WifiAccess.prefs(this).edit().putBoolean("auto_connect",checked).apply();
            if(!checked) stopAttempt();
        });root.addView(automatic);
        ScrollView scroll=new ScrollView(this);LinearLayout detail=new LinearLayout(this);detail.setOrientation(1);
        result=text(16);result.setTextColor(0xff69d7c6);detail.addView(result);
        TextView help=text(15);help.setText(WifiAccess.supportsControl()
            ? "1. 휴대폰 핫스팟 켜기 → 설정에서 첫 연결\n2. 이 화면으로 돌아와 ‘현재 Wi-Fi 기억’\n다음 부팅 때 최대 1분간 찾습니다. 핫스팟을 늦게 켰다면 ‘다시 연결’.\n비밀번호는 기기 설정이 보관합니다. 화면 공유 시작은 휴대폰에서 승인하세요."
            : "직접 연결 제어는 iQ 3의 Android 5에서 사용합니다. 이 Android 버전에서는 위의 Wi-Fi 설정 화면에서 연결해 주세요.");
        detail.addView(help);
        Button clear=new Button(this);clear.setText("기억한 대상 해제");clear.setOnClickListener(v->{
            stopAttempt();WifiAccess.prefs(this).edit().remove("target_key").remove("target_name").putBoolean("auto_connect",false).apply();
            automatic.setChecked(false);showStatus();
        });detail.addView(clear);scroll.addView(detail);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    private TextView text(int size) {TextView t=new TextView(this);t.setTextColor(0xffecf1f5);t.setTextSize(size);t.setPadding(0,3,0,3);return t;}
    private void button(LinearLayout row,String label,View.OnClickListener click,boolean enabled) {
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(52);
        b.setTextColor(enabled?0xffecf1f5:0xff80909c);b.setBackgroundTintList(ColorStateList.valueOf(0xff283840));
        b.setEnabled(enabled);b.setOnClickListener(click);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void message(String message) {new AlertDialog.Builder(this).setMessage(message).setPositiveButton("확인",null).show();}
    private void showStatus() {
        try {connection.setText(access.summary());}catch(RuntimeException e){connection.setText("Wi-Fi 상태 확인 불가 · 설정 화면을 확인하세요.");}
        target.setText("기억한 Wi-Fi: "+WifiAccess.prefs(this).getString("target_name","아직 없음"));
        result.setText((WifiConnectionService.active?"찾는 중 · "+WifiConnectionService.status:
            "최근 결과: "+WifiAccess.prefs(this).getString("last_result","아직 연결 시도 없음")));
    }
    private void rememberCurrent() {
        try {
            int current=access.connectedId();
            for(WifiReconnect.Profile p:access.profiles()) if(current>=0 && p.id==current) {remember(p);return;}
            message("현재 연결의 저장 정보를 읽지 못했습니다. 설정에서 Wi-Fi 연결과 IP 할당을 확인해 주세요.");
        }catch(RuntimeException e){message("저장 정보를 읽을 수 없습니다 · "+e.getClass().getSimpleName());}
    }
    private void chooseSaved() {
        try {
            final List<WifiReconnect.Profile> list=access.profiles();
            if(list.isEmpty()) {message("저장된 Wi-Fi가 없습니다. 설정 화면에서 먼저 연결해 주세요.");return;}
            String[] names=new String[list.size()];for(int i=0;i<names.length;i++) names[i]=list.get(i).name;
            new AlertDialog.Builder(this).setTitle("연결할 Wi-Fi 기억하기").setItems(names,(d,index)->remember(list.get(index))).setNegativeButton("취소",null).show();
        }catch(RuntimeException e){message("저장 목록을 읽을 수 없습니다 · "+e.getClass().getSimpleName());}
    }
    private void remember(WifiReconnect.Profile p) {
        if(WifiReconnect.find(access.profiles(),p.key)==null) {message("같은 이름의 저장 정보가 중복됩니다. 설정에서 정리 후 다시 선택해 주세요.");return;}
        stopAttempt();
        WifiAccess.prefs(this).edit().putString("target_key",p.key).putString("target_name",p.name)
            .putBoolean("auto_connect",true).putString("last_result","기억했습니다. 다음 부팅 때 자동으로 찾습니다.").apply();
        automatic.setChecked(true);showStatus();
    }
    private void stopAttempt() {
        stopService(new Intent(this,WifiConnectionService.class));
        WifiAccess.prefs(this).edit().putString("last_result","앱의 연결 시도를 중지했습니다. 기기의 기본 자동 연결은 유지됩니다.").apply();
    }
    private void openSettings(boolean vendor) {
        stopAttempt();
        if(vendor) {
            try {
                // Confirmed in the card's Setting.apk: ActivitySetting$1 reads categoryName.
                startActivity(new Intent().setClassName("com.finedigital.setting","com.finedigital.setting.ActivitySetting")
                    .putExtra("categoryName","휴대폰 연결"));return;
            }catch(RuntimeException ignored){}
            try {
                Intent launch=getPackageManager().getLaunchIntentForPackage("com.finedigital.setting");
                if(launch!=null){startActivity(launch);return;}
            }catch(RuntimeException ignored){}
            message("파인 연결 설정을 열 수 없습니다. ‘Wi-Fi 설정 열기’를 사용해 주세요.");
        } else {
            try {startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}
            catch(RuntimeException e){message("Android Wi-Fi 설정을 열 수 없습니다. ‘파인 연결 설정’을 사용해 주세요.");}
        }
    }
    @Override protected void onResume(){super.onResume();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
}
