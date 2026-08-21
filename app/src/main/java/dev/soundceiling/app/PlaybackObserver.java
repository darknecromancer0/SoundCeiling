package dev.soundceiling.app;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Observes public playback activity only. It deliberately makes no package/UID claims. */
final class PlaybackObserver implements AutoCloseable {
    private final AudioManager audio;
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
        this.audio = audio;
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
            return false;
        }
    }

    PlaybackSnapshot snapshot() {
        return snapshot;
    }

    private void publish(List<AudioPlaybackConfiguration> configs) {
        ArrayList<Integer> usages = new ArrayList<>();
        int activePlayers = 0;
        if (configs != null) {
            for (AudioPlaybackConfiguration config : configs) {
                if (config == null || !config.isActive()) continue;
                activePlayers++;
                AudioAttributes attributes = config.getAudioAttributes();
                if (attributes != null) usages.add(attributes.getUsage());
            }
        }
        snapshot = new PlaybackSnapshot(activePlayers > 0, usages, activePlayers,
                SystemClock.elapsedRealtime(), true, "public_playback_callback");
    }

    @Override public void close() {
        if (!registered) return;
        registered = false;
        try { audio.unregisterAudioPlaybackCallback(callback); }
        catch (RuntimeException ignored) {}
    }
}
