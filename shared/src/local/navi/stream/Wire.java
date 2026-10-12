package local.navi.stream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned, bounded LAN stream. A new random pairing code is shown per receiver session. */
public final class Wire {
    public static final int PORT=52741, MAGIC=0x4e415631, MAX_PACKET=1024*1024;
    public static final int FORMAT=1, FRAME=2, HEARTBEAT=3, HIDDEN=4;
    private static final SecureRandom RANDOM=new SecureRandom();
    private Wire() {}
    public static String newCode() {
        byte[] bytes=random(8); StringBuilder s=new StringBuilder();
        for(byte b:bytes) s.append(String.format(java.util.Locale.US,"%02X",b&255));
        return s.toString();
    }
    public static String normalizeCode(String s) {
        return s.replace("-", "").replace(" ", "").trim().toUpperCase(java.util.Locale.US);
    }
    private static byte[] random(int n) { byte[] b=new byte[n]; RANDOM.nextBytes(b); return b; }
    private static byte[] secret(String code) throws GeneralSecurityException,IOException {
        code=normalizeCode(code);
        if(!code.matches("[0-9A-F]{16}")) throw new IOException("연결 코드는 16자리입니다");
        return MessageDigest.getInstance("SHA-256").digest(("NaviMirror-v1:"+code).getBytes(StandardCharsets.UTF_8));
    }
    private static byte[] mac(byte[] key,String label,byte[] server,byte[] client) throws GeneralSecurityException {
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));
        m.update(label.getBytes(StandardCharsets.UTF_8));m.update(server);return m.doFinal(client);
    }
    public static Reader accept(InputStream input,OutputStream output,String code) throws IOException,GeneralSecurityException {
        DataInputStream in=new DataInputStream(input);DataOutputStream out=new DataOutputStream(output);
        byte[] key=secret(code),sn=random(32),cn=new byte[32],proof=new byte[32];
        out.writeInt(MAGIC);out.write(sn);out.flush();in.readFully(cn);in.readFully(proof);
        if(!MessageDigest.isEqual(proof,mac(key,"client",sn,cn))) throw new IOException("연결 코드 불일치");
        out.write(mac(key,"server",sn,cn));out.flush();
        return new Reader(in,mac(key,"stream",sn,cn));
    }
    public static Writer connect(InputStream input,OutputStream output,String code) throws IOException,GeneralSecurityException {
        DataInputStream in=new DataInputStream(input);DataOutputStream out=new DataOutputStream(output);
        byte[] key=secret(code),sn=new byte[32],cn=random(32),proof=new byte[32];
        if(in.readInt()!=MAGIC) throw new IOException("지원하지 않는 수신 앱");
        in.readFully(sn);out.write(cn);out.write(mac(key,"client",sn,cn));out.flush();in.readFully(proof);
        if(!MessageDigest.isEqual(proof,mac(key,"server",sn,cn))) throw new IOException("수신 앱 인증 실패");
        return new Writer(out,mac(key,"stream",sn,cn));
    }
    private static Cipher cipher(int mode,byte[] key,long sequence) throws GeneralSecurityException {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        byte[] nonce=ByteBuffer.allocate(12).putInt(MAGIC).putLong(sequence).array();
        c.init(mode,new SecretKeySpec(java.util.Arrays.copyOf(key,16),"AES"),new GCMParameterSpec(128,nonce));
        return c;
    }
    public static final class Writer {
        private final DataOutputStream out; private final byte[] key;private long sequence;
        private Writer(DataOutputStream out,byte[] key){this.out=out;this.key=key;}
        public void packet(int type,byte[] payload) throws IOException,GeneralSecurityException {
            if(payload.length>MAX_PACKET-1)throw new IOException("영상 패킷이 너무 큽니다");
            byte[] plain=new byte[payload.length+1];plain[0]=(byte)type;System.arraycopy(payload,0,plain,1,payload.length);
            byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,key,sequence++).doFinal(plain);
            out.writeInt(encrypted.length);out.write(encrypted);out.flush();
        }
    }
    public static final class Reader {
        private final DataInputStream in;private final byte[] key;private long sequence;
        private Reader(DataInputStream in,byte[] key){this.in=in;this.key=key;}
        public Packet packet() throws IOException,GeneralSecurityException {
            int n=in.readInt();if(n<17||n>MAX_PACKET+16)throw new IOException("잘못된 패킷 크기");
            byte[] bytes=new byte[n];in.readFully(bytes);
            byte[] plain=cipher(Cipher.DECRYPT_MODE,key,sequence++).doFinal(bytes);
            return new Packet(plain[0]&255,java.util.Arrays.copyOfRange(plain,1,plain.length));
        }
    }
    public static final class Packet {
        public final int type;public final byte[] data;
        public Packet(int type,byte[] data){this.type=type;this.data=data;}
    }
    public static byte[] format(int w,int h,byte[] csd0,byte[] csd1) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        d.writeInt(w);d.writeInt(h);blob(d,csd0);blob(d,csd1);return b.toByteArray();
    }
    public static VideoFormat readFormat(byte[] bytes) throws IOException {
        DataInputStream d=new DataInputStream(new ByteArrayInputStream(bytes));int w=d.readInt(),h=d.readInt();
        if(w<16||h<16||w>800||h>480)throw new IOException("지원 범위를 벗어난 영상 크기");
        VideoFormat f=new VideoFormat(w,h,blob(d,65536),blob(d,65536));
        if(f.csd0.length==0||d.available()!=0)throw new IOException("잘못된 영상 설정");return f;
    }
    public static byte[] frame(long pts,int flags,byte[] bytes) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        d.writeLong(pts);d.writeInt(flags);d.write(bytes);return b.toByteArray();
    }
    public static Frame readFrame(byte[] bytes) throws IOException {
        if(bytes.length<13)throw new IOException("빈 영상 프레임");
        ByteBuffer b=ByteBuffer.wrap(bytes);long pts=b.getLong();int flags=b.getInt();
        byte[] payload=new byte[b.remaining()];b.get(payload);return new Frame(pts,flags,payload);
    }
    private static void blob(DataOutputStream d,byte[] bytes) throws IOException { d.writeInt(bytes.length);d.write(bytes); }
    private static byte[] blob(DataInputStream d,int limit) throws IOException {
        int n=d.readInt();if(n<0||n>limit||n>d.available())throw new IOException("잘못된 코덱 설정");
        byte[] b=new byte[n];d.readFully(b);return b;
    }
    public static final class VideoFormat {
        public final int width,height;public final byte[] csd0,csd1;
        VideoFormat(int w,int h,byte[] a,byte[] b){width=w;height=h;csd0=a;csd1=b;}
    }
    public static final class Frame {
        public final long pts;public final int flags;public final byte[] data;
        public Frame(long pts,int flags,byte[] data){this.pts=pts;this.flags=flags;this.data=data;}
        public boolean key(){return (flags&1)!=0;}
    }
}
