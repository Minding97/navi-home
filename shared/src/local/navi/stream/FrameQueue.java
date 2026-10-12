package local.navi.stream;
import java.util.ArrayDeque;

/** Dropping an AVC reference frame invalidates dependents: wait for the next key frame. */
public final class FrameQueue {
    private final ArrayDeque<Wire.Frame> queue=new ArrayDeque<>();
    private final int capacity;private boolean waiting=true;private long dropped;
    public FrameQueue(int capacity){if(capacity<1)throw new IllegalArgumentException();this.capacity=capacity;}
    public synchronized boolean offer(Wire.Frame f) {
        if(queue.size()>=capacity){dropped+=queue.size();queue.clear();waiting=true;}
        if(waiting&&!f.key()){dropped++;return false;}
        if(f.key())waiting=false;
        queue.add(f);notifyAll();return true;
    }
    public synchronized Wire.Frame poll(long millis) throws InterruptedException {
        if(queue.isEmpty())wait(millis);return queue.poll();
    }
    public synchronized void reset(){queue.clear();waiting=true;}
    public synchronized long dropped(){return dropped;}
}
