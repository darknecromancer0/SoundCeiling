package dev.soundceiling.app;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.SystemClock;

import java.util.Arrays;
import java.util.Locale;

/**
 * Owns one fail-closed Accessibility Relay epoch. The service supplies resolved facts and PCM;
 * this class alone owns the Media-zero lease, audible renderer and recovery ordering.
 */
final class AccessibilityRelayRuntime implements AutoCloseable {
    private static final long MEDIA_MUTE_RETRY_MS = 150L;
    private static final int MUTED_PROOF_BLOCKS = 5;
    private static final long MUTED_PROOF_WINDOW_MS = 500L;
    private static final long MUTED_CAPTURE_PROOF_TIMEOUT_MS = 2_000L;
    private static final long QUIET_PROBE_MS = 5_000L;
    private static final float QUIET_PROBE_PEAK_DBFS = -30f;
    private static final long SOURCE_END_GRACE_MS = 2_000L;
    private static final long OUTPUT_DOMAIN_RECHECK_MS = 500L;
    private static final long ACCESSIBILITY_RECHECK_MS = 1_000L;
    private static final int LATENCY_MIN_SAMPLES = 20;
    private static final float LATENCY_MEDIAN_LIMIT_MS = 120f;
    private static final float LATENCY_P95_LIMIT_MS = 200f;

    interface Listener {
        boolean onRelaySnapshot(Snapshot snapshot);
    }

    static final class Frame {
        final long epoch;
        final long atMs;
        final int observedMediaIndex;
        final int safetyMaximumIndex;
        final boolean targetedCapture;
        final boolean exactSource;
        final boolean sourceAllowed;
        final boolean systemSource;
        final boolean protectedSource;
        final boolean playbackActive;
        final boolean captureWarmupConfirmed;
        final boolean builtInSpeaker;
        final int endpointCount;
        final int sourceUid;
        final String sourcePackage;
        final String routeKey;
        final CaptureReferenceEstimator.Mode captureReference;
        final float sourcePeakDbfs;
        final float sourceLoudnessDb;
        final OutputCeilingState ceilings;
        final ControlProfile profile;
        final PcmCaptureBackend.CaptureTimestamp captureTimestamp;
        final RelayGenerationToken generations;
        final boolean rendererOwnershipProven;

        Frame(long epoch, long atMs, int observedMediaIndex,
                int safetyMaximumIndex, boolean targetedCapture,
                boolean exactSource, boolean sourceAllowed,
                boolean systemSource, boolean protectedSource,
                boolean playbackActive, boolean captureWarmupConfirmed,
                boolean builtInSpeaker, int endpointCount, int sourceUid,
                String sourcePackage, String routeKey,
                CaptureReferenceEstimator.Mode captureReference,
                float sourcePeakDbfs, float sourceLoudnessDb,
                OutputCeilingState ceilings, ControlProfile profile,
                PcmCaptureBackend.CaptureTimestamp captureTimestamp,
                RelayGenerationToken generations,
                boolean rendererOwnershipProven) {
            this.epoch = epoch;
            this.atMs = Math.max(0L, atMs);
            this.observedMediaIndex = observedMediaIndex;
            this.safetyMaximumIndex = Math.max(0, safetyMaximumIndex);
            this.targetedCapture = targetedCapture;
            this.exactSource = exactSource;
            this.sourceAllowed = sourceAllowed;
            this.systemSource = systemSource;
            this.protectedSource = protectedSource;
            this.playbackActive = playbackActive;
            this.captureWarmupConfirmed = captureWarmupConfirmed;
            this.builtInSpeaker = builtInSpeaker;
            this.endpointCount = Math.max(0, endpointCount);
            this.sourceUid = sourceUid;
            this.sourcePackage = sourcePackage == null ? "" : sourcePackage;
            this.routeKey = routeKey == null ? "" : routeKey;
            this.captureReference = captureReference == null
                    ? CaptureReferenceEstimator.Mode.UNKNOWN
                    : captureReference;
            this.sourcePeakDbfs = sourcePeakDbfs;
            this.sourceLoudnessDb = sourceLoudnessDb;
            this.ceilings = ceilings;
            this.profile = profile;
            this.captureTimestamp = captureTimestamp;
            this.generations = generations;
            this.rendererOwnershipProven = rendererOwnershipProven;
        }
    }

    static final class Snapshot {
        final long epoch;
        final AccessibilityRelayGate.State state;
        final String reason;
        final boolean audible;
        final boolean fullExperimental;
        final boolean recoveryRequired;
        final int volumeIndex;
        final int volumeHardMaximum;
        final float requestedGainDb;
        final float appliedGainDb;
        final float outputPeakDbfs;
        final long latestLatencyMs;
        final long probeRemainingMs;

        Snapshot(long epoch, AccessibilityRelayGate.State state,
                String reason, boolean audible, boolean fullExperimental,
                boolean recoveryRequired, int volumeIndex,
                int volumeHardMaximum, float requestedGainDb,
                float appliedGainDb, float outputPeakDbfs,
                long latestLatencyMs, long probeRemainingMs) {
            this.epoch = Math.max(0L, epoch);
            this.state = state;
            this.reason = reason == null ? "relay_off" : reason;
            this.audible = audible
                    && state == AccessibilityRelayGate.State.ACTIVE;
            this.fullExperimental = fullExperimental && this.audible;
            this.recoveryRequired = recoveryRequired
                    || state == AccessibilityRelayGate.State.RECOVERY_REQUIRED;
            this.volumeIndex = Math.max(0, volumeIndex);
            this.volumeHardMaximum = Math.max(0, volumeHardMaximum);
            this.requestedGainDb = Float.isFinite(requestedGainDb)
                    ? requestedGainDb : 0f;
            this.appliedGainDb = Float.isFinite(appliedGainDb)
                    ? appliedGainDb : 0f;
            this.outputPeakDbfs = outputPeakDbfs;
            this.latestLatencyMs = latestLatencyMs < 0L
                    ? -1L : latestLatencyMs;
            this.probeRemainingMs = Math.max(0L, probeRemainingMs);
        }
    }

    private final Context context;
    private final AudioManager audio;
    private final AudioDeviceInfo expectedDevice;
    private final String expectedRouteKey;
    private final Listener listener;
    private final HybridRuntimeResolver playbackResolver;
    private final AccessibilityRelayGate gate =
            new AccessibilityRelayGate();
    private final RelayRecoveryStore recovery;
    private final RelayPcmDsp dsp = new RelayPcmDsp();

