package dev.soundceiling.app;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker-thread lifecycle for v0.7.7 non-zero session DSP.
 * Discovery evidence never becomes control authority until exact UID ownership and differential
 * verification both succeed.
 */
final class EnhancedSessionDspRuntime {
    private static final int MIN_PAIRS = 8;
    private static final long MIN_WINDOW_MS = 250L;
    private static final long DISCOVERY_INTERVAL_MS = 1000L;
    private static final long PROBE_COOLDOWN_MS = 1200L;
    private static final long PROBE_MAX_ACTIVE_MS = 1500L;

    private final AudioSessionDiscovery discovery;
    private final OptionalDspController dsp;

    private AudioSessionDiscovery.Snapshot lastDiscovery =
            new AudioSessionDiscovery.Snapshot(false, Collections.emptyList(),
                    "not_discovered", 0L);
    private DspEndpointHandle candidate;
    private boolean collecting;
    private boolean transportAttached;
    private boolean probeApplied;
    private int baselinePairs;
    private int attachPairs;
    private int probePairs;
    private long baselineFirstMs;
    private long attachFirstMs;
    private long probeFirstMs;
    private long transportAttachedAtMs;
    private long lastDiscoveryMs = -DISCOVERY_INTERVAL_MS;
    private long lastProbeAttemptMs = -PROBE_COOLDOWN_MS;
    private int probeMediaIndex = -1;
    private String routeIdentity = "";
    private int suppressedSessionId = -1;
    private String suppressedReason = "";
    private String reason = "not_started";

