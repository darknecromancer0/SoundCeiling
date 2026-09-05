package dev.soundceiling.app;

/** Pure final arbiter between app policy, emergency safety and adaptive-envelope loudness control. */
final class HybridEngineCoordinator {
    static final class ControlPlan {
        final int requestedIndex;
        final boolean recoveryBlocked;
        final boolean raiseBlocked; // temporary source alias
        final String reason;

        ControlPlan(int requestedIndex, boolean recoveryBlocked, String reason) {
            this.requestedIndex = requestedIndex;
            this.recoveryBlocked = recoveryBlocked;
            this.raiseBlocked = recoveryBlocked;
            this.reason = reason == null ? "" : reason;
        }
    }

    /** v0.6 compatibility overload remains one-way. */
    static ControlPlan plan(int currentIndex,
                            int emergencyTargetIndex,
                            int comfortTargetIndex,
                            int effectiveMaxIndex,
                            EffectivePolicy policy,
                            boolean manualPause,
                            boolean emergencyActive) {
        return plan(currentIndex, emergencyTargetIndex, comfortTargetIndex, effectiveMaxIndex,
                currentIndex, policy, manualPause, emergencyActive, false);
    }

    static ControlPlan plan(int currentIndex,
                            int emergencyTargetIndex,
                            int comfortTargetIndex,
                            int effectiveMaxIndex,
                            int recoveryCeilingIndex,
                            EffectivePolicy policy,
                            boolean manualPause,
                            boolean emergencyActive,
                            boolean recoveryAllowed) {
        if (policy == null) throw new IllegalArgumentException("policy == null");
        int max = Math.max(0, effectiveMaxIndex);
        int current = Math.max(0, currentIndex);
        int recoveryCeiling = Math.min(max, Math.max(0, recoveryCeilingIndex));

        // Explicit app/source OFF precedes source-specific analysis. Global hard caps are
        // enforced independently by SafeVolumeController.
        if (!policy.sourceControlEnabled) {
            return new ControlPlan(current, comfortTargetIndex > current,
                    policy.recoveryBlockReason.isEmpty() ? "source_control_disabled" : policy.recoveryBlockReason);
        }

        if (emergencyActive && emergencyTargetIndex < current) {
            int requested = Math.max(0, Math.min(emergencyTargetIndex, max));
            return new ControlPlan(Math.min(current, requested), false, "emergency_downward");
        }

        int comfort = Math.max(0, Math.min(comfortTargetIndex, max));
        if (comfort < current) {
            return new ControlPlan(comfort, false, "comfort_downward");
        }
        if (comfort > current) {
            if (recoveryAllowed && !manualPause && recoveryCeiling > current) {
                int requested = Math.min(comfort, recoveryCeiling);
                return new ControlPlan(requested, false,
                        requested > current ? "adaptive_recovery" : "adaptive_hold");
            }
            String reason = manualPause ? "manual_safety_pause"
                    : recoveryAllowed ? "adaptive_recovery_ceiling" : "adaptive_no_owned_debt";
            return new ControlPlan(current, true, reason);
        }
        return new ControlPlan(Math.min(current, max), false, "hold");
    }

    private HybridEngineCoordinator() {}
}
