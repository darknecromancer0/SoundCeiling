# Sound Ceiling for Android - v0.9.0

SoundCeiling is a no-root Android 10+ adaptive audio safety controller. v0.9.0 is an install-over
corrective and PCM feasibility build driven by Samsung v0.8.0 field evidence. Samsung Media remains
the user master; hard Media/Safety actions remain separate from ordinary normalization.

## v0.9.0 PCM shadow feasibility and Session DSP quarantine

- **Enhanced Session DSP is field-quarantined before construction.** The neutral v0.8.0 Samsung
  effect could break physical Media authority even though its custom topology and handshake read
  back correctly. Runtime, facade, manager, and Android factory all reject it with
  `field_quarantined_neutral_media_bypass`. The v0.8 matrix remains only as historical regression
  code and cannot be selected or attached.
- **The obsolete DUMP setup is removed.** v0.9.0 neither declares `android.permission.DUMP` nor
  asks the user to run an ADB grant command. Simple, Advanced, Diagnostics, and logs report the
  field quarantine directly.
- **PCM normalization now runs in an isolated shadow buffer.** For a positively allowed,
  high-confidence targeted MEDIA source with a known output-domain projection, the processor
  calculates continuous positive gain for quiet material and attenuation for loud material/peaks.
  It enforces the configured output peak ceiling and `-0.5 dBFS` PCM headroom with saturating PCM16
  conversion and lifecycle reset.
- **PCM DSP is deliberately non-audible in this build.** Public Android playback capture keeps the
  original playback rendered. Starting a normal output track from the processed copy would duplicate
  the sound, so the immutable verdict is `SHADOW_ONLY`, `audibleOutputAllowed=false`, reason
  `public_playback_capture_keeps_original_audio`. No audible PCM renderer is created.
- **The UI and logs do not claim that shadow gain was applied to the speaker.** Telemetry uses
  `pcm_dsp_feasibility`, `pcm_dsp_shadow`, and `pcm_dsp_runtime`, including requested/shadow gain,
  projected/PCM peaks, clipping count, eligibility, and the blocked-output reason.
- **Safety and user authority are unchanged.** Volume Down wins immediately. Automatic Media UP
  remains limited to acknowledged SoundCeiling-owned attenuation debt. Safety Maximum, hard cap,
  Quiet Now, source exclusions, route/capture reset, and stop/restart fail-closed behavior remain
  active without a Session effect.

This release verifies the PCM algorithm and safe routing boundary; it does **not yet make quiet and
loud content converge audibly**. Audible replacement remains blocked until an exclusive public
route can be proven without duplicating playback or taking authority away from Samsung Media.

Samsung field acceptance: `docs/field-tests/2026-08-29-v0.9.0-samsung-checklist.md`.

## v0.8.0 safe Samsung Session DSP matrix

- **OEM-default Enhanced Session DSP stays quarantined.** The unsafe one-argument
  `DynamicsProcessing(sessionId)` constructor is never used for third-party sessions. v0.7.7.9
  proved that plausible readback of an unknown Samsung topology was not enough to make it safe.
- **Three explicit stereo candidates are tried in a fixed order.** The first mirrors Android CTS:
  frequency-resolution, 9.5 ms frames, two bands in PreEQ/MBC/PostEQ and a limiter, with every
  optional stage disabled. Two simpler frequency-resolution bypass profiles follow. The historical
  field-rejected time-resolution topology and mono guesses are excluded.
- **Topology identity is part of authority.** Variant, frame duration, channel count, stage-in-use
  flags, band counts, disabled state, control ownership and per-channel gains must all read back
  exactly before the bounded `0 -> -0.5 -> 0 dB` handshake can promote a candidate.
- **Positive gain is a `+3 dB` field pilot.** Attenuation retains the existing range and immediate
  hard-safety path, but quiet-program gain cannot exceed `+3 dB` in this build. Requested and
  actually applied gain remain distinct in telemetry.
