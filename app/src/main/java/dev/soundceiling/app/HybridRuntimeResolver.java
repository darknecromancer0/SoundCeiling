package dev.soundceiling.app;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Android evidence collector + pure policy bridge. It never writes any volume. */
final class HybridRuntimeResolver implements AutoCloseable {
    static final class Snapshot {
        final PlaybackSnapshot playback;
        final SourceSet sources;
        final PcmAvailabilityState pcmState;
        final EngineCapabilities capabilities;
        final EffectivePolicy policy;
        final SourceDescriptor exactSource;
        final AppPolicy exactAppPolicy;
        final long sourceChangedAtMs;

        Snapshot(PlaybackSnapshot playback, SourceSet sources, PcmAvailabilityState pcmState,
                 EngineCapabilities capabilities, EffectivePolicy policy,
                 SourceDescriptor exactSource, AppPolicy exactAppPolicy, long sourceChangedAtMs) {
            this.playback = playback;
            this.sources = sources;
            this.pcmState = pcmState;
            this.capabilities = capabilities;
            this.policy = policy;
            this.exactSource = exactSource;
            this.exactAppPolicy = exactAppPolicy;
            this.sourceChangedAtMs = sourceChangedAtMs;
        }
    }

    private final Context context;
    private final PlaybackObserver observer;
    private final MediaSessionEvidenceProvider sessions;
    private long epoch = 1L;
    private long sourceChangedAtMs;
    private String sourceSignature = "";
    private SourceDescriptor captureTarget;
    private List<PlaybackEvidence> candidateEvidence = Collections.emptyList();

    HybridRuntimeResolver(Context context, AudioManager audio) {
        this.context = context.getApplicationContext();
        this.observer = new PlaybackObserver(audio);
        this.sessions = new MediaSessionEvidenceProvider(context);
    }

    boolean start() {
        return observer.start();
    }

    PcmCaptureRequest prepareCaptureRequest() {
        refreshCandidates();
        SourceSet candidates = SourceResolver.resolve(candidateEvidence, epoch);
        if (candidates.sources().size() == 1
                && candidates.confidence == EngineCapabilities.SourceIdentityConfidence.LIKELY) {
            captureTarget = candidates.sources().get(0);
            return PcmCaptureRequest.targeted(captureTarget.uid);
        }
        captureTarget = null;
        return PcmCaptureRequest.mixed();
    }

    Snapshot resolvePcm(PcmCaptureBackend capture, boolean validPcm, boolean signalPresent,
                        boolean outputMixEvidence, ControlProfile globalProfile,
                        DeviceProfileV2 deviceProfile, long nowElapsedMs) {
        PlaybackSnapshot playback = observer.snapshot();
        refreshCandidates();
        ArrayList<PlaybackEvidence> evidence = new ArrayList<>(candidateEvidence);
        if (capture != null && capture.targeted() && captureTarget != null
                && validPcm && signalPresent && capture.targetUid() == captureTarget.uid) {
            evidence.add(PlaybackEvidence.targetedPcmProof(captureTarget, epoch));
        }
        SourceSet sources = SourceResolver.resolve(evidence, epoch);
        markSourceTransition(sources, nowElapsedMs);

        long noPcmMs = capture == null ? Long.MAX_VALUE : capture.sampleAgeMs(nowElapsedMs);
        PcmStateResolver.Input input = new PcmStateResolver.Input.Builder()
                .playbackActive(playback.active)
                .captureRequested(capture != null)
                .captureHealthy(capture != null && capture.healthy())
                .sourceEligible(true)
                .validPcm(validPcm)
                .signalPresent(signalPresent)
                .independentAudioEvidence(outputMixEvidence)
                .noValidPcmMs(noPcmMs == Long.MAX_VALUE ? PcmStateResolver.BLOCKED_AFTER_MS + 1 : noPcmMs)
                .build();
        PcmAvailabilityState pcm = PcmStateResolver.resolve(input).state;
        boolean exactPcm = capture != null && capture.targeted()
                && sources.confidence == EngineCapabilities.SourceIdentityConfidence.EXACT;
        EngineCapabilities capabilities = new EngineCapabilities(
                playback.observerHealthy ? EngineCapabilities.PlaybackObservationCapability.AVAILABLE
                        : EngineCapabilities.PlaybackObservationCapability.DEGRADED,
                sources.confidence,
                exactPcm ? EngineCapabilities.MeteringCapability.PCM_EXACT
                        : validPcm ? EngineCapabilities.MeteringCapability.PCM_MIXED
                        : outputMixEvidence ? EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS
                        : EngineCapabilities.MeteringCapability.ACTIVITY_ONLY,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                capture != null && capture.healthy(),
                exactPcm ? "targeted_pcm_verified" : "shared_or_unverified_source");
        return buildSnapshot(playback, sources, pcm, capabilities, globalProfile, deviceProfile,
                nowElapsedMs);
    }