    private RelayMediaLease lease;
    private RelayOutputDomain.Snapshot outputDomain;
    private AccessibilityPcmRenderer renderer;
    private boolean rendererOwnershipClaimed;
    private Frame lastFrame;
    private short[] lastOutputBuffer;
    private String expectedSourcePackage = "";
    private int expectedSourceUid = -1;
    private String reason = "relay_off";
    private boolean audible;
    private boolean fullExperimental;
    private boolean recoveryRequired;
    private int mutedProofBlocks;
    private long mutedProofFirstMs;
    private long mediaZeroAckedAtMs;
    private long lastMuteWriteMs = Long.MIN_VALUE;
    private long probeDeadlineMs;
    private long inactiveSinceMs;
    private long lastOutputDomainCheckMs;
    private long lastAccessibilityCheckMs;
    private long lastRelayKeyWriteSequence;
    private float requestedGainDb;
    private float appliedGainDb;
    private float outputPeakDbfs = Float.NaN;
    private long latestLatencyMs = -1L;
    private RelayGenerationToken expectedGenerations;
    private String lastGainTelemetryState = "";
    private String lastLatencyTelemetryState = "";
    private boolean lastListenerAccepted = true;
    private Snapshot snapshot;

    AccessibilityRelayRuntime(Context context, AudioManager audio,
            AudioDeviceInfo expectedDevice,
            HybridRuntimeResolver playbackResolver, Listener listener) {
        if (context == null) throw new IllegalArgumentException("context == null");
        if (audio == null) throw new IllegalArgumentException("audio == null");
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
        this.audio = audio;
        this.expectedDevice = expectedDevice;
        if (playbackResolver == null) {
            throw new IllegalArgumentException("playbackResolver == null");
        }
        this.playbackResolver = playbackResolver;
        expectedRouteKey = DeviceDetector.key(expectedDevice);
        this.listener = listener;
        recovery = new RelayRecoveryStore(this.context);
        recoveryRequired = recovery.hasPending();
        lastRelayKeyWriteSequence =
                StrictSafetyState.relayAccessibilityWrite().sequence;
        snapshot = buildSnapshot(SystemClock.elapsedRealtime());
    }

    synchronized Snapshot requestStart(long epoch,
            RelayGenerationToken generations) {
        if (epoch <= 0L || generations == null
                || !generations.valid()) {
            return publish("relay_invalid_epoch");
        }
        if (recovery.hasPending() || recoveryRequired) {
            recoveryRequired = true;
            StrictSafetyState.clearRelayKeyAuthority();
            return publish("relay_recovery_required");
        }
        if (!neutralizeRenderer()) {
            recoveryRequired = true;
            StrictSafetyState.clearRelayKeyAuthority();
            return publish("relay_recovery_required:"
                    + "relay_renderer_stop_unconfirmed");
        }
        AccessibilityRelayGate.Decision decision = gate.start(epoch);
        if (decision.next != AccessibilityRelayGate.State.PREFLIGHT) {
            return publish(decision.reason);
        }
        resetEpochState();
        expectedGenerations = generations;
        return publish(decision.reason);
    }

    synchronized Snapshot acceptProbe(long epoch) {
        if (recoveryRequired || gate.state()
                != AccessibilityRelayGate.State.AWAITING_CONFIRMATION
                || epoch != gate.epoch()) {
            return publish("relay_probe_accept_stale");
        }
        long now = SystemClock.elapsedRealtime();
        String invalid = lastFrame == null || now - lastFrame.atMs > 1_000L
                ? "relay_capture_not_ready"
                : invalidCommonFact(lastFrame, true);
        int observedMedia = readMediaIndex();
        if (invalid != null || observedMedia != 0) {
            String failure = invalid != null ? invalid
                    : observedMedia < 0
                            ? "relay_media_volume_unavailable"
                            : "relay_user_media_exit";
            AccessibilityRelayGate.Cleanup cleanup = invalid != null
                    ? AccessibilityRelayGate.Cleanup.RESTORE_OWNED
                    : observedMedia < 0
                            ? AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED
                            : AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA;
            abort(failure, cleanup);
            return snapshot;
        }
        AccessibilityRelayGate.Decision decision = gate.on(
                AccessibilityRelayGate.Event.PROBE_ACCEPTED, epoch,
                "relay_probe_accepted");
        if (decision.command
                != AccessibilityRelayGate.Command.START_ACTIVE_RENDERER) {
            return publish(decision.reason);
        }
        if (!publishForRendererStart(
                "relay_active_renderer_starting")) return snapshot;
        fullExperimental = false;
        dsp.reset();
        if (!openRenderer(false)) return snapshot;
        return publish(decision.reason);
    }

    synchronized Snapshot rejectProbe(long epoch, String detail) {
        if (gate.state()
                != AccessibilityRelayGate.State.AWAITING_CONFIRMATION
                || epoch != gate.epoch()) {
            return publish("relay_probe_reject_stale");
        }
        DiagnosticLog.event("relay_duplicate_or_echo_reported",
                "epoch=" + epoch + " detail=" + safe(detail));
        AccessibilityRelayGate.Decision decision = gate.on(
                AccessibilityRelayGate.Event.PROBE_REJECTED, epoch,
                "relay_duplicate_or_echo_reported");
        finishAbort(decision);
        return snapshot;
    }

    synchronized Snapshot setFullExperimental(boolean enabled) {
        if (gate.state() != AccessibilityRelayGate.State.ACTIVE
                || !audible) {
            fullExperimental = false;
            return publish("relay_full_requires_active_output");
        }
        fullExperimental = enabled;
        dsp.reset();
        return publish(enabled ? "relay_full_enabled"
                : "relay_safe_enabled");
    }

