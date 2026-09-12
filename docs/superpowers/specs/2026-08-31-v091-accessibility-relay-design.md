# SoundCeiling v0.9.1 Accessibility Relay Field Design

## Status

This design was approved in chat on 2026-08-31. It defines one Samsung field release, not a
store-ready product release.

Release identity:

- versionName: `0.9.1`;
- versionCode: `37`;
- install-over source: signed `v0.9.0` / versionCode `36`;
- first field device and route: Samsung SM-A528B built-in speaker;
- PR #8 remains draft until field evidence passes.

## Goal

Test the last credible universal no-root architecture for audible loudness normalization:

`target playback -> exact-UID PlaybackCapture -> SoundCeiling PCM DSP -> Accessibility AudioTrack`

While Relay is active, SoundCeiling temporarily holds Samsung Media at `0`, so the copied original
is inaudible, and plays only the processed PCM through Android's separate Accessibility stream. The
user controls Relay volume instead of Samsung Media for that session. This intentionally replaces
the earlier requirement that Samsung Media remain the direct master during normalization; the user
approved that trade-off.

The processed stream must make quiet allowed material louder, attenuate loud material and peaks,
and remain bounded by both a digital peak ceiling and a user-controlled Relay stream ceiling.

## Feasibility boundary

Public `AudioPlaybackCapture` remains copy-only. v0.9.1 does not reinterpret it as exclusive
replacement. It tests whether safe replacement can be assembled from two separate public controls:

1. targeted `PRE_VOLUME` capture continues to provide PCM while the original Media stream is at
   `0`;
2. `USAGE_ASSISTANCE_ACCESSIBILITY` playback remains audible and independently controllable while
   Media is at `0`;
3. the Accessibility output is excluded from the capture mix, producing one stream rather than a
   feedback loop or echo.

If either of the first two conditions fails on the field device, or if duplicate audio cannot be
excluded, Relay must abort before normal-volume output. No other ordinary no-root APK mechanism is
considered a safe universal fallback.

Relevant platform contracts:

- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/reference/android/media/AudioAttributes#USAGE_ASSISTANCE_ACCESSIBILITY
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_ENABLE_ACCESSIBILITY_VOLUME
- https://developer.android.com/reference/android/media/AudioAttributes#ALLOW_CAPTURE_BY_NONE

## Field scope

v0.9.1 supports only:

- one exact, policy-allowed, non-system source UID;
- MEDIA playback that the source application allows Android to capture;
- a positively identified `PRE_VOLUME` capture domain;
- one active playback endpoint;
- the built-in speaker route on the first Samsung field release;
- explicit MediaProjection consent for the current service epoch;
- explicit Relay test confirmation for the current source, route, and projection epoch.

Bluetooth, wired headsets, USB audio, casting, calls, alarms, ringtone, notifications, assistants,
accessibility speech, protected playback, multiple sources, system applications, `POST_VOLUME` and
unknown capture domains remain blocked. Route support may expand only in a later field release with
route-specific evidence and limits.

## Preserved safety invariants

- Enhanced Session DSP remains field-quarantined before every Android effect constructor. Relay
  must not weaken or bypass `field_quarantined_neutral_media_bypass`.
- No `DynamicsProcessing`, `LoudnessEnhancer`, Equalizer, session-zero effect, hidden API, root,
  Shizuku, privileged signing or audio-policy reflection is introduced.
- Media reaches acknowledged zero before any Relay `AudioTrack` may emit a non-zero sample.
- SoundCeiling never automatically raises Media or Accessibility stream volume during Relay.
- Hardware Volume Down takes effect immediately and is never repaid.
- Hardware Volume Up advances only the Relay stream, one bounded step, and never exceeds the Relay
  hard maximum.
- `automatic target <= configured ceiling <= safety ceiling` is enforced in settings, pure policy,
  and the final renderer boundary.
- The Relay renderer is silenced and stopped before any Media restoration write.
- Verification is scoped to one source/route/projection epoch and never survives restart, route
  change, capture replacement or source transition.
