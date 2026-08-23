# SoundCeiling v0.7.2 Corrective Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the intended SoundCeiling behavior on Samsung: the system Media slider is the user's master anchor, live output projection uses proven PRE/POST capture semantics and the real route curve, app-owned attenuation is reversible, Linked Lock works live, source evidence is actionable, and Global DSP is attempted and verified truthfully.

**Architecture:** Keep the existing coordinator/service split, but add two pure state boundaries: `LiveCaptureReference` owns evidence-driven PRE/POST projection inputs, and `MediaAnchorState` owns user master authority plus app attenuation debt. `NormalizerService` only gathers Android evidence and executes one coordinator command; DSP remains a verified preferred actuator and Media remains a bounded reversible fallback.

**Tech Stack:** Android Java, public Android media/audiofx APIs, pure Java regression mains, shell source contracts, GitHub Actions `assembleDebug`.

**Spec:** `docs/superpowers/specs/2026-08-23-soundceiling-v072-corrective-architecture-design.md`

## Global Constraints

- Version name is `0.7.2`; increment versionCode from v0.7.1.
- Continue on `feature/v0.7-adaptive-envelope` and PR #8.
- Samsung Media is user master authority; app-owned writes never redefine the user anchor.
- UNKNOWN source may recover only app-owned attenuation debt up to the user anchor; it may not create arbitrary positive gain above the anchor.
- Capture reference is evidence-driven and resets on route/capture replacement.
- PRE_VOLUME projection includes current route Media gain; POST_VOLUME projection does not double-count it.
- Global DSP preference defaults ON but non-zero session-0 gain requires verified effect plus authorized whole-output scope.
- Hard Media cap, hard peak safety, OFF-policy fail-closed behavior, and neutralize-before-fallback ordering may not be weakened.
- Device-specific success remains `awaiting device test` until Samsung SM-A528B testing.

---

### Task 1: Field Regression Fixtures and Live Capture Reference

**Files:**
- Create: `app/src/test/java/dev/soundceiling/app/V072SamsungFieldRegressionPureTest.java`
- Create: `app/src/main/java/dev/soundceiling/app/LiveCaptureReference.java`
- Modify: `app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java`
- Modify: `app/src/main/java/dev/soundceiling/app/OutputGainPlanner.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Consumes: `ControlVolumeCurve.gainDbForIndex(int)`, `CaptureReferenceEstimator.Mode`.
- Produces: `LiveCaptureReference.mode()`, `LiveCaptureReference.observeMediaChange(float mediaDeltaDb, float beforePcmDb, float afterPcmDb)`, and `OutputGainPlanner.Input(..., float mediaGainDb, ...)`.

- [ ] **Step 1: Write the failing Samsung field regression**

```java
public final class V072SamsungFieldRegressionPureTest {
    public static void main(String[] args) {
        preVolumeEvidenceSurvivesLowMediaIndex();
        preVolumeProjectionIncludesMediaGain();
        postVolumeProjectionDoesNotDoubleCountMediaGain();
        System.out.println("V072SamsungFieldRegressionPureTest: PASS");
    }

    private static void preVolumeEvidenceSurvivesLowMediaIndex() {
        LiveCaptureReference ref = new LiveCaptureReference();
        ref.observeMediaChange(-5f, -1.0f, -1.2f);
        ref.observeMediaChange(-5f, -1.3f, -1.1f);
        ref.observeMediaChange(-5f, -1.1f, -1.2f);
        assertEquals(CaptureReferenceEstimator.Mode.PRE_VOLUME, ref.mode());
    }

    private static void preVolumeProjectionIncludesMediaGain() {
        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(testInput(
                CaptureReferenceEstimator.Mode.PRE_VOLUME, -18f, -1f, -53f, 0f));
        assertTrue(plan.projectedPeakDbfs() < -50f,
                "Media 1/15 must not look like a near-0 dBFS physical output peak");
    }