- **Contradictory output fails closed.** If a positive Session gain coincides with near-full-scale
  actual output while the PRE_VOLUME projection remains safely below the hard ceiling, the session
  is neutralized, disabled, released and suppressed until the session or route changes.
- **Samsung Media remains untouched by ordinary normalization.** Manual moves rebase the anchor;
  normal Media UP remains debt-only; hard cap and Quiet Now retain their explicit safety authority.
  If all custom candidates fail, ordinary normalization truthfully holds instead of reviving a
  coarse target-chasing fallback.

Samsung physical acceptance: `docs/field-tests/2026-08-29-v0.8.0-samsung-checklist.md`.

## v0.7.7.2 Samsung Session DSP compatibility corrective

- **The Samsung constructor failure is now isolated.** SM-A528B / Android 14 accepted the v0.7.7 architecture when a Limiter stage existed, but rejected the v0.7.7.1 architecture where every optional processing stage was out of use with `dsp_create_failed:custom=IllegalArgumentException`.
- **A disabled limiter compatibility shell is used only for engine creation.** Enhanced Session `DynamicsProcessing` declares Limiter `inUse=true` so the Samsung engine accepts the architecture, but configures that limiter `enabled=false` on every channel. Android documents disabled stages as bypassed, so input gain remains the only active processing control.
- **Readback must prove the shell is still bypassed.** PreEQ, MBC and PostEQ must remain out of use, every limiter channel must read back disabled, `AudioEffect.hasControl()` must be true, and the bounded input-gain handshake remains `0 dB -> -0.5 dB -> 0 dB`.
- **Any active limiter fails closed.** If even one limiter channel reads back enabled, Session DSP authority is rejected. The historical v0.7.7 active 10:1 / -1 dBFS limiter configuration is explicitly regression-locked out.
- **No OEM default topology fallback for Enhanced Session.** If Samsung still rejects the explicit compatibility topology, the session stays unavailable rather than switching to an unknown OEM configuration.
- **Samsung Media remains the user master.** Media **3/15** remains a required acceptance point. Ordinary normalization must use verified non-zero Session DSP and must not move the Samsung slider.

Samsung physical acceptance: `docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md`.

## v0.7.7.1 deterministic Session DSP corrective

- **Enhanced Session verification is deterministic.** Exact non-zero `sessionId + UID + package` ownership establishes which playback session may be controlled. Transport authority is verified through Android `DynamicsProcessing` configuration/readback, not through asynchronous PCM/Visualizer level comparisons.
- **v0.7.7.1 attempted a pure input-gain-only architecture.** PreEQ, MBC, PostEQ and Limiter were all out of use. Samsung field evidence then showed that this all-optional-stages-disabled architecture itself was rejected during engine creation, which v0.7.7.2 corrects with a disabled compatibility shell.
- **The bounded handshake is `0 dB -> -0.5 dB -> 0 dB`.** Every configured channel must read back neutral gain, the requested small probe gain, and the restored neutral gain. Any topology, channel-count, gain-readback or restore mismatch fails closed and the exact session is not promoted.
- **PCM/Visualizer residuals are diagnostics, not the Enhanced Session authority gate.** Samsung field traces showed the same session producing apparent residual shifts from about -16.6 dB to +9.7 dB because targeted PCM and Visualizer are not audio-time synchronized. Those values remain useful for metering/output-domain diagnostics but cannot blacklist an otherwise valid non-zero DSP transport.
- **Samsung Media remains the user master.** Media **3/15** is a required acceptance point. Safety Maximum and Quiet Now retain their explicit Media authority.
- **Debug signing is temporary during development.** Public GitHub Actions currently uses the normal generated debug signing identity. Production/RuStore signing remains a separate release concern.

Historical Samsung acceptance: `docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md`.

## v0.7.7 Enhanced Session DSP

