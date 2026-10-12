package local.navi.diagnostic;

/** In-memory session only. No route list, wall-clock arithmetic, or gap interpolation. */
public final class TripMetrics {
    public static final long FRESH_MS = 10000;
    private long started, fixAt = -1, anchorAt = -1;
    private double meters, speed = Double.NaN, accuracy = Double.NaN;
    private double lat, lon, anchorSpeed;
    private boolean quality;
    public TripMetrics(long now) { started = now; }
    public void reset(long now) { started = now; meters = 0; anchorAt = -1; }
    public void unavailable() { fixAt = -1; anchorAt = -1; quality = false; }
    public void accept(long now, long stamp, double latitude, double longitude,
                       double accuracyMeters, double speedMps) {
        if (stamp < 0 || stamp > now || now - stamp > FRESH_MS || stamp <= fixAt) return;
        if (!finite(latitude) || !finite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) return;
        fixAt = stamp; accuracy = accuracyMeters;
        quality = finite(accuracyMeters) && accuracyMeters >= 0 && accuracyMeters <= 30;
        speed = quality && finite(speedMps) && speedMps >= 0 && speedMps <= 75 ? speedMps : Double.NaN;
        if (!quality || !finite(speed) || speed < 1.5) { anchorAt = -1; return; }
        if (anchorAt >= 0) {
            double seconds = (stamp-anchorAt)/1000.0;
            double step = distance(lat,lon,latitude,longitude);
            // Reject gaps and implausible jumps; require two moving, accurate fixes.
            if (seconds > 0 && seconds <= 5 && step/seconds <= 75
                    && step/seconds <= Math.max(12, (anchorSpeed+speed)+10)) meters += step;
        }
        anchorAt = stamp; lat = latitude; lon = longitude; anchorSpeed = speed;
    }
    public Snapshot snapshot(long now) {
        boolean fresh = fixAt >= 0 && now >= fixAt && now-fixAt <= FRESH_MS;
        return new Snapshot(Math.max(0,now-started), meters, fresh && quality ? speed*3.6 : Double.NaN,
                accuracy, fresh, quality, fixAt < 0 ? -1 : Math.max(0,now-fixAt));
    }
    static boolean finite(double d) { return !Double.isNaN(d) && !Double.isInfinite(d); }
    static double distance(double a,double b,double c,double d) {
        double x=Math.toRadians(c-a), y=Math.toRadians(d-b);
        double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);
        return 6371000*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,h))));
    }
    public static final class Snapshot {
        public final long elapsedMs, ageMs;
        public final double meters, speedKmh, accuracy;
        public final boolean fresh, quality;
        Snapshot(long e,double m,double s,double a,boolean f,boolean q,long age) {
            elapsedMs=e;meters=m;speedKmh=s;accuracy=a;fresh=f;quality=q;ageMs=age;
        }
    }
}
