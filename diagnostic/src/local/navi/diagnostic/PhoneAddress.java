package local.navi.diagnostic;

/** The car test only targets an explicitly entered private IPv4 address, never a hostname. */
public final class PhoneAddress {
    public static String normalize(String input) {
        if(input==null) return null;
        String[] fields=input.trim().split("\\.",-1);
        if(fields.length!=4) return null;
        int[] octets=new int[4];
        for(int i=0;i<4;i++) {
            if(!fields[i].matches("[0-9]{1,3}")) return null;
            octets[i]=Integer.parseInt(fields[i]);if(octets[i]>255) return null;
        }
        if(!(octets[0]==10 || octets[0]==172 && octets[1]>=16 && octets[1]<=31 || octets[0]==192 && octets[1]==168)) return null;
        return octets[0]+"."+octets[1]+"."+octets[2]+"."+octets[3];
    }
}
