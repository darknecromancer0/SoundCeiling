package android.view.accessibility;
import java.util.Collections;
import java.util.List;
public final class AccessibilityEvent {
 public static final int TYPE_WINDOW_STATE_CHANGED=32, TYPE_WINDOW_CONTENT_CHANGED=2048;
 public int getEventType() { return 32; }
 public CharSequence getPackageName() { return "com.android.systemui"; }
 public CharSequence getClassName() { return "VolumeDialog"; }
 public CharSequence getContentDescription() { return "Volume"; }
 public List<CharSequence> getText() { return Collections.emptyList(); }
}