# Sound Ceiling for Android - v0.7.7.1

SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.7.1 is a Samsung Session DSP verification corrective on top of v0.7.7 Enhanced Session DSP.

## v0.7.7.1 neutral Session DSP corrective

- **The 0 dB probe is now actually neutral by construction.** Enhanced Session `DynamicsProcessing` uses an input-gain-only topology: PreEQ, MBC, PostEQ and Limiter are disabled/absent. The v0.7.7 limiter could itself alter playback during a so-called neutral attach.
- **No unknown OEM topology fallback for Enhanced Session probes.** If the explicit input-gain-only configuration cannot be created, the candidate stays unavailable instead of silently switching to a vendor default configuration whose stages are unknown.
- **Unstable PCM/Visualizer residuals are inconclusive, not unsafe.** The paired verifier now requires a stable residual window using a robust MAD gate before it can call an attach non-neutral or a probe nonlinear. This addresses Samsung field traces where the same session produced apparent neutral-attach deltas from about -16.6 dB to +9.7 dB.
- **Inconclusive evidence cannot blacklist the session.** `attach_unstable_residuals`, unstable probe evidence and an inconclusive probe timeout release the candidate and retry later. Stable proven non-neutral/nonlinear behavior remains fail-closed and may suppress the session.
- **Samsung Media remains the user master.** The required physical acceptance remains Media **3/15** with ordinary normalization performed by verified non-zero Session DSP and no Samsung slider movement.

Samsung physical acceptance: `docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md`.

## v0.7.7 Enhanced Session DSP

- **Non-zero session authority.** Ordinary normalization may use DSP only after SoundCeiling discovers exactly one active non-zero audio session owned by the exact playback UID and verifies its audible differential response. Session 0 is not the v0.7.7 normalizer target.
- **Samsung Media remains user authority.** Media **3/15** is a first-class user anchor. Ordinary normalization must not pull the Samsung slider away from 3/15, 2/15, or another manual step. With verified Session DSP, correction is continuous DSP gain; without it, ordinary normalization holds.
- **One-time no-root setup.** Enhanced Session DSP discovery requires the Android DUMP permission granted once from ADB: `adb shell pm grant dev.soundceiling.app android.permission.DUMP`. Simple and Advanced modes expose setup status and a copyable command.
- **Fail-closed transport.** A stale, ambiguous, non-neutral, unresponsive, or failed session transport is released. A failed gain apply revokes Session DSP authority and returns ordinary control to HOLD rather than reviving target-chasing Media fallback.
- **Diagnostics expose the real actuator.** Runtime state and logs include session ID, UID, package, requested/applied gain, reason, and `session_dsp_apply`.
- Historical Safety Maximum, Quiet Now, source-policy gates, user-master anchor, debt-only recovery, stop/restart protection, and v0.7.6.x output-domain safety contracts remain intact.

Historical Samsung acceptance: `docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md`.

## v0.7.6.3 Samsung DSP attach evidence corrective

- **Retryable neutral-attach evidence.** `attach_insufficient_pairs` and `attach_insufficient_coverage` no longer masquerade as a proven non-neutral session-zero attach. SoundCeiling keeps the transport neutral at 0 dB and continues collecting bounded evidence instead of suppressing Global DSP immediately.
- **The 250 ms proof floor is retained.** This corrective does not lower or bypass the minimum valid paired-evidence coverage required before a neutral attach is accepted.
- **Attach timing uses a post-attach clock.** The 250 ms service-side wait now starts after `DynamicsProcessing` has actually been enabled, so effect-construction latency is not incorrectly counted as acoustic evidence.
- **True non-neutral attach remains fail-closed.** A conclusive finite residual shift outside the neutral tolerance still detaches the transport immediately and suppresses it for the route.
- **Probe lifetime remains bounded.** Retryable evidence cannot leave an unverified transport hanging indefinitely; the existing 1.5 s differential-probe timeout remains authoritative.
- Diagnostics now expose `retryable`, `coveredMs`, and `dsp_global_attach_wait`, making an inconclusive attach distinguishable from an unsafe attach in Samsung field logs.
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
- Probe order is now **baseline with no DSP → neutral 0 dB attach → attach verification → bounded -0.5 dB differential probe**.
- A neutral attach that changes or loses output is classified unsafe, detached immediately, and suppressed for the route until route change or a deliberate Global DSP toggle.
- A strong/nonlinear response to the small probe is preserved as `RESPONSIVE_NONLINEAR` evidence but never promoted to continuous gain authority.
- Unverified global transports are detached after failed/cancelled probes instead of remaining attached at 0 dB.
- Fresh field-test logs are protected for at least 24 hours and the normal retained budget is 64 MiB.
- Multiband/MBC normalization remains disabled in this corrective build until broadband session-zero attach/gain behavior is proven safe on the Samsung device.

Historical Samsung acceptance: `docs/field-tests/2026-08-24-v0.7.6.1-samsung-checklist.md`.

## v0.7.6 Control Architecture Reset

- **Samsung slider is the user master anchor.** Manual Media movement rebases control immediately. SoundCeiling may never silently redefine the user's chosen Samsung step.
- **Verified DSP is the continuous normalizer.** DSP normalization becomes active only after differential verification proves that the transport measurably changes the intended output path.
- **Unverified DSP is reported honestly.** If differential verification is absent or fails, UI and logs do not claim verified DSP authority.
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

Requires JDK 17 and Android SDK 35. The release workflow runs the full pure suite, every historical behavior/source contract, v0.7.7 Samsung 3/15/telemetry/wiring contracts, the v0.7.7.1 neutral Session DSP verification contract, Android `:app:assembleDebug`, SHA-256 generation, and artifact upload on the same immutable head.

Release artifact: **`SoundCeiling-v0.7.7.1-debug-apk`**, containing `app-debug.apk`.
