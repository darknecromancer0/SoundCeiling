package dev.soundceiling.app;

/** Pure final arbiter between emergency safety, comfort normalization and resolved policy. */
final class HybridEngineCoordinator {
    static final class ControlPlan {
        final int requestedIndex;
        final boolean raiseBlocked;
        final String reason;

        ControlPlan(int requestedIndex, boolean raiseBlocked, String reason) {
            this.requestedIndex = requestedIndex;
            this.raiseBlocked = raiseBlocked;
            this.reason = reason == null ? "" : reason;
        }
    }

    static ControlPlan plan(int currentIndex,
                            int emergencyTargetIndex,
                            int comfortTargetIndex,
                            int effectiveMaxIndex,
                            EffectivePolicy policy,
                            boolean manualPause,
                            boolean emergencyActive) {
        if (policy == null) throw new IllegalArgumentException("policy == null");
        int max = Math.max(0, effectiveMaxIndex);
        int current = Math.max(0, currentIndex);

        if (emergencyActive && emergencyTargetIndex < current) {
            int requested = Math.max(0, Math.min(emergencyTargetIndex, max));
            return new ControlPlan(requested, false, "emergency_downward");
        }

        if (!policy.sourceControlEnabled) {
            return new ControlPlan(Math.min(current, max), comfortTargetIndex > current,
                    policy.raiseBlockReason.isEmpty() ? "source_control_disabled" : policy.raiseBlockReason);
        }

        int comfort = Math.max(0, Math.min(comfortTargetIndex, max));
        if (comfort < current) {
            return new ControlPlan(comfort, false, "comfort_downward");
        }
        if (comfort > current) {
            if (manualPause) {
                return new ControlPlan(Math.min(current, max), true, "manual_safety_pause");
            }
            if (!policy.allowAutomaticRaise) {
                return new ControlPlan(Math.min(current, max), true,
                        policy.raiseBlockReason.isEmpty() ? "raise_blocked_policy" : policy.raiseBlockReason);
            }
            return new ControlPlan(comfort, false, "comfort_upward");
        }
        return new ControlPlan(Math.min(current, max), false, "hold");
    }

    private HybridEngineCoordinator() {}
}
