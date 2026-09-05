# SoundCeiling v0.7.7 Enhanced Session DSP Design

## Status

Approved architectural direction after Samsung SM-A528B field evidence from v0.7.6.3.

## Goal

Make SoundCeiling normalize quiet and loud program material at any user-selected Samsung Media step, including 3/15, without using the Samsung Media slider as the ordinary normalization actuator.

## Core invariant

**Samsung Media is the user master anchor.**

If the user chooses Media 3/15, ordinary normalization must leave Media at 3/15. SoundCeiling may change the gain inside the active playback audio session, but may not translate a normalization request into Samsung Media movement.

The only code paths allowed to move Samsung Media are explicit safety/user-authority paths already present in the project:

- hard `Safety Maximum` enforcement;
- Quiet Now;
- explicit legacy/coarse fallback only when the user has enabled/accepted fallback behavior and no session DSP authority exists.

For v0.7.7 acceptance, ordinary normalization is considered successful only when `DSP_GAIN` changes while Media remains unchanged.

## Why the architecture changes

v0.7.6.3 proved that the existing session-zero `DynamicsProcessing` global-mix candidate is not safe on the target Samsung route. A nominal 0 dB neutral attach caused an approximately -13 dB output change, so the existing safety verifier correctly detached it.

The same field trace also proved that the measurement/planning side can already do the important low-volume math: at Media 3/15, targeted PCM reached `PRE_VOLUME`, the projected output was far below target, and the planner requested up to +30 dB while `dspAppliedGainDb` remained 0 because no trustworthy actuator existed.

The Android `DynamicsProcessing` API is designed to attach to a specific `AudioTrack` or `MediaPlayer` using that playback object's audio session ID. v0.7.7 therefore stops treating audio session 0 as the primary normalization transport and instead discovers active non-zero playback sessions and attaches processing directly to those sessions.

## Chosen approach

### Enhanced Session DSP

SoundCeiling will discover active non-zero playback audio session IDs and maintain one verified DSP transport per eligible session.

The preferred discovery path for v0.7.7 is an **optional one-time ADB-granted `android.permission.DUMP` capability**. The app will not assume that `DUMP` is available. It must detect permission state and degrade truthfully when absent.

Initial user setup may require a one-time ADB command. A future Shizuku discovery provider may be added behind the same discovery interface, but Shizuku is not required for the first v0.7.7 implementation.

### Why this approach

- It preserves the user's Samsung Media step.
- It uses the Android effect model on the scope for which `DynamicsProcessing` is documented: a specific audio session.
- It lets SoundCeiling affect Yandex Music, YouTube, games, and other eligible playback independently of system sounds when their sessions can be discovered and mapped.
- It avoids the unsafe session-zero behavior observed on the target Samsung device.

## Components

### 1. `AudioSessionDiscovery`

A new pure interface returning current active playback session candidates.

Each candidate contains:

- `audioSessionId` (must be > 0);
- owning UID when available;
- package mapping confidence;
- activity state;
- discovery provenance;
- observation timestamp.

Discovery never grants DSP authority by itself.

### 2. `DumpAudioSessionDiscovery`

Android implementation using the optional `DUMP` capability to inspect audio framework state and extract active playback sessions.

Requirements:

- fail closed if permission is absent;
- no root requirement;
- no shell process polling faster than needed for session lifecycle detection;
- parser is isolated from Android execution so recorded dumps can be regression-tested;
- malformed or OEM-specific lines cannot produce trusted session IDs accidentally;
- session ID 0 is always rejected for ordinary normalization.

### 3. `SessionOwnershipResolver`

Combines discovered session evidence with the existing source/UID evidence.

Authority levels:

- `EXACT`: session can be mapped to a currently active eligible UID/package;
- `LIKELY`: useful for diagnostics only, no positive DSP authority;
- `UNKNOWN`: diagnostics only.

Unlike the old global-mix path, exact package identity is required for a per-session effect because scope is intentionally per-app/per-session.

### 4. `SessionDspRegistry`

