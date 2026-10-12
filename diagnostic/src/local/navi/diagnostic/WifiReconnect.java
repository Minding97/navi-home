package local.navi.diagnostic;

import java.util.List;

/** Bounded connection policy, separate from Android so failure paths can be tested. */
public final class WifiReconnect {
    public static final long TIMEOUT_MS = 60000, RETRY_MS = 20000;
    public static final int OFF = 0, STARTING = 1, ON = 2;
    public static final class Profile {
        public final int id;
        public final String key, name;
        public Profile(int id, String key, String name) { this.id=id; this.key=key; this.name=name; }
    }
    public interface Port {
        int radio();
        boolean enableRadio();
        List<Profile> profiles();
        // Only return a network ID when Wi-Fi has an active connection and an IP address.
        int connectedId();
        boolean request(Profile profile);
    }
    public final String target;
    public final boolean automatic;
    public boolean finished;
    public String status = "연결 준비 중";
    private final long started;
    private long nextRequest, nextEnable;
    private boolean sawEnabled;

    public WifiReconnect(String target, boolean automatic, long now) {
        this.target=target; this.automatic=automatic; started=now; nextRequest=nextEnable=now;
    }
    public void cancel() { finish("연결 시도를 중지했습니다."); }
    private void finish(String value) { status=value; finished=true; }
    public static Profile find(List<Profile> profiles, String key) {
        Profile match=null;
        for(Profile p:profiles) if(p.id>=0 && p.key.equals(key)) {
            if(match!=null) return null; // Ambiguous saved profiles require selection in system settings.
            match=p;
        }
        return match;
    }
    public void tick(Port port, long now, boolean allowed) {
        if(finished) return;
        if(!allowed) { cancel(); return; }
        if(target.isEmpty()) { finish("먼저 연결할 Wi-Fi를 기억해 주세요."); return; }
        if(now-started>=TIMEOUT_MS) { finish("1분 안에 연결되지 않았습니다. 핫스팟·동글·비밀번호를 확인 후 다시 연결하세요."); return; }
        try {
            int radio=port.radio();
            if(radio!=ON) {
                if(sawEnabled && radio==OFF) { finish("Wi-Fi가 꺼져 연결 시도를 중지했습니다."); return; }
                status="Wi-Fi 장치가 켜지기를 기다리는 중";
                if(radio==OFF && now>=nextEnable) {
                    nextEnable=now+RETRY_MS;
                    if(!port.enableRadio()) status="Wi-Fi 켜기 요청 거부 · 동글 또는 파인 설정을 확인하세요.";
                }
                return;
            }
            sawEnabled=true;
            List<Profile> profiles=port.profiles();
            Profile selected=find(profiles,target);
            int connected=port.connectedId();
            if(selected!=null && connected==selected.id) { finish("기억한 Wi-Fi 연결 완료 · "+selected.name); return; }
            if(automatic && connected>=0) { finish("다른 Wi-Fi에 연결되어 있습니다. 변경하려면 ‘다시 연결’을 누르세요."); return; }
            if(selected==null) { status="기억한 Wi-Fi의 저장 정보를 찾는 중 · 설정에서 먼저 연결해 주세요."; return; }
            if(now>=nextRequest) {
                nextRequest=now+RETRY_MS;
                status=port.request(selected) ? "연결 요청됨 · 실제 연결 확인 중" : "연결 요청 거부 · 파인 연결 설정을 확인하세요.";
            }
        } catch(SecurityException e) { finish("Wi-Fi 제어 권한이 없습니다. 설정 화면에서 연결해 주세요."); }
          catch(RuntimeException e) { finish("Wi-Fi 제어 실패 · "+e.getClass().getSimpleName()+" · 설정 화면을 확인하세요."); }
    }
}
