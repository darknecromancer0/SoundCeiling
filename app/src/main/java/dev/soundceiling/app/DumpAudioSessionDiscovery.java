package dev.soundceiling.app;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Android implementation backed by the AudioPolicy dumpsys surface exposed by DUMP permission. */
final class DumpAudioSessionDiscovery implements AudioSessionDiscovery {
    private static final int MAX_DUMP_CHARS = 2 * 1024 * 1024;

    private final Context context;

    DumpAudioSessionDiscovery(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public Snapshot discover(long nowMs) {
        if (context.checkSelfPermission(EnhancedSessionSetup.DUMP_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return Snapshot.permissionMissing(nowMs);
        }
        Process process = null;
        try {
            process = new ProcessBuilder("dumpsys", "media.audio_policy")
                    .redirectErrorStream(true)
                    .start();
            String dump = readBounded(process);
            int exit = process.waitFor();
            if (exit != 0) return Snapshot.failed("audio_policy_dump_exit_" + exit, nowMs);
            List<AudioSessionRecord> records = AudioSessionDumpParser.parse(dump, nowMs);
            return Snapshot.success(records, nowMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Snapshot.failed("audio_policy_dump_interrupted", nowMs);
        } catch (IOException | RuntimeException error) {
            return Snapshot.failed("audio_policy_dump_failed:" + error.getClass().getSimpleName(), nowMs);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static String readBounded(Process process) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() + line.length() + 1 > MAX_DUMP_CHARS) {
                    throw new IOException("audio_policy_dump_too_large");
                }
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}