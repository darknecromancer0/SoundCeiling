package android.os;
import java.util.ArrayList;
import java.util.List;
public final class Handler {
 private static final List<Runnable> queue=new ArrayList<>();
 public Handler(Looper l) {}
 public boolean post(Runnable r) { queue.add(r); return true; }
 public void removeCallbacks(Runnable r) { queue.removeIf(x -> x==r); }
 public void removeCallbacksAndMessages(Object token) { queue.clear(); }
 public static void flush() { while (!queue.isEmpty()) queue.remove(0).run(); }
}