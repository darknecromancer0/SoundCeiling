# SoundCeiling v0.7.7 Enhanced Session DSP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ordinary SoundCeiling normalization use verified non-zero playback-session DSP so Samsung Media remains the user-selected master step, including 3/15.

**Architecture:** Discover active non-zero AudioTrack session IDs from `dumpsys media.audio_policy` after the user grants `android.permission.DUMP`; bind only sessions whose UID exactly matches SoundCeiling's already-confirmed playback source; attach one `DynamicsProcessing` instance to that non-zero session and use it as the continuous gain actuator. Session zero is forbidden in the normal v0.7.7 runtime path, and missing/failed session DSP causes HOLD rather than ordinary Media normalization.

**Tech Stack:** Android SDK 35 / minSdk 29, Java 17, Android `DynamicsProcessing`, `PackageManager`, `dumpsys media.audio_policy`, existing playback-capture/source resolver, pure Java regression suite, GitHub Actions `assembleDebug`.

**Spec:** `docs/superpowers/specs/2026-08-25-v077-enhanced-session-dsp-design.md`

## Global Constraints

- Samsung Media is the user master anchor. Ordinary normalization MUST NOT write `STREAM_MUSIC`.
- Safety Maximum / hard cap and Quiet Now retain explicit Media-write authority.
- Session zero (`audioSessionId == 0`) MUST NOT be used as the primary normalizer transport in v0.7.7.
- Only positive, active, non-zero playback session IDs may enter the runtime registry.
- A discovered session receives positive DSP authority only after UID ownership matches the current exact playback source and policy allows DSP.
- `android.permission.DUMP` is setup-only and granted externally with `adb shell pm grant dev.soundceiling.app android.permission.DUMP`; no root or Shizuku dependency in v0.7.7.
- Missing DUMP permission, ambiguous ownership, stale sessions, parser failure, attach failure, or neutral-attach failure MUST fail closed to `SESSION_DSP_UNAVAILABLE`/HOLD, never transfer the requested gain into Media writes.
- Positive gain remains bounded by `OutputGainPlanner.MAX_POSITIVE_GAIN_DB`; existing DSP slew and hard-safety behavior remain in force.
- Stop, source loss, session disappearance, route change, and permission loss neutralize/release active session transports.
- Existing system-app exclusions, app policy, Safety Maximum, Quiet Now, user-authority, and PCM/reference safety contracts remain intact.
- Primary physical acceptance is Yandex Music on SM-A528B at Media 3/15: non-zero session, positive requested/applied DSP gain on quiet material, Media still 3.

---

### Task 1: Parse active non-zero playback sessions from AudioPolicy dump

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/AudioSessionRecord.java`
- Create: `app/src/main/java/dev/soundceiling/app/AudioSessionDumpParser.java`
- Create: `app/src/test/java/dev/soundceiling/app/V077AudioSessionDumpParserPureTest.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `AudioSessionRecord(int sessionId, int uid, boolean active, long observedAtMs, String provenance)`
- Produces: `List<AudioSessionRecord> AudioSessionDumpParser.parse(String dump, long observedAtMs)`
- Parser accepts the AOSP/OneUI-compatible client form `Port ID: <n>; Session ID: <n>; uid <n>; State: Active|Inactive`.

- [ ] **Step 1: Write the failing parser tests**

```java
String dump = "Port ID: 57; Session ID: 233; uid 10292; State: Active\n";
List<AudioSessionRecord> records = AudioSessionDumpParser.parse(dump, 1000L);
require(records.size() == 1, "active session must be discovered");
require(records.get(0).sessionId == 233, "session id");
require(records.get(0).uid == 10292, "uid");
```

Also require inactive clients, session 0, negative/invalid IDs, malformed lines, and duplicate session/UID rows to be rejected/deduplicated.

- [ ] **Step 2: Run the pure suite and verify RED**

