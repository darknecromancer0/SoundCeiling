# SoundCeiling v0.7.2 Corrective Architecture Design

## Status
Approved from Samsung SM-A528B field evidence on 2026-08-23. This design supersedes the broken v0.7.1 live behavior where mixed Playback Capture was treated as POST_VOLUME, fallback Media writes fought the user master slider, app-owned attenuation could become permanent, source identity stayed unknown, and Global DSP never reached a usable verified state.

## Goal
Make SoundCeiling behave like the original product idea: the Samsung Media slider is the user's master loudness anchor, while SoundCeiling continuously normalizes relative to that anchor using verified DSP when available and a bounded reversible Media fallback when DSP is unavailable.

## Field evidence that drives this design
- On SM-A528B, mixed Playback Capture continued to report peaks near 0..-2 dBFS even at Media 1/15, proving that the live capture path must not be hard-coded POST_VOLUME.
- v0.7.1 repeatedly lowered Media to 1/15, while positive recovery was blocked for UNKNOWN sources. User 1->2 writes were often reverted by the app within milliseconds.
- The device/vendor measured Media curve in logs differs radically from the generic control curve, by tens of dB at low indices.
- Every tested run remained targetUid=-1 / source UNKNOWN / DSP UNAVAILABLE. No verified Global DSP actuation occurred.
- Default Linked Lock preference changes could be overwritten by the running service's stale coordinator state.

## Architecture

### 1. Capture reference is runtime evidence, never a constant
`CaptureReferenceEstimator` becomes part of the live service path.

A Media-index change with a known route dB delta is compared with the observed PCM delta:
- PCM stays approximately unchanged -> PRE_VOLUME evidence.
- PCM follows the Media delta -> POST_VOLUME evidence.
- conflicting or insufficient samples -> UNKNOWN.

The estimator resets on output-route change and capture reopen. UNKNOWN must remain fail-closed for any calculation that requires exact output projection.

### 2. Projected output uses the real route gain
The control planner receives the current Media route gain explicitly.

For PRE_VOLUME capture:
`projectedProgram = capturedProgram + mediaGain + dspGain`
`projectedPeak = capturedPeak + mediaGain + dspGain`

For POST_VOLUME capture:
`projectedProgram = capturedProgram + dspGain`
`projectedPeak = capturedPeak + dspGain`

For UNKNOWN capture, the controller may perform only bounded fail-safe behavior that cannot create irreversible attenuation.

The route gain must come from the best available measured/vendor curve for the active route. A synthesized generic curve must be visibly labeled fallback and must not masquerade as calibrated physical output.

### 3. Samsung Media slider is the user master anchor
Introduce a pure `MediaAnchorState` owned by the coordinator.

It tracks:
- `userAnchorIndex`: the last genuine user-selected Media index.
- `appAttenuationDebtSteps`: how many downward Media steps SoundCeiling itself currently owns below that anchor.
- provenance of the last observed write.

Rules:
- A genuine USER Media change immediately replaces `userAnchorIndex` and clears/rebases app debt.
- App-owned NORMALIZER_DOWN / PEAK_EMERGENCY / HARD_CAP writes never redefine the user anchor.
- Ordinary fallback normalization may attenuate below the anchor only when required.
- Recovery is always allowed up to the user anchor to repay app-owned attenuation debt, even when source identity is UNKNOWN. This is not treated as arbitrary positive gain.
- Recovery above the user anchor still requires the existing source/policy permission.
- Hard Media safety may clamp below the anchor, but when the safety condition clears the app may recover only toward the anchor, never beyond it without permission.

This removes the 1->2->1 fight loop and the permanent-low-volume failure.

### 4. Linked Lock is a live shared preference, not service writeback state
`Default Linked Lock` remains one shared switch in Simple and Advanced.

When ON:
- both output-ceiling sliders are visible but disabled/dimmed in both modes;
- genuine user movement of Samsung Media shifts the linked output target by the real route dB delta;
- app-owned Media writes never shift that target.

When OFF:
- both ceilings unlock immediately while the service is running;
- values do not jump;
- the service must not overwrite a newer preference value from stale coordinator state.

Preference-to-runtime synchronization is one-way for the linked flag and explicit UI ceiling edits. Runtime may persist only coordinator-owned user-anchor-derived ceiling shifts after proving the observation was USER.

