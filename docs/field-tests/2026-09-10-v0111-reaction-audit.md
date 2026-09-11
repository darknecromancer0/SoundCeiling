# v0.11.1: reaction and compact controls

The user reports that v0.11 finally achieves the intended normalization on Samsung SM-A528B,
Android 14, with occasional loud fragments lasting approximately half a second. The requested
follow-up keeps the independent user target and replaces the large overlay with Samsung-like
vertical capsules. This is the first positive listening report for that architecture.

## New evidence

Both complete supplied logs were parsed, with duplicate diagnostic-dump lines removed for counts:

| Log | Evidence |
| --- | --- |
| `SoundCeiling-20260910-200028.log.txt` | Approximately 14.5 s; no active source, own-slider gestures. Decreasing one percentage point repeatedly triggers a whole physical step and, at index 1, mute then recovery to 1. |
| `SoundCeiling-20260910-200046.log.txt` | Approximately 342.6 s; exact Yandex Music UID 10292 and anddea.youtube UID 11593; 139 actual ordinary writes, both upward and downward. |

The longer run has 36 capture reopen events: 18 targeted opens and 18 returns to mixed capture
because public playback became inactive. Seventeen mixed→targeted pairs have gaps of
436–6686 ms; most are roughly 0.5–1.2 s. Warmup and the preceding close add further delay.
These events support retaining capture through brief pauses/seeks, while still disabling control
whenever playback/source evidence is unavailable.

The logged per-block detect→write interval is 6–121 ms (median 12 ms), but it starts **after** the
AudioRecord read. It does not measure how long an audible spike escaped. v0.11 also adds a 40 ms
dwell per downward step and uses a 60 ms loudness attack envelope. Walking several Media steps
therefore adds avoidable time. The v0.11.1 log adds `attackLoudness` and `captureToWriteMs` to actual
write events; the latter is -1 when Android supplies no valid aligned capture timestamp.

The initial reference remains fixed at approximately -4.4068 dB while the user's desired values
change. This supports the v0.11 correction: automatic Media motion no longer drags the target down.

PCM Shadow was disabled and Relay stayed OFF. Relay abort entries here are normal capture/Stop
cleanup; they are not failed Relay starts. Neither feature explains the observed successful
normalization. Shadow cannot improve the audible attack because it does not render audio. Relay
processes before its own rendering and may improve transient control if the Samsung muted-capture
and separate-output experiment succeeds; that remains untested on this device.

## Changes and regression coverage

- A >=6 dB estimated overshoot uses the faster block measurement and selects an appropriate lower
  nonzero index immediately. The peak guard can also request immediate attenuation. Small
  corrections keep the normal dwell, recovery stays one step at a time, and a short recovery hold
  prevents an immediate rebound after a fast reduction.
- The final writer has a dedicated fast downward path. It does not reuse HARD_CAP/QUIET_NOW to
  bypass the user veto. Pause, a changed fresh Media read, acknowledgement and the nonzero floor
  remain enforced. Legacy downward callers retain their old step limit.
- Targeted AudioRecord survives a short inactive interval of up to 1500 ms. This does not grant
  source confirmation or positive-control permission during inactivity. A different source or
  lost access invalidates the target; a long pause returns to mixed capture.
- Own-slider percentage callbacks no longer cause repeated forced whole-step writes. Hardware
  Down remains an immediate physical reduction and pause. A downward own gesture cannot unmute.
- Two vertical capsules provide desired volume and maximum. Ellipsis opens the expanded card;
  outside touch/close dismisses it, and the idle timeout is approximately 2 s without closing an
  in-progress drag. Android Accessibility remains the existing overlay host.

Tests first reproduced the old delayed step walk, immediate capture teardown and 1→0→1 gesture
cycle. A real generated stereo PCM quiet→loud transition runs through LoudnessMeter, the
coordinator and SafeVolumeController with only Android I/O simulated. The first loud block requests
and applies 9→2 in one write. Separate assertions retain the Down veto and reject a stale read.
All 77 local workflow check scripts pass. Code review found and corrected a mismatch between
the capsule fill and touch coordinates; both now use the same full-height endpoints. The
overlay interaction suite also passes after that correction (40 checks). These tests do not
establish a measured audible latency on Samsung.

## Next phone check

1. Install over v0.11.0. Keep the same desired volume/maximum and use ordinary Start; Shadow and
   Relay are not needed for this comparison.
2. Replay a fragment that previously escaped loudly. Include a quiet→loud transition, a short
   pause/seek, and resumed playback. Export a fresh log if the delay persists.
3. Press a volume key. Check that two capsules appear beside Samsung, move correctly up/down,
   show distinct speaker/maximum icons, and disappear after about 2 s. Tap elsewhere to close.
4. Open ellipsis, check the expanded settings, Pause/Continue and close. Hold a capsule while
   dragging for more than 2 s: it must stay open. Down must still pause and Stop must work once.
5. If desired, test Relay separately with the existing quiet preview and confirmation. Report
   whether it reaches preview and whether sound is clean; do not combine that result with the
   ordinary-mode comparison.
