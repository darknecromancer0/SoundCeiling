package dev.soundceiling.app;

/** Pure final arbiter between app policy, emergency safety and one-way loudness control. */
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

        // Explicit app/source OFF precedes source-specific analysis. Global hard caps are
        // enforced independently by SafeVolumeController.
        if (!policy.sourceControlEnabled) {
            return new ControlPlan(current, comfortTargetIndex > current,
                    policy.raiseBlockReason.isEmpty() ? "source_control_disabled" : policy.raiseBlockReason);
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
            // v0.6 invariant: trust/source/manual state may explain why an old policy wanted an
            // increase, but no policy is authority to move STREAM_MUSIC upward automatically.
            String reason = manualPause ? "manual_safety_pause" : "one_way_hold_below_target";
            return new ControlPlan(current, true, reason);
        }
        return new ControlPlan(Math.min(current, max), false, "hold");
    }

    private HybridEngineCoordinator() {}
}
