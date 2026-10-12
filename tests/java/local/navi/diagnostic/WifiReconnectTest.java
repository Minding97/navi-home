package local.navi.diagnostic;

import java.util.*;

public class WifiReconnectTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static class Fake implements WifiReconnect.Port {
        int radio=WifiReconnect.ON,connected=-1,enables,requests,lastId=-1;
        boolean reject,deny;
        List<WifiReconnect.Profile> saved=new ArrayList<>();
        Fake(){saved.add(new WifiReconnect.Profile(7,"phone-secure","Phone"));}
        public int radio(){return radio;}
        public boolean enableRadio(){enables++;return true;}
        public List<WifiReconnect.Profile> profiles(){return saved;}
        public int connectedId(){return connected;}
        public boolean request(WifiReconnect.Profile p){if(deny)throw new SecurityException();requests++;lastId=p.id;return !reject;}
    }
    public static void main(String[] args){
        Fake p=new Fake();WifiReconnect s=new WifiReconnect("phone-secure",true,0);
        for(int t=0;t<=62000;t+=2000)s.tick(p,t,true);
        check(s.finished,"missing hotspot must terminate");check(p.requests==3,"three bounded requests");
        check(!s.status.contains("완료"),"requests are not success");
        p=new Fake();s=new WifiReconnect("phone-secure",true,0);s.tick(p,0,false);
        check(s.finished && p.requests==0 && p.enables==0,"disabled auto must not mutate Wi-Fi");
        p=new Fake();p.connected=7;s=new WifiReconnect("phone-secure",true,0);s.tick(p,0,true);
        check(s.finished && s.status.contains("연결 완료") && p.requests==0,"already connected does not reconnect");
        p=new Fake();p.connected=42;s=new WifiReconnect("phone-secure",true,0);s.tick(p,0,true);
        check(s.finished && p.requests==0,"auto preserves another connected network");
        s=new WifiReconnect("phone-secure",false,0);s.tick(p,0,true);
        check(p.requests==1 && !s.finished,"manual can request target switch");
        p=new Fake();p.saved.clear();p.saved.add(new WifiReconnect.Profile(7,"other-open","Phone"));
        s=new WifiReconnect("phone-secure",false,0);s.tick(p,0,true);
        check(p.requests==0,"reused ID or same display name must not select wrong profile");
        p.saved.add(new WifiReconnect.Profile(19,"phone-secure","Phone"));s.tick(p,2000,true);
        check(p.lastId==19,"resolve fresh ID after reboot or reconfiguration");
        p.connected=19;s.tick(p,4000,true);check(s.finished && s.status.contains("연결 완료"),"observe actual success");
        p=new Fake();p.saved.add(new WifiReconnect.Profile(8,"phone-secure","Phone"));
        s=new WifiReconnect("phone-secure",false,0);s.tick(p,0,true);
        check(p.requests==0,"ambiguous saved profile needs system settings");
        p=new Fake();p.radio=WifiReconnect.OFF;s=new WifiReconnect("phone-secure",true,0);s.tick(p,0,true);
        check(p.enables==1 && p.requests==0,"enable radio before connect");
        p.radio=WifiReconnect.STARTING;s.tick(p,2000,true);check(p.enables==1,"do not spam enabling radio");
        p.radio=WifiReconnect.ON;s.tick(p,4000,true);check(p.requests==1,"connect after delayed dongle startup");
        p.radio=WifiReconnect.OFF;s.tick(p,6000,true);check(s.finished && p.enables==1,"respect Wi-Fi turned off during attempt");
        p=new Fake();p.reject=true;s=new WifiReconnect("phone-secure",true,0);s.tick(p,0,true);
        check(s.status.contains("거부") && !s.finished,"explicit request rejection");s.tick(p,2000,true);
        check(p.requests==1,"wait before retrying rejection");
        p=new Fake();p.deny=true;s=new WifiReconnect("phone-secure",true,0);s.tick(p,0,true);
        check(s.finished && s.status.contains("권한"),"permission error terminates cleanly");
        p=new Fake();s=new WifiReconnect("",true,0);s.tick(p,0,true);check(s.finished && p.requests==0,"no target cannot connect");
        p=new Fake();s=new WifiReconnect("phone-secure",true,0);s.cancel();s.tick(p,0,true);
        check(p.requests==0,"cancel prevents further calls");
        System.out.println(checks+" Wi-Fi policy checks passed");
    }
}
