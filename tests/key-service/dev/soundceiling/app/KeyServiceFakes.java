package dev.soundceiling.app;
import android.content.Context;
import java.util.function.Consumer;
final class DiagnosticLog {
 static void event(String name,String text) {} static void transition(String name,String key,String text) {}
}
final class Prefs {
 static int reads; static Runnable beforeEdit;
 static final Memory values=new Memory();
 static Memory get(Context c) { reads++; return values; }
 static ControlProfile currentControlProfile(Context c) { reads++; return new ControlProfile(); }
 static final class Memory {
  boolean running;
  Memory edit() { if(beforeEdit!=null)beforeEdit.run(); return this; }
  Memory putBoolean(String name, boolean value) { running=value; return this; }
  boolean getBoolean(String name, boolean fallback) { return running; }
  void apply() {}
 }
}
final class RuntimeStateStore {
 static int reads; static final State value=new State();
 static State get() { reads++; return value; }
 static final class State { boolean running=true; }
}
final class ControlProfile {
 int minMediaIndex=0,maxMediaPercent=100,safetyLockPercent=100,quietIndex=0,recoveryIntervalMs=100;
 boolean safetyLockEnabled;
}
final class UserVolumeControl {
 static boolean owns, pause; static int steps;
 static boolean ownsMedia() { return owns; }
 static void step(Context c,int direction) { steps+=direction; pause=false; }
 static int percent(Context c) { return 50; }
 static boolean paused() { return pause; }
}
final class UserVolumeOverlay {
 static int shows,hides;
 UserVolumeOverlay(Context c) {}
 void show(boolean explicit) { shows++; }
 void hide() { hides++; }
 void setNativeBounds(UserVolumeOverlayPlacement.Box b) {}
}
final class SystemVolumePanelProbe {
 SystemVolumePanelProbe(Context c, Consumer<UserVolumeOverlayPlacement.Box> result) {}
 void inspect(android.view.accessibility.AccessibilityEvent e) {}
 void cancel() {} void close() {}
}
