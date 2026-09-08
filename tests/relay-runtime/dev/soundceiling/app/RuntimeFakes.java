package dev.soundceiling.app;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.util.ArrayList;
import java.util.List;
final class DeviceDetector { static String key(AudioDeviceInfo d){return d==null?"":"speaker";} }
final class Prefs { static ControlProfile currentControlProfile(Context c){return BuiltInProfiles.balanced();} }
final class DiagnosticLog {
 static final List<String> events=new ArrayList<>();
 static void event(String code,String detail){events.add(code+" "+detail);}
 static void transition(String code,String state,String detail){event(code,detail);}
}
final class StrictSafetyState {
 static final MediaAutoVolumeAuthority authority=new MediaAutoVolumeAuthority();
 static MediaAutoVolumeAuthority mediaAutomation(){return authority;}
 static boolean accessibilityConnected(){return true;}
 static boolean isAccessibilityServiceEnabled(Context c){return true;}
 static boolean accessibilityVolumeEnabled(Context c){return true;}
 static boolean keyFilterCapable(){return true;}
 static boolean hasOtherSpokenFeedbackService(Context c){return false;}
 static void clearRelayKeyAuthority(){}
 static void publishRelayKeyAuthority(RelayVolumePolicy.Phase p,int min,int max){}
 static int hardMaxIndex(Context c,AudioManager a){return 15;}
 static final class RelayAccessibilityWrite { final long sequence=0; final int index=3; }
 static RelayAccessibilityWrite relayAccessibilityWrite(){return new RelayAccessibilityWrite();}
}
final class RelayRecoveryStore {
 private RelayMediaLease.Record record;
 RelayRecoveryStore(Context c){}
 boolean hasPending(){return record!=null;}
 boolean save(RelayMediaLease.Record r){record=r;return true;}
 RelayMediaLease.Record load(){return record;}
 boolean clear(){record=null;return true;}
}
final class PlaybackObserver { static final class RendererBaseline { boolean valid=true; } }
final class HybridRuntimeResolver {
 PlaybackObserver.RendererBaseline beginRelayRendererOwnership(){return new PlaybackObserver.RendererBaseline();}
 boolean claimRelayRendererOwnership(PlaybackObserver.RendererBaseline b){return true;}
 void clearRelayRendererOwnership(){}
}
final class PcmCaptureBackend {
 static final int SAMPLE_RATE=48000, CHANNELS=2;
 static final class CaptureTimestamp {
  final boolean valid; final long framePosition,nanoTime;
  CaptureTimestamp(boolean v,long f,long t){valid=v;framePosition=f;nanoTime=t;}
 }
}
final class AccessibilityPcmRenderer {
 static int opens,writes,stops;
 static AudioManager audio;
 static final class UnconfirmedShutdownException extends IllegalStateException {
  private static final long serialVersionUID=1L;
  final AccessibilityPcmRenderer renderer=null;
 }
 static final class WriteResult { boolean success=true; int writtenSamples; String reason="ok"; }
 static final class Health {
  boolean healthy=true; String reason="ok"; RelayLatencyTracker.Stats latency=new RelayLatencyTracker().stats();
  int underrunCount=0; long totalFramesWritten=0; boolean outputEnabled=true; String routeKey="speaker";
 }
 static AccessibilityPcmRenderer open(AudioDeviceInfo d){
  if(audio.media!=0)throw new AssertionError("renderer opened before original was muted");
  opens++;return new AccessibilityPcmRenderer();
 }
 boolean enableOutput(){return true;}
 boolean neutralize(){stops++;return true;}
 Health health(){return new Health();}
 WriteResult write(short[] pcm,int count,PcmCaptureBackend.CaptureTimestamp t){
  if(audio.media!=0)throw new AssertionError("duplicate original playback");
  if(!RelayRendererHealthGuard.withinFinalPcmBoundary(pcm,count))throw new AssertionError("unsafe actual samples");
  writes++;WriteResult r=new WriteResult();r.writtenSamples=count;return r;
 }
}
