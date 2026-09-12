package dev.soundceiling.app;

import java.util.List;

/** Ordered v0.8 explicit Config candidates for third-party non-zero audio sessions. */
final class EnhancedSessionCandidateMatrix {
    enum Variant { FREQUENCY_RESOLUTION, TIME_RESOLUTION }

    static final class Profile {
        final String id;
        final Variant variant;
        final int channelCount;
        final int preEqBandCount;
        final int mbcBandCount;
        final int postEqBandCount;
        final boolean limiterInUse;
        final float preferredFrameDurationMs;
        final boolean explicitConfig;
        final boolean optionalStagesStartDisabled;

        private Profile(String id, Variant variant, int channelCount,
                        int preEqBandCount, int mbcBandCount, int postEqBandCount,
                        boolean limiterInUse, float preferredFrameDurationMs) {
            this.id = id;
            this.variant = variant;
            this.channelCount = channelCount;
            this.preEqBandCount = preEqBandCount;
            this.mbcBandCount = mbcBandCount;
            this.postEqBandCount = postEqBandCount;
            this.limiterInUse = limiterInUse;
            this.preferredFrameDurationMs = preferredFrameDurationMs;
            explicitConfig = true;
            optionalStagesStartDisabled = true;
        }

        boolean preEqInUse() { return preEqBandCount > 0; }
        boolean mbcInUse() { return mbcBandCount > 0; }
        boolean postEqInUse() { return postEqBandCount > 0; }
    }

    private static final float CTS_FRAME_DURATION_MS = 9.5f;
    private static final List<Profile> ORDERED = List.of(
            new Profile("cts_frequency_full_bypass_stereo",
                    Variant.FREQUENCY_RESOLUTION, 2,
                    2, 2, 2, true, CTS_FRAME_DURATION_MS),
            new Profile("frequency_limiter_bypass_stereo",
                    Variant.FREQUENCY_RESOLUTION, 2,
                    0, 0, 0, true, CTS_FRAME_DURATION_MS),
            new Profile("frequency_input_gain_only_stereo",
                    Variant.FREQUENCY_RESOLUTION, 2,
                    0, 0, 0, false, CTS_FRAME_DURATION_MS));

    static List<Profile> orderedProfiles() { return ORDERED; }

    private EnhancedSessionCandidateMatrix() {}
}
