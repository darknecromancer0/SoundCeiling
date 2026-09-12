# SoundCeiling v0.9.1 Accessibility Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fail-closed Samsung field APK that mutes the original Media stream, normalizes exact captured PCM, and renders one independently bounded Accessibility stream.

**Architecture:** Keep MediaProjection and source resolution in `NormalizerService`, but delegate Relay authority to a pure gate plus focused lease, volume, PCM, renderer, recovery, and runtime components. Audible output exists only after exact `PRE_VOLUME` capture survives acknowledged Media zero and the user accepts a five-second quiet probe; every invalidation neutralizes the renderer before ownership-aware stream restoration.

**Tech Stack:** Android API 29-35, Java 17, `AudioPlaybackCapture`, `AudioRecord`, streaming `AudioTrack`, `AccessibilityService`, programmatic View UI, SharedPreferences recovery record, shell source contracts, pure `javac` tests, Gradle, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-31-v091-accessibility-relay-design.md`

## Global Constraints

- Ship `versionName="0.9.1"` and `versionCode=37`, installable over signed `v0.9.0` / versionCode `36` with the unchanged development signer.
- The first field route is Samsung SM-A528B built-in speaker only; Bluetooth, wired, USB and cast routes are blocked.
- Relay requires one exact policy-allowed non-system source UID, one playback endpoint, active capturable MEDIA, and positively proven `PRE_VOLUME` capture.
- `field_quarantined_neutral_media_bypass` remains effective before every Session DSP constructor; no Session DSP, root, Shizuku, hidden API or privileged signing path may become reachable.
- Media must be acknowledged at `0` before any non-zero Relay sample is written.
- Renderer attributes are 48 kHz stereo PCM16, `MODE_STREAM`, `USAGE_ASSISTANCE_ACCESSIBILITY`, `CONTENT_TYPE_MUSIC`, and `ALLOW_CAPTURE_BY_NONE`; capture never matches Accessibility usage.
- Safe positive gain is at most `+3 dB`; explicit post-probe Full experimental gain is at most `+12 dB`; attenuation floor is `-48 dB`; every written PCM block is at or below `-6 dBFS` with zero clipped samples.
- SoundCeiling never automatically raises Media or Accessibility volume. Volume Down is immediate; Volume Up is one user-owned bounded Accessibility step only after probe confirmation.
- A user Media move exits Relay and is preserved. Every other restoration is ownership-aware and never exceeds the current Safety Maximum.
- The renderer is locally muted, paused/flushed, stopped and released before any Media restoration write.
- Relay verification expires on service restart, source transition, capture replacement, projection loss or route change.
- Captured PCM stays in memory and is never logged, saved or transmitted.
- Field acceptance requires timestamp-derived added latency with median at most `120 ms` and p95 at
  most `200 ms`; missing timestamp evidence fails the field release.
- v0.9.1 remains a draft field release; store-clean manifest and policy work are outside this implementation plan.

## File and responsibility map

**Create pure policy/core files:**

- `app/src/main/java/dev/soundceiling/app/AccessibilityRelayGate.java` — legal state transitions, epoch rejection, commands and cleanup modes.
- `app/src/main/java/dev/soundceiling/app/RelayMediaLease.java` — bounded Media-zero acknowledgement, ownership and restore decisions.
- `app/src/main/java/dev/soundceiling/app/RelayVolumePolicy.java` — Accessibility hard-cap mapping and key/slider decisions.
- `app/src/main/java/dev/soundceiling/app/RelayPreflightPolicy.java` — exact fail-closed eligibility verdict.
- `app/src/main/java/dev/soundceiling/app/RelayLatencyTracker.java` — timestamp marker matching and p50/p95 samples.
- `app/src/main/java/dev/soundceiling/app/PcmNormalizer.java` — reusable route-domain PCM gain planning and saturating conversion.
- `app/src/main/java/dev/soundceiling/app/RelayPcmDsp.java` — Relay limits and final independent block peak clamp.

**Create Android boundary files:**

- `app/src/main/java/dev/soundceiling/app/RelayRecoveryStore.java` — durable lease record and explicit recovery resolution.
- `app/src/main/java/dev/soundceiling/app/RelayOutputDomain.java` — built-in-speaker Accessibility stream bounds and finite route gain.
- `app/src/main/java/dev/soundceiling/app/AccessibilityPcmRenderer.java` — the only audible PCM sink and deterministic neutralization.
- `app/src/main/java/dev/soundceiling/app/AccessibilityRelayRuntime.java` — orchestration between the gate, lease, DSP, renderer and service epochs.
- `app/src/main/java/dev/soundceiling/app/RelayCardView.java` — reusable experimental Relay controls/status for Simple and Advanced screens.

**Modify existing runtime files:**

- `PcmShadowDsp.java` delegates math/conversion to `PcmNormalizer` but remains shadow-only.
- `PcmCaptureBackend.java` exposes monotonic capture-frame timestamps without changing capture usage policy for normal shadow mode.
- `StrictSafetyState.java`, `VolumeKeySafetyService.java`, and `volume_key_safety_service.xml` publish Relay key authority and enable Accessibility volume.
- `NormalizerService.java` wires Relay actions/lifecycle and suspends legacy Media writes while a Relay lease owns Media zero.
- `RuntimeState.java`, `RuntimeStateStore.java`, `StatusText.java`, `DiagnosticsView.java`, `SimpleModeView.java`, `AdvancedModeView.java`, and `MainActivity.java` expose truthful state and explicit controls.
- `AndroidManifest.xml`, `app/build.gradle.kts`, `.github/workflows/build-apk.yml`, `README.md`, and a v0.9.1 field checklist package the field release.

---

### Task 1: Pure Relay state and epoch gate

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/AccessibilityRelayGate.java`
- Create: `app/src/test/java/dev/soundceiling/app/V091RelayGatePureTest.java`
- Create: `scripts/run-v091-relay-gate-tests.sh`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `AccessibilityRelayGate.State`, `Event`, `Command`, `Cleanup`, `Decision`, `start(long)`, `on(Event,long,String)`, `state()`, and `epoch()`.
- Consumers: `AccessibilityRelayRuntime` in Task 7 and `RuntimeState` in Task 8.

- [ ] **Step 1: Write the failing transition and stale-epoch tests**

Create a main-style pure test with these exact cases:

```java
public final class V091RelayGatePureTest {
    public static void main(String[] args) {
        requiresEveryActivationGate();
        staleEpochCannotActivateOrAbortCurrentRelay();
        userMediaExitKeepsUserMedia();
        invalidationRestoresOnlyAfterRendererStop();
        processDeathRequiresRecovery();
        System.out.println("V091RelayGatePureTest: PASS");
    }

    private static void requiresEveryActivationGate() {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        eq(AccessibilityRelayGate.State.PREFLIGHT,
                gate.start(41L).next, "start enters preflight");
        eq(AccessibilityRelayGate.State.CAPTURE_PROVEN,
                gate.on(AccessibilityRelayGate.Event.PREFLIGHT_PASSED, 41L,
                        "preflight_passed").next, "preflight proof");
        eq(AccessibilityRelayGate.Command.SAVE_LEASE_AND_MUTE,
                gate.lastDecision().command, "capture proof requests owned mute");
        eq(AccessibilityRelayGate.State.MEDIA_MUTING,
                gate.on(AccessibilityRelayGate.Event.MEDIA_MUTE_STARTED, 41L,
                        "mute_started").next, "mute starts");
        eq(AccessibilityRelayGate.State.MEDIA_MUTED,
                gate.on(AccessibilityRelayGate.Event.MEDIA_ZERO_ACKED, 41L,
                        "zero_acked").next, "zero acknowledgement");
        eq(AccessibilityRelayGate.State.QUIET_PROBE,
                gate.on(AccessibilityRelayGate.Event.MUTED_CAPTURE_PROVEN, 41L,
                        "pcm_survived").next, "muted capture proof");
        eq(AccessibilityRelayGate.State.AWAITING_CONFIRMATION,
                gate.on(AccessibilityRelayGate.Event.PROBE_FINISHED, 41L,
                        "probe_finished").next, "probe must stop before confirmation");
        eq(AccessibilityRelayGate.State.ACTIVE,
                gate.on(AccessibilityRelayGate.Event.PROBE_ACCEPTED, 41L,
                        "one_clean_stream").next, "manual acceptance activates");
    }

    private static void staleEpochCannotActivateOrAbortCurrentRelay() {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        gate.start(41L);
        AccessibilityRelayGate.Decision stale = gate.on(
                AccessibilityRelayGate.Event.PREFLIGHT_PASSED, 40L, "stale");
        eq(AccessibilityRelayGate.State.PREFLIGHT, stale.next, "stale proof cannot advance");
        eq(AccessibilityRelayGate.Command.NONE, stale.command, "stale proof has no command");
        activate(gate, 41L);
        stale = gate.on(AccessibilityRelayGate.Event.INVALIDATED, 40L, "stale_abort");
        eq(AccessibilityRelayGate.State.ACTIVE, stale.next, "stale abort cannot stop current epoch");
    }

    private static void userMediaExitKeepsUserMedia() {
        AccessibilityRelayGate gate = activeGate(42L);
        AccessibilityRelayGate.Decision exit = gate.on(
                AccessibilityRelayGate.Event.USER_MEDIA_CHANGED, 42L, "relay_user_media_exit");
        eq(AccessibilityRelayGate.Command.NEUTRALIZE_RENDERER, exit.command,
                "user exit stops renderer first");
        eq(AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA, exit.cleanup,
                "user Media is preserved");
        AccessibilityRelayGate.Decision stopped = gate.on(
                AccessibilityRelayGate.Event.RENDERER_STOPPED, 42L, "renderer_stopped");
        eq(AccessibilityRelayGate.Command.CLEANUP, stopped.command,
                "cleanup waits for renderer stop");
        eq(AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA, stopped.cleanup,
                "cleanup still preserves user Media");
    }

    private static void invalidationRestoresOnlyAfterRendererStop() {
        AccessibilityRelayGate gate = activeGate(43L);
        AccessibilityRelayGate.Decision invalidated = gate.on(
                AccessibilityRelayGate.Event.INVALIDATED, 43L, "relay_route_changed");
        eq(AccessibilityRelayGate.Command.NEUTRALIZE_RENDERER, invalidated.command,
                "invalidation first neutralizes");
        AccessibilityRelayGate.Decision stopped = gate.on(
                AccessibilityRelayGate.Event.RENDERER_STOPPED, 43L, "renderer_stopped");
        eq(AccessibilityRelayGate.Command.CLEANUP, stopped.command,
                "restore authority appears only after stop");
        eq(AccessibilityRelayGate.Cleanup.RESTORE_OWNED, stopped.cleanup,
                "ordinary invalidation restores only owned streams");
    }

    private static void processDeathRequiresRecovery() {
        AccessibilityRelayGate gate = activeGate(44L);
        AccessibilityRelayGate.Decision died = gate.on(
                AccessibilityRelayGate.Event.PROCESS_DIED, 44L, "process_died");
        eq(AccessibilityRelayGate.State.RECOVERY_REQUIRED, died.next,
                "process death cannot restore automatically");
        eq(AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED, died.cleanup,
                "durable recovery remains pending");
    }

    private static AccessibilityRelayGate activeGate(long epoch) {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        gate.start(epoch);
        activate(gate, epoch);
        return gate;
    }

    private static void activate(AccessibilityRelayGate gate, long epoch) {
        gate.on(AccessibilityRelayGate.Event.PREFLIGHT_PASSED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.MEDIA_MUTE_STARTED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.MEDIA_ZERO_ACKED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.MUTED_CAPTURE_PROVEN, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.PROBE_FINISHED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.PROBE_ACCEPTED, epoch, "ok");
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
}
```

