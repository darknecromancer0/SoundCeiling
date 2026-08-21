package dev.soundceiling.app;

/** Pure final arbiter between app policy, emergency safety and comfort normalization. */
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

        // Explicit source/app OFF precedes all source-specific analysis. The global Safety Lock
        // is enforced independently before this coordinator, so OFF means true hold here.
        if (!policy.sourceControlEnabled) {
            return new ControlPlan(current, comfortTargetIndex > current,
                    policy.raiseBlockReason.isEmpty() ? "source_control_disabled" : policy.raiseBlockReason);
        }

        if (emergencyActive && emergencyTargetIndex < current) {
            int requested = Math.max(0, Math.min(emergencyTargetIndex, max));
            return new ControlPlan(requested, false, "emergency_downward");
        }

        int comfort = Math.max(0, Math.min(comfortTargetIndex, max));
        if (comfort < current) {
            return new ControlPlan(comfort, false, "comfort_downward");
        }
        if (comfort > current) {
            if (manualPause) {
                return new ControlPlan(current, true, "manual_safety_pause");
            }
            if (!policy.allowAutomaticRaise) {
                return new ControlPlan(current, true,
                        policy.raiseBlockReason.isEmpty() ? "raise_blocked_policy" : policy.raiseBlockReason);
            }
            return new ControlPlan(comfort, false, "comfort_upward");
        }
        return new ControlPlan(Math.min(current, max), false, "hold");
    }

    private HybridEngineCoordinator() {}
}
