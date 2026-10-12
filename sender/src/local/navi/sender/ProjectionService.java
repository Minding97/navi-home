package local.navi.sender;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.Surface;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import local.navi.stream.*;

public class ProjectionService extends Service {
    public static volatile String status="공유 대기";
    public static volatile boolean active;
    private final Handler main=new Handler();
    private final FrameQueue frames=new FrameQueue(6);
    private final AtomicBoolean sync=new AtomicBoolean(true);
    private volatile boolean running,visible=true;
    private volatile byte[] streamFormat;
    private volatile Socket socket;
    private volatile long writeBegan;
    private MediaProjection projection;private VirtualDisplay display;private MediaCodec encoder;private Surface encoderSurface;
    private Thread capture,network;private String host,code;
    private int sourceWidth,sourceHeight;
    private boolean screenReceiverRegistered;
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(running&&Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                status="화면이 꺼져 공유를 종료했습니다. 다시 시작하려면 승인이 필요합니다.";
                stopSelf();
            }
        }
    };
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!running)return;
        if(writeBegan>0&&SystemClock.elapsedRealtime()-writeBegan>4000)closeSocket();
        main.postDelayed(this,1000);
    }};
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||intent.getParcelableExtra("consent")==null){stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("mirror","화면 공유",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(this,"mirror").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Navi 화면 전송")
            .setContentText("화면을 내비게이션으로 공유 중 · 앱에서 중지할 수 있습니다").setContentIntent(open).setOngoing(true).build();
        try{
            startForeground(1,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            host=intent.getStringExtra("host");code=intent.getStringExtra("code");
            if(host==null||code==null)throw new IllegalArgumentException("연결 정보 없음");
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(intent.getIntExtra("result",0),(Intent)intent.getParcelableExtra("consent"));
            if(projection==null)throw new IllegalStateException("화면 공유 승인 없음");
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){if(running){status="화면 공유가 종료됐습니다. 다시 시작하려면 승인이 필요합니다.";stopSelf();}}
                @Override public void onCapturedContentResize(int w,int h){sourceWidth=w;sourceHeight=h;/* Android 12L+ fits content into our fixed landscape canvas without stretching. */}
                @Override public void onCapturedContentVisibilityChanged(boolean value){visible=value;frames.reset();sync.set(true);}
            },main);
            running=true;active=true;status="화면 공유 준비 중";
            IntentFilter screenFilter=new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,screenFilter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver,screenFilter);
            screenReceiverRegistered=true;
            capture=new Thread(this::captureLoop,"navi-capture");capture.start();main.post(watchdog);
        }catch(Exception e){status="화면 공유 시작 실패: "+e.getClass().getSimpleName()+" · "+e.getMessage();stopSelf();}
        return START_NOT_STICKY;
    }
    private void captureLoop(){
        try{
            MediaFormat mf=MediaFormat.createVideoFormat("video/avc",800,480);
            mf.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mf.setInteger(MediaFormat.KEY_BIT_RATE,2_000_000);mf.setInteger(MediaFormat.KEY_FRAME_RATE,15);
            mf.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER,15);mf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            mf.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,66_667L);
            mf.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            encoder=MediaCodec.createEncoderByType("video/avc");encoder.configure(mf,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderSurface=encoder.createInputSurface();encoder.start();
            // Exactly one virtual display is created for this user-approved projection token.
            display=projection.createVirtualDisplay("NaviMirror",800,480,getResources().getConfiguration().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,encoderSurface,null,null);
            network=new Thread(this::networkLoop,"navi-transmit");network.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(running){
                if(sync.getAndSet(false)){Bundle request=new Bundle();request.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0);encoder.setParameters(request);}
                int index=encoder.dequeueOutputBuffer(info,20_000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat format=encoder.getOutputFormat();streamFormat=Wire.format(800,480,copy(format.getByteBuffer("csd-0")),copy(format.getByteBuffer("csd-1")));
                }else if(index>=0){
                    try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&visible){
                        ByteBuffer buffer=encoder.getOutputBuffer(index);if(buffer==null)continue;
                        buffer.position(info.offset);buffer.limit(info.offset+info.size);byte[] bytes=new byte[info.size];buffer.get(bytes);
                        if(!frames.offer(new Wire.Frame(info.presentationTimeUs,info.flags,bytes)))sync.set(true);
                    }}finally{encoder.releaseOutputBuffer(index,false);}
                }
            }
        }catch(Exception e){if(running){status="영상 인코딩 실패: "+e.getClass().getSimpleName()+" · "+e.getMessage();main.post(this::stopSelf);}}
        finally{
            if(display!=null){display.release();display=null;}
            if(encoder!=null){try{encoder.stop();}catch(Exception ignored){}try{encoder.release();}catch(Exception ignored){}encoder=null;}
            if(encoderSurface!=null){encoderSurface.release();encoderSurface=null;}
        }
    }
    private static byte[] copy(ByteBuffer buffer){if(buffer==null)return new byte[0];ByteBuffer b=buffer.duplicate();byte[] bytes=new byte[b.remaining()];b.get(bytes);return bytes;}
    private void networkLoop(){
        while(running){
            try(Socket connection=new Socket()){
                socket=connection;status="내비 연결 중 · "+host;
                connection.connect(new InetSocketAddress(host,Wire.PORT),4000);connection.setTcpNoDelay(true);connection.setSoTimeout(5000);
                Wire.Writer writer=Wire.connect(connection.getInputStream(),connection.getOutputStream(),code);
                frames.reset();sync.set(true);byte[] sentFormat=null;long count=0,lastStatus=0;
                while(running){
                    byte[] config=streamFormat;Wire.Frame frame=frames.poll(500);
                    writeBegan=SystemClock.elapsedRealtime();
                    try{
                        if(config!=null&&!Arrays.equals(config,sentFormat)){writer.packet(Wire.FORMAT,config);sentFormat=config;}
                        if(!visible){writer.packet(Wire.HIDDEN,new byte[0]);}
                        else if(frame!=null&&config!=null){writer.packet(Wire.FRAME,Wire.frame(frame.pts,frame.flags,frame.data));count++;}
                        else writer.packet(Wire.HEARTBEAT,new byte[0]);
                    }finally{writeBegan=0;}
                    long now=SystemClock.elapsedRealtime();if(now-lastStatus>1000){status="전송 중 · "+count+"프레임\n800×480 · 목표 15fps / 2Mbps\n공유 원본 "+sourceWidth+"×"+sourceHeight+" · 대기열 생략 "+frames.dropped();lastStatus=now;}
                }
            }catch(Exception e){if(running)status="연결 대기 · 주소 / 코드 / 핫스팟 확인\n"+e.getClass().getSimpleName();}
            finally{socket=null;frames.reset();}
            if(running)try{Thread.sleep(2000);}catch(InterruptedException ignored){}
        }
    }
    private void closeSocket(){try{if(socket!=null)socket.close();}catch(Exception ignored){} }
    @Override public void onDestroy(){running=false;active=false;main.removeCallbacksAndMessages(null);closeSocket();
        if(screenReceiverRegistered){unregisterReceiver(screenReceiver);screenReceiverRegistered=false;}
        if(network!=null)network.interrupt();if(projection!=null){projection.stop();projection=null;}
        if(!status.contains("실패")&&!status.contains("종료"))status="전송을 중지했습니다.";
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