Run: `./scripts/run-pure-tests.sh`
Expected: compile failure because parser/session record do not exist.

- [ ] **Step 3: Implement the immutable record and conservative parser**

Use a line-anchored regex over `Session ID`, `uid`, and `State`; trim each line; only emit `sessionId > 0`, `uid > 0`, and `Active`; dedupe by `sessionId:uid`; return an unmodifiable list.

- [ ] **Step 4: Re-run pure tests and verify GREEN**

Run: `./scripts/run-pure-tests.sh`
Expected: `V077AudioSessionDumpParserPureTest: PASS` and all previous tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/AudioSessionRecord.java \
        app/src/main/java/dev/soundceiling/app/AudioSessionDumpParser.java \
        app/src/test/java/dev/soundceiling/app/V077AudioSessionDumpParserPureTest.java \
        scripts/run-pure-tests.sh
git commit -m "feat: parse active nonzero playback sessions"
```

---

### Task 2: Add DUMP-permission session discovery boundary

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/AudioSessionDiscovery.java`
- Create: `app/src/main/java/dev/soundceiling/app/DumpAudioSessionDiscovery.java`
- Create: `app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java`
- Create: `app/src/test/java/dev/soundceiling/app/V077EnhancedSessionSetupPureTest.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `AudioSessionDiscovery.Snapshot discover(long nowMs)` with `permissionGranted`, immutable `records`, `reason`, and `observedAtMs`.
- Produces: `EnhancedSessionSetup.ADB_GRANT_COMMAND = "adb shell pm grant dev.soundceiling.app android.permission.DUMP"`.
- `DumpAudioSessionDiscovery` checks `Manifest.permission.DUMP`, executes `dumpsys media.audio_policy` only on the service worker/background path, limits collected output size, and parses it with `AudioSessionDumpParser`.

- [ ] **Step 1: Write RED tests for setup-state semantics**

Test exact grant command, permission-missing reason, and immutable empty snapshot factory.

- [ ] **Step 2: Add `<uses-permission android:name="android.permission.DUMP" />`**

The manifest declaration does not grant the permission; the UI/status layer must still detect absence.

- [ ] **Step 3: Implement discovery with bounded process execution**

Use `ProcessBuilder("dumpsys", "media.audio_policy").redirectErrorStream(true)`, read at most 2 MiB, destroy the process on failure, and return `dump_failed:<ExceptionSimpleName>` rather than guessing sessions.

- [ ] **Step 4: Run pure suite and Android compile**

Run: `./scripts/run-pure-tests.sh` then `./gradlew --no-daemon :app:compileDebugJavaWithJavac`.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/dev/soundceiling/app/AudioSessionDiscovery.java \
        app/src/main/java/dev/soundceiling/app/DumpAudioSessionDiscovery.java \
        app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java \
        app/src/test/java/dev/soundceiling/app/V077EnhancedSessionSetupPureTest.java scripts/run-pure-tests.sh
git commit -m "feat: discover audio sessions with DUMP permission"
```

---

### Task 3: Resolve discovered session ownership fail-closed

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/AudioSessionOwnershipResolver.java`
- Create: `app/src/test/java/dev/soundceiling/app/V077AudioSessionOwnershipPureTest.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DspEndpointHandle.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `AudioSessionOwnershipResolver.Decision resolve(List<AudioSessionRecord> records, SourceDescriptor exactSource, long nowMs)`.
- Decision fields: `accepted`, `sessionId`, `uid`, `packageName`, `reason`.
- Produces trusted endpoint provenance `DspEndpointHandle.Provenance.ENHANCED_SESSION_DISCOVERY`, constructible only from an accepted ownership decision.

- [ ] **Step 1: Write RED ownership tests**

Cases:
- exactly one active record UID == exact Yandex UID => accept its non-zero session;
- no exact source => reject `no_exact_source`;
- UID mismatch => reject `uid_mismatch`;
- two active sessions for same UID => reject `ambiguous_sessions` for first release rather than guessing;
- session 0 can never produce a handle.

