package dev.soundceiling.app;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android evidence collector + pure policy/capture bridge. It never writes any volume. */
final class HybridRuntimeResolver implements AutoCloseable {
    private static final long CAPTURE_SOURCE_COALESCE_MS = 250L;

    static final class Snapshot {
        final PlaybackSnapshot playback;
        final List<PlaybackEndpoint> playbackEndpoints;
        final SourceSet sources;
        final PcmAvailabilityState pcmState;
        final EngineCapabilities capabilities;
        final EffectivePolicy policy;
        final SourceDescriptor exactSource;
        final AppPolicy exactAppPolicy;
        final long sourceChangedAtMs;
        final String sourceStatusLabel;
        final CaptureRequestCoordinator.SourceAccessState sourceAccessState;

        Snapshot(PlaybackSnapshot playback, List<PlaybackEndpoint> playbackEndpoints,
                 SourceSet sources, PcmAvailabilityState pcmState,
                 EngineCapabilities capabilities, EffectivePolicy policy,
                 SourceDescriptor exactSource, AppPolicy exactAppPolicy, long sourceChangedAtMs,
                 String sourceStatusLabel, CaptureRequestCoordinator.SourceAccessState sourceAccessState) {
            this.playback = playback;
            this.playbackEndpoints = Collections.unmodifiableList(new ArrayList<>(playbackEndpoints));
            this.sources = sources;
            this.pcmState = pcmState;
            this.capabilities = capabilities;
            this.policy = policy;
            this.exactSource = exactSource;
            this.exactAppPolicy = exactAppPolicy;
            this.sourceChangedAtMs = sourceChangedAtMs;
            this.sourceStatusLabel = sourceStatusLabel == null ? "" : sourceStatusLabel;
            this.sourceAccessState = sourceAccessState == null
                    ? CaptureRequestCoordinator.SourceAccessState.ACCESS_MISSING : sourceAccessState;
        }
    }

    private final Context context;
    private final PlaybackObserver observer;
    private final MediaSessionEvidenceProvider sessions;
    private final AtomicBoolean captureReconcileRequested = new AtomicBoolean(true);
    private long epoch = 1L;
    private long sourceChangedAtMs;
    private String sourceSignature = "";
    private CaptureRequestCoordinator captureCoordinator =
            new CaptureRequestCoordinator(CAPTURE_SOURCE_COALESCE_MS);
    private List<PlaybackEvidence> candidateEvidence = Collections.emptyList();

    HybridRuntimeResolver(Context context, AudioManager audio) {
        this.context = context.getApplicationContext();
        this.observer = new PlaybackObserver(audio, this::requestCaptureReconcile);
        this.sessions = new MediaSessionEvidenceProvider(context, this::requestCaptureReconcile);
    }

    boolean start() {
        boolean observerReady = observer.start();
        sessions.start();
        refreshCandidates(SystemClock.elapsedRealtime());
        return observerReady;
    }

    /** Start conservative. Live reconciliation may promote mixed capture after 250 ms of stability. */
    PcmCaptureRequest prepareCaptureRequest() {
        refreshCandidates(SystemClock.elapsedRealtime());
        return PcmCaptureRequest.mixed();
    }

    CaptureRequestCoordinator.Decision reconcileCapture(PcmCaptureBackend capture, long nowElapsedMs) {
        refreshCandidates(nowElapsedMs);
        if (capture != null && capture.targeted()) {
            PcmCaptureBackend.TargetWarmupStatus warmup = capture.targetWarmupStatus(nowElapsedMs);
            if (warmup == PcmCaptureBackend.TargetWarmupStatus.CONFIRMED) {
                captureCoordinator.recordTargetObservation(capture.targetUid(), true, nowElapsedMs);
                DiagnosticLog.transition("target_probe", "confirmed", "uid=" + capture.targetUid());
            } else if (warmup == PcmCaptureBackend.TargetWarmupStatus.FAILED) {
                captureCoordinator.recordTargetObservation(capture.targetUid(), false, nowElapsedMs);
                DiagnosticLog.transition("target_probe", "silent", "uid=" + capture.targetUid());
            }
        }
        PcmCaptureRequest current = capture == null ? PcmCaptureRequest.mixed() : capture.request();
        CaptureRequestCoordinator.Decision decision = captureCoordinator.reconcile(
                current, observer.snapshot(), nowElapsedMs);
        captureReconcileRequested.set(false);
        return decision;
    }