- [ ] **Step 2: Add the isolated runner and verify RED**

`scripts/run-v091-relay-gate-tests.sh` compiles only the gate and its test with
`javac -Xlint:all -Werror`, then runs the main class. Run:

```bash
bash scripts/run-v091-relay-gate-tests.sh
```

Expected: FAIL because `AccessibilityRelayGate` does not exist.

- [ ] **Step 3: Implement the minimal gate**

Use these exact enums and decision surface:

```java
final class AccessibilityRelayGate {
    enum State { OFF, PREFLIGHT, CAPTURE_PROVEN, MEDIA_MUTING, MEDIA_MUTED,
        QUIET_PROBE, AWAITING_CONFIRMATION, ACTIVE, ABORTING, RECOVERY_REQUIRED }
    enum Event { PREFLIGHT_PASSED, PREFLIGHT_FAILED, MEDIA_MUTE_STARTED,
        MEDIA_ZERO_ACKED, MEDIA_ZERO_FAILED, MUTED_CAPTURE_PROVEN, PROBE_FINISHED,
        PROBE_ACCEPTED, PROBE_REJECTED, USER_MEDIA_CHANGED, SOURCE_ENDED,
        INVALIDATED, RENDERER_STOPPED, PROCESS_DIED, RECOVERY_RESOLVED }
    enum Command { NONE, RUN_PREFLIGHT, SAVE_LEASE_AND_MUTE, VERIFY_MUTED_CAPTURE,
        START_QUIET_PROBE, SILENCE_AND_WAIT, START_ACTIVE_RENDERER,
        NEUTRALIZE_RENDERER, CLEANUP }
    enum Cleanup { NONE, RESTORE_OWNED, KEEP_USER_MEDIA, RECOVERY_REQUIRED }

    static final class Decision {
        final State previous, next;
        final Command command;
        final Cleanup cleanup;
        final String reason;
        Decision(State previous, State next, Command command, Cleanup cleanup, String reason) {
            this.previous = previous;
            this.next = next;
            this.command = command;
            this.cleanup = cleanup;
            this.reason = reason;
        }
    }

    private State state = State.OFF;
    private long epoch;
    private Cleanup pendingCleanup = Cleanup.NONE;
    private Decision lastDecision = new Decision(State.OFF, State.OFF,
            Command.NONE, Cleanup.NONE, "relay_off");

    private Decision move(State next, Command command, Cleanup cleanup, String reason) {
        Decision decision = new Decision(state, next, command, cleanup, reason);
        state = next;
        lastDecision = decision;
        return decision;
    }

    private Decision stay(String reason) {
        return move(state, Command.NONE, Cleanup.NONE, reason);
    }

    synchronized Decision start(long newEpoch) {
        if (state != State.OFF) return stay("relay_start_requires_off");
        epoch = newEpoch;
        pendingCleanup = Cleanup.NONE;
        return move(State.PREFLIGHT, Command.RUN_PREFLIGHT, Cleanup.NONE,
                "relay_start_requested");
    }
    synchronized Decision on(Event event, long eventEpoch, String reason) {
        if (eventEpoch != epoch) return stay("relay_stale_epoch");
        if (event == Event.PROCESS_DIED) {
            return move(State.RECOVERY_REQUIRED, Command.NONE,
                    Cleanup.RECOVERY_REQUIRED, "relay_process_died");
        }
        if (event == Event.USER_MEDIA_CHANGED) {
            pendingCleanup = Cleanup.KEEP_USER_MEDIA;
            return move(State.ABORTING, Command.NEUTRALIZE_RENDERER,
                    pendingCleanup, reason);
        }
        if (event == Event.PREFLIGHT_FAILED || event == Event.MEDIA_ZERO_FAILED
                || event == Event.PROBE_REJECTED || event == Event.SOURCE_ENDED
                || event == Event.INVALIDATED) {
            pendingCleanup = Cleanup.RESTORE_OWNED;
            return move(State.ABORTING, Command.NEUTRALIZE_RENDERER,
                    pendingCleanup, reason);
        }
        if (state == State.ABORTING && event == Event.RENDERER_STOPPED) {
            return move(State.OFF, Command.CLEANUP, pendingCleanup, reason);
        }
        if (state == State.RECOVERY_REQUIRED && event == Event.RECOVERY_RESOLVED) {
            return move(State.OFF, Command.NONE, Cleanup.NONE, reason);
        }
        if (state == State.PREFLIGHT && event == Event.PREFLIGHT_PASSED)
            return move(State.CAPTURE_PROVEN, Command.SAVE_LEASE_AND_MUTE,
                    Cleanup.NONE, reason);
        if (state == State.CAPTURE_PROVEN && event == Event.MEDIA_MUTE_STARTED)
            return move(State.MEDIA_MUTING, Command.NONE, Cleanup.NONE, reason);
        if (state == State.MEDIA_MUTING && event == Event.MEDIA_ZERO_ACKED)
            return move(State.MEDIA_MUTED, Command.VERIFY_MUTED_CAPTURE,
                    Cleanup.NONE, reason);
        if (state == State.MEDIA_MUTED && event == Event.MUTED_CAPTURE_PROVEN)
            return move(State.QUIET_PROBE, Command.START_QUIET_PROBE,
                    Cleanup.NONE, reason);
        if (state == State.QUIET_PROBE && event == Event.PROBE_FINISHED)
            return move(State.AWAITING_CONFIRMATION, Command.SILENCE_AND_WAIT,
                    Cleanup.NONE, reason);
        if (state == State.AWAITING_CONFIRMATION && event == Event.PROBE_ACCEPTED)
            return move(State.ACTIVE, Command.START_ACTIVE_RENDERER,
                    Cleanup.NONE, reason);
        return stay("relay_illegal_transition:" + state + ':' + event);
    }
    synchronized State state() { return state; }
    synchronized long epoch() { return epoch; }
    synchronized Decision lastDecision() { return lastDecision; }
}
```

Implement every legal transition from the spec explicitly. Any unlisted state/event pair returns a
no-change `Decision` whose reason concatenates `relay_illegal_transition:`, the current enum name,
another colon and the event enum name; it never grants a renderer command.

- [ ] **Step 4: Verify GREEN and add the test to the full pure suite**

Run:

```bash
bash scripts/run-v091-relay-gate-tests.sh
bash scripts/run-pure-tests.sh
```

Expected: both PASS after adding the new source/test to `run-pure-tests.sh`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/AccessibilityRelayGate.java app/src/test/java/dev/soundceiling/app/V091RelayGatePureTest.java scripts/run-v091-relay-gate-tests.sh scripts/run-pure-tests.sh
git commit -m "feat(v0.9.1): add fail-closed relay state gate"
```

### Task 2: Media lease and Accessibility volume authority

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/RelayMediaLease.java`
- Create: `app/src/main/java/dev/soundceiling/app/RelayVolumePolicy.java`
- Create: `app/src/test/java/dev/soundceiling/app/V091RelayLeaseVolumePureTest.java`
- Create: `scripts/run-v091-relay-lease-volume-tests.sh`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `RelayMediaLease.begin(long,int,int,int,long)`, `noteMuteWrite(long)`, `observeMedia(int,long)`,
  `noteAccessibilityWrite(int)`, `mayRestoreMedia(int)`, `mayRestoreAccessibility(int)`,
  `restoreMediaTarget(int)`, and immutable `Record` fields used by `RelayRecoveryStore`.
- Produces: `RelayVolumePolicy.hardMaxIndex(int,int,int)`, `probeIndex(int,int,int)`,
  `onKey(Phase,int,int,int,int,int)`, and `clampRequestedIndex(int,int,int)` used by both Android
  key and UI paths.

- [ ] **Step 1: Write failing lease and volume tests**

Cover the field races directly:

```java
RelayMediaLease lease = RelayMediaLease.begin(9L, 8, 3, 4, 1000L);
eq(RelayMediaLease.MuteAction.WRITE_ZERO, lease.nextMuteAction(1000L).action,
        "first mute write");
lease.noteMuteWrite(1000L);
eq(RelayMediaLease.MuteAction.WAIT, lease.observeMedia(0, 1050L).action,
        "zero is not stable for 100 ms");
eq(RelayMediaLease.MuteAction.ACKNOWLEDGED, lease.observeMedia(0, 1150L).action,
        "stable zero acknowledgement");
eq(RelayMediaLease.MuteAction.USER_EXIT, lease.observeMedia(7, 1160L).action,
        "non-owned Samsung panel write exits");
require(!lease.mayRestoreMedia(7), "user Media must never be overwritten");
eq(5, lease.restoreMediaTarget(5), "restore is bounded by current Safety Maximum");

eq(3, RelayVolumePolicy.hardMaxIndex(0, 15, 20), "percentage maps with floor");
eq(1, RelayVolumePolicy.probeIndex(0, 15, 3), "probe uses minimum audible step");
eq(2, RelayVolumePolicy.onKey(RelayVolumePolicy.Phase.ACTIVE,
        RelayVolumePolicy.KEY_VOLUME_DOWN, RelayVolumePolicy.ACTION_DOWN,
        3, 0, 3).targetIndex, "Volume Down is immediate");
eq(3, RelayVolumePolicy.onKey(RelayVolumePolicy.Phase.ACTIVE,
        RelayVolumePolicy.KEY_VOLUME_UP, RelayVolumePolicy.ACTION_DOWN,
        3, 0, 3).targetIndex, "Volume Up holds at hard maximum");
eq(1, RelayVolumePolicy.onKey(RelayVolumePolicy.Phase.PROBE,
        RelayVolumePolicy.KEY_VOLUME_UP, RelayVolumePolicy.ACTION_DOWN,
        1, 0, 3).targetIndex, "probe blocks Volume Up");

RelayMediaLease exhausted = RelayMediaLease.begin(10L, 8, 3, 4, 2000L);
for (int i = 0; i < 3; i++) exhausted.noteMuteWrite(2000L + i * 20L);
eq(RelayMediaLease.MuteAction.FAILED, exhausted.nextMuteAction(2060L).action,
        "three unacknowledged writes fail closed");
RelayMediaLease timedOut = RelayMediaLease.begin(11L, 8, 3, 4, 3000L);
eq(RelayMediaLease.MuteAction.FAILED, timedOut.nextMuteAction(3501L).action,
        "500 ms window is bounded");
lease.noteAccessibilityWrite(2);
require(lease.mayRestoreAccessibility(2), "owned Accessibility value may restore");
require(!lease.mayRestoreAccessibility(3), "external Accessibility change is preserved");
eq(0, RelayVolumePolicy.hardMaxIndex(15, 0, -5), "malformed range and percent clamp");
eq(15, RelayVolumePolicy.hardMaxIndex(15, 0, 105), "upper malformed range clamps");
```

