package local.navi.diagnostic;
public class TripMetricsTest {
 static int checks;
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;System.out.println("PASS "+s);}
 static void fix(TripMetrics m,long t,double lat,double acc,double speed){m.accept(t,t,lat,127,acc,speed);}
 public static void main(String[] x){
  TripMetrics m=new TripMetrics(0);
  check(Double.isNaN(m.snapshot(0).speedKmh),"no GPS does not display zero");
  fix(m,1000,37,5,10);check(Math.abs(m.snapshot(1000).speedKmh-36)<.001,"provider speed in km/h");
  check(m.snapshot(1000).meters==0,"first fix no distance");
  fix(m,2000,37.00009,5,10);double first=m.snapshot(2000).meters;
  check(first>9&&first<11,"moving fixes add measured distance");
  check(Double.isNaN(m.snapshot(12001).speedKmh),"stale GPS hidden after ten seconds");
  fix(m,20000,37.01,5,10);check(m.snapshot(20000).meters==first,"signal gap not filled");
  fix(m,21000,38,5,10);check(m.snapshot(21000).meters==first,"teleport rejected");
  fix(m,22000,38.00009,100,10);check(m.snapshot(22000).meters==first&&Double.isNaN(m.snapshot(22000).speedKmh),"poor accuracy excludes distance and speed");
  fix(m,23000,38.00018,5,10);check(m.snapshot(23000).meters==first,"quality recovery does not bridge gap");
  fix(m,24000,38.0002,5,0);check(m.snapshot(24000).speedKmh==0&&m.snapshot(24000).meters==first,"valid stationary zero and jitter suppression");
  fix(m,25000,38.0003,5,0);check(m.snapshot(25000).meters==first,"stationary jitter ignored");
  fix(m,26000,38.0004,5,Double.NaN);check(Double.isNaN(m.snapshot(26000).speedKmh),"missing speed not guessed");
  fix(m,27000,38.0005,5,10);check(m.snapshot(27000).meters==first,"missing speed breaks distance chain");
  m.accept(28000,26000,38.2,127,5,20);check(m.snapshot(28000).speedKmh==36,"out-of-order fix ignored");
  m.accept(28000,29000,38.2,127,5,20);check(m.snapshot(28000).speedKmh==36,"future fix ignored");
  m.accept(28000,1000,38.2,127,5,20);check(m.snapshot(28000).speedKmh==36,"cached fix ignored");
  m.accept(28000,28000,Double.NaN,127,5,20);check(m.snapshot(28000).speedKmh==36,"nonfinite coordinate rejected");
  m.accept(28000,28000,91,127,5,20);check(m.snapshot(28000).speedKmh==36,"invalid coordinate rejected");
  fix(m,28000,38.0006,5,100);check(Double.isNaN(m.snapshot(28000).speedKmh),"implausible speed unavailable");
  m.unavailable();check(!m.snapshot(29000).fresh&&Double.isNaN(m.snapshot(29000).speedKmh),"provider disabled clears value");
  check(m.snapshot(29000).meters==first,"GPS pause preserves aggregate");
  m.reset(30000);check(m.snapshot(31000).meters==0&&m.snapshot(31000).elapsedMs==1000,"reset session distance and monotonic elapsed");
  fix(m,32000,37,5,10);check(m.snapshot(32000).meters==0,"reset does not join old coordinate");
  TripMetrics empty=new TripMetrics(40000);check(empty.snapshot(40000).meters==0&&!empty.snapshot(40000).fresh,"new process session starts empty");
  System.out.println(checks+" checks passed");
 }
}
