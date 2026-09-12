# SoundCeiling v0.9 PCM Shadow Feasibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship install-over-safe SoundCeiling v0.9.0 with third-party Session DSP blocked before construction and a truthful, non-audible PCM shadow-normalization feasibility pipeline.

**Architecture:** Existing `AudioPlaybackCapture` remains the metering/source input. A pure PCM16 shadow processor reuses the current output-domain projection and continuous gain controller, but a separate public-API feasibility gate permanently blocks audible rendering because playback capture keeps the original audio rendered. Session `DynamicsProcessing` is rejected in depth at setup, runtime, facade, manager, and Android factory boundaries.

**Tech Stack:** Android Java 17, Android SDK 35, `AudioRecord`/`AudioPlaybackCapture`, existing pure Java control core, shell release contracts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-29-v090-pcm-shadow-feasibility-design.md`

## Global Constraints

- Continue PR #8 on `feature/v0.7-adaptive-envelope`; do not create another branch or PR.
- Samsung Media remains the user master; Volume Down is never intercepted or automatically reversed.
- Enhanced Session runtime reason is exactly `field_quarantined_neutral_media_bypass`.
- Public playback capture is `COPY_ONLY`; audible PCM output remains `BLOCKED`.
- No root, Shizuku, hidden API, reflection, privileged/system signing, stream substitution, or Media muting.
- Default controlled usage remains MEDIA; calls, alarms, ringtone, notifications, DTMF, accessibility, assistant, and system apps remain excluded.
- versionCode is `36`; versionName is `0.9.0`; stable development signer SHA-256 remains `5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2`.

---

### Task 1: Quarantine Enhanced Session DSP before every constructor boundary

**Files:**
- Create: `app/src/test/java/dev/soundceiling/app/V090SessionDspQuarantinePureTest.java`
- Create: `scripts/run-v090-session-quarantine-tests.sh`
- Create: `scripts/check-v090-session-quarantine-contract.sh`
- Modify: `app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java`
- Modify: `app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java`
- Modify: `app/src/main/java/dev/soundceiling/app/OptionalDspController.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DspTransportManager.java`
- Modify: `app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java`
- Modify: `app/src/test/java/dev/soundceiling/app/V080SafeCustomMatrixPureTest.java`
- Modify: `scripts/check-v080-session-matrix-contract.sh`
- Modify: `scripts/check-v080-release-contract.sh`

**Interfaces:**
- Produces: `EnhancedSessionSetup.RUNTIME_QUARANTINED`, `RUNTIME_QUARANTINE_REASON`, and `runtimeAllowed()`.
- Consumes: the v0.8 field conclusion that neutral Session attachment is unsafe independently of gain readback.

- [x] **Step 1: Write the failing pure quarantine test**

```java
package dev.soundceiling.app;