These assertions cover the complete mute-write budget, timeout, Accessibility ownership and
malformed range/percentage behavior.

- [ ] **Step 2: Add the isolated runner and verify RED**

Run:

```bash
bash scripts/run-v091-relay-lease-volume-tests.sh
```

Expected: FAIL because both policy classes are missing.

- [ ] **Step 3: Implement exact lease constants and decisions**

```java
final class RelayMediaLease {
    static final int MAX_MUTE_WRITES = 3;
    static final long ACK_TIMEOUT_MS = 500L;
    static final long STABLE_ZERO_MS = 100L;

    enum MuteAction { WAIT, WRITE_ZERO, ACKNOWLEDGED, USER_EXIT, FAILED }
    static final class Decision {
        final MuteAction action;
        final String reason;
        Decision(MuteAction action, String reason) {
            this.action = action;
            this.reason = reason;
        }
    }
    static final class Record {
        final long epoch;
        final int preMediaIndex, capturedSafetyMaxIndex, preAccessibilityIndex;
        final boolean mediaZeroOwned;
        final int lastOwnedAccessibilityIndex;
        Record(long epoch, int preMediaIndex, int capturedSafetyMaxIndex,
               int preAccessibilityIndex, boolean mediaZeroOwned,
               int lastOwnedAccessibilityIndex) {
            this.epoch = epoch;
            this.preMediaIndex = preMediaIndex;
            this.capturedSafetyMaxIndex = capturedSafetyMaxIndex;
            this.preAccessibilityIndex = preAccessibilityIndex;
            this.mediaZeroOwned = mediaZeroOwned;
            this.lastOwnedAccessibilityIndex = lastOwnedAccessibilityIndex;
        }
    }

    private final long epoch, startedAtMs;
    private final int preMediaIndex, capturedSafetyMaxIndex, preAccessibilityIndex;
    private int muteWriteCount, lastOwnedAccessibilityIndex;
    private long firstZeroAtMs = -1L;
    private boolean mediaZeroOwned;

    private RelayMediaLease(long epoch, int preMediaIndex, int capturedSafetyMaxIndex,
                            int preAccessibilityIndex, long startedAtMs) {
        this.epoch = epoch;
        this.preMediaIndex = preMediaIndex;
        this.capturedSafetyMaxIndex = capturedSafetyMaxIndex;
        this.preAccessibilityIndex = preAccessibilityIndex;
        this.lastOwnedAccessibilityIndex = preAccessibilityIndex;
        this.startedAtMs = startedAtMs;
    }

    private static Decision decision(MuteAction action, String reason) {
        return new Decision(action, reason);
    }

    static RelayMediaLease begin(long epoch, int preMedia, int safetyMax,
                                 int preAccessibility, long nowMs) {
        if (epoch <= 0L || preMedia < 0 || safetyMax < 0 || preAccessibility < 0)
            throw new IllegalArgumentException("invalid relay lease");
        return new RelayMediaLease(epoch, preMedia, safetyMax, preAccessibility, nowMs);
    }
    synchronized Decision nextMuteAction(long nowMs) {
        if (mediaZeroOwned) return decision(MuteAction.ACKNOWLEDGED, "relay_media_zero_acked");
        if (nowMs - startedAtMs > ACK_TIMEOUT_MS || muteWriteCount >= MAX_MUTE_WRITES)
            return decision(MuteAction.FAILED, "relay_media_zero_not_acknowledged");
        return decision(MuteAction.WRITE_ZERO, "relay_media_zero_write");
    }
    synchronized void noteMuteWrite(long nowMs) {
        if (nowMs - startedAtMs <= ACK_TIMEOUT_MS && muteWriteCount < MAX_MUTE_WRITES)
            muteWriteCount++;
    }
    synchronized Decision observeMedia(int observedIndex, long nowMs) {
        if (mediaZeroOwned && observedIndex > 0)
            return decision(MuteAction.USER_EXIT, "relay_user_media_exit");
        if (observedIndex != 0) {
            firstZeroAtMs = -1L;
            return nextMuteAction(nowMs).action == MuteAction.FAILED
                    ? decision(MuteAction.FAILED, "relay_media_zero_not_acknowledged")
                    : decision(MuteAction.WAIT, "relay_media_zero_pending");
        }
        if (firstZeroAtMs < 0L) firstZeroAtMs = nowMs;
        if (nowMs - firstZeroAtMs < STABLE_ZERO_MS)
            return decision(MuteAction.WAIT, "relay_media_zero_stabilizing");
        mediaZeroOwned = true;
        return decision(MuteAction.ACKNOWLEDGED, "relay_media_zero_acked");
    }
    synchronized void noteAccessibilityWrite(int index) {
        lastOwnedAccessibilityIndex = Math.max(0, index);
    }
    synchronized boolean mayRestoreMedia(int observedMedia) { return mediaZeroOwned && observedMedia == 0; }
    synchronized boolean mayRestoreAccessibility(int observedAccessibility) {
        return observedAccessibility == lastOwnedAccessibilityIndex;
    }
    synchronized int restoreMediaTarget(int currentSafetyMax) {
        return Math.max(0, Math.min(preMediaIndex, currentSafetyMax));
    }
    synchronized Record record() {
        return new Record(epoch, preMediaIndex, capturedSafetyMaxIndex,
                preAccessibilityIndex, mediaZeroOwned, lastOwnedAccessibilityIndex);
    }
}
```

- [ ] **Step 4: Implement one canonical volume clamp**

```java
final class RelayVolumePolicy {
    enum Phase { OFF, PROBE, AWAITING_CONFIRMATION, ACTIVE }
    static final int KEY_VOLUME_UP = 24, KEY_VOLUME_DOWN = 25;
    static final int ACTION_DOWN = 0, ACTION_UP = 1;
    static final class Decision {
        final boolean consume, write;
        final int targetIndex;
        final String reason;
        Decision(boolean consume, boolean write, int targetIndex, String reason) {
            this.consume = consume;
            this.write = write;
            this.targetIndex = targetIndex;
            this.reason = reason;
        }
    }
    static int hardMaxIndex(int min, int max, int safetyPercent) {
        int low = Math.min(min, max), high = Math.max(min, max);
        int percent = Math.max(0, Math.min(100, safetyPercent));
        return low + (int) Math.floor((high - low) * (percent / 100d));
    }
    static int probeIndex(int min, int max, int hardMax) {
        return Math.min(Math.max(min, Math.min(max, hardMax)), Math.min(max, min + 1));
    }
    static int clampRequestedIndex(int requested, int min, int hardMax) {
        return Math.max(min, Math.min(hardMax, requested));
    }
    static Decision onKey(Phase phase, int keyCode, int action,
                          int current, int min, int hardMax) {
        if (phase == Phase.OFF || (keyCode != KEY_VOLUME_UP && keyCode != KEY_VOLUME_DOWN))
            return new Decision(false, false, current, "relay_key_not_owned");
        if (action != ACTION_DOWN)
            return new Decision(true, false, current, "relay_key_release_consumed");
        if (keyCode == KEY_VOLUME_DOWN) {
            int target = clampRequestedIndex(current - 1, min, hardMax);
            return new Decision(true, target != current, target, "relay_volume_down");
        }
        if (phase != Phase.ACTIVE)
            return new Decision(true, false, current, "relay_volume_up_blocked_before_confirm");
        int target = clampRequestedIndex(current + 1, min, hardMax);
        return new Decision(true, target != current, target, "relay_volume_up");
    }
}
```

- [ ] **Step 5: Verify dedicated and historical tests**

```bash
bash scripts/run-v091-relay-lease-volume-tests.sh
bash scripts/run-pure-tests.sh
```

Expected: all PASS; no historical user-down behavior changes yet.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/RelayMediaLease.java app/src/main/java/dev/soundceiling/app/RelayVolumePolicy.java app/src/test/java/dev/soundceiling/app/V091RelayLeaseVolumePureTest.java scripts/run-v091-relay-lease-volume-tests.sh scripts/run-pure-tests.sh
git commit -m "feat(v0.9.1): define relay stream ownership policy"
```

### Task 3: Preflight verdict and timestamp latency accounting

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/RelayPreflightPolicy.java`
- Create: `app/src/main/java/dev/soundceiling/app/RelayLatencyTracker.java`
- Create: `app/src/test/java/dev/soundceiling/app/V091RelayPreflightLatencyPureTest.java`
- Create: `scripts/run-v091-relay-preflight-latency-tests.sh`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `RelayPreflightPolicy.Input`, `Verdict evaluate(Input)`, and stable reason strings.
- Produces: `RelayLatencyTracker.noteWrite(long,long)`, `observe(long,long,int)`, and `Stats` with
  `latestMs`, `medianMs`, `p95Ms`, and `sampleCount`.
- Consumers: `AccessibilityRelayRuntime` and `AccessibilityPcmRenderer`.

- [ ] **Step 1: Write the failing preflight matrix**

Build one all-valid input, change one field per assertion, and require the exact first failure:

```java
RelayPreflightPolicy.Input valid = validInput(77L, 77L);
require(RelayPreflightPolicy.evaluate(valid).allowed, "all field gates allow preflight");
eq("relay_prevolume_not_proven", RelayPreflightPolicy.evaluate(
        new RelayPreflightPolicy.Input.Builder(valid)
                .captureReference(CaptureReferenceEstimator.Mode.UNKNOWN).build()).reason,
        "unknown domain fails closed");
eq("relay_route_unsupported", RelayPreflightPolicy.evaluate(
        new RelayPreflightPolicy.Input.Builder(valid).builtInSpeaker(false).build()).reason,
        "headphones are blocked");
eq("relay_source_not_exact", RelayPreflightPolicy.evaluate(
        new RelayPreflightPolicy.Input.Builder(valid).exactSource(false).build()).reason,
        "mixed source is blocked");
eq("relay_multiple_endpoints", RelayPreflightPolicy.evaluate(
        new RelayPreflightPolicy.Input.Builder(valid).endpointCount(2).build()).reason,
        "second endpoint is blocked");
eq("relay_spoken_accessibility_conflict", RelayPreflightPolicy.evaluate(
        new RelayPreflightPolicy.Input.Builder(valid)
                .spokenAccessibilityConflict(true).build()).reason,
        "shared speech stream is blocked");
eq("relay_recovery_required", RelayPreflightPolicy.evaluate(
        new RelayPreflightPolicy.Input.Builder(valid).recoveryPending(true).build()).reason,
        "unresolved lease blocks start");

private static RelayPreflightPolicy.Input validInput(long serviceEpoch, long projectionEpoch) {
    return new RelayPreflightPolicy.Input.Builder()
            .recoveryPending(false)
            .accessibilityConnected(true)
            .accessibilityVolumeEnabled(true)
            .spokenAccessibilityConflict(false)
            .outputDomainValid(true)
            .builtInSpeaker(true)
            .epochs(serviceEpoch, projectionEpoch)
            .targetedCapture(true)
            .exactSource(true)
            .sourcePolicy(true, false, false)
            .endpointCount(1)
            .playback(true, true)
            .captureReference(CaptureReferenceEstimator.Mode.PRE_VOLUME)
            .build();
}
```

