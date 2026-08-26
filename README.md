# Sound Ceiling for Android - v0.7.7.1

SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.7.1 is a Samsung Session DSP verification corrective on top of v0.7.7 Enhanced Session DSP.

## v0.7.7.1 deterministic Session DSP corrective

- **Enhanced Session verification is deterministic.** Exact non-zero `sessionId + UID + package` ownership establishes which playback session may be controlled. Transport authority is then verified through Android `DynamicsProcessing` configuration/readback, not through asynchronous PCM/Visualizer level comparisons.
- **The topology is input-gain-only.** Enhanced Session `DynamicsProcessing` is created with PreEQ, MBC, PostEQ and Limiter out of use. If that explicit topology cannot be created, the candidate stays unavailable instead of silently falling back to an unknown OEM default configuration.
- **The bounded handshake is `0 dB -> -0.5 dB -> 0 dB`.** Every configured channel must read back neutral gain, the requested small probe gain, and the restored neutral gain. Any topology, channel-count, gain-readback or restore mismatch fails closed and the exact session is not promoted.
- **PCM/Visualizer residuals are diagnostics, not the Enhanced Session authority gate.** Samsung field traces showed the same session producing apparent residual shifts from about -16.6 dB to +9.7 dB because targeted PCM and Visualizer are not audio-time synchronized. Those values remain useful for metering/output-domain diagnostics but cannot blacklist an otherwise valid non-zero DSP transport.
- **Samsung Media remains the user master.** Media **3/15** remains a required acceptance point. Ordinary normalization must use verified non-zero Session DSP and must not move the Samsung slider. Safety Maximum and Quiet Now retain their explicit Media authority.
- **Debug update identity is now stable.** GitHub Actions persists one development debug keystore, so after the first build signed with this identity, later debug APKs can be installed as updates without rotating the signing key. Production/RuStore signing remains a separate future release concern.

Samsung physical acceptance: `docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md`.

## v0.7.7 Enhanced Session DSP

- **Non-zero session authority.** v0.7.7 introduced discovery of exactly one active non-zero audio session owned by the exact playback UID. Its original audible differential verification is retained as historical code/tests; v0.7.7.1 replaces that production verification gate with deterministic Android readback.
- **Samsung Media remains user authority.** Media **3/15** is a first-class user anchor. Ordinary normalization must not pull the Samsung slider away from 3/15, 2/15, or another manual step. With verified Session DSP, correction is continuous DSP gain; without it, ordinary normalization holds.
- **One-time no-root setup.** Enhanced Session DSP discovery requires the Android DUMP permission granted once from ADB: `adb shell pm grant dev.soundceiling.app android.permission.DUMP`. Simple and Advanced modes expose setup status and a copyable command.
- **Fail-closed transport.** A stale, ambiguous, failed or unverified session transport is released. A failed gain apply revokes Session DSP authority and returns ordinary control to HOLD rather than reviving target-chasing Media fallback.
- **Diagnostics expose the real actuator.** Runtime state and logs include session ID, UID, package, requested/applied gain, reason, `session_dsp_readback_result`, and `session_dsp_apply`.
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
- **Verified DSP is the continuous normalizer.** DSP normalization becomes active only after verification proves that the transport controls the intended output path with the required scope.
- **Historical v0.7.6 scope proof used route-scoped differential verification.** That acoustic proof remains a historical contract; v0.7.7.1 Enhanced Session production authority now uses deterministic Android readback instead.
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

Requires JDK 17 and Android SDK 35. The release workflow runs the full pure suite, every historical behavior/source contract, v0.7.7 Samsung 3/15/telemetry/wiring contracts, the v0.7.7.1 input-gain-only topology contract, deterministic readback pure/wiring contracts, Android `:app:assembleDebug`, SHA-256 generation, and artifact upload on the same immutable head.

Release artifact: **`SoundCeiling-v0.7.7.1-debug-apk`**, containing `app-debug.apk`.
