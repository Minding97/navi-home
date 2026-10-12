package local.navi.stream;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;

public final class WireTest {
    static int checks;
    static void check(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
    static void rejects(Throwing action,String msg)throws Exception{checks++;try{action.run();throw new AssertionError(msg);}catch(IOException|java.security.GeneralSecurityException expected){}}
    interface Throwing{void run()throws Exception;}
    static Wire.Writer writer(OutputStream out)throws Exception{
        Constructor<Wire.Writer> c=Wire.Writer.class.getDeclaredConstructor(DataOutputStream.class,byte[].class);c.setAccessible(true);return c.newInstance(new DataOutputStream(out),new byte[32]);
    }
    static Wire.Reader reader(byte[] bytes)throws Exception{
        Constructor<Wire.Reader> c=Wire.Reader.class.getDeclaredConstructor(DataInputStream.class,byte[].class);c.setAccessible(true);return c.newInstance(new DataInputStream(new ByteArrayInputStream(bytes)),new byte[32]);
    }
    static void handshake(boolean wrong)throws Exception{
        PipedInputStream si=new PipedInputStream(4096),ci=new PipedInputStream(4096);
        PipedOutputStream co=new PipedOutputStream(si),so=new PipedOutputStream(ci);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        Future<Wire.Packet> result=executor.submit(()->{try{return Wire.accept(si,so,"1234567890ABCDEF").packet();}finally{so.close();}});
        try{
            if(wrong){rejects(()->Wire.connect(ci,co,"0000000000000000"),"Wrong pairing key accepted");
                try{result.get(2,TimeUnit.SECONDS);throw new AssertionError();}catch(ExecutionException expected){check(expected.getCause() instanceof IOException,"Server must reject wrong key");}}
            else{Wire.Writer w=Wire.connect(ci,co,"1234-5678-90ab-cdef");w.packet(Wire.HEARTBEAT,new byte[0]);check(result.get(2,TimeUnit.SECONDS).type==Wire.HEARTBEAT,"Handshake stream mismatch");}
        }finally{si.close();ci.close();co.close();so.close();executor.shutdownNow();}
    }
    public static void main(String[] args)throws Exception{
        handshake(false);handshake(true);
        byte[] config=Wire.format(800,480,new byte[]{0,0,0,1,103},new byte[]{0,0,0,1,104});
        check(Wire.readFormat(config).width==800,"Format roundtrip");
        byte[] bad=config.clone();bad[2]=4;rejects(()->Wire.readFormat(bad),"Oversize format accepted");
        rejects(()->Wire.readFormat(Arrays.copyOf(config,13)),"Truncated config accepted");
        rejects(()->Wire.readFormat(Wire.format(800,480,new byte[0],new byte[0])),"Missing SPS accepted");
        byte[] content=new byte[100000];new Random(123).nextBytes(content);
        ByteArrayOutputStream raw=new ByteArrayOutputStream();Wire.Writer w=writer(raw);
        w.packet(Wire.FORMAT,config);int first=raw.size();w.packet(Wire.FRAME,Wire.frame(1234567,1,content));w.packet(Wire.HEARTBEAT,new byte[0]);
        Wire.Reader r=reader(raw.toByteArray());check(Arrays.equals(config,r.packet().data),"Config encryption roundtrip");
        Wire.Frame f=Wire.readFrame(r.packet().data);check(f.pts==1234567&&f.key()&&Arrays.equals(f.data,content),"Frame encryption roundtrip");check(r.packet().type==Wire.HEARTBEAT,"Sequence roundtrip");
        byte[] mutated=raw.toByteArray();mutated[20]^=1;rejects(()->reader(mutated).packet(),"Modified ciphertext accepted");
        byte[] shortPacket=Arrays.copyOf(raw.toByteArray(),first-2);rejects(()->reader(shortPacket).packet(),"Truncated packet accepted");
        ByteArrayOutputStream replay=new ByteArrayOutputStream();replay.write(raw.toByteArray(),0,first);replay.write(raw.toByteArray(),0,first);
        Wire.Reader replayReader=reader(replay.toByteArray());replayReader.packet();rejects(replayReader::packet,"Replay accepted");
        byte[] oversized={0x7f,(byte)0xff,(byte)0xff,(byte)0xff};rejects(()->reader(oversized).packet(),"Unbounded packet accepted");
        rejects(()->writer(new ByteArrayOutputStream()).packet(Wire.FRAME,new byte[Wire.MAX_PACKET]),"Oversize sender frame accepted");
        rejects(()->Wire.readFrame(new byte[12]),"Empty frame accepted");
        FrameQueue queue=new FrameQueue(2);Wire.Frame key=new Wire.Frame(0,1,new byte[]{1}),delta=new Wire.Frame(1,0,new byte[]{2});
        check(!queue.offer(delta),"Queue must initially wait for keyframe");check(queue.offer(key)&&queue.offer(delta),"Valid GOP rejected");
        check(!queue.offer(delta)&&queue.poll(1)==null,"Overflow must discard dependent GOP");check(queue.offer(key)&&queue.poll(1).key(),"Keyframe recovery failed");
        queue.reset();check(!queue.offer(delta),"Reconnect must wait for keyframe");
        check(Wire.newCode().matches("[0-9A-F]{16}"),"Invalid session code");
        System.out.println("PASS: "+checks+" protocol, authentication, malformed-input and frame-recovery checks");
    }
}
