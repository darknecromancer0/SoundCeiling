package dev.soundceiling.app;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Observes immutable public playback facts only. It deliberately makes no package/UID claims. */
final class PlaybackObserver implements AutoCloseable {
    interface Listener { void onPlaybackEvidenceChanged(); }

    private final AudioManager audio;
    private final Listener listener;
    private volatile PlaybackSnapshot snapshot = new PlaybackSnapshot(
            false, Collections.emptyList(), 0, 0L, false, "not_started");
    private boolean registered;

    private final AudioManager.AudioPlaybackCallback callback =
            new AudioManager.AudioPlaybackCallback() {
                @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                    publish(configs);
                }
            };

    PlaybackObserver(AudioManager audio) {
        this(audio, null);
    }

    PlaybackObserver(AudioManager audio, Listener listener) {
        this.audio = audio;
        this.listener = listener;
    }

    boolean start() {
        if (registered) return true;
        try {
            audio.registerAudioPlaybackCallback(callback, null);
            registered = true;
            publish(audio.getActivePlaybackConfigurations());
            return true;
        } catch (RuntimeException e) {
            snapshot = new PlaybackSnapshot(false, Collections.emptyList(), 0,
                    SystemClock.elapsedRealtime(), false,
                    "observer_start_failed:" + e.getClass().getSimpleName());
            notifyListener();
            return false;
        }
    }

    PlaybackSnapshot snapshot() {
        return snapshot;
    }

    private void publish(List<AudioPlaybackConfiguration> configs) {
        long now = SystemClock.elapsedRealtime();
        ArrayList<PlaybackSnapshot.PlayerFact> facts = new ArrayList<>();
        int activePlayers = 0;
        // AudioManager#getActivePlaybackConfigurations() and the callback payload already
        // contain the currently active configurations. Public API exposes AudioAttributes but
        // not a third-party UID/session mapping suitable for control authority.
        if (configs != null) {
            for (AudioPlaybackConfiguration config : configs) {
                if (config == null) continue;
                activePlayers++;
                AudioAttributes attributes = config.getAudioAttributes();
                int usage = attributes == null ? AudioAttributes.USAGE_UNKNOWN : attributes.getUsage();
                int contentType = attributes == null
                        ? AudioAttributes.CONTENT_TYPE_UNKNOWN : attributes.getContentType();
                facts.add(new PlaybackSnapshot.PlayerFact(usage, contentType, now));
            }
        }
        snapshot = PlaybackSnapshot.fromPlayerFacts(facts, activePlayers, now, true,
                "public_playback_callback");
        notifyListener();
    }

    private void notifyListener() {
        if (listener == null) return;
        try { listener.onPlaybackEvidenceChanged(); }
        catch (RuntimeException ignored) {}
    }

    @Override public void close() {
        if (!registered) return;
        registered = false;
        try { audio.unregisterAudioPlaybackCallback(callback); }
        catch (RuntimeException ignored) {}
    }
}
