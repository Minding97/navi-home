package local.navi.diagnostic;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MirrorActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final Handler ui=new Handler();
    private ReceiverSession session;private Surface surface;
    private TextureView video;private LinearLayout panel;private TextView state,details,rate,shield;
    private FrameLayout root;private String diagnostics="",currentState="준비",report="";
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private boolean resumed,fullScreen;private long lastCount,lastTick;
    private final Runnable tick=new Runnable(){public void run(){
        ReceiverSession s=session;long now=SystemClock.elapsedRealtime();
        if(s!=null){
            double fps=lastTick==0?0:(s.frames-lastCount)*1000.0/Math.max(1,now-lastTick);
            boolean fresh=resumed&&s.lastFrame>0&&now-s.lastFrame<2000;
            String shownState=!fresh&&currentState.equals("영상 수신 중")?"영상 대기 · 새 프레임 없음":currentState;
            rate.setText(String.format(Locale.US,"%.1f fps · %s",Math.max(0,fps),shownState));lastCount=s.frames;lastTick=now;
            shield.setVisibility(fresh?View.GONE:View.VISIBLE);
            shield.setText(s.lastFrame>0?"영상 수신 중단\n연결 상태를 확인하세요":shownState);
        }
        ui.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root=new FrameLayout(this);root.setBackgroundColor(0xff111820);
        video=new TextureView(this);video.setSurfaceTextureListener(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        shield=text(24);shield.setGravity(Gravity.CENTER);shield.setBackgroundColor(0xff111820);
        root.addView(shield,new FrameLayout.LayoutParams(-1,-1));
        panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(18,8,18,8);panel.setBackgroundColor(0xff111820);
        TextView title=text(23);title.setText("휴대폰 지도 연결");title.setTextColor(0xff69d7c6);panel.addView(title);
        LinearLayout buttons=new LinearLayout(this);
        button(buttons,"연결 대기",v->startReceiver());
        button(buttons,"Wi-Fi 관리",v->startActivity(new Intent(this,WifiActivity.class)));
        button(buttons,"진단 저장",v->saveReport());
        button(buttons,"닫기",v->finish());panel.addView(buttons);
        state=text(18);state.setText("연결 대기를 누르세요.");panel.addView(state);
        ScrollView scroll=new ScrollView(this);details=text(16);scroll.addView(details);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout strip=new LinearLayout(this);strip.setGravity(Gravity.CENTER_VERTICAL);strip.setBackgroundColor(0xdd111820);
        rate=text(14);strip.addView(rate,new LinearLayout.LayoutParams(0,-2,1));
        Button toggle=new Button(this);toggle.setText("영상 / 연결 정보");toggle.setTextSize(14);toggle.setOnClickListener(v->{fullScreen=!fullScreen;panel.setVisibility(fullScreen?View.GONE:View.VISIBLE);});strip.addView(toggle);
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,48,Gravity.BOTTOM);root.addView(strip,bottom);panel.setPadding(18,8,18,52);
        setContentView(root);
    }
    private TextView text(int size){TextView t=new TextView(this);t.setTextColor(0xffecf1f5);t.setTextSize(size);t.setPadding(0,4,0,4);return t;}
    private void button(LinearLayout row,String text,View.OnClickListener click){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setOnClickListener(click);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
    private void message(String text){new AlertDialog.Builder(this).setMessage(text).setPositiveButton("확인",null).show();}
    private void startReceiver(){
        if(session!=null&&session.running){message("이미 연결 대기 중입니다. 새 코드는 이 화면을 닫았다 다시 열면 생성됩니다.");return;}
        session=new ReceiverSession(new ReceiverSession.Events(){
            public void state(String value){ui.post(()->{if(isFinishing())return;currentState=value;state.setText(connectionText()+"\n"+value);record(value);if(!value.equals("영상 수신 중")){shield.setVisibility(View.VISIBLE);shield.setText(value);}else{fullScreen=true;panel.setVisibility(View.GONE);}});}
            public void decoded(long now){/* The UI polls; never post one runnable per frame. */}
        });
        session.foreground=resumed;session.target=surface;lastCount=lastTick=0;session.start();
        diagnose();
    }
    private String connectionText(){
        String code=session==null?"":session.code.replaceAll("(.{4})(?!$)","$1-");
        return "주소: "+addresses()+" : "+local.navi.stream.Wire.PORT+"\n연결 코드: "+code;
    }
    private void record(String value){if(history.size()>=60)history.removeFirst();history.addLast(SystemClock.elapsedRealtime()+" "+value);}
    private static String addresses(){
        ArrayList<String> list=new ArrayList<>();
        try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();
                if(!n.isUp()||n.isLoopback())continue;
                Enumeration<InetAddress> aa=n.getInetAddresses();while(aa.hasMoreElements()){
                    InetAddress a=aa.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&!a.isLinkLocalAddress())list.add(a.getHostAddress());
                }
            }
        }catch(Exception ignored){}
        return list.isEmpty()?"IPv4 없음 · Wi-Fi 연결 필요":android.text.TextUtils.join(" / ",list);
    }
    private void diagnose(){
        new Thread(()->{
            StringBuilder s=new StringBuilder("Android "+Build.VERSION.RELEASE+" / "+Build.MODEL+"\n");
            s.append("Wi-Fi 기능 선언: ").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)?"있음":"없음");
            try{WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                s.append("\nWi-Fi 서비스: ").append(wifi==null?"없음":(wifi.isWifiEnabled()?"켜짐":"꺼짐"));
            }catch(Exception e){s.append("\nWi-Fi 상태 조회 실패: ").append(e.getClass().getSimpleName());}
            s.append("\nIP: ").append(addresses()).append("\n\nH.264 디코더 지원 목록 (실제 재생 확인은 별도)\n");
            try{int count=0;for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()){
                if(info.isEncoder())continue;for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase("video/avc")){
                    count++;s.append(info.getName());
                    try{s.append(" · 800×480 / 15fps: ").append(info.getCapabilitiesForType(type).getVideoCapabilities().areSizeAndRateSupported(800,480,15)?"지원 선언":"지원 미확인");}catch(Exception ignored){}
                    s.append('\n');
                }
            }if(count==0)s.append("없음\n");}catch(Exception e){s.append(e.getClass().getSimpleName());}
            s.append("\n같은 Wi-Fi 또는 휴대폰 핫스팟에 연결하세요.\nWi-Fi 꺼짐/기능 선언만으로 동글 유무를 확정하지 않습니다.\n전체 전송 지연은 휴대폰 지연 시험 화면과 비교해 측정합니다.");
            diagnostics=s.toString();ui.post(()->{if(!isFinishing()){details.setText(diagnostics);if(session!=null)state.setText(connectionText()+"\n"+currentState);}});
        },"navi-diagnostics").start();
    }
    private void saveReport(){
        report=diagnostics+"\n\n"+(session==null?"수신 미실행":session.metrics())+"\n상태 이력\n"+android.text.TextUtils.join("\n",history)
            +"\n\n실물 네이버지도/카메라/10분 안정성/전체 지연은 사용자가 별도로 확인해야 합니다.\n";
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"NaviMirror-diagnostics.txt"),3);}
        catch(Exception e){message(report);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==3&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(java.io.OutputStream o=getContentResolver().openOutputStream(data.getData())){if(o==null)throw new java.io.IOException();o.write(report.getBytes("UTF-8"));message("진단 결과를 저장했습니다.");}catch(Exception e){message("저장 실패: "+e.getClass().getSimpleName());}}
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(session!=null)session.foreground=true;ui.post(tick);record("화면 복귀");diagnose();}
    @Override protected void onPause(){resumed=false;if(session!=null)session.pauseRendering();shield.setVisibility(View.VISIBLE);shield.setText("화면 전환 중");ui.removeCallbacks(tick);record("화면 이탈");super.onPause();}
    @Override protected void onDestroy(){if(session!=null)session.stop();ui.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){surface=new Surface(t);if(session!=null)session.target=surface;fit(w,h);}
    private void fit(int w,int h){android.graphics.Matrix m=new android.graphics.Matrix();float ratio=Math.min(w/800f,h/480f);m.setScale(800f*ratio/w,480f*ratio/h,w/2f,h/2f);video.setTransform(m);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){fit(w,h);}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){if(session!=null)session.target=null;if(surface!=null)surface.release();surface=null;return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
}
