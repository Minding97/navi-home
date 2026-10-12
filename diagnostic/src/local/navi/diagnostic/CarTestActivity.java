package local.navi.diagnostic;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Explicit, foreground-only launch controls and a user-entered test notebook. */
public class CarTestActivity extends Activity {
    static final String OPENHU="com.andrerinas.headunitrevived";
    private static final String[] TESTS={"앱 업데이트 / 부팅 자동 실행","Wi-Fi 연결 / 기억 / 수동 재연결","Wi-Fi 부팅 연결 / 1분 종료",
        "기존 미러링 10분 / 회전 / 공유 중지","미러링 중 R·D 카메라 / 복귀","Android Auto 연결 / 지도 터치",
        "Android Auto 10분 / 오디오","Android Auto 중 R·D 카메라 / 복귀","Android Auto 끊김 / 재연결 / 파인 홈"};
    private static final String[] RESULTS={"미확인","통과","실패","건너뜀"};
    private LinearLayout body;private TextView status;private EditText address,notes;
    private SharedPreferences prefs;private int tab;private boolean resumed;
    private final Handler ui=new Handler();private volatile Socket probe;private boolean probing;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("evening_test",MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(14,8,14,8);root.setBackgroundColor(0xff141b21);
        TextView title=text("오늘 시험 · Android Auto",23);title.setTextColor(0xff69d7c6);root.addView(title);
        LinearLayout shortcuts=new LinearLayout(this);
        button(shortcuts,"Wi-Fi 관리",v->startActivity(new Intent(this,WifiActivity.class)));
        button(shortcuts,"기존 미러링",v->startActivity(new Intent(this,MirrorActivity.class)));
        button(shortcuts,"Open Headunit",v->launchOpenHu());
        button(shortcuts,"홈",v->finish());root.addView(shortcuts);
        LinearLayout tabs=new LinearLayout(this);
        button(tabs,"① 시험 안내",v->show(0));button(tabs,"② AA 연결",v->show(1));button(tabs,"③ 결과 기록",v->show(2));root.addView(tabs);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);show(state==null?0:state.getInt("tab",0));
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setTextColor(0xffecf1f5);t.setTextSize(size);t.setText(value);t.setPadding(0,6,0,6);return t;}
    private void button(LinearLayout row,String label,View.OnClickListener click){
        Button b=new Button(this);b.setText(label);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(50);
        b.setTextColor(0xffecf1f5);b.setBackgroundTintList(ColorStateList.valueOf(0xff283840));b.setOnClickListener(click);
        row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void message(String value){new AlertDialog.Builder(this).setMessage(value).setPositiveButton("확인",null).show();}
    private void saveFields(){
        SharedPreferences.Editor e=prefs.edit();
        if(address!=null)e.putString("phone_ip",address.getText().toString());
        if(notes!=null)e.putString("notes",notes.getText().toString());e.apply();
    }
    private void show(int which){
        saveFields();tab=which;address=null;notes=null;status=null;body.removeAllViews();
        if(which==0){
            try(InputStream in=getResources().openRawResource(getResources().getIdentifier("evening_guide","raw",getPackageName()));
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                body.addView(text(new String(out.toByteArray(),"UTF-8"),17));
            }catch(Exception e){body.addView(text("SD카드의 EVENING-TEST-0.5-KO.txt를 확인하세요.",18));}
        }else if(which==1){showConnection();}else{showResults();}
    }
    private String installed(){
        try{PackageInfo p=getPackageManager().getPackageInfo(OPENHU,0);return "Open Headunit "+p.versionName+" 설치됨";}
        catch(Exception e){return "Open Headunit 미설치 · SD카드 OpenHeadunit-3.3.0.apk를 먼저 설치하세요.";}
    }
    private void showConnection(){
        body.addView(text(installed(),18));
        String network;try{network=new WifiAccess(this).summary();}catch(RuntimeException e){network="Wi-Fi 상태 확인 불가";}
        body.addView(text(network,16));
        body.addView(text("핫스팟 시험: 휴대폰 Android Auto 설정 → 버전 10회 → ⋮ → 헤드 유닛 서버 시작.\n아래에는 휴대폰 주소를 입력합니다. 기존 미러링에서 사용한 내비 주소와 다릅니다.",16));
        address=new EditText(this);address.setTextColor(0xffecf1f5);address.setSingleLine(true);address.setTextSize(18);
        address.setInputType(InputType.TYPE_CLASS_PHONE);address.setHintTextColor(0xff95a4b0);address.setHint("휴대폰 IP · 예: 192.168.x.x");
        address.setText(prefs.getString("phone_ip",""));body.addView(address);
        LinearLayout actions=new LinearLayout(this);
        button(actions,"핫스팟 주소 가져오기",v->useGateway());button(actions,"서버 확인",v->probeServer());button(actions,"AA 연결 요청",v->connect());body.addView(actions);
        status=text(probing?"서버 확인 중…":"연결 전: Open Headunit 초기 설정을 먼저 마치세요.",16);status.setTextColor(0xff69d7c6);body.addView(status);
        body.addView(text("‘주소 가져오기’는 내비가 휴대폰 핫스팟에 연결된 경우에만 사용하세요. 집 공유기에서는 휴대폰 Wi-Fi 상세정보의 IP를 직접 입력합니다.\n서버 확인은 TCP 포트 응답만 검사합니다. 실제 Android Auto 성공은 화면과 터치로 확인해야 합니다.\n유선 시험: Open Headunit 실행 → USB 케이블 연결 → USB에서 휴대폰 선택·접근 허용.",16));
        LinearLayout stop=new LinearLayout(this);button(stop,"AA 종료 요청",v->sendCommand("exit",null));body.addView(stop);
    }
    private void useGateway(){
        try{
            WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            DhcpInfo dhcp=wifi==null?null:wifi.getDhcpInfo();int n=dhcp==null?0:dhcp.gateway;
            String ip=PhoneAddress.normalize((n&255)+"."+((n>>8)&255)+"."+((n>>16)&255)+"."+((n>>24)&255));
            if(ip==null){message("핫스팟 주소를 읽지 못했습니다. Wi-Fi 연결을 확인하거나 휴대폰 IP를 직접 입력하세요.");return;}
            address.setText(ip);saveFields();status.setText("게이트웨이 주소를 가져왔습니다. 연결한 Wi-Fi가 휴대폰 핫스팟인지 확인하세요.");
        }catch(RuntimeException e){message("주소 조회 실패 · "+e.getClass().getSimpleName());}
    }
    private String selectedIp(){
        String ip=PhoneAddress.normalize(address.getText().toString());
        if(ip==null)message("휴대폰의 로컬 IPv4 주소만 입력하세요. 포트 번호나 http://는 붙이지 않습니다.");
        else{address.setText(ip);saveFields();}return ip;
    }
    private void probeServer(){
        if(probing)return;String ip=selectedIp();if(ip==null)return;
        probing=true;status.setText("서버 확인 중 · 최대 3초");
        new Thread(()->{
            String result;
            try(Socket socket=new Socket()){
                probe=socket;socket.connect(new InetSocketAddress(ip,5277),3000);
                result="TCP 5277 응답 있음 · AA 연결 요청으로 실제 화면을 확인하세요.";
            }catch(IOException e){result="서버 응답 없음 · 핫스팟 연결, 휴대폰 주소, 헤드 유닛 서버 시작을 확인하세요.";}
            finally{probe=null;}
            final String value=result;ui.post(()->{probing=false;if(resumed && tab==1 && status!=null)status.setText(value);});
        },"aa-server-check").start();
    }
    private void connect(){String ip=selectedIp();if(ip!=null)sendCommand("connect",ip);}
    private void sendCommand(String action,String ip){
        try{
            getPackageManager().getPackageInfo(OPENHU,0);
            Uri.Builder uri=new Uri.Builder().scheme("headunit").authority(action);if(ip!=null)uri.appendQueryParameter("ip",ip);
            // The exported 3.3.0 MainActivity handles these URIs and keeps the connection
            // coroutine alive; its short-lived AutomationActivity immediately finishes.
            startActivity(new Intent(Intent.ACTION_VIEW,uri.build())
                .setClassName(OPENHU,"com.andrerinas.openheadunit.main.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch(Exception e){message("Open Headunit에 요청을 전달하지 못했습니다. 먼저 설치·초기 설정을 마친 뒤 앱의 Wi-Fi 또는 USB 버튼으로 시험하세요.");}
    }
    private void launchOpenHu(){
        try{Intent launch=getPackageManager().getLaunchIntentForPackage(OPENHU);if(launch==null)throw new IllegalStateException();startActivity(launch);}
        catch(RuntimeException e){message("SD카드 NaviTest/OpenHeadunit-3.3.0.apk를 설치한 뒤 다시 눌러 주세요.");}
    }
    private void showResults(){
        body.addView(text("직접 확인한 결과를 눌러 기록하세요. 앱이 자동으로 통과 처리하지 않습니다.",16));
        for(int i=0;i<TESTS.length;i++){
            final int index=i;LinearLayout row=new LinearLayout(this);
            String result=RESULTS[Math.max(0,Math.min(3,prefs.getInt("test_"+i,0)))];
            button(row,(i+1)+". "+TESTS[i]+" · "+result,v->new AlertDialog.Builder(this).setTitle(TESTS[index])
                .setItems(RESULTS,(d,selected)->{prefs.edit().putInt("test_"+index,selected).apply();show(2);}).show());body.addView(row);
        }
        notes=new EditText(this);notes.setTextColor(0xffecf1f5);notes.setHintTextColor(0xff95a4b0);notes.setHint("오류 문구 / 소리 출력 위치 / 끊김·복귀 시간 메모");
        notes.setMinLines(3);notes.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);notes.setText(prefs.getString("notes",""));body.addView(notes);
        LinearLayout export=new LinearLayout(this);button(export,"메모 저장",v->{saveFields();message("이 기기에 저장했습니다.");});button(export,"결과 파일 저장",v->export());body.addView(export);
    }
    private String report(){
        saveFields();StringBuilder s=new StringBuilder("iQ 3 저녁 시험 · 사용자 기록\n");
        s.append(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())).append("\nAndroid ").append(Build.VERSION.RELEASE).append(" / ").append(Build.MODEL).append("\n").append(installed()).append("\n\n");
        for(int i=0;i<TESTS.length;i++)s.append(i+1).append(". ").append(TESTS[i]).append(": ").append(RESULTS[Math.max(0,Math.min(3,prefs.getInt("test_"+i,0)))]).append('\n');
        return s.append("\n메모:\n").append(prefs.getString("notes","")).toString();
    }
    private void export(){
        saveFields();try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"Navi-evening-result.txt"),55);}
        catch(RuntimeException e){message("파일 저장 화면이 없습니다. 결과는 앱에 보관됩니다. 결과 화면을 촬영해 주세요.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=55 || result!=RESULT_OK || data==null || data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException();out.write(report().getBytes("UTF-8"));message("결과 파일을 저장했습니다.");}
        catch(Exception e){message("파일 저장 실패. 앱에 보관된 결과 화면을 촬영해 주세요.");}
    }
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){resumed=false;saveFields();Socket s=probe;if(s!=null)try{s.close();}catch(IOException ignored){}super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("tab",tab);}
}