    EnhancedSessionDspRuntime(AudioSessionDiscovery discovery, OptionalDspController dsp) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.dsp = Objects.requireNonNull(dsp, "dsp");
    }

    void update(HybridRuntimeResolver.Snapshot snapshot,
                float sourceRmsDb, boolean sourceValid,
                float outputRmsDb, boolean outputValid,
                int mediaIndex, String currentRouteIdentity,
                boolean enabled, long nowMs) {
        String route = currentRouteIdentity == null ? "" : currentRouteIdentity;
        if (!route.equals(routeIdentity)) {
            release("session_route_changed", true);
            routeIdentity = route;
        }
        if (!enabled) {
            release("session_dsp_disabled", false);
            return;
        }

        SourceDescriptor exact = snapshot == null ? null : snapshot.exactSource;
        AppPolicy exactPolicy = snapshot == null ? null : snapshot.exactAppPolicy;
        boolean playbackActive = snapshot != null && snapshot.playback != null
                && snapshot.playback.active;
        if (exact == null || exact.uid <= 0 || exactPolicy == null || !exactPolicy.allowsDspControl()) {
            if (dsp.enhancedSessionId() > 0 || collecting) release("session_no_exact_source", false);
            reason = "session_no_exact_source";
            return;
        }

        boolean freshDiscovery = false;
        if (nowMs - lastDiscoveryMs >= DISCOVERY_INTERVAL_MS) {
            lastDiscovery = discovery.discover(nowMs);
            lastDiscoveryMs = nowMs;
            freshDiscovery = true;
            DiagnosticLog.transition("session_discovery_permission",
                    lastDiscovery.permissionGranted ? "granted" : "missing",
                    "reason=" + lastDiscovery.reason + " records=" + lastDiscovery.records.size());
        }
        if (!lastDiscovery.permissionGranted) {
            release("session_dump_permission_missing", false);
            reason = "session_dump_permission_missing";
            DiagnosticLog.transition("session_dsp_unavailable", reason,
                    "setup=" + EnhancedSessionSetup.ADB_GRANT_COMMAND);
            return;
        }

        AudioSessionOwnershipResolver.Decision ownership = AudioSessionOwnershipResolver.resolve(
                lastDiscovery.records, exact, nowMs);
        Optional<DspEndpointHandle> maybeHandle = ownership.toDspHandle(
                exact.packageName, exactPolicy);
        if (maybeHandle.isEmpty()) {
            if (freshDiscovery && (dsp.enhancedSessionId() > 0 || collecting)) {
                release("session_ownership_lost:" + ownership.reason, false);
            }
            reason = "session_rejected:" + ownership.reason;
            DiagnosticLog.transition("session_rejected", ownership.reason,
                    "package=" + exact.packageName + " uid=" + exact.uid
                            + " records=" + lastDiscovery.records.size());
            return;
        }
        DspEndpointHandle wanted = maybeHandle.get();
        DiagnosticLog.transition("session_discovered", wanted.audioSessionId + ":" + wanted.sourceUid,
                "session=" + wanted.audioSessionId + " uid=" + wanted.sourceUid
                        + " package=" + wanted.sourcePackage);

        int verifiedSession = dsp.enhancedSessionId();
        if (verifiedSession > 0) {
            if (dsp.hasVerifiedEnhancedSession(wanted)) {
                reason = "session_dsp_active";
                return;
            }
            release("session_changed", true);
        }

        if (wanted.audioSessionId == suppressedSessionId) {
            reason = "session_suppressed:" + suppressedReason;
            DiagnosticLog.transition("session_dsp_unavailable", reason,
                    "session=" + wanted.audioSessionId + " retry=session_change_or_route_change");
            return;
        }

        if (!playbackActive || !sourceValid || !outputValid
                || !Float.isFinite(sourceRmsDb) || !Float.isFinite(outputRmsDb)) {
            if (collecting) cancelProbe("session_paired_meter_unavailable", nowMs);
            reason = "session_paired_meter_unavailable";
            return;
        }

        if (collecting && candidate != null && !candidate.equals(wanted)) {
            cancelProbe("session_changed_during_probe", nowMs);
        }
        if (collecting && probeMediaIndex != mediaIndex) {
            cancelProbe("session_media_changed_during_probe", nowMs);
        }

        if (!collecting) {
            if (nowMs - lastProbeAttemptMs < PROBE_COOLDOWN_MS) return;
            if (!dsp.beginEnhancedSessionDifferentialProbe(wanted, routeIdentity,
                    mediaIndex, true, nowMs)) {
                reason = "session_probe_begin_failed:" + dsp.detail();
                lastProbeAttemptMs = nowMs;
                return;
            }
            candidate = wanted;
            collecting = true;
            probeMediaIndex = mediaIndex;
            baselineFirstMs = nowMs;
            baselinePairs = 0;
            attachPairs = 0;
            probePairs = 0;
            DiagnosticLog.transition("session_bound", "probe_baseline",
                    "session=" + wanted.audioSessionId + " uid=" + wanted.sourceUid
                            + " package=" + wanted.sourcePackage + " media=" + mediaIndex);
        }

        if (!transportAttached) {
            dsp.addEnhancedSessionBaseline(sourceRmsDb, outputRmsDb, nowMs);
            baselinePairs++;
            if (baselinePairs < MIN_PAIRS || nowMs - baselineFirstMs < MIN_WINDOW_MS) return;
            if (!dsp.attachEnhancedSessionDifferentialProbe(nowMs)) {
                suppressAndRelease("session_neutral_attach_failed:" + dsp.detail(), nowMs);
                return;
            }
            transportAttached = true;
            transportAttachedAtMs = nowMs;
            attachFirstMs = nowMs;
            DiagnosticLog.transition("session_dsp_attached", "neutral_0db",
                    "session=" + candidate.audioSessionId + " baselinePairs=" + baselinePairs);
            return;
        }

        if (nowMs - transportAttachedAtMs > PROBE_MAX_ACTIVE_MS) {
            cancelProbe("session_probe_inconclusive_timeout", nowMs);
            return;
        }

        if (!probeApplied) {
            dsp.addEnhancedSessionNeutralAttach(sourceRmsDb, outputRmsDb, nowMs);
            attachPairs++;
            if (attachPairs < MIN_PAIRS || nowMs - attachFirstMs < MIN_WINDOW_MS) return;
            DspDifferentialVerifier.AttachResult attach =
                    dsp.evaluateEnhancedSessionNeutralAttach(nowMs);
            DiagnosticLog.transition("session_dsp_attach_result", attach.reason,
                    "session=" + candidate.audioSessionId + " safe=" + attach.safe
                            + " retryable=" + attach.retryable() + " deltaDb=" + attach.deltaDb
                            + " coveredMs=" + attach.coveredMs + " samples=" + attach.attachPairs);
            if (attach.retryable()) {
                if ("attach_unstable_residuals".equals(attach.reason)) {
                    DiagnosticLog.transition("session_dsp_attach_inconclusive", attach.reason,
                            "session=" + candidate.audioSessionId + " coveredMs="
                                    + attach.coveredMs + " samples=" + attach.attachPairs);
                    cancelProbe("session_attach_inconclusive", nowMs);
                }
                return;
            }
            if (!attach.safe) {
                suppressAndRelease("session_neutral_attach_unsafe:" + attach.deltaDb, nowMs);
                return;
            }
            if (!dsp.activateEnhancedSessionDifferentialProbe(nowMs)) {
                suppressAndRelease("session_probe_gain_failed:" + dsp.detail(), nowMs);
                return;
            }
            probeApplied = true;
            probeFirstMs = nowMs;
            DiagnosticLog.transition("session_dsp_probe_begin", "active",
                    "session=" + candidate.audioSessionId
                            + " requestedGainDb=" + DspDifferentialVerifier.REQUESTED_PROBE_DB);
            return;
        }

        dsp.addEnhancedSessionProbePair(sourceRmsDb, outputRmsDb, nowMs);
        probePairs++;
        if (probePairs < MIN_PAIRS || nowMs - probeFirstMs < MIN_WINDOW_MS) return;
        DspScopeProbe.Evidence evidence = dsp.finishEnhancedSessionDifferentialProbe(nowMs);
        boolean verified = evidence.allowedMediaEffectVerified()
                && dsp.enhancedSessionId() > 0;
        DiagnosticLog.transition("session_dsp_probe_result", evidence.reason,
                "session=" + (candidate == null ? -1 : candidate.audioSessionId)
                        + " verified=" + verified + " classification=" + evidence.classification
                        + " deltaDb=" + evidence.affectedDeltaDb
                        + " samples=" + evidence.sampleCount);
        if (verified) {
            reason = "session_dsp_active";
            DiagnosticLog.transition("session_dsp_attached", "verified",
                    "session=" + dsp.enhancedSessionId() + " uid=" + dsp.enhancedSessionUid()
                            + " package=" + dsp.enhancedSessionPackage());
            resetProbeState(nowMs);
        } else if (evidence.classification
                == DspDifferentialVerifier.Classification.INSUFFICIENT_EVIDENCE) {
            reason = "session_probe_inconclusive:" + evidence.reason;
            DiagnosticLog.transition("session_dsp_probe_inconclusive", evidence.reason,
                    "session=" + (candidate == null ? -1 : candidate.audioSessionId)
                            + " samples=" + evidence.sampleCount);
            resetProbeState(nowMs);
        } else {
            suppressAndRelease("session_probe_unverified:" + evidence.classification
                    + ":" + evidence.reason, nowMs);
        }
    }

    boolean permissionGranted() { return lastDiscovery.permissionGranted; }
    String reason() { return reason; }
    int sessionId() { return dsp.enhancedSessionId(); }
    int sessionUid() { return dsp.enhancedSessionUid(); }
    String sessionPackage() { return dsp.enhancedSessionPackage(); }
    boolean active() { return dsp.enhancedSessionId() > 0; }

    void onCaptureReplaced() { release("session_capture_replaced", false); }
    void onPolicyChanged() { release("session_policy_changed", true); }
    void onApplyFailed(String why) {
        String actual = why == null || why.isEmpty() ? "session_dsp_apply_failed" : why;
        release(actual, false);
        DiagnosticLog.transition("session_dsp_unavailable", actual, "applyFailed=true");
    }
    void onStopped() { release("session_service_stopped", true); }

    private void cancelProbe(String why, long nowMs) {
        if (collecting || dsp.enhancedSessionProbeActive()) dsp.cancelEnhancedSessionProbe(why);
        reason = why;
        resetProbeState(nowMs);
    }

    private void suppressAndRelease(String why, long nowMs) {
        int badSession = candidate == null ? -1 : candidate.audioSessionId;
        if (badSession > 0) {
            suppressedSessionId = badSession;
            suppressedReason = why;
        }
        dsp.releaseEnhancedSession(why);
        reason = why;
        DiagnosticLog.transition("session_dsp_unavailable", why,
                "session=" + badSession + " suppressed=true");
        resetProbeState(nowMs);
    }

    private void release(String why, boolean clearSuppression) {
        if (dsp.enhancedSessionId() > 0 || dsp.enhancedSessionProbeActive() || collecting) {
            int oldSession = dsp.enhancedSessionId() > 0
                    ? dsp.enhancedSessionId() : candidate == null ? -1 : candidate.audioSessionId;
            dsp.releaseEnhancedSession(why);
            DiagnosticLog.transition("session_dsp_released", why, "session=" + oldSession);
        }
        if (clearSuppression) {
            suppressedSessionId = -1;
            suppressedReason = "";
        }
        reason = why;
        resetProbeState(0L);
    }

    private void resetProbeState(long nowMs) {
        collecting = false;
        transportAttached = false;
        probeApplied = false;
        baselinePairs = 0;
        attachPairs = 0;
        probePairs = 0;
        baselineFirstMs = 0L;
        attachFirstMs = 0L;
        probeFirstMs = 0L;
        transportAttachedAtMs = 0L;
        probeMediaIndex = -1;
        candidate = null;
        if (nowMs > 0L) lastProbeAttemptMs = nowMs;
    }
}