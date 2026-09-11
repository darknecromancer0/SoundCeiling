package dev.soundceiling.app;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.view.KeyEvent;

/** Executes the production Accessibility service and shared state, not a duplicate key gate. */
public final class V0114KeyServiceBridgeTest {
 public static void main(String[] args) {
  VolumeKeySafetyService service=new VolumeKeySafetyService();
  AudioManager audio=new AudioManager(); ((android.content.Context)service).audio=audio;
  service.onServiceConnected();
  require(!armed(service), "idle Accessibility must not ask Android to filter keys");
  require(StrictSafetyState.keyFilterCapable(), "declared Relay capability remains available while idle");
  audio.resetCalls(); Prefs.reads=0; RuntimeStateStore.reads=0;
  require(!service.onKeyEvent(key(0,24)), "stopped Up passes through");
  require(!service.onKeyEvent(key(1,24)), "stopped Up release passes through");
  require(!service.onKeyEvent(key(0,25)), "stopped Down passes through");
  require(!service.onKeyEvent(key(0,29)), "nonvolume keys pass through");
  noSlowReads(audio);

  UserVolumeControl.owns=true; StrictSafetyState.setEngineRunning(service,true);
  require(armed(service), "start arms the actual Android service flags");
  require(service.onKeyEvent(key(0,24)), "ordinary Up owns intent");
  require(UserVolumeControl.steps==1 && audio.adjusts==0 && UserVolumeOverlay.shows==0,
    "key intent applies before queued window creation");
  Handler.flush(); require(audio.adjusts==1 && UserVolumeOverlay.shows==1, "UI displays after key callback");
  service.onKeyEvent(key(1,24));
  service.onKeyEvent(key(0,25)); require(UserVolumeControl.pause, "Down still pauses");
  int shown=UserVolumeOverlay.shows;
  UserVolumeControl.owns=false;
  Prefs.beforeEdit=()->require(!armed(service), "Stop must disarm before preference I/O and teardown");
  StrictSafetyState.setEngineRunning(service,false); Prefs.beforeEdit=null;
  Handler.flush(); require(UserVolumeOverlay.shows==shown, "queued panel cannot reopen after Stop");
  audio.resetCalls(); Prefs.reads=0; RuntimeStateStore.reads=0;
  require(service.onKeyEvent(key(1,25)), "already queued owned release finishes without audio I/O");
  for(int n=0;n<30;n++)require(!service.onKeyEvent(key(0,24)), "fresh held-key repeats pass after Stop");
  noSlowReads(audio);

  StrictSafetyState.publishRelayKeyAuthority(RelayVolumePolicy.Phase.ACTIVE,0,5);
  require(armed(service), "Relay authority arms filtering independently");
  require(service.onKeyEvent(key(0,24)), "Relay still owns key");
  require(audio.writes==1 && audio.lastStream==AudioManager.STREAM_ACCESSIBILITY && audio.media==6,
    "Relay key writes only its separate stream");
  StrictSafetyState.clearRelayKeyAuthority(); require(!armed(service), "Relay teardown de-arms idle service");
  service.onDestroy();
  System.out.println("V0114KeyServiceBridgeTest PASS");
 }
 private static boolean armed(VolumeKeySafetyService s) {
  return (s.getServiceInfo().flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS)!=0;
 }
 private static KeyEvent key(int action,int key) { return new KeyEvent(action,key); }
 private static void noSlowReads(AudioManager audio) {
  require(audio.reads==0 && audio.writes==0 && audio.adjusts==0 && Prefs.reads==0 && RuntimeStateStore.reads==0,
    "idle callback must have zero audio/prefs/runtime-store access");
 }
 private static void require(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
