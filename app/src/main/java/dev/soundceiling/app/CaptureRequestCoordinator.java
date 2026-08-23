package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure live-capture state machine. Package candidates remain unconfirmed until targeted PCM proves them. */
final class CaptureRequestCoordinator {
    enum Action { KEEP, OPEN_MIXED, OPEN_TARGETED, CLOSE }

    static final class Candidate {
        final SourceDescriptor source;
        final AppPolicy policy;
        final long observedAtMs;
        final String evidenceSource;

        Candidate(SourceDescriptor source, AppPolicy policy, long observedAtMs,
                  String evidenceSource) {
            this.source = Objects.requireNonNull(source, "source");
            this.policy = Objects.requireNonNull(policy, "policy");
            this.observedAtMs = Math.max(0L, observedAtMs);
            this.evidenceSource = evidenceSource == null ? "" : evidenceSource;
        }

        String identityKey() {
            return source.packageName + ':' + source.uid + ':' + policy.mode + ':'
                    + policy.dspPreference + ':' + policy.allowsBoundedRecovery();
        }
    }

    static final class Decision {
        final Action action;
        final PcmCaptureRequest request;
        final boolean sourceConfirmed;
        final boolean positiveControlAllowed;
        final boolean globalDspAllowed;
        final String reason;
        final Candidate candidate;

        private Decision(Action action, PcmCaptureRequest request, boolean sourceConfirmed,
                         boolean positiveControlAllowed, boolean globalDspAllowed,
                         String reason, Candidate candidate) {
            this.action = Objects.requireNonNull(action, "action");
            this.request = request;
            this.sourceConfirmed = sourceConfirmed;
            this.positiveControlAllowed = positiveControlAllowed;
            this.globalDspAllowed = globalDspAllowed;
            this.reason = reason == null ? "" : reason;
            this.candidate = candidate;
        }
    }

    private final long coalescingWindowMs;
    private List<Candidate> candidates = Collections.emptyList();
    private boolean mediaSessionAccess;
    private String candidateSignature = "";
    private long candidatesChangedAtMs;
    private int observedTargetUid = PcmCaptureRequest.NO_TARGET_UID;
    private Boolean observedTargetAudible;
    private int suppressedTargetUid = PcmCaptureRequest.NO_TARGET_UID;
    private String suppressedSignature = "";
    private Candidate confirmedCandidate;

    CaptureRequestCoordinator(long coalescingWindowMs) {
        this.coalescingWindowMs = Math.max(0L, coalescingWindowMs);
    }

    void updateCandidates(List<Candidate> next, boolean accessAvailable, long nowMs) {
        ArrayList<Candidate> clean = new ArrayList<>();
        if (next != null) {
            for (Candidate candidate : next) if (candidate != null) clean.add(candidate);
        }
        clean.sort(Comparator.comparing((Candidate c) -> c.source.packageName)
                .thenComparingInt(c -> c.source.uid));
        String nextSignature = signature(clean, accessAvailable);
        boolean changed = !nextSignature.equals(candidateSignature);
        mediaSessionAccess = accessAvailable;
        candidates = Collections.unmodifiableList(clean);
        if (changed) {
            candidateSignature = nextSignature;
            candidatesChangedAtMs = Math.max(0L, nowMs);
            observedTargetUid = PcmCaptureRequest.NO_TARGET_UID;
            observedTargetAudible = null;
            confirmedCandidate = null;
            if (!nextSignature.equals(suppressedSignature)) {
                suppressedTargetUid = PcmCaptureRequest.NO_TARGET_UID;
                suppressedSignature = "";
            }
        }
    }

    void recordTargetObservation(int uid, boolean stableProgramAudio, long nowMs) {
        if (uid < 0) return;
        observedTargetUid = uid;
        observedTargetAudible = stableProgramAudio;
        Candidate only = uniqueCandidate();
        if (stableProgramAudio && only != null && only.source.uid == uid) {
            confirmedCandidate = only;
            suppressedTargetUid = PcmCaptureRequest.NO_TARGET_UID;
            suppressedSignature = "";
        } else if (!stableProgramAudio) {
            confirmedCandidate = null;
            suppressedTargetUid = uid;
            suppressedSignature = candidateSignature;
        }
    }

