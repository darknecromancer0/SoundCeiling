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
    private static final long OWNERSHIP_PROOF_TIMEOUT_MS = 500L;
    private static final long OWNERSHIP_POLL_MS = 10L;

    interface Listener { void onPlaybackEvidenceChanged(); }

    static final class RendererBaseline {
        final boolean valid;
        final List<AudioPlaybackConfiguration> configurations;

        RendererBaseline(boolean valid,
                List<AudioPlaybackConfiguration> configurations) {
            this.valid = valid;
            this.configurations = Collections.unmodifiableList(
                    new ArrayList<>(configurations));
        }
    }

    private final AudioManager audio;
    private final Listener listener;
    private volatile PlaybackSnapshot snapshot = new PlaybackSnapshot(
            false, Collections.emptyList(), 0, 0L, false, "not_started");
    private boolean registered;
    private volatile AudioPlaybackConfiguration ownedRendererConfiguration;

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

    RendererBaseline beginRendererOwnership() {
        if (ownedRendererConfiguration != null) {
            return new RendererBaseline(false, Collections.emptyList());
        }
        try {
            List<AudioPlaybackConfiguration> active =
                    audio.getActivePlaybackConfigurations();
            return new RendererBaseline(true, safeCopy(active));
        } catch (RuntimeException ignored) {
            return new RendererBaseline(false, Collections.emptyList());
        }
    }

    boolean claimRendererOwnership(RendererBaseline baseline) {
        if (baseline == null || !baseline.valid
                || ownedRendererConfiguration != null) {
            return false;
        }
        long deadline = SystemClock.elapsedRealtime()
                + OWNERSHIP_PROOF_TIMEOUT_MS;
        do {
            final List<AudioPlaybackConfiguration> current;
            try {
                current = safeCopy(audio.getActivePlaybackConfigurations());
            } catch (RuntimeException ignored) {
                return false;
            }
            AudioPlaybackConfiguration candidate =
                    RelayPlaybackOwnership.uniqueNewByStableKey(
                            baseline.configurations, current,
                            PlaybackObserver::stableRendererIdentity,
                            PlaybackObserver::isExpectedRenderer);
            if (candidate != null) {
                ownedRendererConfiguration = candidate;
                publish(current);
                if (snapshot.rendererOwnershipProven) return true;
                ownedRendererConfiguration = null;
                publish(current);
                return false;
            }
            SystemClock.sleep(OWNERSHIP_POLL_MS);
        } while (SystemClock.elapsedRealtime() <= deadline);
        return false;
    }

    void clearRendererOwnership() {
        ownedRendererConfiguration = null;
        try {
            publish(audio.getActivePlaybackConfigurations());
        } catch (RuntimeException ignored) {
            snapshot = new PlaybackSnapshot(false, Collections.emptyList(), 0,
                    SystemClock.elapsedRealtime(), false,
                    "renderer_ownership_clear_refresh_failed");
            notifyListener();
        }
    }

    boolean rendererOwnershipProven() {
        PlaybackSnapshot current = snapshot;
        return current.rendererOwnershipExpected
                && current.rendererOwnershipProven;
    }

    private synchronized void publish(
            List<AudioPlaybackConfiguration> configs) {
        long now = SystemClock.elapsedRealtime();
        ArrayList<PlaybackSnapshot.PlayerFact> facts = new ArrayList<>();
        AudioPlaybackConfiguration owned = ownedRendererConfiguration;
        RelayPlaybackOwnership.FilterResult<AudioPlaybackConfiguration>
                filtered = RelayPlaybackOwnership.excludeOwnedByStableKey(
                        safeCopy(configs), owned,
                        PlaybackObserver::stableRendererIdentity);
        boolean ownershipExpected = owned != null;
        boolean ownershipProven = !ownershipExpected
                || filtered.ownershipProven();
        List<AudioPlaybackConfiguration> external = ownershipExpected
                && ownershipProven ? filtered.remaining : safeCopy(configs);
        int activePlayers = 0;
        // AudioManager#getActivePlaybackConfigurations() and the callback payload already
        // contain the currently active configurations. Public API exposes AudioAttributes but
        // not a third-party UID/session mapping suitable for control authority.
        if (external != null) {
            for (AudioPlaybackConfiguration config : external) {
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
                "public_playback_callback", ownershipExpected,
                ownershipProven);
        notifyListener();
    }

    private static String stableRendererIdentity(
            AudioPlaybackConfiguration configuration) {
        if (configuration == null) return null;
        AudioAttributes attributes = configuration.getAudioAttributes();
        if (attributes == null) return null;
        // Public, semantically stable fields only. Do not include mutable route,
        // player state, mute state or device identifiers in ownership identity.
        return attributes.getUsage() + ":" + attributes.getContentType();
    }

    private static boolean isExpectedRenderer(
            AudioPlaybackConfiguration configuration) {
        if (configuration == null) return false;
        AudioAttributes attributes = configuration.getAudioAttributes();
        return attributes != null
                && attributes.getUsage()
                        == AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                && attributes.getContentType()
                        == AudioAttributes.CONTENT_TYPE_MUSIC;
    }

    private static List<AudioPlaybackConfiguration> safeCopy(
            List<AudioPlaybackConfiguration> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<AudioPlaybackConfiguration> copy = new ArrayList<>();
        for (AudioPlaybackConfiguration configuration : source) {
            if (configuration != null) copy.add(configuration);
        }
        return copy;
    }

    private void notifyListener() {
        if (listener == null) return;
        try { listener.onPlaybackEvidenceChanged(); }
        catch (RuntimeException ignored) {}
    }

    @Override public void close() {
        ownedRendererConfiguration = null;
        if (!registered) return;
        registered = false;
        try { audio.unregisterAudioPlaybackCallback(callback); }
        catch (RuntimeException ignored) {}
    }
}