public final class V090SessionDspQuarantinePureTest {
    public static void main(String[] args) {
        require(EnhancedSessionSetup.RUNTIME_QUARANTINED, "runtime quarantine");
        require(!EnhancedSessionSetup.runtimeAllowed(), "runtime must be denied");
        require("field_quarantined_neutral_media_bypass".equals(
                EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON), "stable field reason");
        System.out.println("V090SessionDspQuarantinePureTest: PASS");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
```

- [x] **Step 2: Add the RED runner and wiring contract**

`run-v090-session-quarantine-tests.sh` compiles only `EnhancedSessionSetup` and the new pure test.
`check-v090-session-quarantine-contract.sh` must verify that runtime/facade/manager/factory each
tests `runtimeAllowed()` before discovery, matrix iteration, or `new AndroidDynamicsProcessingTransport`.

- [x] **Step 3: Run RED and confirm the missing quarantine API is the failure**

Run:

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v090-session-quarantine-tests.sh
```

Expected: compilation fails because `RUNTIME_QUARANTINED`, `RUNTIME_QUARANTINE_REASON`, and
`runtimeAllowed()` do not exist.

- [x] **Step 4: Add the canonical quarantine and defense-in-depth guards**

```java
static final boolean RUNTIME_QUARANTINED = true;
static final String RUNTIME_QUARANTINE_REASON =
        "field_quarantined_neutral_media_bypass";
static boolean runtimeAllowed() { return !RUNTIME_QUARANTINED; }
```

Every Enhanced Session boundary returns unavailable with the canonical reason before any side
effect. Preserve historical matrix types for documentation/tests, but make the v0.8 tests and
contracts explicitly quarantine-aware instead of requiring live matrix authority.

- [x] **Step 5: Run GREEN and the historical matrix tests**

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v090-session-quarantine-tests.sh
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v080-safe-custom-matrix-tests.sh
bash ./scripts/check-v090-session-quarantine-contract.sh
bash ./scripts/check-v080-session-matrix-contract.sh
```

Expected: all four commands pass.

- [x] **Step 6: Commit the quarantine slice**

```bash
git add app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java \
  app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java \
  app/src/main/java/dev/soundceiling/app/OptionalDspController.java \
  app/src/main/java/dev/soundceiling/app/DspTransportManager.java \
  app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java \
  app/src/test/java/dev/soundceiling/app/V080SafeCustomMatrixPureTest.java \
  app/src/test/java/dev/soundceiling/app/V090SessionDspQuarantinePureTest.java \
  scripts/run-v090-session-quarantine-tests.sh scripts/check-v090-session-quarantine-contract.sh \
  scripts/check-v080-session-matrix-contract.sh scripts/check-v080-release-contract.sh
git commit -m "fix(v0.9): quarantine unsafe Samsung session DSP"
```

### Task 2: Add the public-API PCM replacement feasibility gate

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/PcmDspFeasibility.java`
- Create: `app/src/test/java/dev/soundceiling/app/V090PcmFeasibilityPureTest.java`
- Create: `scripts/run-v090-pcm-feasibility-tests.sh`

**Interfaces:**
- Produces: `PcmDspFeasibility.publicPlaybackCapture()` returning an immutable `Verdict`.
- Produces: `Verdict.mode`, `captureSemantics`, `duplicatePrevention`, `audibleOutputAllowed`, and `reason`.
- Consumes: AOSP public playback-capture semantics (`LOOP_BACK | RENDER`).

- [x] **Step 1: Write the failing feasibility test**

```java
PcmDspFeasibility.Verdict verdict = PcmDspFeasibility.publicPlaybackCapture();
require(verdict.mode == PcmDspFeasibility.Mode.SHADOW_ONLY, "shadow mode");
require(verdict.captureSemantics == PcmDspFeasibility.CaptureSemantics.COPY_ONLY,
        "public capture is copy-only");
require(verdict.duplicatePrevention == PcmDspFeasibility.DuplicatePrevention.UNAVAILABLE,
        "no duplicate prevention");
require(!verdict.audibleOutputAllowed, "AudioTrack must be blocked");
require("public_playback_capture_keeps_original_audio".equals(verdict.reason), "reason");
```

- [x] **Step 2: Run RED**

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v090-pcm-feasibility-tests.sh
```

Expected: compilation fails because `PcmDspFeasibility` does not exist.

- [x] **Step 3: Implement the immutable gate**

Implement enums `Mode { SHADOW_ONLY, ACTIVE_REPLACEMENT }`,
`CaptureSemantics { COPY_ONLY, EXCLUSIVE_REPLACEMENT }`, and
`DuplicatePrevention { UNAVAILABLE, VERIFIED }`. The public factory returns only the exact blocked
verdict from the test; no runtime preference can promote it.

- [x] **Step 4: Run GREEN**

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v090-pcm-feasibility-tests.sh
```

Expected: `V090PcmFeasibilityPureTest: PASS`.

- [x] **Step 5: Commit the feasibility gate**

```bash
git add app/src/main/java/dev/soundceiling/app/PcmDspFeasibility.java \
  app/src/test/java/dev/soundceiling/app/V090PcmFeasibilityPureTest.java \
  scripts/run-v090-pcm-feasibility-tests.sh
git commit -m "feat(v0.9): add fail-closed PCM replacement gate"
```

### Task 3: Build the non-audible PCM16 shadow normalizer

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/PcmShadowDsp.java`
- Create: `app/src/test/java/dev/soundceiling/app/V090PcmShadowDspPureTest.java`
- Create: `scripts/run-v090-pcm-shadow-tests.sh`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Consumes: `OutputLevelModel`, `OutputCeilingState`, `ControlProfile`,
  `ContinuousDspController`, and `CaptureReferenceEstimator.Mode`.
- Produces: `PcmShadowDsp.process(...) -> Result` and `PcmShadowDsp.reset()`.

- [x] **Step 1: Write RED tests for quiet, loud, peak, recovery, conversion, and reset**

Use this API:

```java
PcmShadowDsp.Result result = dsp.process(
        atMs, input, input.length, shadow,
        sourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb,
        CaptureReferenceEstimator.Mode.PRE_VOLUME,
        OutputCeilingState.of(true, -20f, -20f), profile, true);
```

Assertions must prove:

- quiet projected output requests/applies positive gain;
- loud projected output requests/applies negative gain;
- a hard peak violation attenuates on the first block;
- recovery after attenuation is slower than attack;
- positive gain never exceeds PCM digital headroom (`-0.5 dBFS`);
- `clippedSamples == 0` for legal output;
- `reset()` returns applied gain to `0 dB` and clears controller history;
- invalid/unknown output-domain evidence remains inactive.

- [x] **Step 2: Run RED**

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v090-pcm-shadow-tests.sh
```

Expected: compilation fails because `PcmShadowDsp` does not exist.

- [x] **Step 3: Implement minimal shadow processing**

`process` validates buffers, projects the output with current shadow gain, asks
`ContinuousDspController` for the next gain, immediately clamps any newly unsafe gain to both the
configured output peak ceiling and `-0.5 dBFS` PCM headroom, writes a saturating PCM16 copy, and
returns bounded metrics. It must never import or reference `AudioTrack`.

- [x] **Step 4: Run GREEN and the complete pure suite**

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash ./scripts/run-v090-pcm-shadow-tests.sh
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH ./scripts/run-pure-tests.sh
```

Expected: the v0.9 test and every historical pure test pass with `-Xlint:all -Werror`.

- [x] **Step 5: Commit the shadow DSP core**

```bash
git add app/src/main/java/dev/soundceiling/app/PcmShadowDsp.java \
  app/src/test/java/dev/soundceiling/app/V090PcmShadowDspPureTest.java \
  scripts/run-v090-pcm-shadow-tests.sh scripts/run-pure-tests.sh
git commit -m "feat(v0.9): add PCM shadow normalization core"
```

### Task 4: Wire truthful v0.9 lifecycle, telemetry, and UI

**Files:**
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeState.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java`
- Modify: `app/src/main/java/dev/soundceiling/app/StatusText.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DiagnosticsView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/AdvancedModeView.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `scripts/check-v090-runtime-wiring-contract.sh`

**Interfaces:**
- Consumes: `PcmDspFeasibility.Verdict` and `PcmShadowDsp.Result`.
- Produces: Runtime fields for PCM DSP mode/reason, shadow requested/applied gain, projected peak,
  clipped samples, and an unambiguous non-audible UI status.

- [x] **Step 1: Write the RED wiring contract**

The contract must require:

```text
pcm_dsp_feasibility
pcm_dsp_shadow
SHADOW_ONLY
public_playback_capture_keeps_original_audio
field_quarantined_neutral_media_bypass
```

It must reject `android.permission.DUMP` in the manifest, reject an `AudioTrack` import/reference in
`NormalizerService` and `PcmShadowDsp`, and reject visible Enhanced Session setup buttons.

- [x] **Step 2: Run RED**

```bash
bash ./scripts/check-v090-runtime-wiring-contract.sh
```

Expected: failure because runtime/state/UI wiring is absent.

- [x] **Step 3: Integrate shadow processing without actuator authority**

Create one reusable shadow buffer in `loopPlaybackCapture`, process every valid PCM block after
source/output projection is known, but only when the existing policy has positively allowed a
high-confidence MEDIA source and the selected profile enables PCM DSP. Unknown, low-confidence,
disabled, and excluded sources must reset/hold neutral. Publish bounded metrics. Log the feasibility
verdict once per capture lifecycle and shadow summaries through `DiagnosticLog.transition`. Do not
pass shadow gain to `OptionalDspController`, `NormalizerControlCoordinator` as verified authority,
or any output sink.

- [x] **Step 4: Reset on every lifecycle boundary**

Call `PcmShadowDsp.reset()` on capture replacement, route change, stop, destroy, and new service
epoch. The stop path must remain idempotent and must not restore any Media value.

- [x] **Step 5: Make the UI truthful and remove obsolete DUMP setup**

Remove the DUMP declaration. Hide both copy-command buttons. Render Session DSP as field-quarantined
before checking permission, and render PCM DSP as `Shadow only · audible output blocked` with the
canonical reason and metrics in Diagnostics.

- [x] **Step 6: Run GREEN plus UI/historical contracts**

```bash
bash ./scripts/check-v090-runtime-wiring-contract.sh
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH ./scripts/run-pure-tests.sh
bash ./scripts/check-v071-ui-contract.sh
bash ./scripts/check-v0776-strict-safety-contract.sh
```

Expected: all commands pass.

- [x] **Step 7: Commit runtime wiring**

```bash
git add app/src/main/java/dev/soundceiling/app/NormalizerService.java \
  app/src/main/java/dev/soundceiling/app/RuntimeState.java \
  app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java \
  app/src/main/java/dev/soundceiling/app/StatusText.java \
  app/src/main/java/dev/soundceiling/app/DiagnosticsView.java \
  app/src/main/java/dev/soundceiling/app/SimpleModeView.java \
  app/src/main/java/dev/soundceiling/app/AdvancedModeView.java \
  app/src/main/AndroidManifest.xml scripts/check-v090-runtime-wiring-contract.sh
git commit -m "feat(v0.9): wire truthful PCM shadow runtime"
```

### Task 5: Package, verify, and publish v0.9.0

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/build-apk.yml`
- Modify: `README.md`
- Create: `docs/field-tests/2026-08-29-v0.9.0-samsung-checklist.md`
- Create: `scripts/check-v090-release-contract.sh`
- Modify: `scripts/check-v071-release-contract.sh`
- Modify: `scripts/check-v080-release-contract.sh`
- Modify: `docs/superpowers/specs/2026-08-29-v090-pcm-shadow-feasibility-design.md`
- Modify: `docs/superpowers/plans/2026-08-29-v090-pcm-shadow-feasibility.md`

**Interfaces:**
- Produces: signed `SoundCeiling-v0.9.0-debug-apk` artifact and checksum.
- Consumes: every v0.9 pure/wiring/release runner and all historical CI gates.

- [x] **Step 1: Write and run the RED release contract**

Require versionCode `36`, versionName `0.9.0`, all three v0.9 test/wiring commands in Actions,
stable signer verification, artifact name `SoundCeiling-v0.9.0-debug-apk`, README release section,
and the Samsung checklist.

```bash
bash ./scripts/check-v090-release-contract.sh
```

Expected: failure on the old v0.8.0 packaging.

- [x] **Step 2: Update version, CI, README, and field checklist**

The checklist begins with install-over v0.8.0 and confirms the field quarantine before any slider or
audio test. It then checks no Session candidate/apply events, no physical Media bypass, no audible
shadow output, positive/negative shadow direction, peak bounds, Volume Down authority, hard-cap
panel behavior, stop/restart, route change, and log export.

- [x] **Step 3: Run every local verification gate**

```bash
PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH ./scripts/run-pure-tests.sh
for script in scripts/check-*.sh scripts/run-*.sh; do
  PATH=/workspace/scratch/027786b503fb/tooling/bin:$PATH bash "$script"
done
git diff --check
git status --short
```

Expected: every script exits 0, diff check is clean, and status contains only intended v0.9 files.

- [x] **Step 4: Commit release packaging**

```bash
git add app/build.gradle.kts .github/workflows/build-apk.yml README.md \
  docs/field-tests/2026-08-29-v0.9.0-samsung-checklist.md \
  docs/superpowers/specs/2026-08-29-v090-pcm-shadow-feasibility-design.md \
  docs/superpowers/plans/2026-08-29-v090-pcm-shadow-feasibility.md \
  scripts/check-v090-release-contract.sh
git commit -m "chore(v0.9): package PCM shadow feasibility build"
```

- [ ] **Step 5: Push the existing PR branch and verify immutable CI evidence**

Push only as a fast-forward to `feature/v0.7-adaptive-envelope`. Verify the exact remote HEAD, API
35 build step, every v0.9 gate, signer fingerprint, APK checksum, and uploaded artifact on the same
commit. Do not mark PR #8 ready or merge it.

- [ ] **Step 6: Perform code review and fix Critical/Important findings**

Review the complete diff from `b20ffcd6a07ff67c1ef2c9882515793b190f20ec` to the v0.9 HEAD against
the spec. Re-run the full local suite and remote CI after any review fix.

Review hardening implemented before final CI:

- rejected PCM clears the full shadow buffer and reports `processedSamples=0`/no source metrics;
- `PcmShadowEligibility` has behavioral coverage for exact, OFF, confidence, protected usage,
  multi-endpoint, source transition, unknown domain, and system-source cases;
- privileged DUMP discovery and ADB setup are absent from production source; the historical parser
  is test-only;
- the historical session-zero constructor is guarded by the same v0.9 field quarantine;
- Simple/Advanced present the preference as non-audible PCM Shadow rather than active Global DSP.

- [ ] **Step 7: Download and verify the deliverable**

Download `SoundCeiling-v0.9.0-debug-apk`, verify its SHA-256 and signer locally, and provide the APK,
field checklist, immutable commit SHA, CI run, and PR #8 link. State explicitly that PCM DSP is
shadow-only and does not yet change audible output.