    private static void postVolumeProjectionDoesNotDoubleCountMediaGain() {
        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(testInput(
                CaptureReferenceEstimator.Mode.POST_VOLUME, -18f, -20f, -53f, 0f));
        assertNear(-20f, plan.projectedPeakDbfs(), .01f);
    }
}
```

- [ ] **Step 2: Register and run the new test to prove RED**

Run: `bash scripts/run-pure-tests.sh`

Expected: FAIL because `LiveCaptureReference` and the new `mediaGainDb` planner input do not exist.

- [ ] **Step 3: Implement the pure live reference wrapper**

```java
final class LiveCaptureReference {
    private final CaptureReferenceEstimator estimator =
            new CaptureReferenceEstimator(3, 2f, 1.25f);

    void observeMediaChange(float mediaDeltaDb, float beforePcmDb, float afterPcmDb) {
        estimator.observe(mediaDeltaDb, afterPcmDb - beforePcmDb);
    }

    CaptureReferenceEstimator.Mode mode() { return estimator.mode(); }
    void onRouteChanged() { estimator.resetForOutputRouteChange(); }
    void onCaptureReplaced() { estimator.resetForCaptureRestart(); }
}
```

- [ ] **Step 4: Pass route Media gain into output projection**

Update `OutputGainPlanner.Input` to store `mediaGainDb`. Compute:

```java
float mediaGain = Float.isFinite(input.mediaGainDb()) ? input.mediaGainDb() : 0f;
float projectedProgramDbfs = input.programDbfs();
float projectedPeakDbfs = input.rawPeakDbfs();
if (input.captureReference() == CaptureReferenceEstimator.Mode.PRE_VOLUME) {
    projectedProgramDbfs += mediaGain + appliedGainDb;
    projectedPeakDbfs += mediaGain + appliedGainDb;
} else if (input.captureReference() == CaptureReferenceEstimator.Mode.POST_VOLUME) {
    projectedProgramDbfs += appliedGainDb;
    projectedPeakDbfs += appliedGainDb;
}
```

UNKNOWN must not assume POST_VOLUME.

- [ ] **Step 5: Wire live observations in `NormalizerService`**

Keep the PCM level immediately before a genuine observed Media change and, after a stable follow-up window, feed the route delta and PCM delta to `LiveCaptureReference`. Replace the hard-coded:

```java
.captureReference(CaptureReferenceEstimator.Mode.POST_VOLUME)
```

with:

```java
.captureReference(liveCaptureReference.mode())
.mediaGainDb(controlCurve.gainDbForIndex(current))
```

Reset `liveCaptureReference` from the existing route-change and capture-rebind lifecycle hooks.

- [ ] **Step 6: Run pure + v0.7/v0.7.1 contracts**

Run:

```bash
bash scripts/run-pure-tests.sh
bash scripts/check-v07-adaptive-contract.sh
bash scripts/check-v071-ui-contract.sh
bash scripts/check-v071-release-contract.sh
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/LiveCaptureReference.java \
  app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java \
  app/src/main/java/dev/soundceiling/app/OutputGainPlanner.java \
  app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java \
  app/src/main/java/dev/soundceiling/app/NormalizerService.java \
  app/src/test/java/dev/soundceiling/app/V072SamsungFieldRegressionPureTest.java \
  scripts/run-pure-tests.sh
git commit -m "fix: use live capture reference for output projection"
```

---

### Task 2: User Master Anchor and Reversible Media Attenuation Debt

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/MediaAnchorState.java`
- Create: `app/src/test/java/dev/soundceiling/app/V072MediaAnchorPureTest.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java`
- Modify: `app/src/main/java/dev/soundceiling/app/StableOutputController.java`
- Modify: `app/src/main/java/dev/soundceiling/app/ControlCommand.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Consumes: coordinator volume observation + command provenance.
- Produces: `MediaAnchorState.userAnchorIndex()`, `debtSteps()`, `recordUserIndex(int)`, `recordAppAppliedIndex(int)`, `maxDebtRecoveryIndex()`.

- [ ] **Step 1: Write failing anchor/debt tests**

```java
private static void appDropDoesNotMoveUserAnchor() {
    MediaAnchorState state = MediaAnchorState.start(5);
    state = state.recordAppAppliedIndex(4);
    state = state.recordAppAppliedIndex(3);
    assertEquals(5, state.userAnchorIndex());
    assertEquals(2, state.debtSteps());
}

