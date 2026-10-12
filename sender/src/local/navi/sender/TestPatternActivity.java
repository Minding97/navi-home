package local.navi.sender;
import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.view.*;

/** A changing source clock lets a second camera measure glass-to-glass delay. */
public class TestPatternActivity extends Activity {
    private Pattern pattern;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);pattern=new Pattern();setContentView(pattern);}
    @Override protected void onResume(){super.onResume();pattern.running=true;pattern.invalidate();}
    @Override protected void onPause(){pattern.running=false;super.onPause();}
    private class Pattern extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);boolean running;
        Pattern(){super(TestPatternActivity.this);setOnClickListener(v->finish());}
        @Override protected void onDraw(Canvas c){long t=SystemClock.elapsedRealtime();c.drawColor(0xff111820);p.setColor(0xff69d7c6);p.setTextSize(Math.min(getWidth()/12f,72));
            c.drawText(String.format(java.util.Locale.US,"%06d.%03d",t/1000,t%1000),24,getHeight()/2f,p);
            float x=(t%3000)*getWidth()/3000f;c.drawRect(x,getHeight()*0.7f,x+25,getHeight()*0.85f,p);
            p.setColor(Color.WHITE);p.setTextSize(24);c.drawText("지연 시험 · 누르면 돌아갑니다",24,getHeight()/2f+55,p);
            if(running)postInvalidateDelayed(33);
        }
    }
}