- **Non-zero session authority.** v0.7.7 introduced discovery of exactly one active non-zero audio session owned by the exact playback UID. Its original audible differential verification is retained as historical code/tests; v0.7.7.1 replaced that production verification gate with deterministic Android readback.
- **Samsung Media remains user authority.** Media **3/15** is a first-class user anchor. Ordinary normalization must not pull the Samsung slider away from 3/15, 2/15, or another manual step. With verified Session DSP, correction is continuous DSP gain; without it, ordinary normalization holds.
- **One-time no-root setup.** Enhanced Session DSP discovery requires the Android DUMP permission granted once from ADB: `adb shell pm grant dev.soundceiling.app android.permission.DUMP`. Simple and Advanced modes expose setup status and a copyable command.
- **Fail-closed transport.** A stale, ambiguous, failed or unverified session transport is released. A failed gain apply revokes Session DSP authority and returns ordinary control to HOLD rather than reviving target-chasing Media fallback.
- **Diagnostics expose the real actuator.** Runtime state and logs include session ID, UID, package, requested/applied gain, reason, `session_dsp_readback_result`, and `session_dsp_apply`.
- Historical Safety Maximum, Quiet Now, source-policy gates, user-master anchor, debt-only recovery, stop/restart protection, and v0.7.6.x output-domain safety contracts remain intact.

Historical Samsung acceptance: `docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md`.

## v0.7.6.3 Samsung DSP attach evidence corrective

- **Retryable neutral-attach evidence.** `attach_insufficient_pairs` and `attach_insufficient_coverage` no longer masquerade as a proven non-neutral session-zero attach. SoundCeiling keeps the transport neutral at 0 dB and continues collecting bounded evidence instead of suppressing Global DSP immediately.
- **The 250 ms proof floor is retained.** This corrective does not lower or bypass the minimum valid paired-evidence coverage required before a neutral attach is accepted.
- **Attach timing uses a post-attach clock.** The 250 ms service-side wait starts after `DynamicsProcessing` has actually been enabled, so effect-construction latency is not incorrectly counted as acoustic evidence.
- **True non-neutral attach remains fail-closed.** A conclusive finite residual shift outside the neutral tolerance still detaches the transport immediately and suppresses it for the route.
- **Probe lifetime remains bounded.** Retryable evidence cannot leave an unverified transport hanging indefinitely; the existing 1.5 s differential-probe timeout remains authoritative.
- Diagnostics expose `retryable`, `coveredMs`, and `dsp_global_attach_wait`, making an inconclusive attach distinguishable from an unsafe attach in Samsung field logs.
- v0.7.6.2 output-domain behavior is preserved: proven `PRE_VOLUME` PCM owns normalizer projection and Visualizer-only UNKNOWN evidence cannot drive ordinary Media normalization.

Historical Samsung acceptance: `docs/field-tests/2026-08-25-v0.7.6.3-samsung-checklist.md`.

## v0.7.6.2 Samsung output-domain corrective

- **PRE_VOLUME PCM outranks Visualizer for normalizer control.** Once targeted playback capture proves that PCM is before Samsung Media attenuation, the output estimate is projected from source level plus the route gain instead of being replaced by a fresh Visualizer frame.
- **Visualizer-only UNKNOWN evidence is fail-closed.** A readable Visualizer frame does not by itself prove post-volume semantics on an OEM route, so ordinary coarse Media normalization cannot use it to walk the Samsung slider downward.
- Coarse Media fallback remains available when the output domain is actually proven. It still moves by at most one step after dwell and recovery remains limited to SoundCeiling-owned attenuation debt.
- Visualizer remains available to the separate paired Global DSP differential-verification path. This corrective does not weaken the v0.7.6.1 neutral-attach and bounded-probe safety sequence.
- The user master anchor, hard Media cap, Quiet Now, source-policy gates, stop/restart lifecycle protection, and all historical contracts remain unchanged.

Historical Samsung acceptance: `docs/field-tests/2026-08-25-v0.7.6.2-samsung-checklist.md`.

## v0.7.6.1 Samsung DSP safety corrective

