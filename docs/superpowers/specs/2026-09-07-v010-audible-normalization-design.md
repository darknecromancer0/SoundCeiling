# v0.10 audible normalization architecture

User direction (2026-09-07): continue the newest factual state, investigate previous
APKs, change the approach where necessary, prioritize audible normalization without
root. Blocking pilot safety mechanisms may be removed; detected Volume Down should
pause processing. This direction supersedes earlier permanent-HOLD and +3 dB pilot
requirements. No root, DRM/capture-policy bypass, or store publication is requested.

## Starting evidence

- GitHub PR #8 is draft, branch `feature/v0.7-adaptive-envelope`, HEAD
  `7a3e7c45c3826791b414769a3062b6cfecd3f220`. CI run 33934450796 / #1008 passed.
- The recovered local prior checkout has an equivalent tree under different commits;
  this work starts from the published HEAD, preserving the old checkout.
- All nine latest logs (2026-09-03) report v0.9.1, not v0.9.2. Real field acceptance
  of v0.9.2 is absent. Earlier chats can be retrieved only as fragments; do not claim
  that every full transcript was read.
- `SoundCeiling-20260903-115722.log.txt`: targeted Yandex PCM has peak about -1 dBFS
  and loudness about -8 while Media=0. Capture actually works independently of the
  muted original. Relay enters PREFLIGHT seven times across the latest logs but
  immediately returns OFF, overwriting the failure with `relay_cleanup_complete`.
- Relay demands PRE_VOLUME before it permits the mute-and-capture proof that could
  establish independence. Ordinary v0.9.2 Media always uses the full PRE/POST interval,
  even with a known reference. At Samsung index 3 this interval is 43 dB wide.
- The linked target starts as absolute -20 dB instead of source reference loudness
  plus the user's route gain. OutputCeilingState clamps below -60 dB; a correct linked
  target at index 3 is -18 + -43 = -61 dB, so runtime target math must avoid that clamp.
- Per-block PCM currently inherits a 0.2 dB command deadband from a slow external
  actuator. Small updates are discarded forever at short block intervals. The +3 dB
  pilot also prevents meaningful whisper lift.

## Chosen architecture

Preserve capture, source policy, volume provenance, UI and lifecycle infrastructure.
Provide two mutually exclusive audible paths. Session DynamicsProcessing remains
quarantined: field readback was not proof of correct speaker routing. An owned player
would provide reliable local-file DSP but would not serve YouTube/Yandex. Accessibility
Relay is the existing public-API path worth completing; Media provides coarse control.

### PCM processing

Normalize the PCM source to the reference loudness (`ControlProfile.targetLoudness`)
when Linked Lock is enabled. Accessibility hardware volume then defines listening
level. Unlinked ceilings retain their explicit output-domain meaning. Do not chase
hardware volume with digital gain in linked mode. Use a continuous per-block gain
envelope without an actuator command deadband. Preserve the final -6 dBFS sample
ceiling, finite checks, stereo balance and no-signal behavior. Default gain range is
-48 to +24 dB; explicit Full permits +30 dB. These are DSP limits, not acoustic SPL
guarantees. Verify quiet/loud convergence using actual PCM arrays at 10 ms cadence.

### Ordinary Media

Use source PCM plus the vendor route curve as the feedforward estimate; label the
public-capture PRE interpretation explicitly instead of calling an assumed reference
measured. Linked target = source reference loudness + curve(user anchor), fixed across
app writes and recomputed for explicit user changes. Runtime target is not limited
by presentation slider bounds. Unlinked target remains the chosen ceiling interval.
Select only adjacent steps that improve error with hysteresis; use faster downward
evidence than upward evidence. Do not use raw source peak as physical output peak.
Keep the explicit Media maximum/Safety Maximum, mute, source policy and user-down
latch. Report saturation when the floor/cap prevents convergence. This path cannot
catch a transient before it is played and does not claim a PCM limiter.

### Relay bootstrap and lifecycle

Preflight checks access, route, exact allowed capture, current generation and output
domain. It does not require prior PRE_VOLUME classification. Before creating output,
save the recovery record, acknowledge Media=0, discard buffered pre-mute audio and
require fresh non-silent PCM spanning at least 500 ms. This per-epoch evidence is the
authority for starting the bounded preview. No signal means timeout and cleanup.
Keep explicit one-stream preview confirmation for this previously untested renderer.
Preview must use an audible user-related Accessibility index within the explicit cap,
not compound a minimum hardware step with extreme digital attenuation.

Keep root-cause diagnostics through cleanup. Temporary startup warmup should wait
within a bounded startup interval instead of dropping the request. Fresh source,
route, token or output topology failures still revoke the old renderer. A detected
Volume Down aborts Relay and leaves Media muted; automatic processing remains paused
until an explicit restart. The user may change Media manually. No silent restart.

## Validation and delivery

Behavior tests must fail on the old code for at least: PRE bootstrap rejection;
short-cadence PCM non-convergence; ordinary Samsung linked low-volume HOLD; retained
failure reason. Check no clipping, no amplification of silence, caps, user down,
stop/restart, recovery and output exclusivity. Existing historical tests that forbid
the newly authorized behavior are updated explicitly, not counted as current proof.
Build and sign using the existing GitHub workflow and stable debug key, keep PR #8
draft, deliver one install-over v0.10.0 APK and a short device-test procedure. Report
desktop/CI results separately from physical Samsung results, which require the user.

Primary platform boundary: https://developer.android.com/media/platform/av-capture
documents a copy of capturable MEDIA/GAME/UNKNOWN playback, not interception of all
device audio; protected or opted-out playback remains unsupported.
