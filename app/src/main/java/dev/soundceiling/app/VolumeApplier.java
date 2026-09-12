package dev.soundceiling.app;

import android.media.AudioManager;

/** Low-level Android Media writer. Callers must go through SafeVolumeController. */
final class VolumeApplier {
    private final AudioManager audio;

    VolumeApplier(AudioManager audio) { this.audio = audio; }

    int readIndex() { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }

    int applyIndex(int target, int fallbackIndex) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (RuntimeException e) {
            DiagnosticLog.event("volume_apply_error",
                    "target=" + target + " errorClass=" + e.getClass().getSimpleName());
            try { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
            catch (RuntimeException ignored) { return fallbackIndex; }
        }
    }
}
