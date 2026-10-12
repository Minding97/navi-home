package local.navi.diagnostic;
public class PhoneAddressTest {
    public static void main(String[] args){
        String[][] cases={{"192.168.43.1","192.168.43.1"},{" 10.0.2.2 ","10.0.2.2"},{"172.16.0.1","172.16.0.1"},{"172.31.9.1","172.31.9.1"},{"192.168.001.010","192.168.1.10"},
            {"192.168.43.1:5277",null},{"http://192.168.43.1",null},{"8.8.8.8",null},{"127.0.0.1",null},{"172.32.0.1",null},{"172.15.0.1",null},{"192.168.256.1",null},{"192.168.1",null},{"192..1.1",null},{"localhost",null},{"::1",null},{"10.1.2.-1",null},{null,null}};
        for(String[] c:cases){String result=PhoneAddress.normalize(c[0]);if(c[1]==null?result!=null:!c[1].equals(result))throw new AssertionError("Unexpected address acceptance: "+c[0]);}
        System.out.println(cases.length+" private IPv4 validation checks passed");
    }
}