- [ ] **Step 2: Implement resolver and handle factory**

Add `DspEndpointHandle.forEnhancedSession(Decision)`; keep generic `tryCreate` fail-closed so raw dump records cannot self-promote.

- [ ] **Step 3: Run pure suite**

Expected: new ownership test and historical trusted-handle tests all pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/AudioSessionOwnershipResolver.java \
        app/src/main/java/dev/soundceiling/app/DspEndpointHandle.java \
        app/src/test/java/dev/soundceiling/app/V077AudioSessionOwnershipPureTest.java scripts/run-pure-tests.sh
git commit -m "feat: verify ownership of discovered audio sessions"
```

---

### Task 4: Add verified non-zero Session DSP registry

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/SessionDspRegistry.java`
- Create: `app/src/test/java/dev/soundceiling/app/V077SessionDspRegistryPureTest.java`
- Modify: `app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DspTransportManager.java`
- Modify: `app/src/main/java/dev/soundceiling/app/OptionalDspController.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- `AndroidDynamicsProcessingTransport.forVerifiedEnhancedSession(DspEndpointHandle handle, int channelCount)` accepts only `ENHANCED_SESSION_DISCOVERY`, requires `audioSessionId > 0`, starts neutral at 0 dB, and reports `VERIFIED_POLICY_SCOPED` only after successful construction/neutral setup.
- `SessionDspRegistry.bind(DspEndpointHandle handle)` replaces a different session only after neutralizing/releasing the old transport.
- `SessionDspRegistry.applyGain(float gainDb, boolean hardSafety)` delegates only to the currently bound non-zero session.
- `SessionDspRegistry.release(String reason)` neutralizes/releases and clears authority.
- `OptionalDspController.bindEnhancedSession(...)`, `enhancedSessionId()`, `enhancedSessionActive()` expose the registry to the service without session-zero probing.

- [ ] **Step 1: Write RED lifecycle tests with a fake transport factory**

Require session 0 rejection, bind 233, apply gain, replace 233→241 by release+neutral, source/session disappearance release, and service stop release.

- [ ] **Step 2: Implement registry behind a small transport factory seam**

Keep Android framework construction outside pure tests. Registry stores only one accepted session in v0.7.7; multiple simultaneous owned sessions remain a future extension.

- [ ] **Step 3: Add non-zero enhanced-session factory to Android transport**

Do not reuse `forNeutralGlobalProbe`. The factory rejects zero before constructing `DynamicsProcessing`.

- [ ] **Step 4: Wire manager/controller methods without removing historical global-probe code yet**

Historical session-zero code stays for diagnostic compatibility but v0.7.7 service will not call it as the normal runtime path.

- [ ] **Step 5: Run pure suite and Android compile**

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/SessionDspRegistry.java \
        app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java \
        app/src/main/java/dev/soundceiling/app/DspTransportManager.java \
        app/src/main/java/dev/soundceiling/app/OptionalDspController.java \
        app/src/test/java/dev/soundceiling/app/V077SessionDspRegistryPureTest.java scripts/run-pure-tests.sh
git commit -m "feat: add nonzero session DSP registry"
```

---

### Task 5: Make Session DSP the only ordinary normalization actuator

