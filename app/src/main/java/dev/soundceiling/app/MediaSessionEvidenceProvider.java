package dev.soundceiling.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** Optional package candidates from active MediaSessions. Never verifies a source by itself. */
final class MediaSessionEvidenceProvider implements AutoCloseable {
    interface Listener { void onMediaSessionEvidenceChanged(); }

    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName listenerComponent;
    private final Listener listener;
    private boolean activeListenerRegistered;

    private final MediaSessionManager.OnActiveSessionsChangedListener activeSessionsListener =
            controllers -> notifyListener();

    MediaSessionEvidenceProvider(Context context) {
        this(context, null);
    }

    MediaSessionEvidenceProvider(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(context, SoundCeilingNotificationListener.class);
        this.listener = listener;
    }

    boolean start() {
        return ensureActiveListenerRegistered();
    }

    boolean available() {
        return manager != null && SoundCeilingNotificationListener.isConnected();
    }

    List<CaptureRequestCoordinator.Candidate> currentCaptureCandidates(long nowElapsedMs) {
        ensureActiveListenerRegistered();
        if (!available()) return Collections.emptyList();
        try {
            List<MediaController> controllers = manager.getActiveSessions(listenerComponent);
            if (controllers == null || controllers.isEmpty()) return Collections.emptyList();
            LinkedHashMap<String, CaptureRequestCoordinator.Candidate> unique = new LinkedHashMap<>();
            long observedAt = Math.max(nowElapsedMs, SystemClock.elapsedRealtime());
            for (MediaController controller : controllers) {
                if (controller == null || !isPotentiallyAudible(controller.getPlaybackState())) continue;
                String packageName = controller.getPackageName();
                SourceDescriptor source = PackageSourceRepository.resolve(context, packageName);
                if (source == null) continue;
                AppRule.Mode defaultMode = AppClassifier.defaultMode(
                        source.packageName, source.systemApp, source.samsungApp);
                AppPolicy policy = AppPolicyStore.load(context, source.packageName, defaultMode);
                unique.put(source.packageName, new CaptureRequestCoordinator.Candidate(
                        source, policy, observedAt, "media_session"));
            }
            return Collections.unmodifiableList(new ArrayList<>(unique.values()));
        } catch (SecurityException e) {
            return Collections.emptyList();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    List<PlaybackEvidence> currentCandidates(long epoch) {
        List<CaptureRequestCoordinator.Candidate> candidates =
                currentCaptureCandidates(SystemClock.elapsedRealtime());
        if (candidates.isEmpty()) return Collections.emptyList();
        ArrayList<PlaybackEvidence> out = new ArrayList<>();
        for (CaptureRequestCoordinator.Candidate candidate : candidates) {
            out.add(PlaybackEvidence.mediaSessionCandidate(candidate.source, epoch));
        }
        return Collections.unmodifiableList(out);
    }

    private boolean ensureActiveListenerRegistered() {
        if (activeListenerRegistered) return true;
        if (!available()) return false;
        try {
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent);
            activeListenerRegistered = true;
            return true;
        } catch (SecurityException e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void notifyListener() {
        if (listener == null) return;
        try { listener.onMediaSessionEvidenceChanged(); }
        catch (RuntimeException ignored) {}
    }

    private static boolean isPotentiallyAudible(PlaybackState state) {
        if (state == null) return false;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }

    @Override public void close() {
        if (!activeListenerRegistered || manager == null) return;
        activeListenerRegistered = false;
        try { manager.removeOnActiveSessionsChangedListener(activeSessionsListener); }
        catch (RuntimeException ignored) {}
    }
}
