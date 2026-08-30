# SoundCeiling v0.9 PCM Shadow Feasibility and Session DSP Quarantine

## Goal

Ship an install-over-safe `v0.9.0` field build for Samsung SM-A528B that removes every runtime
path to the unsafe third-party Session `DynamicsProcessing` effect, preserves Samsung Media as the
user master, and evaluates the approved `PlaybackCapture -> SoundCeiling PCM DSP -> AudioTrack`
architecture without ever producing duplicate or uncontrolled audible output.

v0.9 is a feasibility release. It must not claim that captured PCM is controlling audible output
unless exclusive replacement of the original playback, output routing, latency, lifecycle, and
Samsung Media authority are all independently proven.

## Field evidence and root causes

The three v0.8.0 Samsung runs in `upload/SoundCeiling-20260830-011212.log.txt`,
`upload/SoundCeiling-20260830-011341.log.txt`, and
`upload/SoundCeiling-20260830-011558.log.txt` all selected
`cts_frequency_full_bypass_stereo` and promoted it to verified authority.

The combined evidence is:

- all 58 logged `session_dsp_apply` attempts reported `applied=false`; the reported applied gain
  stayed `0.0 dB`, so quiet/loud normalization never reached the actuator;
- the transport returns a normal slew/deadband hold as a rejected apply, and the service then
  treats that ordinary hold as a fatal Session DSP failure;
- the output anomaly guard never ran because it requires positive applied gain, while the dangerous
  field behavior occurred with the neutral effect at `0.0 dB` and sometimes without a trusted
  output-domain meter;
- the first run logged 116 rejected Samsung panel overshoots, including 14 corrective writes whose
  immediate readback was still above the active hard maximum; this proves the panel boundary remains
  reactive even though illegal values did not become the user anchor;
- the user heard the neutral effect detach physical loudness from Samsung Media/Safety, including a
  full-volume result. Framework topology/readback therefore cannot prove physical Media authority on
  this route.

The root cause is architectural, not another candidate-profile mismatch: attaching a third-party
Session effect is itself unsafe on the field route. v0.9 must not repair the slew bug and try the
effect again.

## Android feasibility boundary

Public `AudioPlaybackCapture` is copy-only. AOSP builds its capture mix with
`ROUTE_FLAG_LOOP_BACK | ROUTE_FLAG_RENDER`; `AudioMix` defines that combination as keeping the
targeted playback unaffected while exposing a copy to `AudioRecord`.

References:

- https://developer.android.com/media/platform/av-capture
- https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/AudioPlaybackCaptureConfiguration.java
- https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiopolicy/AudioMix.java
- https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiopolicy/AudioPolicy.java

Starting a normal `AudioTrack` from that copy would layer processed audio over the still-audible
original. Exclusive route replacement is not exposed by the public no-root API used by SoundCeiling;
hidden/system audio policy is outside this release and cannot preserve the current install/runtime
contract.

Therefore v0.9 uses a hard output gate:

- capture semantics: `COPY_ONLY`;
- duplicate prevention: `UNAVAILABLE`;
- audible PCM renderer: `BLOCKED`;
- runtime mode: `SHADOW_ONLY`;
- reason: `public_playback_capture_keeps_original_audio`.

No Samsung field action can override this gate. No `AudioTrack` is created by the normalizer.

## Runtime architecture

### 1. Enhanced Session fail-closed quarantine

`EnhancedSessionSetup` exposes one canonical runtime flag and reason:

- `RUNTIME_QUARANTINED = true`;
- `RUNTIME_QUARANTINE_REASON = field_quarantined_neutral_media_bypass`.

The runtime, facade, manager, and Android factory each reject Enhanced Session verification before
candidate iteration or effect construction. Defense in depth is intentional. Historical matrix code
may remain for regression documentation, but it is unreachable from v0.9 runtime.

The app no longer asks the user to grant `android.permission.DUMP`, and the manifest no longer
declares it. Existing UI setup buttons are hidden. Diagnostics identify the field quarantine instead
of calling it a missing setup step.

### 2. PCM shadow DSP

The existing playback capture and source-identity pipeline remains the only audio input. Shadow
processing is eligible only when the existing policy has positively allowed the source as MEDIA,
source confidence is sufficient, the output-domain projection is known, and PCM DSP is enabled by
the selected profile. Unknown or excluded usages remain inactive. For each eligible block, a pure
`PcmShadowDsp`:

