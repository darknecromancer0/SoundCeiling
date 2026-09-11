package android.accessibilityservice;
import android.content.Context;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
public abstract class AccessibilityService extends Context {
 private AccessibilityServiceInfo info=new AccessibilityServiceInfo();
 protected void onServiceConnected() {}
 protected boolean onKeyEvent(KeyEvent e) { return false; }
 public abstract void onAccessibilityEvent(AccessibilityEvent e);
 public abstract void onInterrupt();
 public void onDestroy() {}
 public AccessibilityServiceInfo getServiceInfo() {
  AccessibilityServiceInfo copy=new AccessibilityServiceInfo(); copy.flags=info.flags; return copy;
 }
 public void setServiceInfo(AccessibilityServiceInfo value) { info=value; }
}