    void recordTargetOpenFailure(int targetUid, long nowElapsedMs) {
        captureCoordinator.recordTargetObservation(targetUid, false, nowElapsedMs);
        DiagnosticLog.transition("target_probe", "open_failed", "uid=" + targetUid);
        captureReconcileRequested.set(true);
    }

    boolean consumeCaptureReconcileRequest() {
        return captureReconcileRequested.getAndSet(false);
    }

    String sourceStatusLabel() {
        return captureCoordinator.sourceStatusLabel();
    }

    Snapshot resolvePcm(PcmCaptureBackend capture, boolean validPcm, boolean signalPresent,
                        boolean outputMixEvidence, ControlProfile globalProfile,
                        DeviceProfileV2 deviceProfile, long nowElapsedMs) {
        PlaybackSnapshot playback = observer.snapshot();
        refreshCandidates(nowElapsedMs);
        ArrayList<PlaybackEvidence> evidence = new ArrayList<>(candidateEvidence);
        CaptureRequestCoordinator.Candidate confirmed = captureCoordinator.confirmedCandidate();
        if (capture != null && capture.targeted() && confirmed != null
                && capture.targetUid() == confirmed.source.uid
                && capture.targetWarmupStatus(nowElapsedMs)
                == PcmCaptureBackend.TargetWarmupStatus.CONFIRMED) {
            evidence.add(PlaybackEvidence.targetedPcmProof(confirmed.source, epoch));
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
                .noValidPcmMs(noPcmMs == Long.MAX_VALUE
                        ? PcmStateResolver.BLOCKED_AFTER_MS + 1 : noPcmMs)
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
        refreshCandidates(nowElapsedMs);
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
        SourceDescriptor exactSource = sources.confidence
                == EngineCapabilities.SourceIdentityConfidence.EXACT
                && sources.sources().size() == 1 ? sources.sources().get(0) : null;
        AppPolicy exactPolicy = null;
        if (exactSource != null) {
            AppRule.Mode defaultMode = AppClassifier.defaultMode(exactSource.packageName,
                    exactSource.systemApp, exactSource.samsungApp);
            exactPolicy = AppPolicyStore.load(context, exactSource.packageName, defaultMode);
        }
        List<PlaybackEndpoint> endpoints = PlaybackEndpointResolver.resolve(playback,
                captureCoordinator.candidates(), captureCoordinator.confirmedCandidate());
        return new Snapshot(playback, endpoints, sources, pcm, capabilities, policy,
                exactSource, exactPolicy, sourceChangedAtMs, captureCoordinator.sourceStatusLabel(), captureCoordinator.sourceAccessState());
    }

    private void refreshCandidates(long nowElapsedMs) {
        List<CaptureRequestCoordinator.Candidate> fresh = sessions.currentCaptureCandidates(nowElapsedMs);
        boolean access = sessions.available();
        captureCoordinator.updateCandidates(fresh, access, nowElapsedMs);
        DiagnosticLog.transition("media_session_access", access ? "available" : "missing",
                "candidates=" + fresh.size());
        StringBuilder candidateLog = new StringBuilder();
        for (CaptureRequestCoordinator.Candidate candidate : fresh) {
            if (candidateLog.length() > 0) candidateLog.append(',');
            candidateLog.append(candidate.source.packageName).append(':').append(candidate.source.uid);
        }
        DiagnosticLog.transition("source_candidates", fresh.isEmpty() ? "none" : candidateLog.toString(),
                "count=" + fresh.size());
        if (fresh.isEmpty()) {
            candidateEvidence = Collections.emptyList();
            return;
        }
        ArrayList<PlaybackEvidence> evidence = new ArrayList<>();
        for (CaptureRequestCoordinator.Candidate candidate : fresh) {
            evidence.add(PlaybackEvidence.mediaSessionCandidate(candidate.source, epoch));
        }
        candidateEvidence = Collections.unmodifiableList(evidence);
    }

    private void requestCaptureReconcile() {
        captureReconcileRequested.set(true);
    }

    private void markSourceTransition(SourceSet sources, long nowElapsedMs) {
        StringBuilder signature = new StringBuilder(sources.confidence.name()).append(':');
        for (SourceDescriptor source : sources.sources()) {
            signature.append(source.packageName).append(':').append(source.uid).append('|');
        }
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
        captureCoordinator = new CaptureRequestCoordinator(CAPTURE_SOURCE_COALESCE_MS);
        candidateEvidence = Collections.emptyList();
        captureReconcileRequested.set(true);
    }

    @Override public void close() {
        observer.close();
        sessions.close();
    }
}
