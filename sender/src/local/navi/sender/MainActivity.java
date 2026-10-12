package local.navi.sender;

import android.app.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import local.navi.stream.Wire;

public class MainActivity extends Activity {
    private EditText address,code;private TextView status;private Button start;
    private final Handler handler=new Handler();
    private String pendingHost,pendingCode;
    private final Runnable refresh=new Runnable(){public void run(){
        status.setText(ProjectionService.status);start.setEnabled(!ProjectionService.active);handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);root.setBackgroundColor(0xff111820);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(24,24+insets.getSystemWindowInsetTop(),24,24+insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);root.requestApplyInsets();
        TextView title=text("Navi 화면 전송",28);title.setTextColor(0xff69d7c6);root.addView(title);
        root.addView(text("휴대폰 핫스팟을 켜고 내비를 연결하세요.\n집에서는 두 기기를 같은 공유기에 연결해도 됩니다.",17));
        root.addView(text("내비 화면의 IPv4 주소",16));address=new EditText(this);address.setId(1001);address.setTextColor(0xffecf1f5);address.setSingleLine();address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setHint("예: 192.168.43.123");address.setHintTextColor(0xff929ca5);
        address.setText(getPreferences(0).getString("host",""));root.addView(address);
        root.addView(text("연결 코드 · 내비에 표시된 16자리",16));code=new EditText(this);code.setId(1002);code.setTextColor(0xffecf1f5);code.setSingleLine();code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setHint("ABCD-1234-EF56-7890");code.setHintTextColor(0xff929ca5);root.addView(code);
        start=button(root,"화면 공유 시작",v->requestShare());
        button(root,"전송 중지",v->{stopService(new Intent(this,ProjectionService.class));});
        button(root,"지연 시험 화면",v->startActivity(new Intent(this,TestPatternActivity.class)));
        status=text(ProjectionService.status,18);root.addView(status);
        root.addView(text("네이버지도 하나만 공유하거나 전체 화면을 선택할 수 있습니다.\n지연 시험은 ‘전체 화면’을 공유한 뒤 시험 화면을 여세요.\n지도 조작과 음성 안내는 휴대폰에서 사용합니다.\n휴대폰을 잠그거나 공유를 끝내면 다시 승인이 필요합니다.\n영상은 연결 코드로 암호화해 내비에 전송하며 저장하지 않습니다.",15));
        if(b!=null){pendingHost=b.getString("pendingHost");pendingCode=b.getString("pendingCode");}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},8);
    }
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(0xffecf1f5);v.setTextSize(size);v.setPadding(0,10,0,10);return v;}
    private Button button(LinearLayout row,String value,View.OnClickListener click){Button b=new Button(this);b.setText(value);b.setOnClickListener(click);row.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void requestShare(){
        if(ProjectionService.active)return;
        String host=address.getText().toString().trim(),key=Wire.normalizeCode(code.getText().toString());
        String[] parts=host.split("\\.");boolean valid=parts.length==4;
        for(String part:parts)try{valid&=part.matches("[0-9]{1,3}")&&Integer.parseInt(part)<=255;}catch(Exception e){valid=false;}
        if(!valid||!key.matches("[0-9A-F]{16}")){ProjectionService.status="내비 화면의 IPv4 주소와 16자리 연결 코드를 확인하세요.";status.setText(ProjectionService.status);return;}
        pendingHost=host;pendingCode=key;getPreferences(0).edit().putString("host",host).apply();
        startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),1);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("pendingHost",pendingHost);b.putString("pendingCode",pendingCode);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request!=1)return;
        if(result!=RESULT_OK||data==null){ProjectionService.status="화면 공유가 취소됐습니다.";status.setText(ProjectionService.status);return;}
        if(pendingHost==null||pendingCode==null){status.setText("주소를 다시 입력하고 공유를 시작하세요.");return;}
        try{startForegroundService(new Intent(this,ProjectionService.class).putExtra("host",pendingHost).putExtra("code",pendingCode).putExtra("result",result).putExtra("consent",data));}
        catch(RuntimeException e){status.setText("공유 시작 실패: "+e.getClass().getSimpleName());}
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
