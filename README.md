# Sound Ceiling for Android - v0.7.4

SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.4 is a field-driven corrective build based on the physical Samsung SM-A528B v0.7.3 trace. The trace proved that Samsung can instantiate the session-zero `DynamicsProcessing` candidate through the default-config fallback, but also exposed a control-loop bug that immediately neutralized SoundCeiling's own bounded -2 dB verification probe. v0.7.4 fixes that proof lifecycle without relaxing Samsung Media user authority.

## v0.7.4 corrective highlights

- **Global DSP probe no longer cancels itself.** The only legal non-zero gain on an unverified global transport is the bounded -2 dB scope probe. Ordinary coordinator control now holds while that probe is measured instead of misclassifying it as stale DSP and immediately writing 0 dB.
- **Safety still interrupts the probe correctly.** Hard Media cap and Quiet Now neutralize the in-flight probe first; fallback/Media action follows only after neutral DSP state, preserving neutralize-before-fallback ordering.
- **Stale rebind samples cannot verify Global DSP.** If a mixed↔targeted capture replacement cancels a live probe, its old before/after collector cannot turn post-rebind samples into a new route proof. A fresh probe is required.
- **Capture reference stays capture-scoped.** mixed↔targeted replacement still resets PRE/POST estimator evidence because it is capture-dependent measurement state. Samsung user anchor, linked ceilings, attenuation debt and route-scoped Global DSP state remain separate and are preserved as designed.
- **Silence is not PRE/POST evidence.** Media changes whose before/after PCM is effectively silent are ignored by the capture-reference estimator.
- **v0.7.3 user authority remains intact.** Ordinary Media UP remains debt-only and can never exceed the latest user anchor; hard safety retains independent downward authority.

## v0.7.3 corrective highlights

- **User beats recovery immediately.** Any Media observation that does not match an app-owned pending target is external/user authority, including an APP_WRITE_MISMATCH race. It rebases the master anchor and cancels recoverable debt.
- **Media UP is debt-only.** Without verified DSP, SoundCeiling may attenuate Media and later repay only those owned steps. Exact source recognition never grants permission to push the Samsung slider above the user's latest choice.
- **Capture rebind is not route change.** mixed↔targeted PCM changes reset capture-dependent meters/controller dwell only. They preserve the Samsung anchor, linked ceilings, debt, and route-scoped session-zero DSP transport/proof. An in-flight -2 dB probe is neutralized before its meter is replaced.
- **Global DSP gets an OEM-compatible bootstrap fallback.** SoundCeiling first tries the explicit custom `DynamicsProcessing.Config`; if the OEM rejects it, it tries Android's documented default-config constructor. Construction is logged after the persistent session logger is attached. Non-zero gain still requires measured route verification.
- Source-access copy is generic: **«Разрешить распознавание источника для DSP»**.

## v0.7.2 corrective highlights

- **Samsung Media is the user anchor.** A real user volume move rebases the anchor; SoundCeiling-owned attenuation creates recoverable debt and cannot silently redefine the user's chosen step.
- **Playback Capture is no longer hard-coded POST_VOLUME.** Live PRE/POST evidence controls output projection, and PRE_VOLUME includes the measured/vendor Samsung route gain. UNKNOWN blocks ordinary normalization writes that cannot be projected safely.
- **Ordinary Media fallback uses a route-relative floor** instead of treating absolute index 1 as the universal default target. Hard emergency safety remains independent and may attenuate further when genuinely required.
- **Source diagnostics are actionable.** Missing Notification Listener access, package candidates, targeted PCM confirmation/silence/failure, and Global DSP transport/probe status are logged separately.
- **Simple mode has Reset Defaults** for normalizer controls only; logs, calibration and app policies are preserved. `Частотный спектр` is explicitly diagnostic and does not control normalization.
- **Global DSP** is ON by default as a user preference in both Simple and Advanced mode. It becomes active only after the existing session-zero `DynamicsProcessing` path is actually verified for the current route. Successful effect construction alone is not proof.
- When verified Global DSP is active, ordinary positive and negative normalization uses DSP gain/limiter first so the Samsung Media slider can stay where the user put it. Hard Media safety remains independent.
- If Global DSP is unavailable or loses proof, the effect is neutralized and SoundCeiling falls back to compatible selective/DSP-like/Media control without claiming a verified global path.
- **Default Linked Lock** is shared by Simple and Advanced. When ON, Minimum/Maximum Output Ceiling remain visible but disabled/dimmed; when OFF they unlock in place without a value jump. User Samsung Media movement may shift the linked target, app-owned writes may not.
- Apps/System exclusions remain honest: controls that cannot be guaranteed on an indivisible verified global mix are disabled only while that global path is actually active.
- Playback source evidence remains live. YouTube and **Yandex Music** become exact only after UID-targeted PCM produces stable program audio; candidate identity alone is not verification.
- Visualizer fallback now uses real FFT bands, timestamps and explicit unavailable state. Stale or failed readings are not rendered as five fake zero measurements.
- Calibration persistence is route-bound and affects only approximate dB SPL display.
- Runtime control summaries are transition/rate-limited. Stable summaries are written at most once every 2 seconds while decision context remains available in the in-memory ring for anomaly snapshots.