Owns the lifecycle of non-zero session DSP transports.

Responsibilities:

- create at most one SoundCeiling transport per active session ID;
- neutral attach at 0 dB;
- verify that neutral attach does not materially change output;
- run a bounded differential probe before granting continuous authority;
- release transport when the session disappears, package becomes ineligible, route changes invalidate proof, or service stops;
- never reuse proof from one session ID for another;
- never promote session 0.

### 5. `AndroidSessionDynamicsProcessingTransport`

A focused transport for a known non-zero session ID.

It uses `DynamicsProcessing` input gain and limiter stages. The first implementation keeps the topology intentionally simple: broadband input gain plus limiter. Multiband compression remains deferred until broadband gain is physically proven stable.

The transport must support:

- neutral attach at 0 dB;
- bounded negative differential probe;
- continuous gain with existing slew limits;
- immediate bounded safety attenuation;
- neutralize/release;
- truthful capability/reason reporting.

### 6. Coordinator integration

`NormalizerControlCoordinator` remains the single ordinary control planner.

New actuator priority:

1. hard safety Media write, only when its independent Media ceiling is violated;
2. verified exact-session DSP;
3. optional coarse Media fallback when explicitly permitted and no verified session DSP exists;
4. hold.

When verified session DSP exists, ordinary loudness imbalance must generate `DSP_GAIN`, never a Media-index normalization write.

## Samsung Media semantics

The Samsung slider is not an internal normalization parameter.

Examples:

- User sets 3/15. A quiet passage needs +12 dB. SoundCeiling applies session gain toward +12 dB while Media stays 3.
- The passage becomes loud. Session gain falls smoothly toward 0 or negative attenuation while Media stays 3.
- User manually changes 3 -> 2. SoundCeiling immediately treats 2 as the new master anchor, recomputes projected output, and continues through session DSP.
- User manually changes 2 -> 5. Same behavior; no snap-back.
- `Safety Maximum=4` and user attempts 5. Existing hard-cap path may return Media to 4 because that is explicit safety policy, not normalization.

No ordinary normalization routine may raise Media above the user's chosen anchor to compensate for quiet content.

## Gain model

The projected output model remains:

`projectedOutput = PRE_VOLUME program level + Samsung route gain + applied session DSP gain`

For POST_VOLUME capture, route gain is not applied twice.

The planner may request positive gain at low Samsung steps. Existing hard limits remain authoritative. v0.7.7 does not increase the current maximum positive DSP gain merely to make tests pass.

## Source and app policy

Session DSP applies only when:

- playback is active;
- a non-zero audio session is discovered;
- ownership is `EXACT`;
- the app/session policy is not `OFF`;
- the source is eligible for ordinary normalization;
- the session transport has passed neutral-attach and differential verification.

System applications remain excluded by default according to existing policy behavior. A system sound is not affected merely because Global DSP is enabled.

## Permission and setup UX

v0.7.7 adds an Enhanced Session DSP capability state:

- `READY` — DUMP permission present and session discovery operational;
- `SETUP_REQUIRED` — permission absent;
- `DISCOVERY_UNAVAILABLE` — permission present but platform/OEM output cannot be parsed safely;
- `NO_ELIGIBLE_SESSION` — discovery works but no exact eligible active session exists;
- `SESSION_VERIFYING` — transport attached neutrally/probing;
- `SESSION_DSP_ACTIVE` — at least one exact session has verified DSP authority.

The UI must never show `Global DSP active` merely because `DynamicsProcessing` construction succeeded.

Setup copy explains that the ADB grant is optional and why it exists. The app must show the exact package-specific command generated for its application ID. The design does not require root.

## Session-zero policy

For v0.7.7 ordinary normalization:

- session-zero `DynamicsProcessing` is disabled as the primary transport;
- it may remain in source history/tests for diagnostic comparison, but runtime ordinary normalization must not select it;
- the Samsung field failure remains a permanent regression test: a global neutral attach changing output by roughly 13 dB must never be accepted as harmless.

## Fallback policy

