# v0.10 Samsung field check

Install v0.10.0 over the existing app; versionCode 39 and the same development signer.
No root or ADB grant is needed. This is a new field candidate, not physical acceptance.

## 1. Ordinary Media

Use the built-in speaker and a recognized, allowed player. Start at a comfortable
Media level (for comparison, 3/15), Linked Lock ON, Normalization ON. Press Start and
grant playback capture. Play alternating quiet speech and loud speech/music for
30–60 seconds. Allow several seconds per segment. The app should make adjacent
Media changes without redefining the chosen anchor after its own writes. An explicit
Safety Maximum can limit recovery; this must be reported as a cap, not silent success.

Press Volume Down once while processing: automatic changes must stop. Volume Up
must not rearm. Use Stop then Start for the next run. Record what was actually heard,
not just gain values. Ordinary Media has coarse steps and reactive delay.

## 2. Audible PCM Relay

Stop ordinary processing. Keep one recognized, allowed player playing through the
built-in speaker, with Media above zero. Enable SoundCeiling's Accessibility service
if requested, then select «Запустить Relay-тест» and grant playback capture.

Expected sequence: conditions → Media 0 → fresh PCM proof → five-second preview →
confirmation. The original is temporarily muted; the preview should contain one
clean stream. Only if that is what you hear, select «Один чистый тихий поток».
For echo, silence or broken audio select «Эхо / громко / не работает».

After confirmation compare quiet and loud passages. Normal offers up to +24 dB;
Full offers +30 dB, while final sample peak remains bounded. Use Relay volume for
listening level. Press hardware Volume Down: output must stop and stay stopped;
Media must not jump back up. Explicit Start begins a new test. Stop or recovery
restores only the stream values still owned by the app, within current caps.

## What to return

Export the log after each scenario from the existing logs menu. Include: APK version,
source app, initial Media level, last Relay state/reason, whether quiet sections
became louder, whether loud sections became quieter, and whether you heard any echo.
A screenshot of the final state helps when Relay cannot reach the preview.

After the short check succeeds, test 10 minutes, source pause/resume, Stop/Start,
route changes and projection revocation. Measured median <= 120 ms and p95 <= 200 ms
are the current renderer thresholds; actual Samsung latency must be observed.