**Files:**
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java`
- Modify: `app/src/main/java/dev/soundceiling/app/ControlCommand.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Modify: `app/src/main/java/dev/soundceiling/app/OutputLevelModel.java`
- Modify: `app/src/test/java/dev/soundceiling/app/V076ArchitectureRegressionPureTest.java`
- Create: `app/src/test/java/dev/soundceiling/app/V077SessionDspControlPureTest.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Add actuator/tier label `SESSION_DSP` for verified enhanced-session gain.
- Service periodically calls session discovery on the worker, resolves ownership against `hybridSnapshot.exactSource`, binds/rebinds `OptionalDspController`, then frames `verifiedDsp=true` only when the owned non-zero session transport is active.
- Projected PRE_VOLUME output remains `source + mediaRouteGainDb + sessionDspAppliedGainDb`.
- If ordinary normalization requests positive/negative correction and Session DSP is unavailable, return HOLD `session_dsp_unavailable`; do NOT call Coarse Media fallback.
- Hard Media cap and Quiet Now paths remain unchanged.

- [ ] **Step 1: Write RED acceptance regression for Media 3/15**

```java
// PRE_VOLUME, media 3/15, quiet output, verified session DSP.
// Expect DSP_GAIN > 0 and requested Media index unchanged at 3.
```

Also require missing session DSP at the same 3/15 to HOLD with no Media write, and hard cap 5→4 to remain legal.

- [ ] **Step 2: Remove ordinary Coarse Media fallback authority from default control path**

The class may remain for historical/explicit fallback tests, but normal v0.7.7 coordinator/service path must not select it as compensation for missing DSP.

- [ ] **Step 3: Integrate discovery/binding on the playback worker**

Throttle `dumpsys` discovery to a conservative interval (initially 1000 ms) and trigger immediate refresh on source transition/session loss. Never run shell discovery on UI thread.

- [ ] **Step 4: Apply coordinator DSP commands to the bound session transport**

Log requested and applied gain. If apply fails, release/downgrade the session transport and HOLD on subsequent ordinary frames.

- [ ] **Step 5: Run full pure suite and Android compile**

Expected: `V077SessionDspControlPureTest: PASS`; historical hard cap/Quiet Now/user authority tests remain green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java \
        app/src/main/java/dev/soundceiling/app/ControlCommand.java \
        app/src/main/java/dev/soundceiling/app/NormalizerService.java \
        app/src/main/java/dev/soundceiling/app/OutputLevelModel.java \
        app/src/test/java/dev/soundceiling/app/V076ArchitectureRegressionPureTest.java \
        app/src/test/java/dev/soundceiling/app/V077SessionDspControlPureTest.java scripts/run-pure-tests.sh
git commit -m "feat: make session DSP the normalizer actuator"
```

---

### Task 6: Expose setup state, session telemetry, and copyable ADB command

**Files:**
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeState.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java`
- Modify: `app/src/main/java/dev/soundceiling/app/StatusText.java`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/AdvancedModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DiagnosticsView.java`
- Create: `app/src/test/java/dev/soundceiling/app/V077SessionDspTelemetryPureTest.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Runtime fields: `enhancedSessionPermissionGranted`, `sessionDspActive`, `sessionId`, `sessionUid`, `sessionPackage`, requested/applied gain, and setup reason.
- Required status when permission absent: `Enhanced Session DSP setup required` plus the exact ADB grant command.
- Required events: `session_discovery_permission`, `session_discovered`, `session_rejected`, `session_bound`, `session_dsp_attached`, `session_dsp_apply`, `session_dsp_released`, `session_dsp_unavailable`.

- [ ] **Step 1: Write RED telemetry/status tests**

Require session ID/UID/package in active status, exact setup command when permission missing, and no claim of active DSP when no non-zero session is bound.

- [ ] **Step 2: Extend runtime state and status text**

Keep copy concise; do not describe Media fallback as normalization.

- [ ] **Step 3: Add setup block/button in Simple and Advanced**

Provide copy-to-clipboard action for `EnhancedSessionSetup.ADB_GRANT_COMMAND`; do not claim permission can be granted from the app itself.

- [ ] **Step 4: Extend diagnostics**

Show permission, session, source/UID, requested/applied gain, and last session reason.

- [ ] **Step 5: Run tests and compile**

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/RuntimeState.java \
        app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java \
        app/src/main/java/dev/soundceiling/app/StatusText.java \
        app/src/main/java/dev/soundceiling/app/SimpleModeView.java \
        app/src/main/java/dev/soundceiling/app/AdvancedModeView.java \
        app/src/main/java/dev/soundceiling/app/DiagnosticsView.java \
        app/src/test/java/dev/soundceiling/app/V077SessionDspTelemetryPureTest.java scripts/run-pure-tests.sh
git commit -m "feat: expose enhanced session DSP setup and telemetry"
```

