package dev.soundceiling.app;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import java.util.Arrays;
public final class V010RelayRuntimeTest {
 private final AudioManager audio=new AudioManager();
 private final RelayGenerationToken gen=new RelayGenerationToken(1,1,1,1,1);
 private final short[] input=new short[1920],output=new short[1920];
 private final AccessibilityRelayRuntime runtime;
 private long time=1000;
 private V010RelayRuntimeTest(){
  SystemClock.now=time;StrictSafetyState.authority.start();
  AccessibilityPcmRenderer.audio=audio;
  AccessibilityPcmRenderer.opens=AccessibilityPcmRenderer.writes=AccessibilityPcmRenderer.stops=0;
  Arrays.fill(input,(short)4000);
  runtime=new AccessibilityRelayRuntime(new Context(),audio,new AudioDeviceInfo(),new HybridRuntimeResolver(),s->true);
  runtime.requestStart(1,gen);
 }
 private AccessibilityRelayRuntime.Snapshot tick(boolean signal,boolean drain){
  time+=20;SystemClock.now=time;
  AccessibilityRelayRuntime.Frame f=new AccessibilityRelayRuntime.Frame(1,time,audio.media,15,
   true,true,true,false,false,true,true,true,1,42,"player","speaker",
   CaptureReferenceEstimator.Mode.UNKNOWN,signal?-18f:-90f,signal?-21f:-90f,
   OutputCeilingState.defaultLinked(),BuiltInProfiles.balanced(),
   new PcmCaptureBackend.CaptureTimestamp(true,time*48,time*1000000),gen,true);
  AccessibilityRelayRuntime.Snapshot s=runtime.onPcmBlock(f,input,input.length,output);
  if(drain&&runtime.needsMutedCaptureDrain())runtime.onMutedCaptureDrained(true);
  return s;
 }
 private void reachProbe(){for(int i=0;i<80&&runtime.snapshot().state!=AccessibilityRelayGate.State.QUIET_PROBE;i++)tick(true,true);
  require(runtime.snapshot().state==AccessibilityRelayGate.State.QUIET_PROBE,"UNKNOWN reference must bootstrap: "+runtime.snapshot().reason);
 }
 public static void main(String[] args){
  V010RelayRuntimeTest t=new V010RelayRuntimeTest();
  for(int i=0;i<80;i++)t.tick(true,false);
  require(AccessibilityPcmRenderer.opens==0,"queued pre-mute samples cannot open output");
  require(t.runtime.needsMutedCaptureDrain(),"capture worker must drain before proof");
  t.runtime.onMutedCaptureDrained(true); t.reachProbe();
  require(t.audio.media==0,"original remains muted");
  require(t.audio.accessibility==3,"preview retains usable user volume");
  t.tick(true,true);
  require(t.runtime.snapshot().outputPeakDbfs > -24f
   && t.runtime.snapshot().outputPeakDbfs <= -18f+.01f,
   "preview must be audible at existing hardware level with a -18 dBFS peak bound");
  for(int i=0;i<260&&t.runtime.snapshot().state!=AccessibilityRelayGate.State.AWAITING_CONFIRMATION;i++)t.tick(true,true);
  require(t.runtime.snapshot().state==AccessibilityRelayGate.State.AWAITING_CONFIRMATION,"bounded preview waits for confirmation");
  require(!t.runtime.snapshot().audible,"preview stops while awaiting confirmation");
  t.runtime.acceptProbe(1);t.tick(true,true);
  require(t.runtime.snapshot().state==AccessibilityRelayGate.State.ACTIVE,"confirmed stream activates");
  StrictSafetyState.authority.onKeyEvent(25,0);t.tick(true,true);
  require(t.runtime.snapshot().state==AccessibilityRelayGate.State.OFF,"down stops renderer");
  require(t.runtime.snapshot().reason.equals("relay_paused_user_down"),"pause reason survives cleanup");
  require(t.audio.media==0,"down never restores audible original");
  int writes=AccessibilityPcmRenderer.writes;t.tick(true,true);
  require(writes==AccessibilityPcmRenderer.writes,"down stays latched");
  t=new V010RelayRuntimeTest();
  for(int i=0;i<150;i++)t.tick(false,true);
  require(AccessibilityPcmRenderer.opens==0,"lost-at-mute PCM never renders");
  require(t.runtime.snapshot().reason.equals("relay_capture_lost_at_media_zero"),"real timeout reason retained: "+t.runtime.snapshot().reason);
  require(t.audio.media==3,"failed proof restores only original owned Media");
  t=new V010RelayRuntimeTest();
  for(int i=0;i<30&&!t.runtime.needsMutedCaptureDrain();i++)t.tick(true,false);
  t.runtime.onMutedCaptureDrained(false);
  require(t.runtime.snapshot().reason.equals("relay_capture_drain_failed"),"drain failure not overwritten");
  require(AccessibilityPcmRenderer.opens==0,"failed drain cannot render");
  t=new V010RelayRuntimeTest();
  for(int i=0;i<150;i++)t.tick(true,false);
  require(t.runtime.snapshot().reason.equals("relay_capture_drain_timeout"),"missing drain has a bounded timeout");
  require(t.audio.media==3 && AccessibilityPcmRenderer.opens==0,"drain timeout restores without output");
  t=new V010RelayRuntimeTest();t.audio.accessibility=0;t.reachProbe();
  require(t.audio.accessibility==3,"explicit start initializes unused Accessibility volume from user's Media level");
  t.runtime.abort("test_stop",AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
  require(t.audio.accessibility==0 && t.audio.media==3,"startup volume ownership restores original levels");
  t=new V010RelayRuntimeTest();t.audio.media=0;t.audio.accessibility=0;
  for(int i=0;i<150;i++)t.tick(true,true);
  require(t.audio.media==0 && t.audio.accessibility==0 && AccessibilityPcmRenderer.opens==0,
   "muted source stays muted at explicit start");
  t=new V010RelayRuntimeTest();t.audio.media=0;
  for(int i=0;i<150;i++)t.tick(true,true);
  require(t.audio.media==0 && AccessibilityPcmRenderer.opens==0,
   "Media mute also wins when Accessibility already has a nonzero volume");
  System.out.println("V010RelayRuntimeTest: PASS (real runtime, fake Android I/O)");
 }
 private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
