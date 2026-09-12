package android.accessibilityservice;
import android.content.pm.ResolveInfo;
public final class AccessibilityServiceInfo {
 public static final int FLAG_REQUEST_FILTER_KEY_EVENTS=32, FLAG_ENABLE_ACCESSIBILITY_VOLUME=128,
 FLAG_REPORT_VIEW_IDS=16, FLAG_RETRIEVE_INTERACTIVE_WINDOWS=64, CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS=8, FEEDBACK_ALL_MASK=-1, FEEDBACK_SPOKEN=1;
 public int flags=FLAG_REQUEST_FILTER_KEY_EVENTS, eventTypes; public String[] packageNames;
 public int getCapabilities() { return CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS; }
 public ResolveInfo getResolveInfo() { return null; }
}