- Captured PCM is processed only in memory and is never logged, saved or transmitted.

## Runtime state machine

`AccessibilityRelayGate` is a pure state machine. Android wrappers report facts and execute emitted
actions; they cannot skip a transition.

States:

- `OFF`: no lease, renderer or Relay authority;
- `PREFLIGHT`: validate Accessibility, source, route, projection, output curve and recovery state;
- `CAPTURE_PROVEN`: exact targeted PCM and `PRE_VOLUME` are stable;
- `MEDIA_MUTING`: persist the lease, write Media `0`, and wait for bounded acknowledgement;
- `MEDIA_MUTED`: Media zero is stable and targeted PCM still contains signal;
- `QUIET_PROBE`: emit at most five seconds of severely attenuated processed PCM;
- `AWAITING_CONFIRMATION`: renderer is silent while the user reports the probe result;
- `ACTIVE`: full Relay processing is allowed for the current epoch;
- `ABORTING`: renderer neutralization and ownership-aware cleanup are in progress;
- `RECOVERY_REQUIRED`: a previous lease cannot be safely resolved automatically.

The only activation sequence is:

`OFF -> PREFLIGHT -> CAPTURE_PROVEN -> MEDIA_MUTING -> MEDIA_MUTED -> QUIET_PROBE -> AWAITING_CONFIRMATION -> ACTIVE`

Any failed gate moves to `ABORTING`. A process death or an unconfirmed renderer stop leaves a durable
`RECOVERY_REQUIRED` record. There is no transition directly from `OFF`, `PREFLIGHT`, or
`CAPTURE_PROVEN` to audible output.

## Components

### 1. `AccessibilityRelayGate`

The gate owns transition legality, epoch identity, timeouts and required actions. Its input is a
small immutable snapshot containing source identity, route, capture domain, playback state,
Accessibility connection, stream bounds, Media acknowledgement, capture signal proof and renderer
health. It contains no Android classes and is exhaustively pure-tested.

### 2. `RelayMediaLease`

Before the first Media write, the lease durably records:

- service/projection/source/route epoch;
- pre-Relay Media index;
- current Safety Maximum;
- pre-Relay Accessibility index;
- whether each stream value is still SoundCeiling-owned.

Muting uses at most three downward-only writes inside a 500 ms acknowledgement window. Media must be
observed at `0` continuously for at least 100 ms before the mute is accepted. After acknowledgement,
any observed Media value above `0` that is not an outstanding SoundCeiling write is a user exit.
SoundCeiling then stops Relay and leaves that user-selected Media value unchanged. It must not fight
the Samsung panel.

For an ordinary stop, Media is restored only after renderer shutdown and only when the current
Media value is still the app-owned zero. The restore target is:

`min(preRelayMediaIndex, currentSafetyMaximumIndex)`.

If Media has changed independently, restoration is skipped. If ownership or renderer shutdown is
uncertain, Media remains at `0` and recovery requires an explicit user action on the next foreground
launch.

### 3. `RelayVolumeController`

`VolumeKeySafetyService` requests both key filtering and
`FLAG_ENABLE_ACCESSIBILITY_VOLUME`. Outside Relay it preserves the existing Strict Safety behavior.
During `QUIET_PROBE`, `AWAITING_CONFIRMATION`, and `ACTIVE`, it consumes complete Volume Up/Down key
pairs and targets `STREAM_ACCESSIBILITY`, never `STREAM_MUSIC`.

The Relay hard maximum is calculated by mapping the existing Safety Maximum percentage into the
Accessibility stream's reported min/max range with
`min + floor((max - min) * safetyPercent / 100)`. The result is clamped to the reported range and is
clamped again immediately before every stream write. The probe starts at the minimum non-zero
Accessibility step not exceeding that maximum. SoundCeiling may lower the stream for startup safety
but may never increase it automatically; only an explicit user button/slider action or hardware
Volume Up may advance one step.