    Decision reconcile(PcmCaptureRequest current, PlaybackSnapshot snapshot, long nowMs) {
        PcmCaptureRequest actual = current == null ? PcmCaptureRequest.mixed() : current;
        PlaybackSnapshot playback = snapshot == null
                ? new PlaybackSnapshot(false, Collections.emptyList(), 0, nowMs, false,
                "snapshot_missing") : snapshot;
        Candidate only = uniqueCandidate();
        boolean single = only != null;
        boolean confirmed = single && confirmedCandidate != null
                && sameCandidate(only, confirmedCandidate)
                && observedTargetUid == only.source.uid
                && Boolean.TRUE.equals(observedTargetAudible);
        boolean positiveAllowed = positiveControlAllowed(playback);
        boolean globalAllowed = globalDspAllowed(playback);

        if (!playback.active || playback.observedPlayers <= 0) {
            confirmedCandidate = null;
            if (actual.targeted()) {
                return decision(Action.OPEN_MIXED, PcmCaptureRequest.mixed(), false,
                        positiveAllowed, globalAllowed, "playback_inactive_return_mixed", only);
            }
            return decision(Action.KEEP, actual, false, positiveAllowed, globalAllowed,
                    "playback_inactive", only);
        }

        if (actual.targeted()) {
            if (!single || actual.targetUid != only.source.uid) {
                confirmedCandidate = null;
                return decision(Action.OPEN_MIXED, PcmCaptureRequest.mixed(), false,
                        positiveAllowed, globalAllowed, "stale_target_return_mixed", only);
            }
            if (observedTargetUid == actual.targetUid && Boolean.FALSE.equals(observedTargetAudible)) {
                confirmedCandidate = null;
                return decision(Action.OPEN_MIXED, PcmCaptureRequest.mixed(), false,
                        positiveAllowed, globalAllowed, "target_unconfirmed_return_mixed", only);
            }
            return decision(Action.KEEP, actual, confirmed, positiveAllowed, globalAllowed,
                    confirmed ? "targeted_pcm_confirmed" : "target_probe_pending", only);
        }

        if (!mediaSessionAccess) {
            return decision(Action.KEEP, actual, false, false, false,
                    "media_session_access_unavailable", null);
        }
        if (!single) {
            return decision(Action.KEEP, actual, false, positiveAllowed, globalAllowed,
                    candidates.isEmpty() ? "no_package_candidate" : "multiple_package_candidates", null);
        }
        if (suppressedTargetUid == only.source.uid && suppressedSignature.equals(candidateSignature)) {
            return decision(Action.KEEP, actual, false, positiveAllowed, globalAllowed,
                    "target_unconfirmed_suppressed", only);
        }
        if (nowMs < candidatesChangedAtMs
                || nowMs - candidatesChangedAtMs < coalescingWindowMs) {
            return decision(Action.KEEP, actual, false, positiveAllowed, globalAllowed,
                    "source_churn_coalescing", only);
        }
        return decision(Action.OPEN_TARGETED, PcmCaptureRequest.targeted(only.source.uid), false,
                positiveAllowed, globalAllowed, "stable_candidate_probe_target", only);
    }

    Candidate confirmedCandidate() {
        return confirmedCandidate;
    }

    List<Candidate> candidates() {
        return candidates;
    }

    boolean mediaSessionAccessAvailable() {
        return mediaSessionAccess;
    }

    String sourceStatusLabel() {
        if (!mediaSessionAccess) return "Не подтверждён: нет доступа к активным медиасеансам";
        if (confirmedCandidate != null) return confirmedCandidate.source.displayName;
        Candidate only = uniqueCandidate();
        if (only != null) return "Не подтверждён: " + only.source.displayName;
        if (candidates.size() > 1) return "Не подтверждён: несколько активных источников";
        return "Не подтверждён: источник не определён";
    }

    private Candidate uniqueCandidate() {
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private boolean positiveControlAllowed(PlaybackSnapshot playback) {
        if (!mediaSessionAccess || candidates.isEmpty() || candidates.size() != playback.observedPlayers) {
            return false;
        }
        for (Candidate candidate : candidates) {
            if (!candidate.policy.allowsBoundedRecovery()) return false;
        }
        return true;
    }

    private boolean globalDspAllowed(PlaybackSnapshot playback) {
        if (!positiveControlAllowed(playback)) return false;
        for (Candidate candidate : candidates) {
            if (!candidate.policy.allowsDspControl()) return false;
        }
        return true;
    }

    private static Decision decision(Action action, PcmCaptureRequest request,
                                     boolean sourceConfirmed, boolean positiveControlAllowed,
                                     boolean globalDspAllowed, String reason, Candidate candidate) {
        return new Decision(action, request, sourceConfirmed, positiveControlAllowed,
                globalDspAllowed, reason, candidate);
    }

    private static String signature(List<Candidate> values, boolean accessAvailable) {
        StringBuilder out = new StringBuilder(accessAvailable ? "access:" : "no_access:");
        for (Candidate candidate : values) out.append(candidate.identityKey()).append('|');
        return out.toString();
    }

    private static boolean sameCandidate(Candidate a, Candidate b) {
        return a != null && b != null && a.identityKey().equals(b.identityKey());
    }
}