    Snapshot resolveFallback(boolean outputMixAvailable, ControlProfile globalProfile,
                             DeviceProfileV2 deviceProfile, long nowElapsedMs) {
        PlaybackSnapshot playback = observer.snapshot();
        refreshCandidates();
        SourceSet sources = SourceResolver.resolve(candidateEvidence, epoch);
        markSourceTransition(sources, nowElapsedMs);
        EngineCapabilities capabilities = new EngineCapabilities(
                playback.observerHealthy ? EngineCapabilities.PlaybackObservationCapability.AVAILABLE
                        : EngineCapabilities.PlaybackObservationCapability.DEGRADED,
                sources.confidence,
                outputMixAvailable ? EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS
                        : EngineCapabilities.MeteringCapability.ACTIVITY_ONLY,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                true,
                outputMixAvailable ? "safe_output_mix_fallback" : "system_limiter_only");
        PcmAvailabilityState pcm = playback.active
                ? PcmAvailabilityState.UNCERTAIN : PcmAvailabilityState.IDLE;
        return buildSnapshot(playback, sources, pcm, capabilities, globalProfile, deviceProfile,
                nowElapsedMs);
    }

    private Snapshot buildSnapshot(PlaybackSnapshot playback, SourceSet sources,
                                   PcmAvailabilityState pcm, EngineCapabilities capabilities,
                                   ControlProfile globalProfile, DeviceProfileV2 deviceProfile,
                                   long nowElapsedMs) {
        Map<String, AppPolicy> overrides = AppPolicyStore.allOverrides(context);
        SystemStreamPolicy media = deviceProfile.streamPolicies().get(SystemStreamPolicy.Kind.MEDIA);
        EffectivePolicy policy = PolicyResolver.resolve(globalProfile, deviceProfile, sources,
                overrides, media, capabilities, pcm, nowElapsedMs, sourceChangedAtMs);
        SourceDescriptor exactSource = sources.confidence == EngineCapabilities.SourceIdentityConfidence.EXACT
                && sources.sources().size() == 1 ? sources.sources().get(0) : null;
        AppPolicy exactPolicy = null;
        if (exactSource != null) {
            AppRule.Mode defaultMode = AppClassifier.defaultMode(exactSource.packageName,
                    exactSource.systemApp, exactSource.samsungApp);
            exactPolicy = AppPolicyStore.load(context, exactSource.packageName, defaultMode);
        }
        return new Snapshot(playback, sources, pcm, capabilities, policy,
                exactSource, exactPolicy, sourceChangedAtMs);
    }

    private void refreshCandidates() {
        List<PlaybackEvidence> fresh = sessions.currentCandidates(epoch);
        candidateEvidence = fresh == null ? Collections.emptyList() : fresh;
    }

    private void markSourceTransition(SourceSet sources, long nowElapsedMs) {
        StringBuilder signature = new StringBuilder(sources.confidence.name()).append(':');
        for (SourceDescriptor source : sources.sources()) signature.append(source.packageName).append('|');
        String next = signature.toString();
        if (!next.equals(sourceSignature)) {
            sourceSignature = next;
            sourceChangedAtMs = nowElapsedMs;
            DiagnosticLog.event("source_transition", "signature=" + next);
        }
    }

    void newEpoch() {
        epoch++;
        sourceSignature = "";
        sourceChangedAtMs = SystemClock.elapsedRealtime();
        captureTarget = null;
        candidateEvidence = Collections.emptyList();
    }

    @Override public void close() {
        observer.close();
    }
}