Coarse Media fallback becomes visibly secondary.

Default behavior for an eligible app when Enhanced Session DSP setup is missing or verification fails:

- continue measurement and safety protection;
- report why normalization authority is unavailable;
- do not pretend the main normalization goal is being achieved;
- do not continuously walk Samsung Media up/down.

Existing debt-only recovery and one-step/dwell safety rules remain if coarse fallback is explicitly used.

## Lifecycle

A session transport is invalidated on:

- session disappearance;
- UID/package ownership change;
- route change when proof is route-sensitive;
- permission loss;
- Global DSP toggle OFF;
- service stop;
- transport exception or control loss.

Restart must not leave orphan effects attached.

## Logging

Add event/summary fields sufficient to answer, from a single field log:

- discovery capability and permission state;
- active discovered session IDs;
- exact UID/package mapping;
- session attach begin/result;
- neutral delta and valid coverage;
- differential probe result;
- selected actuator session ID;
- requested/applied session gain;
- Samsung Media index before/after each control tick;
- reason when DSP is unavailable.

The key success signature for the target device is:

`media=3 -> session=<nonzero> -> SESSION_DSP_ACTIVE -> dspRequestedGainDb>0 -> dspAppliedGainDb>0 -> media=3`

## Safety

- Never attach ordinary normalization to session 0.
- Never attach positive gain to an unverified or ambiguously owned session.
- Neutral attach must be measured before differential probing.
- Differential probe remains bounded and negative.
- Any material non-neutral attach change causes immediate detach.
- Existing transient/hard-peak safeguards remain independent of loudness normalization.
- User Media lowering is never fought by normalization.

## Testing strategy

### Pure tests

- dump parser accepts known Samsung/AOSP session layouts;
- malformed input yields zero trusted sessions;
- session 0 is rejected;
- ownership resolver requires exact UID/package match;
- registry does not reuse proof after session replacement;
- low-volume scenario: Media 3, Safety Maximum 3 or higher, quiet PRE_VOLUME signal, verified session DSP -> positive `DSP_GAIN`, Media unchanged;
- loud signal -> negative/zero DSP adjustment, Media unchanged;
- manual 3 -> 2 rebases projection without a Media write-back;
- session disappears -> transport released and authority removed.

### Android contract tests

- `DUMP` is treated as optional enhanced capability, not a normal runtime permission dialog;
- session-zero runtime normalization selection is forbidden;
- all direct Media writes remain inside the existing safe controller boundary;
- service stop releases all session effects.

### Samsung physical acceptance

Target device: Samsung SM-A528B, Android 14.

Yandex Music test:

1. Set Samsung Media to 3/15.
2. Keep Safety Maximum at the desired normal value; do not raise it merely for normalization.
3. Start Smart PCM with Global DSP / Enhanced Session DSP enabled.
4. Verify a non-zero Yandex Music audio session is discovered and mapped exactly.
5. Verify neutral attach does not create a material loudness jump/drop.
6. Verify differential probe succeeds.
7. Play material with clearly quiet and loud passages for at least 60 seconds.
8. Pass only if `dspAppliedGainDb` becomes non-zero as needed while Samsung Media remains 3/15 unless the user touches it or hard safety independently fires.
9. Repeat at 2/15 and 5/15.
10. Stop/restart playback and SoundCeiling repeatedly to verify session lifecycle cleanup/rebind.

## Non-goals for v0.7.7

- Root/audio-HAL modification.
- Shizuku as a required dependency.
- Re-recording and replaying captured audio.
- Multiband mastering before broadband session gain is proven.
- Making every Android app capturable/processable when the platform does not expose a usable session.
- Removing Safety Maximum, Quiet Now, app exclusions, or existing diagnostics.

## Acceptance definition

v0.7.7 is not considered successful merely because it builds or discovers sessions.

The release is successful only if the target Samsung physical test demonstrates that a real non-zero playback session can be controlled with non-zero DSP gain at low Samsung volume while the Samsung Media slider remains at the user's chosen step.
