package local.navi.diagnostic;

import android.content.*;
import android.net.*;
import android.net.wifi.*;
import android.os.Build;
import java.util.*;

final class WifiAccess implements WifiReconnect.Port {
    private final Context context;
    private final WifiManager wifi;
    WifiAccess(Context c) {
        context=c.getApplicationContext(); wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
    }
    // iQ 3 uses Android 5. Later Android releases restrict other apps' saved profiles.
    static boolean supportsControl() { return Build.VERSION.SDK_INT>=21 && Build.VERSION.SDK_INT<=22; }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("wifi_connection",Context.MODE_PRIVATE); }
    static String target(Context c) { return prefs(c).getString("target_key",""); }
    static String name(String raw) {
        if(raw==null || raw.equals("<unknown ssid>")) return "이름 확인 불가";
        return raw.length()>=2 && raw.startsWith("\"") && raw.endsWith("\"") ? raw.substring(1,raw.length()-1) : raw;
    }
    private static String key(WifiConfiguration c) {
        // Never persist credentials or rely on a networkId surviving a reboot/reconfiguration.
        return c.SSID+"|"+c.allowedKeyManagement+"|"+c.allowedProtocols+"|"+c.allowedAuthAlgorithms+"|"+c.BSSID;
    }
    private List<WifiConfiguration> configurations() {
        if(wifi==null || !supportsControl()) return Collections.emptyList();
        List<WifiConfiguration> result=wifi.getConfiguredNetworks();
        return result==null?Collections.emptyList():result;
    }
    @Override public List<WifiReconnect.Profile> profiles() {
        List<WifiReconnect.Profile> result=new ArrayList<>();
        for(WifiConfiguration c:configurations()) if(c.SSID!=null && c.networkId>=0)
            result.add(new WifiReconnect.Profile(c.networkId,key(c),name(c.SSID)));
        return result;
    }
    @Override public int radio() {
        if(wifi==null) return WifiReconnect.OFF;
        int state=wifi.getWifiState();
        if(state==WifiManager.WIFI_STATE_ENABLED) return WifiReconnect.ON;
        return state==WifiManager.WIFI_STATE_ENABLING || state==WifiManager.WIFI_STATE_DISABLING ? WifiReconnect.STARTING : WifiReconnect.OFF;
    }
    @Override public boolean enableRadio() { return supportsControl() && wifi!=null && wifi.setWifiEnabled(true); }
    @Override public int connectedId() {
        if(wifi==null) return -1;
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network=cm==null?null:cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        WifiInfo info=wifi.getConnectionInfo();
        return network!=null && network.isConnected() && info!=null && info.getIpAddress()!=0 ? info.getNetworkId() : -1;
    }
    @Override public boolean request(WifiReconnect.Profile selected) {
        if(!supportsControl() || wifi==null) return false;
        List<WifiConfiguration> before=configurations();
        List<WifiReconnect.Profile> fresh=new ArrayList<>();
        for(WifiConfiguration c:before) fresh.add(new WifiReconnect.Profile(c.networkId,key(c),name(c.SSID)));
        WifiReconnect.Profile resolved=WifiReconnect.find(fresh,selected.key);
        if(resolved==null || resolved.id!=selected.id) return false;
        // Android 5 temporarily disables other networks when selecting one. Restore only those
        // that were enabled, and re-check their identities in case settings changed the IDs.
        try {
            wifi.startScan();
            return wifi.enableNetwork(selected.id,true);
        } finally {
            List<WifiConfiguration> after=configurations();
            boolean restoreFailed=false;
            for(WifiConfiguration old:before) {
                if(old.networkId==selected.id || old.status==WifiConfiguration.Status.DISABLED) continue;
                for(WifiConfiguration current:after) if(current.networkId==old.networkId && key(current).equals(key(old))) {
                    try {if(!wifi.enableNetwork(current.networkId,false)) restoreFailed=true;}
                    catch(RuntimeException e){restoreFailed=true;}
                    break;
                }
            }
            if(restoreFailed) throw new IllegalStateException("Network restore rejected");
        }
    }
    String summary() {
        if(wifi==null) return "Wi-Fi 서비스 없음 · 호환 동글 인식 여부를 확인하세요.";
        int state=radio();
        String text="Wi-Fi: "+(state==WifiReconnect.ON?"켜짐":state==WifiReconnect.STARTING?"전환 중":"꺼짐 / 장치 확인 필요");
        WifiInfo info=wifi.getConnectionInfo();
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network=cm==null?null:cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(network!=null && network.isConnected()) {
            int ip=info==null?0:info.getIpAddress();
            text+="\n현재 연결: 연결됨 · "+name(info==null?null:info.getSSID());
            text+=ip==0?" · IP 확인 불가":"  ·  IP "+(ip&255)+"."+((ip>>8)&255)+"."+((ip>>16)&255)+"."+((ip>>24)&255);
        } else text+="\n현재 연결: 연결 안 됨 / IP 대기";
        return text;
    }
}