## Safety model

The v0.7 Adaptive Envelope invariant remains:

`automatic target <= User ceiling <= Safety ceiling`

Automatic recovery may repay only attenuation previously created by SoundCeiling. Manual down remains authoritative. Peak/transient protection and hard cap use a separate immediate downward path. `Quiet Now` remains non-raising. Microphone/call audio is not used as a production analysis source.

Global DSP and Linked Lock are independent. Global DSP chooses how SoundCeiling acts on audio; Linked Lock chooses how the output ceilings are represented.

## Playback capture

Precise PCM uses Android `AudioPlaybackCapture`, whose permission is exposed through `MediaProjection`. SoundCeiling uses it for playback-audio analysis, not screen video. If exact capture is unavailable, diagnostics show the fallback instead of inventing exact source evidence.

## Частотный спектр and calibrated dB SPL

The frequency display contains five diagnostic low/mid/high-band indicators. It does not control normalization. PCM spectrum is preferred when precise capture is available; otherwise output-mix Visualizer FFT may provide fallback bands. A short labeled hold can preserve the last live shape during a transient gap, after which it becomes explicitly unavailable.

Calibrated dB SPL is an approximate route-specific display. A saved value is restored only for the same output route and never changes digital normalization or safety behavior.

## Logs

Default location: `Downloads/SoundCeilingLogs`. Session parts are tracked index-first and reconciled with MediaStore/SAF discovery, so an empty **MediaStore** discovery does not erase a just-created session.

Transitions are logged immediately. Unchanged control summaries are bounded to one line per 2 seconds and include actuator, desired/applied gain, raw/projected peak, effective policy, capture reference and reason.

## Samsung field test

The current device checklist is `docs/field-tests/2026-08-24-v0.7.4-samsung-corrective-checklist.md`. Physical acceptance remains **awaiting device test** until the exact v0.7.4 CI APK is installed on the Samsung and one full new exported log is returned.

The next trace is expected to answer two empirical questions that v0.7.3 could not answer because of the probe-control bug: whether Samsung's default-config session-zero DynamicsProcessing really changes the measured output mix, and whether three valid Media changes during one stable capture interval can establish PRE/POST evidence without silence contaminating it.

Historical compatibility retained from **v0.5.1**, v0.6 and earlier v0.7 corrective releases includes volume-neutral calibration, non-raising Quiet Now, transient re-arm, projected-peak fixes, persistent linked-band EQ, logical single-session log sharing, Samsung user authority and debt-only Media recovery.

## Build and verification

Requires JDK 17 and Android SDK 35. CI runs the pure suite, historical regression contracts, v0.7 Adaptive Envelope contract, v0.7.1 historical contracts, v0.7.2 corrective/release contracts, v0.7.3 user-authority/release contracts, the v0.7.4 Samsung field regression/corrective contract, the v0.7.4 release contract, then `:app:assembleDebug`.

The field-build artifact is **`SoundCeiling-v0.7.4-debug-apk`** and contains `app-debug.apk`. The workflow calculates an SHA-256 checksum before upload; the exact final artifact hash is recorded for the Samsung test.
