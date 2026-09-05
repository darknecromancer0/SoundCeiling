# v0.7.7.6 Strict Safety Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the configured Samsung Media hard ceiling non-contaminating and resistant to held Volume-Up / panel-drag races while preserving absolute user authority for Volume-Down.

**Architecture:** Split strict safety into two boundaries. `VolumeKeySafetyService` filters hardware Volume-Up before Android handles it, while `HardCapLatch` owns reactive correction for slider/other external writes. `VolumeWriteTracker` and `NormalizerControlCoordinator` gain an explicit rejected-overshoot observation so an illegal index can never become a user anchor or move Linked Lock ceilings.

**Tech Stack:** Android 14 / Java 17, AccessibilityService, AudioManager, existing pure-Java controller/tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-28-v1-roadmap-strict-safety-design.md`

## Global Constraints

- Volume-Down must never be consumed or automatically reversed as a response to the user's downward action.
- Any observed Media index above `SafetySettings.hardMax()` is illegal state and cannot update `MediaAnchorState`, Linked Lock ceilings, recovery debt, or user-authority state.
- Hardware Volume-Up filtering is active only while SoundCeiling is running and Strict Safety accessibility is enabled.
- Samsung panel/other external writes are corrected through a bounded latch until multiple consecutive legal readbacks are observed.
- No OEM default `DynamicsProcessing` fallback is restored by this work.
- Stable development signing remains unchanged.

---

### Task 1: Pure rejected-overshoot authority model

**Files:**
- Modify: `app/src/main/java/dev/soundceiling/app/VolumeWriteTracker.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java`
- Test: `app/src/test/java/dev/soundceiling/app/V0776StrictSafetyPureTest.java`

**Interfaces:**
- Produces: `VolumeWriteTracker.ObservationKind.REJECTED_HARD_CAP_OVERSHOOT`
- Produces: `VolumeWriteTracker.observe(int index, long nowMs, int hardMaxIndex)`
- Coordinator consumes rejected overshoot as safety-only authority and never calls `recordUserIndex` for it.

- [ ] **Step 1: Write the failing pure test**

Create tests covering `4 -> 5 -> 7 -> 11 -> 15` while hard max is 4. Assert every index above 4 is classified `REJECTED_HARD_CAP_OVERSHOOT`, authority origin is `HARD_PEAK_SAFETY`, and coordinator `mediaAnchorState().userAnchorIndex()` remains 4.

- [ ] **Step 2: Run the focused test and confirm RED**

Run the repository pure-test harness for `V0776StrictSafetyPureTest`. Expected: compile/test failure because the rejected-overshoot kind and overload do not exist.

- [ ] **Step 3: Implement minimal tracker/coordinator behavior**

Add the observation kind and overload. When `index > hardMaxIndex`, clear conflicting pending writes into stale quarantine, update the tracker's physical last-observed index, and return rejected overshoot without USER authority. Teach coordinator authority handling to ignore the rejected overshoot for anchor/ceiling persistence.

- [ ] **Step 4: Re-run focused and existing volume-authority tests**

Expected: PASS, including stale/mismatch tests proving legal external changes are still user-authoritative.

- [ ] **Step 5: Commit**

Commit message: `fix: reject hard-cap overshoot authority`

### Task 2: Hard-cap correction latch

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/HardCapLatch.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Test: `app/src/test/java/dev/soundceiling/app/V0776StrictSafetyPureTest.java`

**Interfaces:**
- Produces: `HardCapLatch.Decision update(int observedIndex, int hardMaxIndex, long nowMs)` with `shouldWrite`, `targetIndex`, `latched`, `confirmationCount`.
- Latch requires 3 consecutive readbacks `<= hardMaxIndex` before releasing after an overshoot.

- [ ] **Step 1: Extend RED test for latch behavior**

Test sequence `4,5,7,11,15,4,5,4,4,4`. Assert every overshoot requests target 4, a rebound during confirmation resets confirmation count, and release occurs only after three consecutive legal readbacks.

- [ ] **Step 2: Run focused test and confirm RED**

Expected: missing `HardCapLatch`.

- [ ] **Step 3: Implement `HardCapLatch` and integrate before coordinator authority**

`NormalizerService.observeVolumeAndEnforce()` must call the tracker with current hard max, update the latch, and immediately call `safeVolume.enforceHardMax()` whenever latch requests correction. Re-observe actual Media after the write and feed the resulting legal/illegal state back into the latch. Log `hard_cap_latch_enter`, `hard_cap_latch_write`, `hard_cap_latch_confirm`, and `hard_cap_latch_release`.

- [ ] **Step 4: Run focused plus full pure suite**

Expected: PASS. Existing hard-cap behavior remains compatible.

- [ ] **Step 5: Commit**

Commit message: `fix: latch Samsung hard-cap correction`

### Task 3: Hardware Volume-Up accessibility gate

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/VolumeKeySafetyService.java`
- Create: `app/src/main/res/xml/volume_key_safety_service.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create/Modify: strict-safety state helper under `app/src/main/java/dev/soundceiling/app/`
- Test: `app/src/test/java/dev/soundceiling/app/V0776StrictSafetyPureTest.java`

**Interfaces:**
- Accessibility service requests `flagRequestFilterKeyEvents` and receives key events before system handling.
- `KEYCODE_VOLUME_UP`: when SoundCeiling is running and current Media >= hard max, consume DOWN/UP events and hold at hard max.
- `KEYCODE_VOLUME_DOWN`: always return false so Android handles it normally.
- When current Media < hard max, Volume-Up returns false so normal stepping remains user-controlled until the ceiling is reached.

- [ ] **Step 1: Add pure key-policy RED tests**

Extract/test a pure `VolumeKeySafetyPolicy.shouldConsume(keyCode, action, running, strictEnabled, current, hardMax)` decision. Cover Up below ceiling=false, Up at/above ceiling=true, Down always=false, service stopped=false.

- [ ] **Step 2: Confirm RED**

Expected: policy/service missing.

- [ ] **Step 3: Implement policy, service, XML and manifest registration**

The service reads current hard max from app preferences and current Media from `AudioManager`; it never raises volume itself. On consumed Up at an already-illegal current index, it may write down to hard max through a safety-only helper. Add diagnostic events for filtered and pass-through decisions without logging every repeated key excessively.

- [ ] **Step 4: Run pure tests and Android build checks**

Expected: pure tests PASS and manifest/resource compilation succeeds.

- [ ] **Step 5: Commit**

Commit message: `feat: add strict volume-key safety gate`

### Task 4: Runtime truthfulness, UI entry and regression locks

**Files:**
- Modify: existing settings/main activity UI files that render safety status
- Modify: `scripts/check-source-invariants.sh` or versioned regression script following repository convention
- Modify: `app/build.gradle.kts`
- Test: add source-contract checks for v0.7.7.6

**Interfaces:**
- UI exposes `Strict Safety` state and an action to open Android Accessibility settings when not enabled.
- Runtime/status wording distinguishes strict hardware-key protection from reactive-only fallback.
- `versionCode=30`, `versionName="0.7.7.6"`.

- [ ] **Step 1: Add failing source/release contracts**

Require accessibility service registration, `flagRequestFilterKeyEvents`, Volume-Down pass-through, rejected-overshoot observation, 3-readback latch, no OEM default Enhanced Session fallback, and stable signing.

- [ ] **Step 2: Confirm RED**

Expected: version/UI/contracts not yet updated.

- [ ] **Step 3: Implement minimal UI/status/version updates**

Keep existing Simple/Advanced design; add only the Strict Safety state/action needed for 1.0 safety truthfulness.

- [ ] **Step 4: Run full CI-equivalent test chain and `:app:assembleDebug`**

Expected: all regression gates PASS, stable signing fingerprint unchanged, APK produced.

- [ ] **Step 5: Commit**

Commit message: `release: prepare v0.7.7.6 strict safety`

### Task 5: Samsung acceptance run

**Files:**
- Add: `docs/field-tests/2026-08-28-v0.7.7.6-samsung-checklist.md`

**Interfaces:**
- Acceptance log must contain no RED `safety_cap_violation` and no illegal overshoot becoming `mediaAnchor`.

- [ ] Install v0.7.7.6 over v0.7.7.5 using the stable signing key.
- [ ] Enable Strict Safety Accessibility service.
- [ ] Set hard max to 4/15 and hold hardware Volume-Up for at least 5 seconds. Media must remain <=4.
- [ ] Drag Samsung panel repeatedly above 4. It may visually/physically transiently attempt a higher index, but the latch must force it back and must not persist the overshoot as user anchor.
- [ ] Exercise `4 -> 3 -> 2 -> 1 -> 0` Volume-Down. SoundCeiling must never raise it in response.
- [ ] Stop SoundCeiling and verify normal Samsung Volume-Up behavior returns.
- [ ] Save and inspect the log before starting v0.8 DSP work.