Volume Down always lowers one step immediately. Volume Up is blocked before probe confirmation and,
after confirmation, advances at most one step up to the hard maximum. An external Accessibility
stream change above the hard maximum causes renderer mute and Relay exit; the app does not keep
rewriting the user's slider.

Because `STREAM_ACCESSIBILITY` is shared, the first field build refuses Relay while another enabled
Accessibility service advertises spoken feedback. The pre-Relay Accessibility index is restored
after renderer shutdown only if the current value still matches SoundCeiling's last owned value.

### 4. `AccessibilityPcmRenderer`

The Android renderer is created only for `QUIET_PROBE` or `ACTIVE`. It uses:

- 48 kHz stereo PCM16;
- `AudioTrack.MODE_STREAM`;
- `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY`;
- `AudioAttributes.CONTENT_TYPE_MUSIC`;
- `AudioAttributes.ALLOW_CAPTURE_BY_NONE`;
- blocking writes in the existing 960-short capture cadence;
- low-latency performance mode when the device accepts it;
- no audio-focus request.

Capture continues to match only source MEDIA/GAME usage and the exact source UID. Accessibility
usage is never added to `PcmCaptureBackend`, providing a second loop-prevention boundary in addition
to `ALLOW_CAPTURE_BY_NONE`.

The renderer reports partial writes, error codes, timestamp-derived latency, routed-device changes
and underrun-count deltas. Three underrun increments within two seconds, any dead-object/write error,
an unexpected route, or missing timestamps during field acceptance aborts Relay.

Immediate neutralization order is fixed:

1. set the track's local gain to zero;
2. pause and flush queued PCM;
3. stop and release the track;
4. clear all reusable PCM buffers;
5. report renderer stopped to the gate;
6. only then allow lease cleanup or Media restoration.

### 5. Relay PCM normalization

The proven pure shadow algorithm remains the basis, but audible Relay uses an explicit Relay output
domain rather than the Media route curve. `RelayOutputDomain` reads the Accessibility stream index
and a finite `AudioManager.getStreamVolumeDb(STREAM_ACCESSIBILITY, index, deviceType)` result for the
current built-in-speaker route. An unavailable or changing output-domain curve blocks positive gain
and aborts field activation.

The reusable PCM core receives source loudness/peak, Accessibility route gain, user ceilings,
normalization strength and a Relay-specific gain policy. Shadow mode remains non-audible and cannot
obtain renderer authority.

Relay gain limits are:

- attenuation floor: `-48 dB`;
- safe mode positive-gain ceiling: `+3 dB`;
- explicit `Full experimental` positive-gain ceiling: `+12 dB` after successful quiet-probe
  confirmation;
- final PCM peak ceiling: `-6 dBFS` in both modes;
- zero clipped samples permitted at the renderer boundary.

The gain planner may request less gain because of the user target, loudness ceiling or peak
headroom. It can never request more than the selected Relay gain ceiling. The renderer performs a
final independent peak scan and attenuation clamp before each write, so a settings/UI defect cannot
bypass the `-6 dBFS` limit.

### 6. `AccessibilityRelayRuntime`

`NormalizerService` continues to own MediaProjection and the capture worker, but delegates Relay
state, lease, volume and renderer coordination to a focused runtime object. The service passes an
eligible processed buffer only when the pure gate returns an explicit render action for the current
epoch. This prevents the already-large capture loop from becoming the authority source itself.

## Activation protocol

### Preflight

All of the following are mandatory:

- the user explicitly starts `Relay test` and accepts the local-only capture disclosure;
- `VolumeKeySafetyService` is connected and both required flags/capabilities are effective;
- no other enabled spoken-feedback Accessibility service is present;
- `STREAM_ACCESSIBILITY` min/max and route dB values are finite;
- the current route is the Samsung built-in speaker;
- MediaProjection belongs to the current epoch;
- source UID is exact, allowed, non-system and the only active endpoint;
- playback is active and capture warmup is confirmed;
- capture reference is positively `PRE_VOLUME`;
- no unresolved recovery lease exists.

### Media-zero proof