---

### Task 7: Add v0.7.7 contracts, release metadata, and Samsung checklist

**Files:**
- Create: `scripts/check-v077-session-dsp-contract.sh`
- Create: `scripts/check-v077-release-contract.sh`
- Create: `docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md`
- Modify: `.github/workflows/build-apk.yml`
- Modify: `app/build.gradle.kts`
- Modify: `README.md`

**Interfaces:**
- `versionCode=24`, `versionName="0.7.7"`.
- Artifact: `SoundCeiling-v0.7.7-debug-apk`.
- Contract forbids session 0 from normal enhanced registry and requires DUMP setup, non-zero ownership, no ordinary Media fallback, session lifecycle release, and the Media=3 regression.

- [ ] **Step 1: Add failing release/architecture contracts**

Require exact v0.7.7 files/tokens and ensure workflow executes both contracts before assembleDebug.

- [ ] **Step 2: Write Samsung physical checklist**

Primary sequence:
1. grant DUMP;
2. install/start v0.7.7;
3. Yandex Music, Media 3/15;
4. confirm non-zero owned session;
5. confirm `SESSION_DSP` and non-zero applied gain;
6. confirm Media stays 3 across quiet/loud material;
7. manually move 3→2 and 2→3; anchor follows user;
8. Safety Maximum test separately;
9. stop/restart Yandex and verify release/rebind;
10. revoke DUMP and verify HOLD/no ordinary Media writes.

- [ ] **Step 3: Bump release metadata and README**

Explicitly document that v0.7.7 deprecates session-zero runtime normalization after the measured Samsung neutral-attach delta of about -13.09 dB.

- [ ] **Step 4: Run all contracts and pure tests**

Run every `scripts/check-*.sh` used by Actions and `./scripts/run-pure-tests.sh`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/check-v077-session-dsp-contract.sh scripts/check-v077-release-contract.sh \
        docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md \
        .github/workflows/build-apk.yml app/build.gradle.kts README.md
git commit -m "release: prepare SoundCeiling v0.7.7"
```

---

### Task 8: Immutable-head CI and physical acceptance handoff

**Files:**
- No production file changes unless CI exposes a concrete defect.

**Interfaces:**
- Final immutable branch HEAD must have one complete successful normal workflow run.
- APK SHA-256 must be reported from that HEAD.

- [ ] **Step 1: Run/observe full GitHub Actions build on final HEAD**

Expected: pure tests, historical contracts, v0.7.7 contracts, `:app:assembleDebug`, checksum, artifact upload all green.

- [ ] **Step 2: Download only `SoundCeiling-v0.7.7-debug-apk` from that successful run**

Do not hand off an artifact from an intermediate commit.

- [ ] **Step 3: Verify APK SHA-256 and hand off the ADB command + APK**

Required setup command:

```bash
adb shell pm grant dev.soundceiling.app android.permission.DUMP
```

- [ ] **Step 4: Physical success criterion**

The first acceptable Samsung log must contain the semantic sequence:

```text
media=3
session=<positive nonzero>
actuatorTier=SESSION_DSP
... dspRequestedGainDb=>0
... dspAppliedGainDb=>0
media=3
```

followed by changing DSP gain on louder material while Media remains 3.

- [ ] **Step 5: If neutral non-zero session attach itself distorts output**

Stop. Do not weaken neutral verification or re-enable Media normalization. Record the failed session/route evidence and move the next design investigation to Shizuku/system/root/HAL options as specified by the approved design rollback.
