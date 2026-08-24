# Sound Ceiling for Android - v0.7.6

SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.6 is the **Control Architecture Reset** release: the normalizer now separates measured source data, projected/output-domain evidence, continuous verified-DSP control, coarse Media fallback, and independent safety authority.

## v0.7.6 Control Architecture Reset

- **Samsung slider is the user master anchor.** Manual Media movement rebases control immediately. SoundCeiling may never silently redefine the user's chosen Samsung step.
- **Verified DSP is the continuous normalizer.** DSP normalization becomes active only after **route-scoped differential verification** proves that the session-zero transport measurably changes the current output route. Creating `DynamicsProcessing` alone is not proof.
- **Unverified DSP is reported honestly.** If differential verification is absent or fails, UI and logs report **Coarse Media fallback** or Safety-only, not “Global DSP active”.
- **Raw peak is not output authority.** A **source peak alone cannot force Media down**. Ordinary control uses measured/projected output evidence. Hard Media cap and Quiet Now remain separate immediate safety paths.
- **Coarse Media fallback is deliberately slow.** Each ordinary fallback write is at most one Samsung step, separated by dwell, and requires sustained imbalance. Recovery repays only SoundCeiling-owned attenuation debt and never exceeds the user anchor.
- **Low-volume authority is explicit.** Media 1/15 and 2/15 are valid user anchors, not automatic normalization destinations. Manual 1→2, 2→3 and 3→2 changes must not snap back.
- **Diagnostics expose actual control.** Rate-limited summaries include actuator tier, meter domain, DSP state, requested/applied DSP gain, source/output levels, Media anchor/debt/dwell, and decision reason.

Samsung physical acceptance: `docs/field-tests/2026-08-24-v0.7.6-samsung-checklist.md`.

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

Requires JDK 17 and Android SDK 35. The v0.7.6 release workflow runs the full pure suite, every historical behavior/source contract, the v0.7.6 architecture and release contracts, Android `:app:assembleDebug`, SHA-256 generation, and artifact upload on the same immutable head.

Release artifact: **`SoundCeiling-v0.7.6-debug-apk`**, containing `app-debug.apk`.