SoundCeiling samples valid targeted PCM first, records the lease, moves Media to `0`, receives the
stable acknowledgement, and then requires at least 500 ms and five non-silent capture blocks from
the same source/route epoch. Failure or source silence prevents renderer creation, performs the
ordinary ownership-aware Media restore, and returns to `PREFLIGHT` for an explicit retry. It does not
guess that capture is pre-volume.

### Quiet probe

The probe plays the live processed source for at most five seconds. Before every probe write, samples
are additionally attenuated so their absolute PCM peak cannot exceed `-30 dBFS`. Volume Up is
blocked; Volume Down remains available. At five seconds the renderer is neutralized and the app asks
for exactly one result:

- `One clean quiet stream`: unlock `ACTIVE` for the current epoch;
- `Echo / loud / broken`: abort, restore safely and export the failure reason.

Confirmation is never persisted across restarts or route/source/projection changes.

## Active Relay rules

- Media remains observed at `0`; it is not repeatedly forced to `0` after the initial owned write.
- Manual Samsung Media movement is an explicit Relay exit and is preserved.
- Hardware keys and the Relay UI adjust only Accessibility volume within the Relay hard maximum.
- Automatic loudness correction changes digital PCM gain, not either Android stream volume.
- Quiet PCM may receive positive digital gain; loud PCM and peaks receive negative gain before the
  same block is written.
- Signal silence alone is not an error and produces digital silence. An inactive source endpoint for
  two seconds ends Relay cleanly.
- A source UID/endpoint transition, second endpoint, route change, projection loss, Accessibility
  loss, output-domain change, renderer error or safety-ordering violation aborts immediately.

## Abort and recovery policy

Every abort first neutralizes the renderer. Cleanup then depends on observed ownership:

- `USER_MEDIA_EXIT`: leave the user's non-zero Media value unchanged;
- ordinary Stop or source end: restore bounded pre-Relay Media if the app still owns zero;
- route change: restore only after the new route is stable and the bounded target is recomputed;
  otherwise enter `RECOVERY_REQUIRED` with Media at `0`;
- process death, uncertain renderer state or corrupt lease: never raise Media automatically;
- Accessibility stream: restore its saved value only when its current value remains app-owned.

On the next foreground launch, recovery explains the saved pre-Relay value and offers an explicit
`Restore safe Media` action clamped to the current Safety Maximum. Starting a new Relay is blocked
until the record is resolved or explicitly discarded at Media `0`.

## UI and truthful status

v0.9.1 adds a clearly experimental Relay card rather than silently changing existing Global DSP:

- `Start Relay test`;
- current gate state and blocking reason;
- dedicated Relay volume and hard-maximum display;
- `Safe +3 dB` and post-probe `Full experimental +12 dB` choices;
- five-second probe countdown;
- the two explicit probe-result actions;
- recovery action when required.

Simple, Advanced, notification and diagnostics text must distinguish:

- Session DSP: quarantined;
- PCM Shadow: measurement only;
- Accessibility Relay: off, probing, active, aborting or recovery required.

No UI may claim audible normalization until the gate is `ACTIVE` and renderer writes are succeeding.

## Telemetry and privacy

Stable events include gate transitions, epoch IDs, source UID/package, route type, Media lease
ownership, stream indices, capture-domain proof, probe result, requested/applied DSP gain, input and
output PCM peaks, renderer latency, partial writes, underruns and abort reason. Logs never contain
raw PCM, microphone data or captured content.

The important field reasons are machine-stable, including:

- `relay_prevolume_not_proven`;
- `relay_media_zero_not_acknowledged`;
- `relay_capture_lost_at_media_zero`;
- `relay_duplicate_or_echo_reported`;
- `relay_accessibility_output_unavailable`;
- `relay_user_media_exit`;
- `relay_route_changed`;
- `relay_renderer_write_failed`;
- `relay_recovery_required`.

## Test strategy

### Pure tests

