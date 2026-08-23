# Sound Ceiling for Android - v0.7.3

SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.3 is a corrective field build based on the next Samsung SM-A528B trace. **user authority is absolute for Samsung Media UP**: ordinary normalization can never raise the Samsung slider above the latest externally chosen index; only SoundCeiling-owned attenuation debt may be repaid back to that anchor. A capture rebind preserves the route-scoped DSP transport and user anchor instead of pretending the physical route changed.

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

The device checklist is `docs/field-tests/2026-08-23-v0.7.3-samsung-corrective-checklist.md`. Physical observations remain `awaiting device test` until the final APK is installed on the Samsung and one full new log is returned.

Historical v0.7 probes remain part of that run:

- **Auto down 7 -> 5:** bounded recovery may return only SoundCeiling-owned attenuation debt.
- **User manual down 7 -> 4:** automation must not raise above the new user ceiling.
- YouTube and Yandex Music source evidence must remain truthful.
- Session visibility must survive temporary MediaStore discovery gaps.

Historical compatibility retained from **v0.5.1** and v0.6 includes volume-neutral calibration, non-raising Quiet Now, transient re-arm, projected-peak fixes, persistent linked-band EQ and logical single-session log sharing.

## Build and verification

Requires JDK 17 and Android SDK 35. CI runs the pure suite, historical regression contracts, v0.7 Adaptive Envelope contract, v0.7.1 historical contracts plus v0.7.2 historical contracts plus v0.7.3 user-authority/DSP/release contracts, then `:app:assembleDebug`.

The field-build artifact is **`SoundCeiling-v0.7.3-debug-apk`** and contains `app-debug.apk`. The workflow calculates an SHA-256 checksum before upload; Task 12 records the immutable final artifact hash used for the Samsung test.
