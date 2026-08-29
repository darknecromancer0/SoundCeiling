package dev.soundceiling.app;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker-thread lifecycle for v0.7.7.1 non-zero Session DSP.
 * Exact UID/session ownership establishes scope; Android effect readback establishes transport
 * authority. Asynchronous PCM/Visualizer residuals are diagnostics, never the authority gate.
 */
final class EnhancedSessionDspRuntime {
    private static final long DISCOVERY_INTERVAL_MS = 1000L;
    private static final long VERIFY_COOLDOWN_MS = 1200L;

    private final AudioSessionDiscovery discovery;
    private final OptionalDspController dsp;

    private AudioSessionDiscovery.Snapshot lastDiscovery =
            new AudioSessionDiscovery.Snapshot(false, Collections.emptyList(),
                    "not_discovered", 0L);
    private long lastDiscoveryMs = -DISCOVERY_INTERVAL_MS;
    private long lastVerifyAttemptMs = -VERIFY_COOLDOWN_MS;
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
        if (!EnhancedSessionSetup.SAFE_CUSTOM_MATRIX_ENABLED) {
            release("session_dsp_custom_matrix_disabled", false);
            DiagnosticLog.transition("session_dsp_unavailable",
                    "session_dsp_custom_matrix_disabled",
                    "authority=custom_matrix_fail_closed");
            return;
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
            if (dsp.enhancedSessionId() > 0 || dsp.enhancedSessionProbeActive()) {
                release("session_no_exact_source", false);
            }
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
            if (freshDiscovery && dsp.enhancedSessionId() > 0) {
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
                reason = "session_dsp_active:" + dsp.enhancedSessionProfileId();
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

        if (!playbackActive) {
            reason = "session_playback_inactive";
            return;
        }
        if (nowMs - lastVerifyAttemptMs < VERIFY_COOLDOWN_MS) return;
        lastVerifyAttemptMs = nowMs;

        // source/output meter values remain intentionally unused here. They are asynchronous on
        // Samsung and are not a trustworthy yes/no transport-verification primitive.
        DiagnosticLog.transition("session_dsp_readback_begin",
                wanted.audioSessionId + ":" + wanted.sourceUid,
                "session=" + wanted.audioSessionId + " uid=" + wanted.sourceUid
                        + " package=" + wanted.sourcePackage + " media=" + mediaIndex
                        + " sourceValid=" + sourceValid + " outputValid=" + outputValid
                        + " sourceRmsDb=" + sourceRmsDb + " outputRmsDb=" + outputRmsDb);

        boolean verified = dsp.verifyEnhancedSessionReadback(wanted, true);
        String detail = dsp.detail();
        DiagnosticLog.transition("session_dsp_readback_result",
                wanted.audioSessionId + ":" + verified + ":" + detail,
                "session=" + wanted.audioSessionId + " uid=" + wanted.sourceUid
                        + " package=" + wanted.sourcePackage + " verified=" + verified
                        + " reason=" + detail);
        if (verified && dsp.enhancedSessionId() > 0) {
            reason = "session_dsp_active:" + dsp.enhancedSessionProfileId();
            DiagnosticLog.transition("session_dsp_attached", "verified_readback",
                    "session=" + dsp.enhancedSessionId() + " uid=" + dsp.enhancedSessionUid()
                            + " package=" + dsp.enhancedSessionPackage()
                            + " profile=" + dsp.enhancedSessionProfileId()
                            + " positivePilotMaxDb="
                            + EnhancedSessionGainPolicy.MAX_POSITIVE_GAIN_DB);
            return;
        }

        suppressAndRelease(wanted.audioSessionId,
                "session_readback_unverified:" + detail);
    }

    boolean permissionGranted() { return lastDiscovery.permissionGranted; }
    String reason() { return reason; }
    int sessionId() { return dsp.enhancedSessionId(); }
    int sessionUid() { return dsp.enhancedSessionUid(); }
    String sessionPackage() { return dsp.enhancedSessionPackage(); }
    String profileId() { return dsp.enhancedSessionProfileId(); }
    boolean active() { return dsp.enhancedSessionId() > 0; }

    void onCaptureReplaced() { release("session_capture_replaced", false); }
    void onPolicyChanged() { release("session_policy_changed", true); }

    void onApplyFailed(String why) {
        String actual = why == null || why.isEmpty() ? "session_dsp_apply_failed" : why;
        release(actual, false);
        DiagnosticLog.transition("session_dsp_unavailable", actual, "applyFailed=true");
    }

    void onOutputAnomaly(String detail) {
        int badSession = dsp.enhancedSessionId();
        suppressAndRelease(badSession, "session_output_guard_suppressed");
        DiagnosticLog.transition("session_dsp_output_guard",
                badSession + ":session_output_guard_suppressed",
                "session=" + badSession + " " + (detail == null ? "" : detail));
    }

    void onStopped() { release("session_service_stopped", true); }

    private void suppressAndRelease(int badSession, String why) {
        if (badSession > 0) {
            suppressedSessionId = badSession;
            suppressedReason = why;
        }
        dsp.releaseEnhancedSession(why);
        reason = why;
        DiagnosticLog.transition("session_dsp_unavailable", why,
                "session=" + badSession + " suppressed=true");
    }

    private void release(String why, boolean clearSuppression) {
        if (dsp.enhancedSessionId() > 0 || dsp.enhancedSessionProbeActive()) {
            int oldSession = dsp.enhancedSessionId();
            dsp.releaseEnhancedSession(why);
            DiagnosticLog.transition("session_dsp_released", why, "session=" + oldSession);
        }
        if (clearSuppression) {
            suppressedSessionId = -1;
            suppressedReason = "";
        }
        reason = why;
    }
}