- every allowed state transition and every forbidden jump;
- epoch invalidation on source, route, projection and capture replacement;
- Media lease acknowledgement, ownership, manual-panel exit and restore math;
- Accessibility range mapping and three-layer Safety Maximum clamping;
- Volume Down immediacy and bounded one-step Volume Up;
- `+3 dB` safe and `+12 dB` explicit gain caps;
- final `-6 dBFS` block clamp and zero-clipping invariant;
- stop-before-restore ordering;
- process-death recovery and stale-lease rejection.

### Android/source contracts

- exact `AudioTrack` attributes, streaming mode and capture exclusion;
- capture configuration never matches Accessibility usage;
- no Session DSP constructor path becomes reachable;
- no renderer is created before Media-zero/capture proof;
- every renderer exit neutralizes before a Media write;
- manifest and Accessibility XML contain the required capabilities;
- v0.9.0 shadow and all historical Samsung/user-authority/Strict Safety regressions remain green.

### CI and package evidence

- all pure and shell contract suites pass;
- `git diff --check` is clean;
- Android API 35 `assembleDebug` passes;
- stable signer fingerprint is unchanged;
- APK SHA-256 and immutable commit are recorded;
- one install-over artifact is uploaded from that commit.

## Samsung field acceptance

v0.9.1 passes only when one uninterrupted session proves all of the following:

1. targeted PCM remains non-silent and stable at Media `0`;
2. the original Media playback is physically silent;
3. the quiet probe produces one clean stream with no echo, recursion or high-volume transient;
4. quiet program material receives logged positive gain and is audibly louder;
5. loud material and peaks receive logged attenuation and are audibly lower;
6. requested settings above Safety Maximum cannot raise the Accessibility stream or renderer above
   their independent hard limits;
7. hardware Volume Down always lowers Relay, and Volume Up stops at the Relay maximum;
8. Samsung Media slider movement stops Relay without a volume fight or later repayment;
9. Stop, source end, Accessibility loss, MediaProjection loss and route-change tests leave no
   processed sound and restore only owned safe values;
10. a ten-minute run has no post-startup underruns, partial writes, renderer errors, full-volume
    lock, source drift or Session DSP event;
11. timestamp-derived added pipeline latency has median at most 120 ms and p95 at most 200 ms.

If timestamp evidence is unavailable, duplicate prevention is uncertain, or any safety test fails,
the result remains experimental failure; it cannot advance to a store build.

## Store boundary after field success

Store work is a separate follow-up release and implementation plan. It may start only after the
Samsung field acceptance above passes. The store-clean variant must:

- remove `QUERY_ALL_PACKAGES`, using explicit supported-app visibility and active source evidence;
- replace `specialUse` with justified `mediaProjection|mediaPlayback` foreground-service types when
  platform behavior is proven;
- request MediaProjection consent for every session as required by current Android versions;
- present prominent in-app Accessibility/MediaProjection disclosure and affirmative consent;
- publish a privacy policy stating that PCM remains local and is neither stored nor transmitted;
- provide Google Play Accessibility declaration text and a truthful demonstration video;
- not set `isAccessibilityTool=true` unless the product is honestly positioned and implemented
  primarily for users with disabilities;
- provide RuStore justification for every sensitive permission and service;
- keep a separate field flavor so engineering permissions cannot leak into the store manifest.

Google Play or RuStore acceptance cannot be guaranteed. A policy rejection is a release constraint,
not permission to disguise Relay, Accessibility usage or audio capture.

Current policy references:

- https://support.google.com/googleplay/android-developer/answer/10964491
- https://support.google.com/googleplay/android-developer/answer/16909972
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps

## Non-goals

- Do not claim universal Android, headphone, Bluetooth, call or protected-content support.
- Do not ship v0.9.1 to Google Play or RuStore.
- Do not revive Session DSP or Media-step normalization as an audible substitute.
- Do not automatically calibrate or raise a hardware stream.
- Do not hide latency, echo, capture failure or safety failures behind a successful UI state.
- Do not resume unrelated post-1.0 UI or feature work in this release.