private static void unknownSourceMayRepayDebtButNotRaiseAboveAnchor() {
    MediaAnchorState state = MediaAnchorState.start(5).recordAppAppliedIndex(3);
    assertEquals(5, state.maxDebtRecoveryIndex());
    assertFalse(state.mayRecoverTo(6));
}

private static void userWriteRebasesAnchorAndCancelsFightLoop() {
    MediaAnchorState state = MediaAnchorState.start(2).recordAppAppliedIndex(1);
    state = state.recordUserIndex(2);
    assertEquals(2, state.userAnchorIndex());
    assertEquals(0, state.debtSteps());
}
```

- [ ] **Step 2: Run RED**

Run: `bash scripts/run-pure-tests.sh`

Expected: FAIL because `MediaAnchorState` does not exist.

- [ ] **Step 3: Implement immutable `MediaAnchorState`**

```java
final class MediaAnchorState {
    private final int userAnchorIndex;
    private final int currentIndex;

    static MediaAnchorState start(int current) {
        return new MediaAnchorState(current, current);
    }

    MediaAnchorState recordUserIndex(int current) {
        return new MediaAnchorState(current, current);
    }

    MediaAnchorState recordAppAppliedIndex(int current) {
        return new MediaAnchorState(userAnchorIndex, current);
    }

    int debtSteps() { return Math.max(0, userAnchorIndex - currentIndex); }
    int maxDebtRecoveryIndex() { return userAnchorIndex; }
    boolean mayRecoverTo(int target) { return target <= userAnchorIndex; }
}
```

Clamp indices using the active curve at the coordinator boundary.

- [ ] **Step 4: Make debt recovery a distinct command provenance**

Add `DEBT_RECOVERY` to `ControlCommand.Provenance`. In the coordinator, when the planner requests upward movement and `currentMediaIndex < userAnchorIndex`, allow a Media step toward `userAnchorIndex` even if source identity is UNKNOWN. Do not treat this as `policyAllowsPositiveGain` above the anchor.

- [ ] **Step 5: Prevent app writes from redefining the anchor**

On `VolumeObservation.USER`, call `recordUserIndex(current)`. On acknowledged `NORMALIZER_DOWN`, `PEAK_EMERGENCY`, or `HARD_CAP`, call `recordAppAppliedIndex(current)`. On mismatch/stale, do not silently modify the anchor.

- [ ] **Step 6: Add the exact 1->2->1 field regression**

The test must assert that after SoundCeiling owns index 1 below user anchor 2, a genuine user observation at index 2 produces no immediate NORMALIZER_DOWN command unless a new independent hard-safety violation is present.

- [ ] **Step 7: Run pure suite**

Run: `bash scripts/run-pure-tests.sh`

Expected: PASS including `V072MediaAnchorPureTest`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/MediaAnchorState.java \
  app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java \
  app/src/main/java/dev/soundceiling/app/StableOutputController.java \
  app/src/main/java/dev/soundceiling/app/ControlCommand.java \
  app/src/test/java/dev/soundceiling/app/V072MediaAnchorPureTest.java scripts/run-pure-tests.sh
git commit -m "fix: make media fallback reversible to user anchor"
```

---

### Task 3: Linked Lock Live-State Authority and Route-Relative Defaults

**Files:**
- Create: `app/src/test/java/dev/soundceiling/app/V072LinkedLockRuntimePureTest.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Modify: `app/src/main/java/dev/soundceiling/app/Prefs.java`
- Modify: `app/src/main/java/dev/soundceiling/app/ControlDefaults.java`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeModel.java`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Consumes: `Prefs.outputCeilings()`, USER observation from Task 2.
- Produces: one-way preference refresh for `linked`, explicit `Prefs.resetNormalizerDefaults(...)`, route-relative fallback floor helper.

