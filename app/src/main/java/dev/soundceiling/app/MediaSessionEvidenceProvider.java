package dev.soundceiling.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional package evidence from active MediaSessions. Never promotes a source above LIKELY by itself. */
final class MediaSessionEvidenceProvider {
    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName listenerComponent;

    MediaSessionEvidenceProvider(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(context, SoundCeilingNotificationListener.class);
    }

    boolean available() {
        return manager != null && SoundCeilingNotificationListener.isConnected();
    }

    List<PlaybackEvidence> currentCandidates(long epoch) {
        if (!available()) return Collections.emptyList();
        try {
            List<MediaController> controllers = manager.getActiveSessions(listenerComponent);
            if (controllers == null || controllers.isEmpty()) return Collections.emptyList();
            ArrayList<PlaybackEvidence> out = new ArrayList<>();
            for (MediaController controller : controllers) {
                if (controller == null || !isPotentiallyAudible(controller.getPlaybackState())) continue;
                String packageName = controller.getPackageName();
                SourceDescriptor source = PackageSourceRepository.resolve(context, packageName);
                if (source != null) {
                    out.add(PlaybackEvidence.mediaSessionCandidate(source, epoch));
                }
            }
            return Collections.unmodifiableList(out);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
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
}