Cover disconnected Accessibility, ineffective volume flag, invalid stream curve, stale projection
epoch, untargeted capture, system/protected/excluded source, inactive playback, failed warmup and
non-one endpoint.

- [ ] **Step 2: Write latency marker tests and verify RED**

```java
RelayLatencyTracker tracker = new RelayLatencyTracker();
tracker.noteWrite(480L, 1_000_000_000L);
tracker.noteWrite(960L, 1_010_000_000L);
tracker.observe(480L, 1_080_000_000L, 48_000);
tracker.observe(960L, 1_100_000_000L, 48_000);
RelayLatencyTracker.Stats stats = tracker.stats();
eq(2, stats.sampleCount, "two markers resolved");
near(85f, stats.medianMs, .01f, "median from monotonic timestamps");
near(90f, stats.p95Ms, .01f, "nearest-rank p95");
```

Run `bash scripts/run-v091-relay-preflight-latency-tests.sh`; expected FAIL because both classes are
missing.

- [ ] **Step 3: Implement ordered preflight checks**

`evaluate` must check in this order so telemetry is deterministic: recovery, Accessibility
connection/capability/conflict, output domain, built-in route, projection epoch, exact targeted
capture, source policy/system/protection, endpoint count, playback/warmup, then `PRE_VOLUME`.

```java
static final class Input {
    final boolean recoveryPending, accessibilityConnected, accessibilityVolumeEnabled;
    final boolean spokenAccessibilityConflict, outputDomainValid, builtInSpeaker;
    final long serviceEpoch, projectionEpoch;
    final boolean targetedCapture, exactSource, sourceAllowed, systemSource, protectedSource;
    final int endpointCount;
    final boolean playbackActive, captureWarmupConfirmed;
    final CaptureReferenceEstimator.Mode captureReference;

    private Input(Builder b) {
        recoveryPending = b.recoveryPending;
        accessibilityConnected = b.accessibilityConnected;
        accessibilityVolumeEnabled = b.accessibilityVolumeEnabled;
        spokenAccessibilityConflict = b.spokenAccessibilityConflict;
        outputDomainValid = b.outputDomainValid;
        builtInSpeaker = b.builtInSpeaker;
        serviceEpoch = b.serviceEpoch;
        projectionEpoch = b.projectionEpoch;
        targetedCapture = b.targetedCapture;
        exactSource = b.exactSource;
        sourceAllowed = b.sourceAllowed;
        systemSource = b.systemSource;
        protectedSource = b.protectedSource;
        endpointCount = b.endpointCount;
        playbackActive = b.playbackActive;
        captureWarmupConfirmed = b.captureWarmupConfirmed;
        captureReference = b.captureReference;
    }

    static final class Builder {
        private boolean recoveryPending, accessibilityConnected, accessibilityVolumeEnabled;
        private boolean spokenAccessibilityConflict, outputDomainValid, builtInSpeaker;
        private long serviceEpoch, projectionEpoch;
        private boolean targetedCapture, exactSource, sourceAllowed, systemSource, protectedSource;
        private int endpointCount;
        private boolean playbackActive, captureWarmupConfirmed;
        private CaptureReferenceEstimator.Mode captureReference =
                CaptureReferenceEstimator.Mode.UNKNOWN;
        Builder() { }
        Builder(Input source) {
            recoveryPending = source.recoveryPending;
            accessibilityConnected = source.accessibilityConnected;
            accessibilityVolumeEnabled = source.accessibilityVolumeEnabled;
            spokenAccessibilityConflict = source.spokenAccessibilityConflict;
            outputDomainValid = source.outputDomainValid;
            builtInSpeaker = source.builtInSpeaker;
            serviceEpoch = source.serviceEpoch;
            projectionEpoch = source.projectionEpoch;
            targetedCapture = source.targetedCapture;
            exactSource = source.exactSource;
            sourceAllowed = source.sourceAllowed;
            systemSource = source.systemSource;
            protectedSource = source.protectedSource;
            endpointCount = source.endpointCount;
            playbackActive = source.playbackActive;
            captureWarmupConfirmed = source.captureWarmupConfirmed;
            captureReference = source.captureReference;
        }
        Builder recoveryPending(boolean value) { recoveryPending = value; return this; }
        Builder accessibilityConnected(boolean value) { accessibilityConnected = value; return this; }
        Builder accessibilityVolumeEnabled(boolean value) { accessibilityVolumeEnabled = value; return this; }
        Builder spokenAccessibilityConflict(boolean value) { spokenAccessibilityConflict = value; return this; }
        Builder outputDomainValid(boolean value) { outputDomainValid = value; return this; }
        Builder builtInSpeaker(boolean value) { builtInSpeaker = value; return this; }
        Builder epochs(long service, long projection) { serviceEpoch = service; projectionEpoch = projection; return this; }
        Builder targetedCapture(boolean value) { targetedCapture = value; return this; }
        Builder exactSource(boolean value) { exactSource = value; return this; }
        Builder sourcePolicy(boolean allowed, boolean system, boolean protectedSourceValue) {
            sourceAllowed = allowed; systemSource = system; protectedSource = protectedSourceValue;
            return this;
        }
        Builder endpointCount(int value) { endpointCount = value; return this; }
        Builder playback(boolean active, boolean warmup) {
            playbackActive = active; captureWarmupConfirmed = warmup; return this;
        }
        Builder captureReference(CaptureReferenceEstimator.Mode value) {
            captureReference = value; return this;
        }
        Input build() { return new Input(this); }
    }
}

static Verdict evaluate(Input in) {
    if (in.recoveryPending) return deny("relay_recovery_required");
    if (!in.accessibilityConnected || !in.accessibilityVolumeEnabled)
        return deny("relay_accessibility_output_unavailable");
    if (in.spokenAccessibilityConflict) return deny("relay_spoken_accessibility_conflict");
    if (!in.outputDomainValid) return deny("relay_output_domain_unavailable");
    if (!in.builtInSpeaker) return deny("relay_route_unsupported");
    if (in.serviceEpoch != in.projectionEpoch) return deny("relay_projection_epoch_stale");
    if (!in.targetedCapture || !in.exactSource) return deny("relay_source_not_exact");
    if (!in.sourceAllowed || in.systemSource || in.protectedSource)
        return deny("relay_source_policy_blocked");
    if (in.endpointCount != 1) return deny("relay_multiple_endpoints");
    if (!in.playbackActive || !in.captureWarmupConfirmed)
        return deny("relay_capture_not_ready");
    if (in.captureReference != CaptureReferenceEstimator.Mode.PRE_VOLUME)
        return deny("relay_prevolume_not_proven");
    return new Verdict(true, "relay_preflight_passed");
}
```

- [ ] **Step 4: Implement bounded latency markers**

Keep at most 256 unresolved markers and 512 completed non-negative samples. Resolve a marker when
`presentedFramePosition >= outputEndFrame`; estimate its presentation time by offsetting the track
timestamp by `(outputEndFrame - presentedFramePosition) / sampleRate`. Use sorted copies for median
and nearest-rank p95. Invalid/non-monotonic timestamps produce no sample.

- [ ] **Step 5: Verify and commit**

```bash
bash scripts/run-v091-relay-preflight-latency-tests.sh
bash scripts/run-pure-tests.sh
git add app/src/main/java/dev/soundceiling/app/RelayPreflightPolicy.java app/src/main/java/dev/soundceiling/app/RelayLatencyTracker.java app/src/test/java/dev/soundceiling/app/V091RelayPreflightLatencyPureTest.java scripts/run-v091-relay-preflight-latency-tests.sh scripts/run-pure-tests.sh
git commit -m "feat(v0.9.1): add relay preflight and latency policy"
```

### Task 4: Reusable PCM core and independently clamped Relay DSP

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/PcmNormalizer.java`
- Create: `app/src/main/java/dev/soundceiling/app/RelayPcmDsp.java`
- Modify: `app/src/main/java/dev/soundceiling/app/PcmShadowDsp.java`
- Create: `app/src/test/java/dev/soundceiling/app/V091RelayPcmDspPureTest.java`
- Create: `scripts/run-v091-relay-pcm-tests.sh`
- Modify: `scripts/run-v090-pcm-shadow-tests.sh`
- Modify: `scripts/run-pure-tests.sh`

**Interfaces:**
- Produces: `PcmNormalizer.Limits`, `Result`,
  `process(long,short[],int,short[],float,float,float,CaptureReferenceEstimator.Mode,OutputCeilingState,ControlProfile,Limits,boolean)`,
  `appliedGainDb()`, and `reset()`.
- Produces:
  `RelayPcmDsp.process(long,short[],int,short[],float,float,float,OutputCeilingState,ControlProfile,boolean,boolean)`
  returning requested gain, applied gain, source peak, output peak, processed count, clipped count and
  reason.
- Preserves: every existing `PcmShadowDsp` signature/result and all v0.9 shadow tests.

- [ ] **Step 1: Add failing audible Relay PCM tests**

Use deterministic PCM16 blocks and assert both algorithmic correction and the independent final
boundary:

```java
RelayPcmDsp dsp = new RelayPcmDsp();
short[] quiet = constantPcm(960, 600);
short[] out = new short[quiet.length];
RelayPcmDsp.Result safe = dsp.process(1000L, quiet, quiet.length, out,
        -34f, -38f, -20f, OutputCeilingState.of(true, -20f, -20f),
        BuiltInProfiles.balanced(), false, true);
require(safe.appliedGainDb > 0f && safe.appliedGainDb <= 3.0001f,
        "safe quiet gain is positive and capped at +3 dB");
require(safe.outputPeakDbfs <= -6f + .01f, "safe block obeys -6 dBFS");
eq(0, safe.clippedSamples, "safe block cannot clip");

dsp.reset();
RelayPcmDsp.Result full = dsp.process(2000L, constantPcm(960, 100), 960, out,
        -50f, -50f, -20f, OutputCeilingState.of(true, -20f, -20f),
        BuiltInProfiles.balanced(), true, true);
require(full.appliedGainDb > 3f && full.appliedGainDb <= 12.0001f,
        "explicit full mode can exceed +3 but not +12 dB");

dsp.reset();
RelayPcmDsp.Result loud = dsp.process(3000L, constantPcm(960, 30000), 960, out,
        -.8f, -5f, -20f, OutputCeilingState.of(true, -20f, -20f),
        BuiltInProfiles.balanced(), true, true);