- [ ] **Step 1: Write failing tests for live OFF persistence and USER-only ceiling shift**

```java
private static void linkedOffIsNotOverwrittenByStaleRuntimeState() {
    OutputCeilingState runtime = OutputCeilingState.defaultLinked();
    OutputCeilingState preference = OutputCeilingState.of(false, -20f, -20f);
    OutputCeilingState refreshed = LinkedLockPreferenceSync.applyPreference(runtime, preference);
    assertFalse(refreshed.linked());
}

private static void appOwnedWriteDoesNotShiftCeilings() {
    OutputCeilingState start = OutputCeilingState.of(true, -20f, -20f);
    assertEquals(start, start.onMediaIndexChanged(5, 4, -5f, true));
}
```

- [ ] **Step 2: Run RED**

Expected: FAIL because the explicit sync boundary does not exist.

- [ ] **Step 3: Remove generic runtime-to-prefs writeback**

Delete unconditional `persistCoordinatorCeilings()` behavior from the service loop. Persist a coordinator ceiling shift only in the branch that has just processed a proven USER volume observation and computed a real route delta.

- [ ] **Step 4: Add route-relative fallback minimum**

Replace first-run `MIN_MEDIA_INDEX = 1` semantics with a helper that derives an ordinary minimum from the current user anchor/route, while keeping the platform stream minimum as the absolute emergency floor. Existing explicit advanced values remain honored.

- [ ] **Step 5: Add reset-normalizer defaults API**

`Prefs.resetNormalizerDefaults(Context, ControlVolumeCurve)` must restore normalizer preferences only: Global DSP ON, Linked Lock ON, default linked target, default normalization preset/strength, safety/default fallback values. It must not clear calibration, app policies, log URIs, or diagnostic history.

- [ ] **Step 6: Run pure/contracts**

Run `bash scripts/run-pure-tests.sh` and current v0.7.1 UI contract. Expected PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java \
  app/src/main/java/dev/soundceiling/app/NormalizerService.java \
  app/src/main/java/dev/soundceiling/app/Prefs.java \
  app/src/main/java/dev/soundceiling/app/ControlDefaults.java \
  app/src/main/java/dev/soundceiling/app/SimpleModeModel.java \
  app/src/test/java/dev/soundceiling/app/V072LinkedLockRuntimePureTest.java scripts/run-pure-tests.sh
git commit -m "fix: make linked lock live preference authoritative"
```

---

### Task 4: Actionable Source Evidence and YouTube/Yandex Target-Probe Diagnostics

**Files:**
- Create: `app/src/test/java/dev/soundceiling/app/V072SourceEvidencePureTest.java`
- Modify: `app/src/main/java/dev/soundceiling/app/CaptureRequestCoordinator.java`
- Modify: `app/src/main/java/dev/soundceiling/app/MediaSessionEvidenceProvider.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeState.java`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DiagnosticsView.java`

**Interfaces:**
- Consumes: existing MediaSession candidate and targeted PCM proof.
- Produces: explicit `SourceAccessState`/status text and event fields for access/candidate/target confirmation.

- [ ] **Step 1: Write failing status-state tests**

Cover these exact states: `ACCESS_MISSING`, `NO_CANDIDATE`, `CANDIDATE_UNCONFIRMED`, `MULTIPLE_CANDIDATES`, `TARGET_CONFIRMED`, `TARGET_SUPPRESSED_SILENT`.

- [ ] **Step 2: Run RED**

Expected: FAIL because current source status is only a free-form label.

- [ ] **Step 3: Add pure enum/state mapping**

Expose state from `CaptureRequestCoordinator` without promoting a MediaSession candidate to verified source.

- [ ] **Step 4: Log every evidence boundary**

Add transition events:

```text
media_session_access available=true|false
source_candidates count=N packages=...
target_probe action=open uid=...
target_probe result=confirmed|silent|failed uid=...
```

Do not log titles, URLs, notification contents, or other private media metadata.

- [ ] **Step 5: Make missing access actionable in Simple mode**