### 5. Source evidence must expose permission and failure reason
The source status must distinguish:
- notification-listener/media-session access unavailable;
- access available but no active candidate;
- one candidate awaiting targeted PCM proof;
- multiple candidates;
- targeted PCM confirmed;
- targeted PCM silent/unconfirmed and suppressed.

Simple mode must surface an actionable button when notification-listener access is absent. Diagnostics/logs must record access state, candidate package/UID count, targeted-open attempts, targeted PCM confirmation/failure, and fallback reason.

YouTube and Yandex Music are not considered recognized until targeted PCM confirms the MediaSession candidate.

### 6. Global DSP must attempt and prove real actuation
Global DSP remains default ON as a preference, but preference is not capability.

On an eligible active media route:
1. create a neutral session-0 `DynamicsProcessing` transport;
2. log creation success/failure and raw capability;
3. run a bounded -2 dB digital scope probe;
4. restore neutral before classifying evidence;
5. promote to `VERIFIED_GLOBAL_MIX` only if the measured allowed-media response proves the effect and whole-output consent authorizes the scope.

Statuses are explicit:
- `UNAVAILABLE`: framework effect could not be created/used.
- `AVAILABLE_UNVERIFIED`: transport exists but effect/scope is not proven.
- `VERIFIED_GLOBAL_MIX`: bounded probe proved allowed-media effect and scope is authorized.
- `ACTIVE`: verified Global DSP is currently the selected actuator.

No non-zero Global DSP gain may be applied before verification. Any capability loss must neutralize before Media fallback.

### 7. Normalization dynamics
Do not fix the field failure by simply increasing aggression.

Once output projection is correct:
- loud-to-safe attenuation should react quickly, with hard-peak safety immediate;
- quiet-to-target recovery should begin without the old permanent positive-gain block when it is only repaying app-owned Media debt;
- DSP remains the preferred continuous actuator because Samsung Media steps are too coarse for near-equal loudness;
- Media fallback remains stepwise and bounded, with the user anchor as its upper ordinary recovery boundary.

The current ~0.2 s pickup can be retained or improved only after reference/anchor correctness is proven.

### 8. Defaults and Simple-mode UX
Add `Вернуть настройки по умолчанию` to Simple mode with confirmation.

Reset restores only normalizer behavior controls:
- Global DSP preference ON;
- Default Linked Lock ON;
- linked output target default;
- normalization strength/preset defaults;
- route-relative fallback minimum and safety defaults.

Reset must not erase logs, calibration profiles, application policies, or diagnostics history.

The old absolute fallback minimum of Media index 1 is no longer the first-run/default policy. The default floor is derived relative to the active route/user anchor and exists only to prevent pathological collapse; explicit advanced user settings may still choose a lower floor.

Rename/describe Spectrum in human terms as `Частотный спектр`: five low/mid/high-band indicators used for diagnostics/visualization. It does not control normalization.

## Field regression requirements
Create deterministic pure/source regressions derived from the uploaded Samsung traces without embedding personal paths or raw logs:
1. PRE_VOLUME: Media delta with almost unchanged PCM must classify PRE_VOLUME.
2. Projected peak at Media 1/15 must include measured route attenuation and must not produce a false near-0 dBFS hard-peak emergency.
3. User anchor 2, app attenuation to 1, user raises to 2: the app must not immediately drive it back to 1 without a new independent safety condition.
4. App-owned attenuation debt must recover to the user anchor even with UNKNOWN source.
5. App-owned writes must not move Linked Lock ceilings; USER writes must.
6. Linked Lock OFF while service is running must remain OFF after settings refresh.
7. Missing MediaSession access must be explicit and actionable.
8. Global DSP creation/probe lifecycle must expose UNAVAILABLE vs AVAILABLE_UNVERIFIED vs VERIFIED_GLOBAL_MIX and neutralize on failure.
9. No regression may weaken hard Media cap, hard peak safety, OFF-policy fail-closed behavior, or neutralize-before-fallback ordering.

## Version and release
- Version name: `0.7.2`.
- Increment versionCode from v0.7.1.
- Continue on the existing `feature/v0.7-adaptive-envelope` branch and PR #8 unless a repository constraint requires otherwise.
- Final Android APK must be built by GitHub Actions after all pure/source/regression gates pass.
- Device claims remain `awaiting device test` until the new APK is tested on the Samsung SM-A528B.