1. constructs the same explicit output-domain projection used by the coordinator;
2. uses the existing output ceilings, tolerance, strength, attack, release, Media route curve, and
   hard peak ceiling to calculate a continuous gain;
3. applies that gain to a separate reusable PCM16 shadow buffer with saturating conversion;
4. reports requested/applied shadow gain, input peak, shadow PCM peak, projected output peak,
   clipped-sample count, and decision reason;
5. never hands the shadow buffer to an audible sink.

Peak violations attenuate immediately. Recovery remains smoothed by the existing
`ContinuousDspController`. Capture replacement, route change, stop, and a new service epoch reset the
shadow controller to neutral.

The shadow computation is not an actuator and does not give the coordinator verified DSP authority.
Ordinary normalization therefore remains `HOLD`; only the existing explicit hard Media cap, hard
output-peak safety, Quiet Now, and proven debt-only recovery retain Media-write authority.

### 3. Truthful state and telemetry

Runtime state adds a PCM DSP mode/reason and shadow metrics. Simple, Advanced, and Diagnostics views
must distinguish:

- playback PCM metering is active;
- Session DSP is quarantined;
- PCM DSP is `SHADOW_ONLY`;
- audible normalization is blocked because the original playback cannot be replaced safely.

Logs include one stable `pcm_dsp_feasibility` event per service/capture lifecycle and bounded
`pcm_dsp_shadow` summaries. They must never imply that shadow gain was audibly applied.

## Preserved safety invariants

- Samsung Media is the user master anchor; manual moves rebase immediately.
- Volume Down is never intercepted and never repaid as SoundCeiling debt.
- Automatic UP may only repay acknowledged SoundCeiling-owned attenuation and may never exceed the
  current user anchor.
- Illegal Samsung panel overshoots never update anchors, Linked Lock ceilings, or debt.
- `automatic target <= user ceiling <= safety ceiling` remains the setting/runtime ordering.
- MEDIA is the only default controlled usage. Calls, alarms, ringtone, notifications, DTMF,
  accessibility, assistant, and system applications remain excluded unless the existing policy says
  otherwise.
- Stop, route change, capture replacement, and epoch invalidation remove all DSP/shadow state.
- No source PCM is saved to logs.

## Release acceptance

v0.9.0 is acceptable for field testing only when all of the following are true:

- versionCode is `36`, versionName is `0.9.0`, and the stable development signer is unchanged;
- install-over from v0.8.0 succeeds;
- no normalizer runtime path can emit `session_dsp_candidate_attempt`,
  `session_dsp_candidate_selected`, `session_dsp_readback_begin`, `session_dsp_apply`, or an active
  Session DSP state;
- the log emits `field_quarantined_neutral_media_bypass` before any Enhanced Session constructor can
  be reached;
- the PCM feasibility verdict is `SHADOW_ONLY/BLOCKED` with
  `public_playback_capture_keeps_original_audio`;
- pure tests show positive shadow gain for quiet material, negative gain for loud material, immediate
  peak attenuation, smoothed recovery, saturating PCM conversion, reset on lifecycle boundaries, and
  no projected peak above the configured ceiling;
- unknown, low-confidence, disabled, and policy-excluded sources never receive shadow gain;
- Android wiring contains no audible PCM renderer in `NormalizerService`;
- Samsung Media and Safety remain physically authoritative with no Session effect attached;
- the full historical pure suite, all release contracts, Android API 35 compilation, APK signing,
  checksum, and artifact upload pass on one immutable commit.

## Implemented v0.9 checkpoint

The v0.9 implementation now enforces the Session quarantine at setup/runtime, facade, manager, and
Android factory boundaries. It removes DUMP from the manifest and UI, publishes the immutable public
playback-capture verdict, runs exact-policy MEDIA PCM through a separate lifecycle-bound shadow
buffer, and exposes non-audible metrics without granting coordinator or output-sink authority.

The remaining release evidence is mechanical: run every historical/v0.9 gate, compile against
Android API 35, verify the stable signer and checksum, and upload the APK from the same immutable
commit. Samsung field acceptance remains required after install-over.

## Non-goals

- Do not try another `DynamicsProcessing`, `LoudnessEnhancer`, Equalizer, or other third-party Session
  effect.
- Do not use hidden APIs, reflection, root, Shizuku, privileged/system signing, or stream substitution.
- Do not mute Media and replay through another Android stream; that would break Samsung Media master
  authority and default system-sound exclusions.
- Do not call shadow processing an audible fix.
- Do not resume post-1.0 UI/features or unrelated refactoring.