When access is missing, show a concise explanation and a button opening `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. The button must be available without stopping the service.

- [ ] **Step 6: Run pure + Android source contracts**

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/CaptureRequestCoordinator.java \
  app/src/main/java/dev/soundceiling/app/MediaSessionEvidenceProvider.java \
  app/src/main/java/dev/soundceiling/app/NormalizerService.java \
  app/src/main/java/dev/soundceiling/app/RuntimeState.java \
  app/src/main/java/dev/soundceiling/app/SimpleModeView.java \
  app/src/main/java/dev/soundceiling/app/DiagnosticsView.java \
  app/src/test/java/dev/soundceiling/app/V072SourceEvidencePureTest.java
git commit -m "fix: expose actionable live source evidence"
```

---

### Task 5: Real Global DSP Probe Lifecycle

**Files:**
- Create: `app/src/test/java/dev/soundceiling/app/V072GlobalDspProbePureTest.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DspTransportManager.java`
- Modify: `app/src/main/java/dev/soundceiling/app/OptionalDspController.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeState.java`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeView.java`

**Interfaces:**
- Consumes: existing `AndroidDynamicsProcessingTransport.forNeutralGlobalProbe()`, `DspScopeProbe`.
- Produces: observable transport creation state and bounded verification lifecycle.

- [ ] **Step 1: Write RED probe-state tests**

Assert transitions:

```text
PREFERENCE_ON + create failure -> UNAVAILABLE
PREFERENCE_ON + neutral create -> AVAILABLE_UNVERIFIED
successful -2 dB proof + consent -> VERIFIED_GLOBAL_MIX
failed/timeout proof -> neutral + AVAILABLE_UNVERIFIED or UNAVAILABLE
capability loss with nonzero gain -> neutralization command before Media fallback
```

- [ ] **Step 2: Run RED**

Expected: FAIL because neutral transport creation is currently hidden inside `beginGlobalProbe()` and cannot distinguish create failure from never attempted.

- [ ] **Step 3: Add explicit neutral global transport preparation**

Add `prepareGlobalProbeTransport(routeIdentity)` in `DspTransportManager`. It creates session-0 neutral transport once per route and returns its raw capability without applying non-zero normalization gain.

- [ ] **Step 4: Make service probe state machine call prepare before collecting proof**

Log `global_dsp_transport create=success|failed capability=... reason=...`. Collect before samples only when transport is at least AVAILABLE_UNVERIFIED, apply bounded -2 dB probe, collect after samples, restore 0 dB, then classify.

- [ ] **Step 5: Keep non-zero gain gated by verification**

Do not relax `AndroidDynamicsProcessingTransport.applyGainDb()`. `VERIFIED_GLOBAL_MIX` remains mandatory for non-zero normalizer gain.

- [ ] **Step 6: Run DSP pure contract + full pure suite**

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/DspTransportManager.java \
  app/src/main/java/dev/soundceiling/app/OptionalDspController.java \
  app/src/main/java/dev/soundceiling/app/NormalizerService.java \
  app/src/main/java/dev/soundceiling/app/RuntimeState.java \
  app/src/main/java/dev/soundceiling/app/SimpleModeView.java \
  app/src/test/java/dev/soundceiling/app/V072GlobalDspProbePureTest.java
git commit -m "fix: attempt and verify global dsp explicitly"
```

---

### Task 6: Simple Reset Defaults, Human Spectrum Copy, and v0.7.2 Contract

