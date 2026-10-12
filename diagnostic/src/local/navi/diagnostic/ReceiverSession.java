package local.navi.diagnostic;

import android.media.*;
import android.os.SystemClock;
import android.view.Surface;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import local.navi.stream.Wire;

/** Codec calls are serialized. Pause can release the codec without waiting for a socket read. */
final class ReceiverSession {
    interface Events { void state(String value); void decoded(long now); }
    final String code=Wire.newCode();
    volatile boolean running=true,foreground;
    volatile Surface target;
    volatile int renderEpoch;
    volatile long frames,bytes,connectedAt,lastFrame,decodeMaxMs;
    volatile String codecName="미실행";
    private final Events events;
    private volatile ServerSocket server;private volatile Socket peer;
    private MediaCodec codec;private Surface codecSurface;private Wire.VideoFormat format;
    private boolean waitingKey=true;private Thread worker;private int codecEpoch;
    ReceiverSession(Events events){this.events=events;}
    void start(){worker=new Thread(this::run,"navi-receiver");worker.start();}
    void pauseRendering(){
        foreground=false;lastFrame=0;renderEpoch++;
        new Thread(()->{
            synchronized(ReceiverSession.this){if(!foreground)release();}
        },"navi-video-pause").start();
    }
    void stop(){running=false;close(peer);try{if(server!=null)server.close();}catch(Exception ignored){} }
    private static void close(Socket s){try{if(s!=null)s.close();}catch(Exception ignored){} }
    private void run(){
        try(ServerSocket listener=new ServerSocket()){
            server=listener;listener.setReuseAddress(true);listener.bind(new InetSocketAddress(Wire.PORT));
            if(!running)return;
            events.state("연결 대기 · 휴대폰에서 주소와 코드를 입력하세요");
            while(running){
                try(Socket s=listener.accept()){
                    peer=s;s.setSoTimeout(5000);s.setTcpNoDelay(true);
                    Wire.Reader reader=Wire.accept(s.getInputStream(),s.getOutputStream(),code);
                    connectedAt=SystemClock.elapsedRealtime();frames=bytes=lastFrame=decodeMaxMs=0;
                    format=null;waitingKey=true;events.state("연결됨 · 화면 공유 대기");
                    while(running){
                        Wire.Packet p=reader.packet();bytes+=p.data.length;
                        if(p.type==Wire.FORMAT){
                            Wire.VideoFormat next=Wire.readFormat(p.data);
                            if(format==null||format.width!=next.width||format.height!=next.height||
                                !Arrays.equals(format.csd0,next.csd0)||!Arrays.equals(format.csd1,next.csd1))release();
                            format=next;
                        } else if(p.type==Wire.FRAME){
                            render(Wire.readFrame(p.data));
                        } else if(p.type==Wire.HIDDEN){
                            release();lastFrame=0;events.state("휴대폰에서 공유한 앱이 보이지 않습니다");
                        } else if(p.type!=Wire.HEARTBEAT)throw new java.io.IOException("지원하지 않는 메시지");
                        if(!foreground||target!=codecSurface)release();
                    }
                }catch(Exception e){
                    if(running)events.state("연결 끊김 · "+e.getClass().getSimpleName()+": "+safeMessage(e));
                    // A failed handshake cannot spin at full speed on a busy LAN.
                    if(running)try{Thread.sleep(250);}catch(InterruptedException ignored){}
                }finally{peer=null;release();lastFrame=0;}
            }
        }catch(Exception e){if(running)events.state("수신 시작 실패 · "+safeMessage(e));}
        finally{running=false;release();}
    }
    private static String safeMessage(Exception e){return e.getMessage()==null?"상세 정보 없음":e.getMessage();}
    private synchronized void release(){
        if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
        codec=null;codecSurface=null;waitingKey=true;
    }
    private synchronized void render(Wire.Frame f) throws Exception {
        Surface surface=target;
        if(!foreground||surface==null||!surface.isValid()){release();return;}
        if(codecSurface!=surface||codecEpoch!=renderEpoch)release();
        if(format==null)return;
        if(waitingKey&&!f.key())return;
        if(codec==null){
            MediaFormat mf=MediaFormat.createVideoFormat("video/avc",format.width,format.height);
            mf.setByteBuffer("csd-0",ByteBuffer.wrap(format.csd0));
            if(format.csd1.length>0)mf.setByteBuffer("csd-1",ByteBuffer.wrap(format.csd1));
            String name=new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(mf);
            if(name==null)throw new java.io.IOException("H.264 디코더 없음");
            codec=MediaCodec.createByCodecName(name);codecName=name;
            codec.configure(mf,surface,null,0);codec.start();codecSurface=surface;codecEpoch=renderEpoch;events.state("영상 수신 중");
        }
        long begin=SystemClock.elapsedRealtime();
        drain();int index=codec.dequeueInputBuffer(10000);
        if(index<0){release();return;}
        ByteBuffer input=codec.getInputBuffer(index);
        if(input==null||input.capacity()<f.data.length)throw new java.io.IOException("디코더 입력 버퍼 부족");
        input.clear();input.put(f.data);codec.queueInputBuffer(index,0,f.data.length,f.pts,0);waitingKey=false;
        drain();decodeMaxMs=Math.max(decodeMaxMs,SystemClock.elapsedRealtime()-begin);
    }
    private void drain(){
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        for(int i=0;i<12&&codec!=null;i++){
            int out=codec.dequeueOutputBuffer(info,0);
            if(out==MediaCodec.INFO_TRY_AGAIN_LATER)break;
            if(out>=0){
                boolean show=foreground&&target==codecSurface&&codecSurface.isValid();
                codec.releaseOutputBuffer(out,show);
                if(show){frames++;lastFrame=SystemClock.elapsedRealtime();events.decoded(lastFrame);}
            }
        }
    }
    String metrics(){
        long seconds=connectedAt==0?0:(SystemClock.elapsedRealtime()-connectedAt)/1000;
        return "연결 "+seconds+"초 · 디코더 출력 "+frames+"프레임 · 수신 "+(bytes/1024)+" KiB\n"
            +"디코더: "+codecName+"\n로컬 프레임 처리 최댓값: "+decodeMaxMs+" ms (전체 전송 지연과 다름)";
    }
}