    synchronized Snapshot requestVolumeIndex(int requestedIndex) {
        AccessibilityRelayGate.State state = gate.state();
        if (outputDomain == null || !outputDomain.valid
                || (state != AccessibilityRelayGate.State.QUIET_PROBE
                        && state != AccessibilityRelayGate.State.AWAITING_CONFIRMATION
                        && state != AccessibilityRelayGate.State.ACTIVE)) {
            return publish("relay_volume_not_available");
        }
        int current = readAccessibilityIndex();
        if (current < outputDomain.minIndex) {
            abort("relay_accessibility_output_unavailable",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return snapshot;
        }
        int target = RelayVolumePolicy.clampRequestedIndex(requestedIndex,
                outputDomain.minIndex, outputDomain.hardMaxIndex);
        if (state != AccessibilityRelayGate.State.ACTIVE
                && target > current) {
            return publish("relay_volume_up_blocked_before_confirm");
        }
        if (target > current) target = Math.min(target, current + 1);
        if (!writeAccessibilityIndex(target, false)) return snapshot;
        return publish("relay_volume_user_request");
    }

    synchronized Snapshot onPcmBlock(Frame frame, short[] input, int count,
            short[] output) {
        if (output != null) Arrays.fill(output, (short) 0);
        lastOutputBuffer = output;
        if (frame == null || input == null || output == null || count <= 0
                || count > input.length || count > output.length) {
            if (gate.state() != AccessibilityRelayGate.State.OFF) {
                abort("relay_pcm_buffer_invalid",
                        AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            }
            return snapshot();
        }
        lastFrame = frame;
        if (recovery.hasPending() && gate.state()
                == AccessibilityRelayGate.State.OFF) {
            recoveryRequired = true;
            return publish(reason);
        }
        if (gate.state() == AccessibilityRelayGate.State.OFF) {
            return snapshot();
        }
        if (frame.epoch != gate.epoch()) {
            abort("capture_replaced",
                    ownsMediaZero()
                            ? AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED
                            : AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return snapshot;
        }
        syncRelayKeyWrite();

        if (gate.state() == AccessibilityRelayGate.State.PREFLIGHT) {
            runPreflight(frame);
        }
        if (gate.state() == AccessibilityRelayGate.State.MEDIA_MUTING) {
            advanceMediaMute(frame);
        }
        if (gate.state() == AccessibilityRelayGate.State.MEDIA_MUTED) {
            advanceMutedCaptureProof(frame);
            return snapshot();
        }
        if (gate.state() == AccessibilityRelayGate.State.QUIET_PROBE
                || gate.state() == AccessibilityRelayGate.State.ACTIVE) {
            render(frame, input, count, output);
        } else if (gate.state()
                == AccessibilityRelayGate.State.AWAITING_CONFIRMATION) {
            String invalid = invalidCommonFact(frame, true);
            if (invalid != null) {
                abort(invalid, AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            } else {
                if (!observeAccessibilityOwnership(
                        readAccessibilityIndex())) return snapshot();
                observeOwnedMedia(frame);
            }
        }
        return snapshot();
    }

    synchronized Snapshot abort(String abortReason,
            AccessibilityRelayGate.Cleanup cleanup) {
        String safeReason = abortReason == null || abortReason.isEmpty()
                ? "relay_invalidated" : abortReason;
        if (cleanup == AccessibilityRelayGate.Cleanup.RESTORE_OWNED
                && ownsMediaZero() && isRouteChange(safeReason)) {
            cleanup = AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED;
        }
        if (gate.state() == AccessibilityRelayGate.State.OFF) {
            StrictSafetyState.clearRelayKeyAuthority();
            if (recovery.hasPending()) recoveryRequired = true;
            return publish(safeReason);
        }
        if (cleanup == AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED) {
            boolean stopped = neutralizeRenderer();
            StrictSafetyState.clearRelayKeyAuthority();
            recoveryRequired = true;
            audible = false;
            gate.on(AccessibilityRelayGate.Event.PROCESS_DIED,
                    gate.epoch(), stopped ? safeReason
                            : "relay_renderer_stop_unconfirmed");
            return publish("relay_recovery_required:"
                    + (stopped ? safeReason
                            : "relay_renderer_stop_unconfirmed"));
        }

        AccessibilityRelayGate.Event event;
        if (cleanup == AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA) {
            event = AccessibilityRelayGate.Event.USER_MEDIA_CHANGED;
        } else if ("source_ended".equals(safeReason)) {
            event = AccessibilityRelayGate.Event.SOURCE_ENDED;
        } else if (gate.state() == AccessibilityRelayGate.State.PREFLIGHT) {
            event = AccessibilityRelayGate.Event.PREFLIGHT_FAILED;
        } else if (gate.state()
                == AccessibilityRelayGate.State.MEDIA_MUTING
                && safeReason.contains("media_zero")) {
            event = AccessibilityRelayGate.Event.MEDIA_ZERO_FAILED;
        } else {
            event = AccessibilityRelayGate.Event.INVALIDATED;
        }
        AccessibilityRelayGate.Decision decision = gate.on(
                event, gate.epoch(), safeReason);
        finishAbort(decision);
        return snapshot;
    }

    synchronized Snapshot onRendererStopped() {
        if (gate.state() != AccessibilityRelayGate.State.ABORTING) {
            return publish(reason);
        }
        AccessibilityRelayGate.Decision stopped = gate.on(
                AccessibilityRelayGate.Event.RENDERER_STOPPED,
                gate.epoch(), "relay_renderer_stopped");
        if (stopped.command == AccessibilityRelayGate.Command.CLEANUP) {
            performCleanup(stopped.cleanup);
        }
        return snapshot;
    }

    synchronized Snapshot restoreSafeMedia() {
        if (!neutralizeRenderer()) {
            StrictSafetyState.clearRelayKeyAuthority();
            recoveryRequired = true;
            return publish("relay_recovery_required:"
                    + "relay_renderer_stop_unconfirmed");
        }
        StrictSafetyState.clearRelayKeyAuthority();
        RelayMediaLease.Record record = recovery.load();
        if (record == null) {
            if (recovery.hasPending()) {
                recoveryRequired = true;
                return publish("relay_recovery_record_invalid");
            }
            recoveryRequired = false;
            return publish("relay_recovery_not_required");
        }
        int observed = readMediaIndex();
        boolean restored = observed >= 0;
        if (observed == 0 && record.mediaZeroOwned) {
            int currentSafety = StrictSafetyState.hardMaxIndex(context, audio);
            int target = Math.max(0,
                    Math.min(record.preMediaIndex, currentSafety));
            restored = writeAndVerify(AudioManager.STREAM_MUSIC, target, 0);
        } else if (observed == 0 && record.preMediaIndex > 0) {
            // Zero was never durably acknowledged as ours; raising it could
            // override a user mute. The user must establish a non-zero Media
            // value before the recovery record may be cleared.
            restored = false;
        }
        if (restored) restored = restoreOwnedAccessibility(record);
        if (restored) restored = recovery.clear();
        recoveryRequired = !restored;
        if (restored) {
            if (gate.state()
                    == AccessibilityRelayGate.State.RECOVERY_REQUIRED) {
                gate.on(AccessibilityRelayGate.Event.RECOVERY_RESOLVED,
                        gate.epoch(), "relay_recovery_resolved");
            }
            lease = null;
            outputDomain = null;
        }
        return publish(restored ? "relay_recovery_resolved"
                : "relay_recovery_required");
    }

    synchronized Snapshot snapshot() {
        if (snapshot == null) {
            snapshot = buildSnapshot(SystemClock.elapsedRealtime());
        }
        return snapshot;
    }

    synchronized boolean ownsMediaZero() {
        return lease != null && lease.record().mediaZeroOwned;
    }

    synchronized boolean suppressesLegacyMediaWrites() {
        AccessibilityRelayGate.State state = gate.state();
        return recoveryRequired || recovery.hasPending() || lease != null
                || state == AccessibilityRelayGate.State.CAPTURE_PROVEN
                || state == AccessibilityRelayGate.State.MEDIA_MUTING
                || state == AccessibilityRelayGate.State.MEDIA_MUTED
                || state == AccessibilityRelayGate.State.QUIET_PROBE
                || state == AccessibilityRelayGate.State.AWAITING_CONFIRMATION
                || state == AccessibilityRelayGate.State.ACTIVE
                || state == AccessibilityRelayGate.State.ABORTING
                || state == AccessibilityRelayGate.State.RECOVERY_REQUIRED;
    }

    private void runPreflight(Frame frame) {
        outputDomain = RelayOutputDomain.read(audio, expectedDevice,
                safetyPercent(frame.profile));
        lastOutputDomainCheckMs = frame.atMs;
        boolean accessibilityReady =
                StrictSafetyState.accessibilityConnected()
                && StrictSafetyState.isAccessibilityServiceEnabled(context);
        RelayPreflightPolicy.Input input =
                new RelayPreflightPolicy.Input.Builder()
                        .recoveryPending(recovery.hasPending())
                        .accessibilityConnected(accessibilityReady)
                        .accessibilityVolumeEnabled(
                                StrictSafetyState.accessibilityVolumeEnabled(
                                        context))
                        .keyFilterCapable(
                                StrictSafetyState.keyFilterCapable())
                        .spokenAccessibilityConflict(
                                StrictSafetyState
                                        .hasOtherSpokenFeedbackService(context))
                        .outputDomainValid(outputDomain.valid)
                        .builtInSpeaker(frame.builtInSpeaker)
                        .epochs(frame.epoch, gate.epoch())
                        .generations(expectedGenerations,
                                frame.generations)
                        .targetedCapture(frame.targetedCapture)
                        .exactSource(frame.exactSource)
                        .sourcePolicy(frame.sourceAllowed,
                                frame.systemSource, frame.protectedSource)
                        .endpointCount(frame.endpointCount)
                        .playback(frame.playbackActive,
                                frame.captureWarmupConfirmed)
                        .captureReference(frame.captureReference)
                        .build();
        RelayPreflightPolicy.Verdict verdict =
                RelayPreflightPolicy.evaluate(input);
        if (!verdict.allowed) {
            abort(verdict.reason,
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }

        int accessibility = readAccessibilityIndex();
        if (accessibility < outputDomain.minIndex
                || accessibility > outputDomain.maxIndex) {
            abort("relay_accessibility_output_unavailable",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        try {
            lease = RelayMediaLease.begin(gate.epoch(),
                    frame.observedMediaIndex, frame.safetyMaximumIndex,
                    accessibility, frame.atMs, expectedGenerations);
        } catch (IllegalArgumentException invalid) {
            abort("relay_lease_invalid",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (!recovery.save(lease.record())) {
            lease = null;
            abort("relay_recovery_persist_failed",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        expectedSourceUid = frame.sourceUid;
        expectedSourcePackage = frame.sourcePackage;
        AccessibilityRelayGate.Decision proven = gate.on(
                AccessibilityRelayGate.Event.PREFLIGHT_PASSED,
                gate.epoch(), "relay_preflight_passed");
        if (proven.command
                != AccessibilityRelayGate.Command.SAVE_LEASE_AND_MUTE) {
            abort("relay_preflight_transition_failed",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        gate.on(AccessibilityRelayGate.Event.MEDIA_MUTE_STARTED,
                gate.epoch(), "relay_media_mute_started");
        publish("relay_media_mute_started");
    }

    private void advanceMediaMute(Frame frame) {
        if (lease == null) {
            abort("relay_lease_missing",
                    AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
            return;
        }
        String invalid = invalidCommonFact(frame, true);
        if (invalid != null) {
            abort(invalid, AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        RelayMediaLease.Decision observed = lease.observeMedia(
                frame.observedMediaIndex, frame.atMs);
        if (observed.action == RelayMediaLease.MuteAction.ACKNOWLEDGED) {
            if (!recovery.save(lease.record())) {
                abort("relay_recovery_persist_failed",
                        AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
                return;
            }
            gate.on(AccessibilityRelayGate.Event.MEDIA_ZERO_ACKED,
                    gate.epoch(), "relay_media_zero_acked");
            mutedProofBlocks = 0;
            mutedProofFirstMs = 0L;
            mediaZeroAckedAtMs = frame.atMs;
            publish("relay_media_zero_acked");
            return;
        }
        if (observed.action == RelayMediaLease.MuteAction.FAILED) {
            abort(observed.reason,
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (frame.observedMediaIndex == 0) return;
        if (lastMuteWriteMs != Long.MIN_VALUE
                && frame.atMs - lastMuteWriteMs < MEDIA_MUTE_RETRY_MS) {
            return;
        }
        RelayMediaLease.Decision next = lease.nextMuteAction(frame.atMs);
        if (next.action == RelayMediaLease.MuteAction.FAILED) {
            abort(next.reason,
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (next.action == RelayMediaLease.MuteAction.WRITE_ZERO) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                lease.noteMuteWrite(frame.atMs);
                lastMuteWriteMs = frame.atMs;
                DiagnosticLog.event("relay_media_zero_write",
                        "epoch=" + gate.epoch());
            } catch (RuntimeException error) {
                abort("relay_media_zero_write_failed",
                        AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            }
        }
    }

    private void advanceMutedCaptureProof(Frame frame) {
        if (!observeOwnedMedia(frame)) return;
        String invalid = invalidCommonFact(frame, true);
        if (invalid != null) {
            abort(invalid, AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (mediaZeroAckedAtMs <= 0L
                || frame.atMs - mediaZeroAckedAtMs
                        > MUTED_CAPTURE_PROOF_TIMEOUT_MS) {
            abort("relay_capture_lost_at_media_zero",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        boolean nonSilent = Float.isFinite(frame.sourcePeakDbfs)
                && frame.sourcePeakDbfs > -58f;
        if (!nonSilent) {
            mutedProofBlocks = 0;
            mutedProofFirstMs = 0L;
            return;
        }
        if (mutedProofBlocks == 0) mutedProofFirstMs = frame.atMs;
        mutedProofBlocks++;
        if (mutedProofBlocks < MUTED_PROOF_BLOCKS
                || frame.atMs - mutedProofFirstMs
                        < MUTED_PROOF_WINDOW_MS) {
            return;
        }
        AccessibilityRelayGate.Decision proof = gate.on(
                AccessibilityRelayGate.Event.MUTED_CAPTURE_PROVEN,
                gate.epoch(), "relay_muted_capture_proven");
        if (proof.command
                != AccessibilityRelayGate.Command.START_QUIET_PROBE) {
            abort("relay_muted_capture_transition_failed",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (!publishForRendererStart(
                "relay_probe_renderer_starting")) return;
        startQuietProbe(frame.atMs);
    }

    private void startQuietProbe(long nowMs) {
        if (outputDomain == null || !outputDomain.valid) {
            abort("relay_output_domain_unavailable",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        int currentAccessibility = readAccessibilityIndex();
        if (currentAccessibility < outputDomain.probeIndex) {
            abort("relay_accessibility_probe_level_required",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (!writeAccessibilityIndex(outputDomain.probeIndex, true)) return;
        dsp.reset();
        fullExperimental = false;
        if (!openRenderer(true)) return;
        probeDeadlineMs = nowMs + QUIET_PROBE_MS;
        publish("relay_quiet_probe");
    }

    private boolean openRenderer(boolean probe) {
        if (!neutralizeRenderer()) {
            enterRendererRecovery("relay_renderer_stop_unconfirmed");
            return false;
        }
        PlaybackObserver.RendererBaseline baseline =
                playbackResolver.beginRelayRendererOwnership();
        if (baseline == null || !baseline.valid) {
            abort("relay_renderer_topology_unproven",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return false;
        }
        try {
            renderer = AccessibilityPcmRenderer.open(expectedDevice);
            if (!playbackResolver.claimRelayRendererOwnership(baseline)) {
                if (!neutralizeRenderer()) {
                    enterRendererRecovery(
                            "relay_renderer_stop_unconfirmed");
                } else {
                    abort("relay_renderer_topology_unproven",
                            AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
                }
                return false;
            }
            rendererOwnershipClaimed = true;
            if (!renderer.enableOutput()) {
                abort("relay_renderer_enable_failed",
                        AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
                return false;
            }
        } catch (AccessibilityPcmRenderer.UnconfirmedShutdownException
                failure) {
            renderer = failure.renderer;
            enterRendererRecovery("relay_renderer_stop_unconfirmed");
            return false;
        } catch (RuntimeException failure) {
            if (renderer != null && !neutralizeRenderer()) {
                enterRendererRecovery(
                        "relay_renderer_stop_unconfirmed");
            } else {
                renderer = null;
                abort("relay_renderer_open_failed",
                        AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            }
            return false;
        }
        publishKeyAuthority(probe
                ? RelayVolumePolicy.Phase.PROBE
                : RelayVolumePolicy.Phase.ACTIVE);
        return true;
    }

    private void render(Frame frame, short[] input, int count,
            short[] output) {
        if (!observeOwnedMedia(frame)) return;
        boolean active = gate.state() == AccessibilityRelayGate.State.ACTIVE;
        String invalid = invalidCommonFact(frame, !active);
        if (invalid != null) {
            abort(invalid, AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        if (active) {
            if (!frame.playbackActive) {
                if (inactiveSinceMs == 0L) inactiveSinceMs = frame.atMs;
                if (frame.atMs - inactiveSinceMs >= SOURCE_END_GRACE_MS) {
                    abort("source_ended",
                            AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
                    return;
                }
            } else {
                inactiveSinceMs = 0L;
            }
        }
        if (renderer == null) {
            abort("relay_renderer_missing",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        int accessibility = readAccessibilityIndex();
        if (!observeAccessibilityOwnership(accessibility)) return;
        if (accessibility < outputDomain.minIndex
                || accessibility > outputDomain.hardMaxIndex) {
            abort("relay_accessibility_volume_out_of_bounds",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }

        float actualPeak = Float.NEGATIVE_INFINITY;
        if (accessibility < outputDomain.probeIndex) {
            dsp.reset();
            Arrays.fill(output, (short) 0);
            requestedGainDb = 0f;
            appliedGainDb = 0f;
            outputPeakDbfs = Float.NEGATIVE_INFINITY;
        } else {
            float routeGain = outputDomain.gainDbForIndex(accessibility);
            RelayPcmDsp.Result processed = dsp.process(frame.atMs,
                    input, count, output, frame.sourcePeakDbfs,
                    frame.sourceLoudnessDb, routeGain, frame.ceilings,
                    frame.profile, active && fullExperimental, true);
            if (!processed.active || processed.clippedSamples != 0) {
                abort(processed.reason.isEmpty()
                                ? "relay_pcm_boundary_failed"
                                : processed.reason,
                        AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
                return;
            }
            requestedGainDb = processed.requestedGainDb;
            appliedGainDb = processed.appliedGainDb;
            actualPeak = processed.outputPeakDbfs;
            if (!active) {
                float clamped = RelayPcmDsp.clampAbsolutePeak(
                        output, count, QUIET_PROBE_PEAK_DBFS);
                if (!validPeak(clamped)) {
                    abort("relay_probe_pcm_boundary_failed",
                            AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
                    return;
                }
                if (Float.isFinite(actualPeak)
                        && Float.isFinite(clamped)) {
                    appliedGainDb += clamped - actualPeak;
                }
                actualPeak = clamped;
            }
            outputPeakDbfs = actualPeak;
        }

        AccessibilityPcmRenderer.WriteResult written = renderer.write(
                output, count, frame.captureTimestamp);
        if (!written.success) {
            abort(written.reason,
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        AccessibilityPcmRenderer.Health health = renderer.health();
        if (!health.healthy) {
            abort(health.reason,
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        RelayLatencyTracker.Stats latency = health.latency;
        if (Float.isFinite(latency.latestMs)) {
            latestLatencyMs = Math.max(0L,
                    Math.round(latency.latestMs));
        }
        logRelayTelemetry(frame, active, latency);
        if (latency.sampleCount >= LATENCY_MIN_SAMPLES
                && (latency.medianMs > LATENCY_MEDIAN_LIMIT_MS
                        || latency.p95Ms > LATENCY_P95_LIMIT_MS)) {
            abort("relay_latency_out_of_bounds",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }

        if (active) {
            audible = accessibility >= outputDomain.probeIndex;
            publish("relay_active");
        } else if (frame.atMs >= probeDeadlineMs) {
            finishQuietProbe();
        } else {
            publish("relay_quiet_probe");
        }
    }

    private void finishQuietProbe() {
        if (!neutralizeRenderer()) {
            enterRendererRecovery("relay_renderer_stop_unconfirmed");
            return;
        }
        dsp.reset();
        AccessibilityRelayGate.Decision finished = gate.on(
                AccessibilityRelayGate.Event.PROBE_FINISHED,
                gate.epoch(), "relay_probe_finished");
        if (finished.command
                != AccessibilityRelayGate.Command.SILENCE_AND_WAIT) {
            abort("relay_probe_finish_transition_failed",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return;
        }
        publish("relay_awaiting_confirmation");
    }

    private void logRelayTelemetry(Frame frame, boolean active,
            RelayLatencyTracker.Stats latency) {
        String phase = active ? "ACTIVE" : "QUIET_PROBE";
        String gainState = gate.epoch() + ":" + phase + ":"
                + quarterDbBucket(requestedGainDb) + ':'
                + quarterDbBucket(appliedGainDb) + ':'
                + halfDbBucket(outputPeakDbfs);
        if (!gainState.equals(lastGainTelemetryState)) {
            lastGainTelemetryState = gainState;
            DiagnosticLog.transition("relay_pcm_gain", gainState,
                    String.format(Locale.US,
                            "epoch=%d phase=%s requestedGainDb=%.2f "
                                    + "appliedGainDb=%.2f inputPeakDbfs=%.2f "
                                    + "outputPeakDbfs=%.2f",
                            gate.epoch(), phase, requestedGainDb,
                            appliedGainDb, frame.sourcePeakDbfs,
                            outputPeakDbfs));
        }

        int markerBucket = latency.sampleCount < LATENCY_MIN_SAMPLES
                ? 0 : latency.sampleCount / LATENCY_MIN_SAMPLES;
        String latencyState = gate.epoch() + ":" + phase + ':'
                + markerBucket;
        if (!latencyState.equals(lastLatencyTelemetryState)) {
            lastLatencyTelemetryState = latencyState;
            DiagnosticLog.transition("relay_renderer_latency",
                    latencyState, String.format(Locale.US,
                            "epoch=%d phase=%s sampleCount=%d latestMs=%.2f "
                                    + "medianMs=%.2f p95Ms=%.2f",
                            gate.epoch(), phase, latency.sampleCount,
                            latency.latestMs, latency.medianMs,
                            latency.p95Ms));
        }
    }

    private boolean observeOwnedMedia(Frame frame) {
        if (lease == null) {
            abort("relay_lease_missing",
                    AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
            return false;
        }
        RelayMediaLease.Decision decision = lease.observeMedia(
                frame.observedMediaIndex, frame.atMs);
        if (decision.action == RelayMediaLease.MuteAction.USER_EXIT) {
            abort(decision.reason,
                    AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA);
            return false;
        }
        if (decision.action != RelayMediaLease.MuteAction.ACKNOWLEDGED) {
            abort("relay_media_zero_ownership_lost",
                    AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
            return false;
        }
        return true;
    }

    private String invalidCommonFact(Frame frame,
            boolean requirePlayback) {
        if (frame == null || frame.epoch != gate.epoch()) {
            return "relay_projection_epoch_stale";
        }
        if (expectedGenerations == null
                || !expectedGenerations.sameAs(frame.generations)) {
            String mismatch = expectedGenerations == null
                    ? "relay_generation_invalid"
                    : expectedGenerations.mismatchReason(
                            frame.generations);
            return mismatch.isEmpty()
                    ? "relay_generation_invalid" : mismatch;
        }
        if (!frame.builtInSpeaker
                || !expectedRouteKey.equals(frame.routeKey)) {
            return "relay_route_changed";
        }
        if (frame.observedMediaIndex < 0) {
            return "relay_media_volume_unavailable";
        }
        if (!frame.targetedCapture || !frame.exactSource
                || frame.sourceUid != expectedSourceUid
                || !expectedSourcePackage.equals(frame.sourcePackage)) {
            return "relay_source_not_exact";
        }
        if (!frame.sourceAllowed || frame.systemSource
                || frame.protectedSource) {
            return "relay_source_policy_blocked";
        }
        if (frame.endpointCount != 1) return "relay_multiple_endpoints";
        if (renderer != null && !frame.rendererOwnershipProven) {
            return "relay_renderer_topology_unproven";
        }
        if (!frame.captureWarmupConfirmed) return "relay_capture_not_ready";
        if (frame.captureReference
                != CaptureReferenceEstimator.Mode.PRE_VOLUME) {
            return "relay_prevolume_not_proven";
        }
        if (requirePlayback && !frame.playbackActive) {
            return "relay_capture_not_ready";
        }
        if (!refreshEnvironment(frame)) {
            return reason;
        }
        return null;
    }

    private boolean refreshEnvironment(Frame frame) {
        if (frame.atMs - lastAccessibilityCheckMs
                >= ACCESSIBILITY_RECHECK_MS) {
            lastAccessibilityCheckMs = frame.atMs;
            if (!StrictSafetyState.accessibilityConnected()
                    || !StrictSafetyState
                            .isAccessibilityServiceEnabled(context)
                    || !StrictSafetyState
                            .accessibilityVolumeEnabled(context)) {
                reason = "relay_accessibility_output_unavailable";
                return false;
            }
            if (!StrictSafetyState.keyFilterCapable()) {
                reason = "relay_accessibility_key_filter_unavailable";
                return false;
            }
            if (StrictSafetyState
                    .hasOtherSpokenFeedbackService(context)) {
                reason = "relay_spoken_accessibility_conflict";
                return false;
            }
        }
        if (outputDomain == null || frame.atMs - lastOutputDomainCheckMs
                >= OUTPUT_DOMAIN_RECHECK_MS) {
            lastOutputDomainCheckMs = frame.atMs;
            RelayOutputDomain.Snapshot refreshed = RelayOutputDomain.read(
                    audio, expectedDevice, safetyPercent(frame.profile));
            if (!refreshed.valid
                    || !expectedRouteKey.equals(refreshed.routeKey)) {
                reason = refreshed.reason.isEmpty()
                        ? "relay_output_domain_unavailable"
                        : refreshed.reason;
                return false;
            }
            if (outputDomain != null && outputDomain.valid
                    && !outputDomain.sameCurveAs(refreshed)) {
                reason = "relay_output_domain_changed";
                return false;
            }
            outputDomain = refreshed;
            publishKeyAuthority(phaseForState(gate.state()));
        }
        return true;
    }

    private boolean writeAccessibilityIndex(int requested,
            boolean appOwnedStartupWrite) {
        if (outputDomain == null || !outputDomain.valid) {
            abort("relay_output_domain_unavailable",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return false;
        }
        int target = RelayVolumePolicy.clampRequestedIndex(requested,
                outputDomain.minIndex, outputDomain.hardMaxIndex);
        int current = readAccessibilityIndex();
        if (current == target) return true;
        if (!writeAndVerify(AudioManager.STREAM_ACCESSIBILITY,
                target, AudioManager.FLAG_SHOW_UI)) {
            abort("relay_accessibility_volume_write_failed",
                    AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
            return false;
        }
        if (lease != null) {
            if (appOwnedStartupWrite) {
                lease.noteAccessibilityWrite(target);
            } else {
                lease.revokeAccessibilityRestore();
            }
            if (!recovery.save(lease.record())) {
                abort("relay_recovery_persist_failed",
                        AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
                return false;
            }
        }
        return true;
    }

    private void syncRelayKeyWrite() {
        StrictSafetyState.RelayAccessibilityWrite write =
                StrictSafetyState.relayAccessibilityWrite();
        if (write.sequence <= lastRelayKeyWriteSequence) return;
        lastRelayKeyWriteSequence = write.sequence;
        if (lease == null || write.index < 0) return;
        lease.revokeAccessibilityRestore();
        if (!recovery.save(lease.record())) {
            abort("relay_recovery_persist_failed",
                    AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
        }
    }

    private void finishAbort(AccessibilityRelayGate.Decision decision) {
        if (decision.command
                != AccessibilityRelayGate.Command.NEUTRALIZE_RENDERER) {
            publish(decision.reason);
            return;
        }
        if (!neutralizeRenderer()) {
            enterRendererRecovery("relay_renderer_stop_unconfirmed");
            return;
        }
        onRendererStopped();
    }

    private boolean neutralizeRenderer() {
        if (renderer != null) {
            if (!renderer.neutralize()) {
                clearAudibleState();
                return false;
            }
            renderer = null;
        }
        if (rendererOwnershipClaimed) {
            playbackResolver.clearRelayRendererOwnership();
            rendererOwnershipClaimed = false;
        }
        clearAudibleState();
        return true;
    }

    private void clearAudibleState() {
        if (lastOutputBuffer != null) {
            Arrays.fill(lastOutputBuffer, (short) 0);
        }
        audible = false;
        fullExperimental = false;
        dsp.reset();
    }

    private void enterRendererRecovery(String detail) {
        clearAudibleState();
        StrictSafetyState.clearRelayKeyAuthority();
        recoveryRequired = true;
        if (gate.state() != AccessibilityRelayGate.State.OFF
                && gate.state()
                        != AccessibilityRelayGate.State.RECOVERY_REQUIRED) {
            gate.on(AccessibilityRelayGate.Event.PROCESS_DIED,
                    gate.epoch(), detail);
        }
        publish("relay_recovery_required:" + detail);
    }

    private void performCleanup(AccessibilityRelayGate.Cleanup cleanup) {
        StrictSafetyState.clearRelayKeyAuthority();
        boolean complete = true;
        RelayMediaLease.Record record = lease == null ? null : lease.record();
        if (record != null
                && cleanup == AccessibilityRelayGate.Cleanup.RESTORE_OWNED) {
            int observedMedia = readMediaIndex();
            if (observedMedia < 0) {
                complete = false;
            } else if (record.mediaZeroOwned && observedMedia == 0) {
                int currentSafety = StrictSafetyState.hardMaxIndex(
                        context, audio);
                int target = Math.max(0,
                        Math.min(record.preMediaIndex, currentSafety));
                complete = writeAndVerify(
                        AudioManager.STREAM_MUSIC, target, 0);
            } else if (!record.mediaZeroOwned && observedMedia == 0
                    && record.preMediaIndex > 0) {
                complete = false;
            }

        }
        if (complete && record != null) {
            complete = restoreOwnedAccessibility(record);
        }
        if (complete) complete = recovery.clear();
        if (!complete) {
            recoveryRequired = true;
            reason = "relay_recovery_required";
            publish(reason);
            return;
        }
        recoveryRequired = false;
        lease = null;
        outputDomain = null;
        expectedSourceUid = -1;
        expectedSourcePackage = "";
        publish(cleanup == AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA
                ? "relay_user_media_preserved" : "relay_cleanup_complete");
    }

    private boolean writeAndVerify(int stream, int target, int flags) {
        try {
            audio.setStreamVolume(stream, target, flags);
            return audio.getStreamVolume(stream) == target;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean restoreOwnedAccessibility(
            RelayMediaLease.Record record) {
        if (record == null || !record.accessibilityValueOwned) return true;
        int observed = readAccessibilityIndex();
        if (observed < 0) return false;
        if (observed != record.lastOwnedAccessibilityIndex) return true;
        final int minimum;
        final int maximum;
        try {
            minimum = audio.getStreamMinVolume(
                    AudioManager.STREAM_ACCESSIBILITY);
            maximum = audio.getStreamMaxVolume(
                    AudioManager.STREAM_ACCESSIBILITY);
        } catch (RuntimeException ignored) {
            return false;
        }
        int currentSafety = RelayVolumePolicy.hardMaxIndex(
                minimum, maximum,
                safetyPercent(Prefs.currentControlProfile(context)));
        int high = Math.max(minimum, Math.min(maximum, currentSafety));
        int target = Math.max(minimum, Math.min(observed,
                Math.min(record.preAccessibilityIndex, high)));
        return writeAndVerify(AudioManager.STREAM_ACCESSIBILITY,
                target, 0);
    }

    private boolean observeAccessibilityOwnership(int observedIndex) {
        if (lease == null || observedIndex < 0
                || !lease.observeAccessibility(observedIndex)) {
            return true;
        }
        if (!recovery.save(lease.record())) {
            abort("relay_recovery_persist_failed",
                    AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED);
            return false;
        }
        return true;
    }

    private int readMediaIndex() {
        try {
            return Math.max(0,
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private int readAccessibilityIndex() {
        try {
            return Math.max(0, audio.getStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private void publishKeyAuthority(RelayVolumePolicy.Phase phase) {
        if (phase == null || phase == RelayVolumePolicy.Phase.OFF
                || outputDomain == null || !outputDomain.valid) {
            StrictSafetyState.clearRelayKeyAuthority();
            return;
        }
        StrictSafetyState.publishRelayKeyAuthority(phase,
                outputDomain.minIndex, outputDomain.hardMaxIndex);
    }

    private static RelayVolumePolicy.Phase phaseForState(
            AccessibilityRelayGate.State state) {
        if (state == AccessibilityRelayGate.State.QUIET_PROBE) {
            return RelayVolumePolicy.Phase.PROBE;
        }
        if (state == AccessibilityRelayGate.State.AWAITING_CONFIRMATION) {
            return RelayVolumePolicy.Phase.AWAITING_CONFIRMATION;
        }
        if (state == AccessibilityRelayGate.State.ACTIVE) {
            return RelayVolumePolicy.Phase.ACTIVE;
        }
        return RelayVolumePolicy.Phase.OFF;
    }

    private Snapshot publish(String nextReason) {
        reason = nextReason == null || nextReason.isEmpty()
                ? "relay_state_changed" : nextReason;
        publishKeyAuthority(phaseForState(gate.state()));
        snapshot = buildSnapshot(SystemClock.elapsedRealtime());
        lastListenerAccepted = true;
        if (listener != null) {
            try {
                lastListenerAccepted = listener.onRelaySnapshot(snapshot);
            } catch (RuntimeException failure) {
                lastListenerAccepted = false;
                DiagnosticLog.event("relay_snapshot_listener_failed",
                        "error=" + failure.getClass().getSimpleName());
            }
        }
        return snapshot;
    }

    private boolean publishForRendererStart(String nextReason) {
        publish(nextReason);
        if (lastListenerAccepted) return true;
        abort("relay_foreground_playback_unavailable",
                AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
        return false;
    }

    private Snapshot buildSnapshot(long nowMs) {
        AccessibilityRelayGate.State visibleState = recoveryRequired
                ? AccessibilityRelayGate.State.RECOVERY_REQUIRED
                : gate.state();
        int volume = readAccessibilityIndex();
        int hardMax = outputDomain == null ? 0
                : outputDomain.hardMaxIndex;
        long remaining = gate.state()
                == AccessibilityRelayGate.State.QUIET_PROBE
                ? Math.max(0L, probeDeadlineMs - nowMs) : 0L;
        return new Snapshot(gate.epoch(), visibleState, reason, audible,
                fullExperimental, recoveryRequired, Math.max(0, volume),
                hardMax, requestedGainDb, appliedGainDb,
                outputPeakDbfs, latestLatencyMs, remaining);
    }

    private void resetEpochState() {
        StrictSafetyState.clearRelayKeyAuthority();
        lease = null;
        outputDomain = null;
        lastFrame = null;
        expectedSourcePackage = "";
        expectedSourceUid = -1;
        mutedProofBlocks = 0;
        mutedProofFirstMs = 0L;
        mediaZeroAckedAtMs = 0L;
        lastMuteWriteMs = Long.MIN_VALUE;
        probeDeadlineMs = 0L;
        inactiveSinceMs = 0L;
        lastOutputDomainCheckMs = 0L;
        lastAccessibilityCheckMs = 0L;
        requestedGainDb = 0f;
        appliedGainDb = 0f;
        outputPeakDbfs = Float.NaN;
        latestLatencyMs = -1L;
        lastGainTelemetryState = "";
        lastLatencyTelemetryState = "";
        audible = false;
        fullExperimental = false;
        recoveryRequired = false;
        lastRelayKeyWriteSequence =
                StrictSafetyState.relayAccessibilityWrite().sequence;
    }

    private static int safetyPercent(ControlProfile profile) {
        if (profile == null) return 0;
        return profile.safetyLockEnabled
                ? profile.safetyLockPercent : profile.maxMediaPercent;
    }

    private static boolean validPeak(float value) {
        return Float.isFinite(value)
                || value == Float.NEGATIVE_INFINITY;
    }

    private static int quarterDbBucket(float value) {
        return Float.isFinite(value) ? Math.round(value * 4f)
                : Integer.MIN_VALUE;
    }

    private static int halfDbBucket(float value) {
        return Float.isFinite(value) ? Math.round(value * 2f)
                : Integer.MIN_VALUE;
    }

    private static boolean isRouteChange(String value) {
        return "route_changed".equals(value)
                || "relay_route_changed".equals(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ')
                .replace('\r', ' ').replace('\t', ' ');
    }

    @Override public synchronized void close() {
        abort("service_destroy",
                AccessibilityRelayGate.Cleanup.RESTORE_OWNED);
    }
}