- Global DSP diagnostics are side-effect free: logging can no longer construct session-zero `DynamicsProcessing`.
- Probe order is **baseline with no DSP → neutral 0 dB attach → attach verification → bounded -0.5 dB differential probe**.
- A neutral attach that changes or loses output is classified unsafe, detached immediately, and suppressed for the route until route change or a deliberate Global DSP toggle.
- A strong/nonlinear response to the small probe is preserved as `RESPONSIVE_NONLINEAR` evidence but never promoted to continuous gain authority.
- Unverified global transports are detached after failed/cancelled probes instead of remaining attached at 0 dB.
- Fresh field-test logs are protected for at least 24 hours and the normal retained budget is 64 MiB.
- Multiband/MBC normalization remains disabled in this corrective build until broadband session-zero attach/gain behavior is proven safe on the Samsung device.

Historical Samsung acceptance: `docs/field-tests/2026-08-24-v0.7.6.1-samsung-checklist.md`.

## v0.7.6 Control Architecture Reset

- **Samsung slider is the user master anchor.** Manual Media movement rebases control immediately. SoundCeiling may never silently redefine the user's chosen Samsung step.
- **Verified DSP is the continuous normalizer.** DSP normalization becomes active only after verification proves that the transport controls the intended output path with the required scope.
- **Historical v0.7.6 scope proof used route-scoped differential verification.** That acoustic proof remains a historical contract; v0.7.7.1+ Enhanced Session production authority uses deterministic Android readback instead.
- **Unverified DSP is reported honestly.** If verification is absent or fails, UI and logs do not claim verified DSP authority.
- **Raw peak is not output authority.** A **source peak alone cannot force Media down**. Ordinary control uses measured/projected output evidence. Hard Media cap and Quiet Now remain separate immediate safety paths.
- **Low-volume authority is explicit.** Media 1/15 and 2/15 are valid user anchors, not automatic normalization destinations. Manual 1→2, 2→3 and 3→2 changes must not snap back.
- **Diagnostics expose actual control.** Rate-limited summaries include actuator tier, meter domain, DSP state, requested/applied DSP gain, source/output levels, Media anchor/debt/dwell, and decision reason.

Historical v0.7.6 acceptance: `docs/field-tests/2026-08-24-v0.7.6-samsung-checklist.md`.

## v0.7.4 corrective highlights

- **Global DSP probe no longer cancels itself.** The bounded verification probe is held for measurement rather than being mistaken for stale DSP state.
- **Silence is not PRE/POST evidence.** Silent before/after samples never establish capture-reference proof.

## v0.7.3 corrective highlights

- **Media UP is debt-only.** Ordinary recovery can repay only attenuation previously created by SoundCeiling and cannot exceed the latest user anchor.

## v0.7.2 corrective highlights

- **Samsung Media is the user anchor.** Real user volume moves rebase the anchor; app-owned attenuation debt is tracked separately.
- Yandex Music and YouTube source recognition remains evidence-based rather than package-name proof.

## Safety and historical compatibility

The v0.7 **Adaptive Envelope** invariant remains `automatic target <= User ceiling <= Safety ceiling`. Historical compatibility from **v0.5.1** onward is retained, including **volume-neutral calibration**, **non-raising Quiet Now**, transient safety, user-authority semantics, route-bound calibration, and repeated stop/restart lifecycle protection.

Historical acceptance examples remain explicit:
- **Auto down 7 -> 5:** recovery may repay only SoundCeiling-owned attenuation debt.
- **User manual down 7 -> 4:** the manual user choice wins immediately.

Playback-capture/log persistence continues to use the existing **MediaStore**/SAF reconciliation model. No production analysis is taken from microphone/call audio.

## Build and verification

Requires JDK 17 and Android SDK 35. The release workflow runs the full pure suite, every historical behavior/source contract, Samsung 3/15/telemetry/wiring contracts, the v0.7.7.2 disabled-limiter compatibility topology contract, deterministic readback pure/wiring contracts, Android `:app:assembleDebug`, SHA-256 generation, and artifact upload on the same immutable head.

Release artifact: **`SoundCeiling-v0.8.0-debug-apk`**, containing `app-debug.apk`.
