package android.view;
public final class KeyEvent {
 public static final int KEYCODE_VOLUME_UP=24, KEYCODE_VOLUME_DOWN=25, ACTION_DOWN=0, ACTION_UP=1;
 private final int action,key;
 public KeyEvent(int action,int key) { this.action=action; this.key=key; }
 public int getAction() { return action; } public int getKeyCode() { return key; }
}