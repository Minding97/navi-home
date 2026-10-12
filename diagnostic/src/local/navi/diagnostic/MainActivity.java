package local.navi.diagnostic;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.hardware.usb.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline home for the 800x480 iQ 3. No overlays, route storage or network dependency. */
public class MainActivity extends Activity {
    static final int BG=0xff101718, CARD=0xff1b2527, INK=0xffeef4f2, MUTED=0xffa7b8b4, TEAL=0xff68ddc1;
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView clock,date,speed,status,distance,elapsed,accuracy;
    private TripService trip;
    private boolean bound,resumed;
    private int page;
    private float scale=1;
    private String serviceError="GPS 준비 중";
    private final Handler handler=new Handler();
    private final Runnable tick=new Runnable(){public void run(){if(!resumed)return;update();handler.postDelayed(this,1000);}};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){trip=((TripService.LocalBinder)b).service();update();}
        public void onServiceDisconnected(ComponentName n){trip=null;serviceError="GPS 세션 종료 · 설정에서 다시 시작";update();}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("offline_home",0);
        if(!BootReceiver.preferences(this).contains("boot_enabled"))BootReceiver.preferences(this).edit().putBoolean("boot_enabled",true).apply();
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        scale=Math.max(.6f,Math.min(dm.widthPixels/800f,dm.heightPixels/480f));
        if(state!=null)page=state.getInt("page",0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();showPage();
    }
    @Override protected void onResume(){super.onResume();resumed=true;immersive();brightness();startSession();showPage();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(connection);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("page",page);super.onSaveInstanceState(b);}
    @Override public void onBackPressed(){if(page!=0){page=0;showPage();}else{confirmExit();}}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private void startSession(){
        Intent i=new Intent(this,TripService.class);
        try{
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            if(!bound)bound=bindService(i,connection,Context.BIND_AUTO_CREATE);
        }catch(RuntimeException e){serviceError="GPS 시작 실패 · "+e.getClass().getSimpleName();}
        if(prefs.getBoolean("gps_enabled",true)&&!TripService.permitted(this)&&Build.VERSION.SDK_INT>=23&&!prefs.getBoolean("permission_asked",false)){
            prefs.edit().putBoolean("permission_asked",true).apply();requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},6);
        }
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){if(r==6){startSession();update();}}
    private int px(float n){return Math.round(n*scale);}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(14));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,px(h)));}
    private Button button(String label,Runnable click){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(19));b.setPadding(px(12),px(4),px(12),px(4));b.setMinHeight(px(56));b.setMinimumHeight(px(56));b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxLines(2);b.setEllipsize(TextUtils.TruncateAt.END);b.setBackground(shape(CARD));b.setOnClickListener(v->click.run());return b;}
    private void weighted(LinearLayout row,View v,float weight,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,height<0?height:px(height),weight);if(row.getChildCount()>0)p.leftMargin=px(12);row.addView(v,p);}
    private void header(String title){
        LinearLayout h=row();TextView t=text(title,25,INK);t.setTypeface(Typeface.create("sans-serif-medium",0));h.addView(t,new LinearLayout.LayoutParams(0,-1,1));t.setGravity(Gravity.CENTER_VERTICAL);
        Button apps=button(page==0?"앱 목록":"홈",()->{page=page==0?1:0;showPage();});h.addView(apps,new LinearLayout.LayoutParams(px(104),-1));
        Button settings=button("설정",()->{page=2;showPage();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(82),-1);p.leftMargin=px(10);h.addView(settings,p);root.addView(h,new LinearLayout.LayoutParams(-1,px(56)));space(root,12);
    }
    private void showPage(){
        speed=null;root=column();root.setBackgroundColor(BG);root.setPadding(px(20),px(16),px(20),px(16));setContentView(root);
        header(page==0?"NAVI  /  HOME":page==1?"앱 목록":"설정 · 진단");
        if(page==0)home();else if(page==1)apps();else settings();
        update();
    }
    private LinearLayout panel(){LinearLayout p=column();p.setBackground(shape(CARD));p.setPadding(px(20),px(12),px(20),px(12));return p;}
    private void home(){
        LinearLayout main=row();main.setGravity(Gravity.FILL);
        LinearLayout time=panel();time.setGravity(Gravity.CENTER_VERTICAL);date=text("",18,MUTED);clock=text("--:--",68,INK);clock.setTypeface(Typeface.create("sans-serif-light",0));
        time.addView(date);space(time,8);time.addView(clock);space(time,8);time.addView(text("인터넷 없이, 지금의 주행",16,MUTED));weighted(main,time,1,-1);
        LinearLayout velocity=panel();velocity.setBackground(shape(0xff20322f));
        status=text("GPS 준비 중",17,TEAL);velocity.addView(status);
        LinearLayout digits=row();speed=text("—",92,INK);speed.setTypeface(Typeface.create("sans-serif-light",0));digits.addView(speed,new LinearLayout.LayoutParams(0,-1,1));speed.setGravity(Gravity.CENTER_VERTICAL);TextView unit=text("km/h",21,TEAL);digits.addView(unit);velocity.addView(digits,new LinearLayout.LayoutParams(-1,0,1));
        velocity.addView(text("GPS 속도",16,MUTED));weighted(main,velocity,1,-1);root.addView(main,new LinearLayout.LayoutParams(-1,0,1));space(root,12);
        LinearLayout stats=row();distance=metric(stats,"이번 이동거리","0.00 km");elapsed=metric(stats,"세션 경과 시간","00:00:00");accuracy=metric(stats,"GPS 수신 정확도","—");root.addView(stats,new LinearLayout.LayoutParams(-1,px(78)));space(root,12);
        root.addView(text("즐겨찾기",15,MUTED),new LinearLayout.LayoutParams(-1,px(24)));
        LinearLayout favorites=row();for(int i=0;i<3;i++){final int slot=i;String key=prefs.getString("favorite_"+i,"");String label=label(key);Button b=button(label==null?"＋ 앱 추가":label,()->{if(label(key)==null)chooseFavorite(slot);else launch(key);});b.setOnLongClickListener(v->{chooseFavorite(slot);return true;});weighted(favorites,b,1,64);}root.addView(favorites,new LinearLayout.LayoutParams(-1,px(64)));
    }
    private TextView metric(LinearLayout row,String name,String value){LinearLayout p=panel();p.setPadding(px(18),px(10),px(18),px(10));p.addView(text(name,15,MUTED));space(p,6);TextView t=text(value,25,INK);p.addView(t);weighted(row,p,1,-1);return t;}
    private void update(){
        if(speed==null)return;
        Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",Locale.KOREA).format(now));date.setText(new SimpleDateFormat("M월 d일  EEEE",Locale.KOREA).format(now));
        if(trip==null){speed.setText("—");status.setText(serviceError);accuracy.setText("—");return;}
        TripMetrics.Snapshot s=trip.snapshot();
        speed.setText(TripMetrics.finite(s.speedKmh)?String.format(Locale.US,"%.0f",s.speedKmh):"—");
        String state=trip.issue();if(state.isEmpty())state=!s.fresh?"GPS 신호 대기":!s.quality?"GPS 정확도 낮음":!TripMetrics.finite(s.speedKmh)?"위치 수신 · 속도 없음":"GPS 수신 중";
        status.setText(state);status.setTextColor(s.fresh&&s.quality?TEAL:MUTED);
        distance.setText(String.format(Locale.US,"%.2f km",s.meters/1000));long sec=s.elapsedMs/1000;
        elapsed.setText(String.format(Locale.US,"%02d:%02d:%02d",sec/3600,sec/60%60,sec%60));
        accuracy.setText(s.fresh&&TripMetrics.finite(s.accuracy)?String.format(Locale.US,"±%.0f m",s.accuracy):"—");
    }
    private List<ResolveInfo> installed(){
        Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ResolveInfo r:getPackageManager().queryIntentActivities(i,0)){
            if(r.activityInfo==null||!r.activityInfo.exported||!r.activityInfo.enabled)continue;
            String pkg=r.activityInfo.packageName;
            if(pkg.equals(getPackageName())||pkg.toLowerCase(Locale.US).contains("atlan"))continue;
            String key=new ComponentName(pkg,r.activityInfo.name).flattenToString();if(seen.add(key))out.add(r);
        }
        Collections.sort(out,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));return out;
    }
    private String label(String key){try{ComponentName c=ComponentName.unflattenFromString(key);if(c==null)return null;ActivityInfo a=getPackageManager().getActivityInfo(c,0);if(!a.enabled||!a.exported)return null;return a.loadLabel(getPackageManager()).toString();}catch(Exception e){return null;}}
    private String component(ResolveInfo r){return new ComponentName(r.activityInfo.packageName,r.activityInfo.name).flattenToString();}
    private void launch(String key){try{ComponentName c=ComponentName.unflattenFromString(key);if(c==null)throw new IllegalArgumentException();startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){message("앱을 열 수 없습니다. 즐겨찾기를 다시 지정해 주세요.");}}
    private void chooseFavorite(int slot){
        List<ResolveInfo> items=installed();String[] labels=new String[items.size()+1];labels[0]="즐겨찾기 비우기";for(int i=0;i<items.size();i++)labels[i+1]=items.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("즐겨찾기 "+(slot+1)).setItems(labels,(d,n)->{prefs.edit().putString("favorite_"+slot,n==0?"":component(items.get(n-1))).apply();showPage();}).setNegativeButton("취소",null).show();
    }
    private LinearLayout scrollBody(){ScrollView s=new ScrollView(this);s.setFillViewport(true);LinearLayout body=column();s.addView(body);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));return body;}
    private void apps(){LinearLayout body=scrollBody();List<ResolveInfo> list=installed();body.addView(text("실행할 앱을 선택하세요 · 즐겨찾기는 설정에서 편집",17,MUTED));space(body,12);LinearLayout line=null;for(int i=0;i<list.size();i++){if(i%3==0){line=row();body.addView(line,new LinearLayout.LayoutParams(-1,px(84)));space(body,12);}ResolveInfo r=list.get(i);Button b=button(r.loadLabel(getPackageManager()).toString(),()->launch(component(r)));weighted(line,b,1,84);}if(list.isEmpty())body.addView(text("실행 가능한 앱을 찾지 못했습니다.",20,INK));}
    private void section(LinearLayout body,String label){space(body,14);body.addView(text(label,18,TEAL));space(body,10);}
    private void settings(){
        LinearLayout body=scrollBody();body.addView(text("Navi 홈 0.6  ·  오프라인",18,MUTED));
        buttons(body,new String[]{"사용 안내"},new Runnable[]{()->guide()});
        section(body,"화면과 소리");
        TextView light=text("홈 밝기 · "+prefs.getInt("brightness",80)+"%",18,INK);body.addView(light);SeekBar br=new SeekBar(this);br.setMax(80);br.setProgress(prefs.getInt("brightness",80)-20);body.addView(br,new LinearLayout.LayoutParams(-1,px(52)));br.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){if(user){prefs.edit().putInt("brightness",n+20).apply();light.setText("홈 밝기 · "+(n+20)+"%");brightness();}}});
        final AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);TextView volume=text("미디어 음량 · "+audio.getStreamVolume(AudioManager.STREAM_MUSIC)+" / "+max,18,INK);body.addView(volume);SeekBar vol=new SeekBar(this);vol.setMax(max);vol.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));body.addView(vol,new LinearLayout.LayoutParams(-1,px(52)));vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){if(user)try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);volume.setText("미디어 음량 · "+audio.getStreamVolume(AudioManager.STREAM_MUSIC)+" / "+b.getMax());}catch(RuntimeException e){message("음량 변경 실패 · 파인 설정에서 확인해 주세요.");}}});
        section(body,"GPS와 시작 화면");
        checkbox(body,"GPS 수신 · 카메라 전환 중에도 현재 세션 유지",prefs.getBoolean("gps_enabled",true),value->{prefs.edit().putBoolean("gps_enabled",value).apply();if(value&&!TripService.permitted(this)&&Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},6);startSession();});
        checkbox(body,"완전 부팅 후 홈 자동 실행",BootReceiver.preferences(this).getBoolean("boot_enabled",false),value->BootReceiver.preferences(this).edit().putBoolean("boot_enabled",value).apply());
        body.addView(text("현재 세션만 집계하며 좌표·이동 경로는 저장하지 않습니다.\nGPS 공백·낮은 정확도·정지 흔들림은 거리에서 제외합니다.\n앱 종료·기기 재부팅·프로세스 종료 시 집계는 초기화됩니다.",16,MUTED));
        buttons(body,new String[]{"세션 초기화","위치 설정","자동 실행 해제"},new Runnable[]{()->new AlertDialog.Builder(this).setTitle("현재 세션 초기화").setMessage("이동거리와 경과 시간을 0으로 되돌릴까요?").setNegativeButton("취소",null).setPositiveButton("초기화",(d,w)->{if(trip!=null)trip.reset();}).show(),()->openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS),()->restoreHome()});
        section(body,"즐겨찾기 편집");buttons(body,new String[]{"첫 번째 앱","두 번째 앱","세 번째 앱"},new Runnable[]{()->chooseFavorite(0),()->chooseFavorite(1),()->chooseFavorite(2)});
        section(body,"기기와 연결");
        body.addView(text("iQ 3의 무선 연결은 호환 Wi-Fi 동글을 확인해야 합니다.\n현재 홈은 네트워크 없이 사용할 수 있습니다.",16,MUTED));
        buttons(body,new String[]{"기기 상태","Wi-Fi 관리","연결 시험"},new Runnable[]{()->deviceInfo(),()->startActivity(new Intent(this,WifiActivity.class)),()->startActivity(new Intent(this,CarTestActivity.class))});
        buttons(body,new String[]{"상세 GPS 진단","파인 설정","파인 홈"},new Runnable[]{()->startActivity(new Intent(this,DiagnosticActivity.class)),()->openPackage("com.finedigital.setting"),()->openPackage("com.finedigital.homescreen")});
        buttons(body,new String[]{"기본 홈 설정","세션 종료"},new Runnable[]{()->openSettings(Settings.ACTION_HOME_SETTINGS),()->confirmExit()});space(body,10);
    }
    interface Toggle{void changed(boolean value);}
    private void checkbox(LinearLayout body,String name,boolean value,Toggle action){CheckBox c=new CheckBox(this);c.setText(name);c.setTextColor(INK);c.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(17));c.setMinHeight(px(56));c.setChecked(value);c.setOnCheckedChangeListener((b,v)->action.changed(v));body.addView(c);}
    private void buttons(LinearLayout body,String[] labels,Runnable[] actions){space(body,10);LinearLayout r=row();for(int i=0;i<labels.length;i++)weighted(r,button(labels[i],actions[i]),1,64);body.addView(r);}
    private void brightness(){WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=prefs.getInt("brightness",80)/100f;getWindow().setAttributes(p);}
    private void openSettings(String action){try{startActivity(new Intent(action));}catch(RuntimeException e){message("이 기기에서 해당 설정을 열 수 없습니다. 파인 설정을 이용해 주세요.");}}
    private void openPackage(String name){try{Intent i=getPackageManager().getLaunchIntentForPackage(name);if(i==null)throw new IllegalStateException();startActivity(i);}catch(RuntimeException e){message("해당 제조사 앱을 찾지 못했습니다.");}}
    private void restoreHome(){new AlertDialog.Builder(this).setTitle("자동 실행 해제").setMessage("부팅 자동 실행과 우리 앱의 기본 홈 지정을 해제합니다.").setNegativeButton("취소",null).setPositiveButton("해제",(d,w)->{BootReceiver.preferences(this).edit().putBoolean("boot_enabled",false).apply();getPackageManager().clearPackagePreferredActivities(getPackageName());showPage();openSettings(Settings.ACTION_HOME_SETTINGS);}).show();}
    private void confirmExit(){new AlertDialog.Builder(this).setTitle("세션 종료").setMessage("GPS 수신을 끝내고 현재 집계를 지웁니다. 다시 열면 새 세션을 시작합니다.").setNegativeButton("취소",null).setPositiveButton("종료",(d,w)->{if(bound){unbindService(connection);bound=false;}trip=null;stopService(new Intent(this,TripService.class));finish();}).show();}
    private void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("확인",null).show();}
    private void guide(){
        StringBuilder s=new StringBuilder();
        try(java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(getResources().openRawResource(getResources().getIdentifier("offline_guide","raw",getPackageName())),"UTF-8"))){
            String line;while((line=r.readLine())!=null)s.append(line).append('\n');
        }catch(Exception e){s.append("설치 파일과 함께 제공된 OFFLINE-HOME-KO.txt를 확인해 주세요.");}
        TextView t=text(s.toString(),18,INK);t.setPadding(px(20),px(12),px(20),px(12));ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle("Navi 홈 사용 안내").setView(scroll).setPositiveButton("닫기",null).show();
    }
    private void deviceInfo(){
        StringBuilder s=new StringBuilder(Build.MANUFACTURER+" / "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT);
        s.append("\n기본 홈: ").append(BootReceiver.isDefaultHome(this)?"Navi 홈":"다른 앱 또는 미지정");
        s.append("\n내부 여유 공간: ").append(String.format(Locale.US,"%.1f GB",getFilesDir().getUsableSpace()/1e9));
        File external=getExternalFilesDir(null);if(external!=null)s.append("\n앱 외부 저장소 여유: ").append(String.format(Locale.US,"%.1f GB",external.getUsableSpace()/1e9));
        s.append("\nGPS: ").append(trip==null?serviceError:trip.issue().isEmpty()?"수신 상태는 홈에서 확인":trip.issue());
        s.append("\n\nAndroid Wi-Fi 기능 선언: ").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)?"있음":"없음");
        s.append("\n기능 선언만으로 동글 장착·호환 여부를 판단할 수 없습니다.");
        UsbManager usb=(UsbManager)getSystemService(USB_SERVICE);s.append("\n연결된 USB 장치:");
        if(usb==null||usb.getDeviceList().isEmpty())s.append(" 없음");else for(UsbDevice u:usb.getDeviceList().values())s.append(String.format(Locale.US,"\nVID %04X / PID %04X · class %d",u.getVendorId(),u.getProductId(),u.getDeviceClass()));
        SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);s.append("\n\n공개 센서:");List<Sensor> sensors=sm==null?Collections.<Sensor>emptyList():sm.getSensorList(Sensor.TYPE_ALL);if(sensors.isEmpty())s.append(" 없음");for(Sensor sensor:sensors)s.append("\n").append(sensor.getName());
        TextView t=text(s.toString(),17,INK);t.setPadding(px(20),px(12),px(20),px(12));ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle("기기 상태").setView(scroll).setPositiveButton("확인",null).show();
    }
}