require(loud.appliedGainDb < 0f, "loud material attenuates");
require(loud.outputPeakDbfs <= -6f + .01f, "loud first block is clamped");
eq(0, loud.clippedSamples, "loud block cannot clip");
```

Also test inactive processing clears the complete output buffer, non-finite Accessibility route gain
fails closed, the attenuation floor is `-48 dB`, and a deliberately excessive requested gain still
leaves the converted block under `-6 dBFS`.

- [ ] **Step 2: Create the dedicated runner and verify RED**

Run:

```bash
bash scripts/run-v091-relay-pcm-tests.sh
```

Expected: FAIL because `PcmNormalizer` and `RelayPcmDsp` do not exist.

- [ ] **Step 3: Extract the existing shadow math without behavior change**

Move the controller, projection, gain clamp, PCM peak scan and saturating conversion from
`PcmShadowDsp` into this route-neutral surface:

```java
final class PcmNormalizer {
    static final class Limits {
        final float minimumGainDb, maximumPositiveGainDb, pcmPeakCeilingDbfs;
        Limits(float minimumGainDb, float maximumPositiveGainDb, float pcmPeakCeilingDbfs) {
            this.minimumGainDb = minimumGainDb;
            this.maximumPositiveGainDb = maximumPositiveGainDb;
            this.pcmPeakCeilingDbfs = pcmPeakCeilingDbfs;
        }
    }
    static final class Result {
        final boolean active;
        final float requestedGainDb, appliedGainDb, inputPeakDbfs;
        final float outputPeakDbfs, projectedOutputPeakDbfs;
        final int clippedSamples, processedSamples;
        final String reason;
    }
    synchronized Result process(long atMs, short[] input, int sampleCount, short[] output,
            float sourcePeakDbfs, float sourceLoudnessDb, float outputRouteGainDb,
            CaptureReferenceEstimator.Mode captureReference, OutputCeilingState ceilings,
            ControlProfile profile, Limits limits, boolean active) {
        validateBuffers(input, sampleCount, output);
        if (!active || sampleCount <= 0 || ceilings == null || profile == null || limits == null
                || captureReference == null
                || captureReference == CaptureReferenceEstimator.Mode.UNKNOWN
                || !Float.isFinite(sourceLoudnessDb) || !Float.isFinite(outputRouteGainDb)) {
            return reject(output, "pcm_output_domain_unavailable");
        }
        float pcmInputPeakDbfs = pcmPeakDbfs(input, sampleCount);
        float effectiveSourcePeakDbfs = Float.isFinite(sourcePeakDbfs)
                ? Math.max(sourcePeakDbfs, pcmInputPeakDbfs) : pcmInputPeakDbfs;
        if (!Float.isFinite(effectiveSourcePeakDbfs))
            return reject(output, "pcm_output_domain_unavailable");

        OutputLevelModel.Snapshot current = project(effectiveSourcePeakDbfs,
                sourceLoudnessDb, outputRouteGainDb, appliedGainDb, captureReference);
        ContinuousDspController.Decision decision = controller.update(
                atMs, current, ceilings, profile, appliedGainDb, true);
        float requested = Float.isFinite(decision.requestedGainDb)
                ? decision.requestedGainDb : appliedGainDb;
        float safe = Math.max(limits.minimumGainDb,
                Math.min(limits.maximumPositiveGainDb, requested));

        OutputLevelModel.Snapshot base = project(effectiveSourcePeakDbfs,
                sourceLoudnessDb, outputRouteGainDb, 0f, captureReference);
        float hardProjectedPeak = Math.min(profile.sourcePeakThresholdDbfs,
                limits.pcmPeakCeilingDbfs);
        if (base.outputProjectionValid && Float.isFinite(base.projectedOutputPeakDbfs))
            safe = Math.min(safe, hardProjectedPeak - base.projectedOutputPeakDbfs);
        if (Float.isFinite(pcmInputPeakDbfs))
            safe = Math.min(safe, limits.pcmPeakCeilingDbfs - pcmInputPeakDbfs);
        safe = Math.max(limits.minimumGainDb,
                Math.min(limits.maximumPositiveGainDb, safe));

        appliedGainDb = safe;
        Conversion conversion = convert(input, sampleCount, output, safe);
        OutputLevelModel.Snapshot applied = project(effectiveSourcePeakDbfs,
                sourceLoudnessDb, outputRouteGainDb, safe, captureReference);
        String reason = safe < requested - .001f
                ? "pcm_safety_clamped" : decision.reason;
        return new Result(true, requested, safe, pcmInputPeakDbfs,
                conversion.peakDbfs, applied.projectedOutputPeakDbfs,
                conversion.clippedSamples, sampleCount, reason);
    }
    synchronized float appliedGainDb() { return appliedGainDb; }
    synchronized void reset() { appliedGainDb = 0f; controller.reset(); }
}
```

`PcmShadowDsp` owns one `PcmNormalizer` and delegates with
`new Limits(-48f, OutputGainPlanner.MAX_POSITIVE_GAIN_DB, -.5f)`. It maps the generic result back to
the unchanged shadow result names. Run the historical shadow tests before adding Relay behavior.

- [ ] **Step 4: Verify the extraction preserves v0.9**

```bash
bash scripts/run-v090-pcm-shadow-tests.sh
```

Expected: PASS with identical assertions and no `AudioTrack` reference in either pure class.

- [ ] **Step 5: Implement Relay limits and a second final peak scan**

```java
final class RelayPcmDsp {
    static final float MIN_GAIN_DB = -48f;
    static final float SAFE_MAX_POSITIVE_GAIN_DB = 3f;
    static final float FULL_MAX_POSITIVE_GAIN_DB = 12f;
    static final float PCM_PEAK_CEILING_DBFS = -6f;
    private final PcmNormalizer normalizer = new PcmNormalizer();

    static final class Result {
        final boolean active;
        final float requestedGainDb, appliedGainDb, outputPeakDbfs;
        final int processedSamples, clippedSamples;
        final String reason;
        private Result(boolean active, float requestedGainDb, float appliedGainDb,
                       float outputPeakDbfs, int processedSamples, int clippedSamples,
                       String reason) {
            this.active = active;
            this.requestedGainDb = requestedGainDb;
            this.appliedGainDb = appliedGainDb;
            this.outputPeakDbfs = outputPeakDbfs;
            this.processedSamples = processedSamples;
            this.clippedSamples = clippedSamples;
            this.reason = reason;
        }
        static Result from(PcmNormalizer.Result value) {
            return new Result(value.active, value.requestedGainDb, value.appliedGainDb,
                    value.outputPeakDbfs, value.processedSamples, value.clippedSamples,
                    value.reason);
        }
        static Result from(PcmNormalizer.Result value, float appliedGainDb, float peakDbfs) {
            return new Result(true, value.requestedGainDb, appliedGainDb, peakDbfs,
                    value.processedSamples, value.clippedSamples, value.reason);
        }
        static Result rejected(String reason) {
            return new Result(false, 0f, 0f, Float.NaN, 0, 0, reason);
        }
    }

    synchronized Result process(long atMs, short[] input, int count, short[] output,
            float sourcePeakDbfs, float sourceLoudnessDb, float accessibilityRouteGainDb,
            OutputCeilingState ceilings, ControlProfile profile,
            boolean fullExperimental, boolean active) {
        float max = fullExperimental ? FULL_MAX_POSITIVE_GAIN_DB : SAFE_MAX_POSITIVE_GAIN_DB;
        PcmNormalizer.Result result = normalizer.process(atMs, input, count, output,
                sourcePeakDbfs, sourceLoudnessDb, accessibilityRouteGainDb,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, ceilings, profile,
                new PcmNormalizer.Limits(MIN_GAIN_DB, max, PCM_PEAK_CEILING_DBFS), active);
        return finalClampAndMap(result, output, count, PCM_PEAK_CEILING_DBFS);
    }

    private static Result finalClampAndMap(PcmNormalizer.Result result, short[] output,
                                            int count, float ceilingDbfs) {
        if (!result.active) return Result.from(result);
        float peak = pcmPeakDbfs(output, count);
        float extraAttenuationDb = Float.isFinite(peak) && peak > ceilingDbfs
                ? ceilingDbfs - peak : 0f;
        if (extraAttenuationDb < 0f) applyGainInPlace(output, count, extraAttenuationDb);
        float finalPeak = pcmPeakDbfs(output, count);
        if (!Float.isFinite(finalPeak) || finalPeak > ceilingDbfs + .01f
                || result.clippedSamples != 0) {
            Arrays.fill(output, 0, count, (short) 0);
            return Result.rejected("relay_pcm_final_boundary_failed");
        }
        return Result.from(result, result.appliedGainDb + extraAttenuationDb, finalPeak);
    }

    private static float pcmPeakDbfs(short[] samples, int count) {
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int magnitude = samples[i] == Short.MIN_VALUE ? 32768 : Math.abs((int) samples[i]);
            peak = Math.max(peak, magnitude);
        }
        return peak == 0 ? Float.NEGATIVE_INFINITY
                : 20f * (float) Math.log10(peak / 32768f);
    }

    private static void applyGainInPlace(short[] samples, int count, float gainDb) {
        double linear = Math.pow(10d, gainDb / 20d);
        for (int i = 0; i < count; i++) samples[i] = (short) Math.round(samples[i] * linear);
    }
    synchronized void reset() { normalizer.reset(); }
}
```

`finalClampAndMap` rescans the converted samples, applies any extra attenuation needed to reach
`-6 dBFS`, rescans again, and rejects/clears the block if a clipped sample or peak violation remains.

- [ ] **Step 6: Verify all PCM and pure suites**

```bash
bash scripts/run-v091-relay-pcm-tests.sh
bash scripts/run-v090-pcm-shadow-tests.sh
bash scripts/run-pure-tests.sh
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/PcmNormalizer.java app/src/main/java/dev/soundceiling/app/RelayPcmDsp.java app/src/main/java/dev/soundceiling/app/PcmShadowDsp.java app/src/test/java/dev/soundceiling/app/V091RelayPcmDspPureTest.java scripts/run-v091-relay-pcm-tests.sh scripts/run-v090-pcm-shadow-tests.sh scripts/run-pure-tests.sh
git commit -m "feat(v0.9.1): add bounded audible PCM normalization"
```

### Task 5: Android output domain, capture timestamps, renderer and recovery store

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/RelayOutputDomain.java`
- Create: `app/src/main/java/dev/soundceiling/app/AccessibilityPcmRenderer.java`
- Create: `app/src/main/java/dev/soundceiling/app/RelayRecoveryStore.java`
- Modify: `app/src/main/java/dev/soundceiling/app/PcmCaptureBackend.java`
- Create: `scripts/check-v091-renderer-contract.sh`

**Interfaces:**
- Produces: `RelayOutputDomain.read(AudioManager,AudioDeviceInfo,int)` returning `Snapshot` with
  `valid`, `minIndex`, `maxIndex`, `currentIndex`, `probeIndex`, `hardMaxIndex`, `routeKey`,
  `reason`, and `gainDbForIndex(int)`.
- Produces: `PcmCaptureBackend.CaptureTimestamp latestTimestamp()`.
- Produces: `AccessibilityPcmRenderer.open(AudioDeviceInfo)`, `write(short[],int,CaptureTimestamp)`,
  `health()`, `neutralize()`, and `close()`.
- Produces: `RelayRecoveryStore.save(Record)`, `load()`, `clear()`, and `hasPending()`.

- [ ] **Step 1: Write a failing source contract before Android code**

`scripts/check-v091-renderer-contract.sh` must require:

```bash
need "$RENDERER" 'AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY'
need "$RENDERER" 'AudioAttributes.CONTENT_TYPE_MUSIC'
need "$RENDERER" 'AudioAttributes.ALLOW_CAPTURE_BY_NONE'
need "$RENDERER" 'AudioTrack.MODE_STREAM'
need "$RENDERER" 'AudioTrack.WRITE_BLOCKING'
need "$RENDERER" 'AudioTrack.PERFORMANCE_MODE_LOW_LATENCY'
need "$RENDERER" 'track.setVolume(0f)'
need "$CAPTURE" 'AudioTimestamp.TIMEBASE_MONOTONIC'
reject "$CAPTURE" 'USAGE_ASSISTANCE_ACCESSIBILITY'
```

Add a Python section-order assertion that `setVolume(0f)` precedes `pause()`, which precedes
`flush()`, `stop()`, and `release()`. Run the contract; expected FAIL because the files are absent.

- [ ] **Step 2: Expose capture timestamps without changing `read` callers**

Add this immutable value and update it after every positive read:

```java
static final class CaptureTimestamp {
    final boolean valid;
    final long framePosition, nanoTime;
    CaptureTimestamp(boolean valid, long framePosition, long nanoTime) {
        this.valid = valid;
        this.framePosition = framePosition;
        this.nanoTime = nanoTime;
    }
}

private volatile CaptureTimestamp latestTimestamp = new CaptureTimestamp(false, 0L, 0L);

private void updateTimestamp() {
    AudioTimestamp timestamp = new AudioTimestamp();
    int status = record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC);
    latestTimestamp = status == AudioRecord.SUCCESS
            ? new CaptureTimestamp(true, timestamp.framePosition, timestamp.nanoTime)
            : new CaptureTimestamp(false, 0L, 0L);
}

CaptureTimestamp latestTimestamp() { return latestTimestamp; }
```

Timestamp failure is reported to Relay but does not break v0.9 shadow reads.

- [ ] **Step 3: Implement the fail-closed Accessibility output domain**

`RelayOutputDomain.read` must reject null/non-built-in devices, invalid min/max/current indices, and
a hard maximum below the minimum non-zero probe step. The hard maximum must come only from
`RelayVolumePolicy.hardMaxIndex(min,max,safetyPercent)`. Read and validate finite, monotonically
non-decreasing `getStreamVolumeDb(STREAM_ACCESSIBILITY,index,deviceType)` values for every index from
the probe index through the hard maximum; index zero may remain `-Infinity`. `gainDbForIndex(int)`
returns finite data only inside that validated audible range.

- [ ] **Step 4: Implement the only audible sink**

Create the track with the exact attributes and format, keep the requested route key, and reject a
different `getRoutedDevice()` as soon as Android reports it. `write` must:

```java
int written = track.write(pcm, 0, count, AudioTrack.WRITE_BLOCKING);
if (written != count) return WriteResult.failed("relay_renderer_partial_write", written);
totalFramesWritten += written / 2L;
latency.noteWrite(totalFramesWritten, captureTimestamp.nanoTime);
AudioTimestamp output = new AudioTimestamp();
if (track.getTimestamp(output)) {
    latency.observe(output.framePosition, output.nanoTime, PcmCaptureBackend.SAMPLE_RATE);
}
int currentUnderruns = track.getUnderrunCount();
underrunWindow.observe(currentUnderruns, SystemClock.elapsedRealtime());
return health().healthy ? WriteResult.ok(written) : WriteResult.failed(health().reason, written);
```

Open the track with local gain zero, call `play()`, and raise local gain to one only when the runtime
issues an explicit probe or active command. `neutralize()` is idempotent and follows the exact six
step order from the spec.

- [ ] **Step 5: Implement durable recovery using `SharedPreferences.commit()`**

Persist the lease before the Media mute write. Store epoch, pre-Media index, captured Safety Maximum,
pre-Accessibility index, ownership booleans and last-owned Accessibility index. Use synchronous
`commit()` and return failure to preflight if persistence fails. `clear()` is also synchronous so a
stale record cannot survive successful cleanup.

- [ ] **Step 6: Run source contracts and Android compilation**

```bash
bash scripts/check-v091-renderer-contract.sh
./gradlew --no-daemon :app:compileDebugJavaWithJavac
```

Expected: contract PASS and Android API compilation PASS. If the local Gradle distribution is not
cached, record the infrastructure failure and rely on the unchanged GitHub API 35 job only after all
local pure/source contracts pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/RelayOutputDomain.java app/src/main/java/dev/soundceiling/app/AccessibilityPcmRenderer.java app/src/main/java/dev/soundceiling/app/RelayRecoveryStore.java app/src/main/java/dev/soundceiling/app/PcmCaptureBackend.java scripts/check-v091-renderer-contract.sh
git commit -m "feat(v0.9.1): add accessibility PCM renderer boundary"
```

### Task 6: Hardware keys and shared Accessibility stream safety

**Files:**
- Modify: `app/src/main/java/dev/soundceiling/app/StrictSafetyState.java`
- Modify: `app/src/main/java/dev/soundceiling/app/VolumeKeySafetyService.java`
- Modify: `app/src/main/res/xml/volume_key_safety_service.xml`
- Create: `scripts/check-v091-accessibility-key-contract.sh`
- Modify: `app/src/test/java/dev/soundceiling/app/V091RelayLeaseVolumePureTest.java`

**Interfaces:**
- Produces: `StrictSafetyState.publishRelayKeyAuthority(Phase,int,int)`,
  `relayKeyAuthority()`, `clearRelayKeyAuthority()`, and
  `hasOtherSpokenFeedbackService(Context)`.
- Consumes: `RelayVolumePolicy.onKey(Phase,int,int,int,int,int)` and adjusts only
  `STREAM_ACCESSIBILITY` during Relay.

- [ ] **Step 1: Extend the pure key tests for complete event pairs**

Assert both ACTION_DOWN and ACTION_UP are consumed for Up and Down during Relay, ACTION_UP never
writes, Volume Down at minimum holds without an upward write, `AWAITING_CONFIRMATION` blocks Up, and
`OFF` leaves the existing Strict Safety policy in control.

- [ ] **Step 2: Write the failing Android source contract**

Require both XML and programmatic flags, and prove stream separation:

```bash
need "$XML" 'flagEnableAccessibilityVolume'
need "$SERVICE" 'AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME'
need "$SERVICE" 'RelayVolumePolicy.onKey('
need "$SERVICE" 'AudioManager.STREAM_ACCESSIBILITY'
need "$SERVICE" 'StrictSafetyState.relayKeyAuthority()'
```

The Python check extracts the Relay branch and rejects `STREAM_MUSIC` inside it; it extracts the
legacy branch and requires the existing `VolumeKeySafetyPolicy` calls unchanged. Run the contract;
expected FAIL.

- [ ] **Step 3: Add immutable shared key authority**

```java
static final class RelayKeyAuthority {
    final RelayVolumePolicy.Phase phase;
    final int minimumIndex, hardMaximumIndex;
    RelayKeyAuthority(RelayVolumePolicy.Phase phase, int minimumIndex, int hardMaximumIndex) {
        this.phase = phase;
        this.minimumIndex = minimumIndex;
        this.hardMaximumIndex = hardMaximumIndex;
    }
    boolean ownsKeys() { return phase != RelayVolumePolicy.Phase.OFF; }
}
```

Publish a new immutable volatile object per update. Clearing or Accessibility disconnect returns the
`OFF` singleton. `hasOtherSpokenFeedbackService` enumerates enabled `FEEDBACK_SPOKEN` services and
ignores SoundCeiling's own component.

- [ ] **Step 4: Route keys through the canonical policy**

In `onServiceConnected`, OR both `FLAG_REQUEST_FILTER_KEY_EVENTS` and
`FLAG_ENABLE_ACCESSIBILITY_VOLUME`. In `onKeyEvent`, check Relay authority first:

```java
StrictSafetyState.RelayKeyAuthority relay = StrictSafetyState.relayKeyAuthority();
if (relay.ownsKeys()) {
    int current = manager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY);
    RelayVolumePolicy.Decision d = RelayVolumePolicy.onKey(relay.phase,
            event.getKeyCode(), event.getAction(), current,
            relay.minimumIndex, relay.hardMaximumIndex);
    if (d.write) manager.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY,
            d.targetIndex, AudioManager.FLAG_SHOW_UI);
    return d.consume;
}
```

Do not call `STREAM_MUSIC` or debt/anchor APIs in the Relay branch. Leave the existing Strict Safety
branch byte-for-byte behaviorally equivalent outside Relay.

- [ ] **Step 5: Verify new and historical authority behavior**

```bash
bash scripts/run-v091-relay-lease-volume-tests.sh
bash scripts/check-v091-accessibility-key-contract.sh
bash scripts/run-pure-tests.sh
bash scripts/check-v0776-strict-safety-contract.sh
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/StrictSafetyState.java app/src/main/java/dev/soundceiling/app/VolumeKeySafetyService.java app/src/main/res/xml/volume_key_safety_service.xml app/src/test/java/dev/soundceiling/app/V091RelayLeaseVolumePureTest.java scripts/check-v091-accessibility-key-contract.sh
git commit -m "feat(v0.9.1): route relay keys to accessibility volume"
```

### Task 7: Relay runtime and `NormalizerService` integration

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/AccessibilityRelayRuntime.java`
- Modify: `app/src/main/java/dev/soundceiling/app/NormalizerService.java`
- Create: `scripts/check-v091-runtime-wiring-contract.sh`

**Interfaces:**
- Produces: `AccessibilityRelayRuntime.Frame`, `Snapshot`, `requestStart(long)`,
  `acceptProbe(long)`, `rejectProbe(long,String)`, `setFullExperimental(boolean)`,
  `requestVolumeIndex(int)`, `onPcmBlock(Frame,short[],int,short[])`,
  `abort(String,Cleanup)`, `onRendererStopped()`, `snapshot()`,
  `ownsMediaZero()`, and `suppressesLegacyMediaWrites()`.
- Consumes: all pure policies and Android boundaries from Tasks 1-6.
- `NormalizerService` remains the only owner of MediaProjection/capture lifecycle and contains no
  direct `AudioTrack` import or constructor.

- [ ] **Step 1: Write the failing runtime wiring contract**

Require the runtime object, every lifecycle abort, and the legacy Media-write suppression:

```bash
need "$SERVICE" 'new AccessibilityRelayRuntime('
need "$SERVICE" 'relayRuntime.onPcmBlock('
need "$SERVICE" 'relayRuntime.suppressesLegacyMediaWrites()'
need "$SERVICE" 'relayRuntime.abort("projection_stopped"'
need "$SERVICE" 'relayRuntime.abort("capture_replaced"'
need "$SERVICE" 'relayRuntime.abort("route_changed"'
need "$SERVICE" 'relayRuntime.abort("service_stop"'
need "$SERVICE" 'relayRuntime.abort("service_destroy"'
reject "$SERVICE" 'new AudioTrack'
reject "$SERVICE" 'import android.media.AudioTrack'
```

The Python portion extracts `loopPlaybackCapture()` and asserts:

1. `relayRuntime.onPcmBlock` runs after exact source/endpoint/capture policy resolution;
2. `observeVolumeAndEnforce` and `applyCoordinatorCommand` are not reachable in the branch where
   `suppressesLegacyMediaWrites()` is true;
3. the reusable Relay output buffer is allocated once outside the loop;
4. no Relay result is copied into `controlFrame()` as Session DSP authority;
5. `resetPcmShadowState` still runs on all historical lifecycle boundaries.

Run `bash scripts/check-v091-runtime-wiring-contract.sh`; expected FAIL.

- [ ] **Step 2: Implement the runtime frame and snapshot surfaces**

Use a frame with all facts already resolved by the service:

```java
static final class Frame {
    final long epoch, atMs;
    final int observedMediaIndex, safetyMaximumIndex;
    final boolean exactSource, sourceAllowed, systemSource, protectedSource;
    final boolean playbackActive, captureWarmupConfirmed, builtInSpeaker;
    final int endpointCount;
    final String sourcePackage, routeKey;
    final CaptureReferenceEstimator.Mode captureReference;
    final float sourcePeakDbfs, sourceLoudnessDb;
    final OutputCeilingState ceilings;
    final ControlProfile profile;
    final PcmCaptureBackend.CaptureTimestamp captureTimestamp;
}

static final class Snapshot {
    final long epoch;
    final AccessibilityRelayGate.State state;
    final String reason;
    final boolean audible, fullExperimental, recoveryRequired;
    final int volumeIndex, volumeHardMaximum;
    final float requestedGainDb, appliedGainDb, outputPeakDbfs;
    final long latestLatencyMs, probeRemainingMs;
}
```

The constructor receives `Context`, `AudioManager`, the initial route, and a callback that reports
state changes to `DiagnosticLog`; it creates no renderer until the gate commands one.

- [ ] **Step 3: Implement preflight and Media-zero proof in the capture cadence**

`requestStart(epoch)` only calls `gate.start(epoch)` and marks an explicit user request. On each PCM
block in `PREFLIGHT`, build `RelayPreflightPolicy.Input` from `Frame`, current Accessibility
connection/flags/conflict, `RelayOutputDomain`, and `RelayRecoveryStore`. On pass:

1. create `RelayMediaLease` from observed values;
2. synchronously persist its record;
3. signal `PREFLIGHT_PASSED` and `MEDIA_MUTE_STARTED`;
4. make only bounded lease-authorized Media-zero writes;
5. feed every observed Media value back to the lease;
6. after stable zero, collect five non-silent blocks over at least 500 ms before
   `MUTED_CAPTURE_PROVEN`.

If the proof fails or the source is silent, do not create a renderer; neutralize as a no-op, restore
owned Media, clear the record, and return to `OFF` with `relay_capture_lost_at_media_zero`.

- [ ] **Step 4: Implement probe and active rendering**

On `START_QUIET_PROBE`, open the renderer on the expected built-in route and apply the bounded probe
index. The user's explicit `Start Relay test` action authorizes this one change to the minimum
non-zero probe step, even when the prior Accessibility index was zero; after that action there is no
background volume increase. Write live processed blocks with an additional `-30 dBFS` absolute
probe clamp for at most 5000 ms. Then neutralize/release and signal `PROBE_FINISHED`.

`acceptProbe(epoch)` is valid only in `AWAITING_CONFIRMATION`, current epoch, and unchanged preflight
facts. It signals `PROBE_ACCEPTED`, opens a fresh renderer, and enables normal Relay PCM. Full mode
is false until the explicit `setFullExperimental(true)` action after acceptance. `rejectProbe`
signals `PROBE_REJECTED`, logs `relay_duplicate_or_echo_reported`, and runs owned cleanup.
`Snapshot.audible` remains false until the fresh active renderer completes its first full successful
write; probe output never sets the user-facing audible-normalization flag.

- [ ] **Step 5: Implement active invalidation and ownership-aware cleanup**

During every active block:

- observed Media above zero calls `USER_MEDIA_CHANGED`, neutralizes the renderer, preserves Media,
  and clears the lease only after stop confirmation;
- external Accessibility volume above its hard maximum neutralizes and exits without fighting it;
- source/endpoint/route/projection/Accessibility/output-domain changes call current-epoch
  `INVALIDATED`;
- inactive playback for 2000 ms calls `SOURCE_ENDED`;
- renderer partial write, three underruns in two seconds, or missing timestamps aborts; after at
  least 20 resolved markers, median above `120 ms` or p95 above `200 ms` aborts with
  `relay_latency_out_of_bounds`;
- PCM silence writes silence and does not by itself abort.

`abort` must call `renderer.neutralize()` first, signal `RENDERER_STOPPED`, then apply the gate's
cleanup mode. On ordinary owned restore, write
`min(preMediaIndex,currentSafetyMaximumIndex)` only if observed Media remains zero. Clear durable
recovery only after all owned cleanup succeeds. Restore the pre-Relay Accessibility index only when
the observed index still equals the lease's last owned value; otherwise preserve the external
change. A route-change abort restores Media only after the new route is stable and its bounded target
can be recomputed; otherwise it leaves Media zero and publishes `RECOVERY_REQUIRED`.

Every gate transition also publishes the corresponding `RelayVolumePolicy.Phase` and output bounds
through `StrictSafetyState.publishRelayKeyAuthority`; cleanup or recovery clears key authority before
legacy Strict Safety resumes.

- [ ] **Step 6: Wire actions and suppress legacy Media control**

Add service actions:

```java
static final String ACTION_RELAY_START = "dev.soundceiling.app.RELAY_START";
static final String ACTION_RELAY_ACCEPT = "dev.soundceiling.app.RELAY_ACCEPT";
static final String ACTION_RELAY_REJECT = "dev.soundceiling.app.RELAY_REJECT";
static final String ACTION_RELAY_STOP = "dev.soundceiling.app.RELAY_STOP";
static final String ACTION_RELAY_RESTORE = "dev.soundceiling.app.RELAY_RESTORE";
static final String ACTION_RELAY_VOLUME = "dev.soundceiling.app.RELAY_VOLUME";
static final String ACTION_RELAY_FULL = "dev.soundceiling.app.RELAY_FULL";
static final String EXTRA_RELAY_REQUESTED = "relay_requested";
static final String EXTRA_RELAY_EPOCH = "relay_epoch";
static final String EXTRA_RELAY_VOLUME_INDEX = "relay_volume_index";
static final String EXTRA_RELAY_FULL_ENABLED = "relay_full_enabled";
```

When starting a new projection with `EXTRA_RELAY_REQUESTED=true`, request Relay only after capture
opens. While `suppressesLegacyMediaWrites()` is true, read Media directly for lease observation but
skip `observeVolumeAndEnforce`, `HardCapLatch`, coordinator Media commands, fallback floor and debt
recovery. Strict Safety for other states remains unchanged.

- [ ] **Step 7: Verify all wiring and historical quarantine**

```bash
bash scripts/check-v091-runtime-wiring-contract.sh
bash scripts/check-v091-renderer-contract.sh
bash scripts/check-v090-runtime-wiring-contract.sh
bash scripts/check-v090-session-quarantine-contract.sh
bash scripts/run-v090-session-quarantine-tests.sh
./gradlew --no-daemon :app:compileDebugJavaWithJavac
```

Expected: every shell/pure contract PASS and Android compilation PASS or only the already-documented
unavailable Gradle distribution infrastructure failure.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/AccessibilityRelayRuntime.java app/src/main/java/dev/soundceiling/app/NormalizerService.java scripts/check-v091-runtime-wiring-contract.sh
git commit -m "feat(v0.9.1): wire fail-closed accessibility relay"
```

### Task 8: Truthful runtime state, explicit Relay UI and recovery controls

**Files:**
- Create: `app/src/main/java/dev/soundceiling/app/RelayCardView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeState.java`
- Modify: `app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java`
- Modify: `app/src/main/java/dev/soundceiling/app/StatusText.java`
- Modify: `app/src/main/java/dev/soundceiling/app/DiagnosticsView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/SimpleModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/AdvancedModeView.java`
- Modify: `app/src/main/java/dev/soundceiling/app/MainActivity.java`
- Modify: `app/src/main/java/dev/soundceiling/app/HelpText.java`
- Create: `scripts/check-v091-relay-ui-contract.sh`
- Modify: `tests/V070StatusModePureTest.java`

**Interfaces:**
- Produces:
  `RuntimeState.Builder.relay(long,String,String,boolean,boolean,boolean,int,int,float,float,float,long,long)`
  and immutable Relay telemetry fields.
- Produces: `StatusText.relay(RuntimeState)`.
- Produces: `RelayCardView.Listener` actions for start, accept, reject, stop, restore, volume target,
  and Full experimental toggle.

- [ ] **Step 1: Add failing truthful-state tests**

Extend the status pure test with exact states:

```java
RuntimeState probing = new RuntimeState.Builder().relay(41L, "QUIET_PROBE",
        "relay_quiet_probe", false, false, false, 1, 3,
        0f, 0f, -30f, 80L, 5000L).build();
contains(StatusText.relay(probing), "Тихая проба", "probe must not claim active normalization");

RuntimeState active = new RuntimeState.Builder().relay(41L, "ACTIVE",
        "relay_active", true, true, false, 2, 3,
        7f, 7f, -6f, 90L, 0L).build();
contains(StatusText.relay(active), "Relay активен", "active state is truthful");

RuntimeState recovery = new RuntimeState.Builder().relay(41L, "RECOVERY_REQUIRED",
        "relay_recovery_required", false, false, true, 0, 0,
        0f, 0f, Float.NaN, -1L, 0L).build();
contains(StatusText.relay(recovery), "нужно восстановление", "recovery is explicit");
```

Run `bash scripts/run-pure-tests.sh`; expected FAIL because the eleven-argument `relay` builder and
`StatusText.relay` do not exist.

- [ ] **Step 2: Add Relay telemetry without changing shadow truth**

Add these immutable fields and preserve them in `copyWithDiagnostics`:

```java
final long relayEpoch;
final String relayState, relayReason;
final boolean relayAudible, relayFullExperimental, relayRecoveryRequired;
final int relayVolumeIndex, relayVolumeHardMaximum;
final float relayRequestedGainDb, relayAppliedGainDb, relayOutputPeakDbfs;
final long relayLatencyMs, relayProbeRemainingMs;