**Files:**
- Create: `scripts/check-v072-corrective-contract.sh`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DiagnosticsView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/HelpText.java`
- Modify: `.github/workflows/build-apk.yml`

**Interfaces:**
- Consumes: `Prefs.resetNormalizerDefaults(...)` from Task 3.
- Produces: one reset button and v0.7.2 source contract.

- [ ] **Step 1: Write failing source contract**

Require:
- `Вернуть настройки по умолчанию` in Simple mode;
- confirmation dialog;
- `Частотный спектр` copy explaining five diagnostic bands and that it does not control normalization;
- Global DSP status distinguishes preferred/available/verified/active;
- missing source access button exists;
- v0.7.2 pure tests are registered;
- workflow runs `check-v072-corrective-contract.sh`.

- [ ] **Step 2: Run RED**

Run: `bash scripts/check-v072-corrective-contract.sh`

Expected: FAIL until the UI and workflow changes exist.

- [ ] **Step 3: Add reset button with confirmation**

The positive action calls `Prefs.resetNormalizerDefaults(getContext(), curve)`, refreshes the model, and logs `normalizer_defaults_reset`. Cancel performs no write.

- [ ] **Step 4: Update spectrum terminology/help**

Use `Частотный спектр` and help text equivalent to: `Пять диагностических полос показывают относительную энергию низких, средних и высоких частот. Они не управляют нормализацией.`

- [ ] **Step 5: Register the v0.7.2 contract in Actions**

Place it after pure tests and before Android assemble so source regression fails fast.

- [ ] **Step 6: Run full local non-Android gates**

```bash
bash scripts/run-pure-tests.sh
bash scripts/check-v072-corrective-contract.sh
bash scripts/check-v071-release-contract.sh
bash scripts/check-v071-ui-contract.sh
bash scripts/check-v07-adaptive-contract.sh
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add scripts/check-v072-corrective-contract.sh \
  app/src/main/java/dev/soundceiling/app/SimpleModeView.java \
  app/src/main/java/dev/soundceiling/app/DiagnosticsView.java \
  app/src/main/java/dev/soundceiling/app/HelpText.java .github/workflows/build-apk.yml
git commit -m "feat: add v0.7.2 corrective controls and contract"
```

---

### Task 7: Release Identity and Complete Regression Verification

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Create: `docs/v0.7.2-samsung-field-checklist.md`
- Modify: `.github/workflows/build-apk.yml`

**Interfaces:**
- Consumes: all prior task behavior.
- Produces: v0.7.2 debug APK artifact and exact Samsung retest checklist.

- [ ] **Step 1: Set release identity**

Set `versionName "0.7.2"` and increment `versionCode` from 11 to 12. Name the artifact `SoundCeiling-v0.7.2-debug-apk`.

- [ ] **Step 2: Write Samsung retest checklist**

Keep physical results unchecked. Include:
- first-run Global DSP/Linked Lock defaults;
- Media 1/15 does not trigger false repeated hard-peak collapse for ordinary content;
- user anchor changes at 2/15, 5/15, 8/15 remain authoritative;
- app attenuation returns to anchor after loud segment;
- Linked Lock toggles live while running;
- source access state and YouTube/Yandex candidate/target result;
- Global DSP transport create/probe status;
- loud/quiet reversal behavior and no permanent attenuation;
- APK SHA-256.

- [ ] **Step 3: Run clean non-Android verification**

Run every `scripts/check-*.sh` workflow gate plus `scripts/run-pure-tests.sh`. Expected PASS.

- [ ] **Step 4: Push and require GitHub Actions `assembleDebug`**

Do not call the task GREEN until the exact commit has successful pure tests, all contracts, Android `assembleDebug`, checksum, and artifact upload.

- [ ] **Step 5: Download exact CI APK and verify**

Compute SHA-256 and run ZIP integrity validation. Record only automated evidence in the field checklist.

- [ ] **Step 6: Focused final review**

Review the final diff specifically for:
- hard-coded POST_VOLUME remnants;
- Media writes that can change user anchor without USER provenance;
- runtime preference writeback races;
- non-zero Global DSP before verification;
- hard-safety regressions;
- raw uploaded-log/private metadata accidentally committed.

Any finding requires a failing regression before a fix and a fresh full CI run.

- [ ] **Step 7: Final commit / evidence**

```bash
git add app/build.gradle README.md docs/v0.7.2-samsung-field-checklist.md .github/workflows/build-apk.yml
git commit -m "release: prepare SoundCeiling v0.7.2 field build"
```

Final status remains `awaiting device test` for Samsung-specific behavior until the user tests the APK.