Builder relay(long epoch, String state, String reason, boolean audible, boolean full,
        boolean recovery, int volume, int hardMaximum, float requestedGain,
        float appliedGain, float outputPeak, long latencyMs, long probeRemainingMs) {
    relayEpoch = Math.max(0L, epoch);
    relayState = state == null ? "OFF" : state;
    relayReason = reason == null ? "relay_off" : reason;
    relayAudible = audible && "ACTIVE".equals(relayState);
    relayFullExperimental = full && relayAudible;
    relayRecoveryRequired = recovery || "RECOVERY_REQUIRED".equals(relayState);
    relayVolumeIndex = Math.max(0, volume);
    relayVolumeHardMaximum = Math.max(0, hardMaximum);
    relayRequestedGainDb = Float.isFinite(requestedGain) ? requestedGain : 0f;
    relayAppliedGainDb = Float.isFinite(appliedGain) ? appliedGain : 0f;
    relayOutputPeakDbfs = outputPeak;
    relayLatencyMs = latencyMs < 0L ? -1L : latencyMs;
    relayProbeRemainingMs = Math.max(0L, probeRemainingMs);
    return this;
}
```

`pcmDspMode` remains `SHADOW_ONLY`; Relay has its own state and is the only field that may report
audible PCM. `RuntimeStateStore` logs `accessibility_relay_runtime` transitions without raw PCM.

- [ ] **Step 3: Build one reusable experimental Relay card**

The card contains:

- state and reason text;
- `Запустить Relay-тест` / `Остановить Relay`;
- five-second probe countdown from runtime state;
- `Один чистый тихий поток` and `Эхо / громко / не работает` only while awaiting confirmation;
- a Relay volume SeekBar bounded by `relayVolumeHardMaximum`;
- `Safe +3 dB` and `Full experimental +12 dB`, with Full disabled before acceptance;
- `Восстановить безопасный Media` only in recovery.

Define the listener exactly:

```java
interface Listener {
    void onStartRelay();
    void onAcceptProbe(long epoch);
    void onRejectProbe(long epoch);
    void onStopRelay();
    void onRestoreMedia();
    void onRelayVolume(int index);
    void onFullExperimental(boolean enabled);
}
```

Insert the same card after `StatusCardView` in Simple and Advanced screens. Do not duplicate Relay
policy in either screen.

- [ ] **Step 4: Wire explicit disclosure and projection start mode**

`MainActivity` tracks a `pendingRelayProjection` boolean. `onStartRelay()` shows a prominent dialog
stating that playback PCM stays local, original Samsung Media is temporarily set to zero, output is
played through Accessibility, and the first build supports the built-in speaker only. The positive
action:

- sends `ACTION_RELAY_START` if exact PCM service is already running;
- otherwise requests a fresh MediaProjection and starts `NormalizerService` with
  `EXTRA_RELAY_REQUESTED=true` after approval.

`RelayCardView` stores the epoch from its most recent `render` call and passes that exact value to
`onAcceptProbe(long)` or `onRejectProbe(long)`. `MainActivity` writes it to `EXTRA_RELAY_EPOCH`; all
other listener methods send the exact service actions from Task 7. Do not grant probe confirmation
in UI state; the service gate remains authoritative.

- [ ] **Step 5: Write and pass the UI/source contract**

Require every visible label, listener action, both screen insertions, disclosure text, RuntimeState
copying, diagnostics fields, and absence of claims that Shadow is audible. Run:

```bash
bash scripts/check-v091-relay-ui-contract.sh
bash scripts/check-v090-runtime-wiring-contract.sh
bash scripts/run-pure-tests.sh
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/soundceiling/app/RelayCardView.java app/src/main/java/dev/soundceiling/app/RuntimeState.java app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java app/src/main/java/dev/soundceiling/app/StatusText.java app/src/main/java/dev/soundceiling/app/DiagnosticsView.java app/src/main/java/dev/soundceiling/app/SimpleModeView.java app/src/main/java/dev/soundceiling/app/AdvancedModeView.java app/src/main/java/dev/soundceiling/app/MainActivity.java app/src/main/java/dev/soundceiling/app/HelpText.java scripts/check-v091-relay-ui-contract.sh tests/V070StatusModePureTest.java
git commit -m "feat(v0.9.1): expose explicit relay field workflow"
```

### Task 9: Release contracts, manifest, CI and Samsung checklist

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `.github/workflows/build-apk.yml`
- Modify: `README.md`
- Create: `docs/field-tests/2026-08-31-v0.9.1-samsung-relay-checklist.md`
- Create: `scripts/run-v091-accessibility-relay-tests.sh`
- Create: `scripts/check-v091-release-contract.sh`

**Interfaces:**
- Produces one immutable v0.9.1 field artifact and checklist.
- Aggregates every new v0.9.1 pure and source contract without removing historical gates.

- [ ] **Step 1: Write the failing release contract**

Require:

```bash
need "$GRADLE" 'versionCode=37'
need "$GRADLE" 'versionName="0.9.1"'
need "$MANIFEST" 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'
need "$MANIFEST" 'mediaProjection|mediaPlayback|specialUse'
need "$WORKFLOW" 'run: bash ./scripts/run-v091-accessibility-relay-tests.sh'
need "$WORKFLOW" 'run: bash ./scripts/check-v091-release-contract.sh'
need "$WORKFLOW" 'name: SoundCeiling-v0.9.1-debug-apk'
need "$CHECKLIST" 'Media 0'
need "$CHECKLIST" 'Один чистый тихий поток'
need "$CHECKLIST" 'Safety Maximum'
need "$CHECKLIST" '10 минут'
need "$README" '# Sound Ceiling for Android - v0.9.1'
```

Also require the stable signer digest and reject `android.permission.DUMP`, a non-draft/store-ready
claim, and any capture match for Accessibility usage. Run the release contract; expected FAIL.

- [ ] **Step 2: Package the Android field service correctly**

Set version code/name, add `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and set the field service type to
`mediaProjection|mediaPlayback|specialUse`. Keep `QUERY_ALL_PACKAGES` and `specialUse` explicitly
documented as field-only; do not begin the separate store-clean flavor here.

- [ ] **Step 3: Add the aggregate v0.9.1 test runner**

`scripts/run-v091-accessibility-relay-tests.sh` runs, in order:

```bash
bash "$R/scripts/run-v091-relay-gate-tests.sh"
bash "$R/scripts/run-v091-relay-lease-volume-tests.sh"
bash "$R/scripts/run-v091-relay-preflight-latency-tests.sh"
bash "$R/scripts/run-v091-relay-pcm-tests.sh"
bash "$R/scripts/check-v091-renderer-contract.sh"
bash "$R/scripts/check-v091-accessibility-key-contract.sh"
bash "$R/scripts/check-v091-runtime-wiring-contract.sh"
bash "$R/scripts/check-v091-relay-ui-contract.sh"
```

Run it and require PASS.

- [ ] **Step 4: Write the exact Samsung field checklist**

The checklist must start from v0.9.0 install-over, verify the stable signer, require built-in speaker
and a single allowed Yandex/YouTube source, then walk through:

1. fresh MediaProjection consent and Accessibility service connection;
2. exact source plus `PRE_VOLUME` log proof;
3. Media-zero proof with continued PCM;
4. five-second quiet probe and one-stream user confirmation;
5. quiet/loud/peak comparison in Safe and explicit Full modes;
6. target above Safety Maximum and Relay slider above cap attempts;
7. repeated hardware Up/Down and Samsung Media-panel movement;
8. Stop, source end, projection revoke, Accessibility disable and route-change aborts;
9. forced process death and explicit recovery;
10. ten-minute stability/latency run and log export.

The final log must contain at least 20 resolved timestamp markers, median added latency no greater
than `120 ms`, and p95 no greater than `200 ms`.

State prominently: stop immediately and do not retest if echo, recursion, a high-volume transient,
full-volume lock or failed Media restoration occurs.

- [ ] **Step 5: Update CI and truthful README**

Add the aggregate runner and release contract before SDK installation. Rename source snapshot and APK
artifacts to v0.9.1. README must describe Relay as an experimental field path, retain Session DSP
quarantine and Shadow-only truth, state built-in-speaker-only scope, and avoid store-readiness claims.

- [ ] **Step 6: Run all release contracts and commit**

```bash
bash scripts/run-v091-accessibility-relay-tests.sh
bash scripts/check-v091-release-contract.sh
bash scripts/check-v090-release-contract.sh
bash scripts/check-stable-debug-signing-contract.sh
git diff --check
git add app/build.gradle.kts app/src/main/AndroidManifest.xml .github/workflows/build-apk.yml README.md docs/field-tests/2026-08-31-v0.9.1-samsung-relay-checklist.md scripts/run-v091-accessibility-relay-tests.sh scripts/check-v091-release-contract.sh
git commit -m "chore(v0.9.1): package Samsung relay field build"
```

Expected: all listed checks PASS before commit.

### Task 10: Full verification, immutable CI build and artifact handoff

**Files:**
- Modify only if a verification failure proves a defect in an already-planned file.
- Produce: CI artifact `SoundCeiling-v0.9.1-debug-apk`, SHA-256, signer evidence, and immutable commit.

**Interfaces:**
- Consumes every task result.
- Produces the single APK the user may install over v0.9.0 and the corresponding field-test link.

- [ ] **Step 1: Run the complete local regression surface**

```bash
bash scripts/run-pure-tests.sh
bash scripts/run-v091-accessibility-relay-tests.sh
bash scripts/check-v091-release-contract.sh
bash scripts/check-v090-release-contract.sh
bash scripts/check-v090-runtime-wiring-contract.sh
bash scripts/check-v090-session-quarantine-contract.sh
bash scripts/check-source-invariants.sh
bash scripts/check-stable-debug-signing-contract.sh
git diff --check
git status --short
```

Expected: every command PASS and the tree clean. Run every other tracked `scripts/check-*.sh` and
`scripts/run-*.sh` once as the final historical sweep; zero failures are allowed.

- [ ] **Step 2: Compile/package locally when the cached Gradle distribution permits**

```bash
./gradlew --no-daemon --stacktrace :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If network-restricted Gradle distribution download is the only failure,
record it without classifying tests as passed and use the GitHub job for Android API 35 evidence.

- [ ] **Step 3: Create one immutable release checkpoint**

```bash
git status --short
git rev-parse HEAD
git rev-parse HEAD^{tree}
```

If any verification-only fix was needed, rerun Step 1 and commit it with a precise `fix(v0.9.1):`
message. Do not amend or rewrite existing history.

- [ ] **Step 4: Push the existing PR #8 branch and wait for its exact CI run**

Push `feature/v0.7-adaptive-envelope` as a fast-forward without creating a new PR. If ordinary Git
credentials are unavailable, use the already-authorized GitHub connector to create equivalent
fast-forward commits in order. Confirm remote HEAD tree equals the verified local tree before
accepting CI.

- [ ] **Step 5: Verify CI evidence rather than only its overall badge**

Require success for:

- all historical and v0.9.1 pure/source contracts;
- Android SDK 35 install and `assembleDebug`;
- stable signer digest
  `5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2`;
- checksum generation;
- both v0.9.1 APK and checksum artifact uploads.

- [ ] **Step 6: Download and independently verify the APK**

```bash
sha256sum SoundCeiling-v0.9.1-debug-apk/app-debug.apk
"${ANDROID_HOME}/build-tools/35.0.0/apksigner" verify --print-certs SoundCeiling-v0.9.1-debug-apk/app-debug.apk
```

Expected: local SHA-256 equals the CI checksum and the signer digest equals the pinned value above.
Keep the downloaded immutable artifact unchanged for handoff.

- [ ] **Step 7: Handoff with explicit safety limits**

Provide one APK link, the exact commit, APK SHA-256, signer SHA-256 and the v0.9.1 Samsung checklist.
State that the APK is built-in-speaker-only and experimental; the user must stop at the first echo,
high-volume transient, lock or restore failure. Keep PR #8 draft until the field log proves every
acceptance item in the spec.
