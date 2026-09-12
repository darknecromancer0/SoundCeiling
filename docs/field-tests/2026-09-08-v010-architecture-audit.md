# SoundCeiling v0.10 architecture audit

Evidence reviewed through 2026-09-08. Physical device: Samsung SM-A528B, Android 14.
Repository baseline: `7a3e7c45c3826791b414769a3062b6cfecd3f220`, v0.9.2, PR #8,
`feature/v0.7-adaptive-envelope`. Baseline Actions run 33934450796 succeeded.

## Coverage and limits

The recovered set contains 101 downloaded log files, 88 unique contents by SHA-256,
147,116 raw lines and 30 version headers from v0.3 to v0.9.1. Repeated ring-buffer
entries are not separate experiments. Nine September 3 logs are v0.9.1, despite
v0.9.2 being the newest built APK. There is no recovered physical v0.9.2 acceptance.
The repository history, previous specifications and incident reports were compared
with those field records. Prior conversation retrieval returned excerpts, plus the
project conversation summaries; this is not a claim of unrestricted full-transcript
access to every chat.

## What worked, and what failed

| Evidence | Actual behavior | Architectural implication |
|---|---|---|
| Early Media versions, through v0.7.5.2 | The actuator changes physical Media, sometimes too far. In `SoundCeiling-20260824-050455.log.txt`, user 1→2 at t=449392032 is followed by PEAK_EMERGENCY 2→1 about 10 ms later. | The actuator is usable. Raw captured peaks were being treated as physical output emergencies; user control and the source/output distinction must be fixed. |
| v0.7.7.8 | Session effect readback could verify, yet normalization held on `dsp_no_output_loudness` with UNKNOWN reference. | Parameter readback does not establish useful audible authority. |
| v0.7.7.9 / v0.8 | Recovered logs contain 19 / 58 unique `session_dsp_apply` records with applied=false. The v0.9 incident analysis also records neutral effect bypass of Samsung Media authority. | Keep the Session DSP quarantine; re-enabling a known broken transport would repeat the regression. |
| v0.9.1, September 3 | Targeted Yandex PCM is present at Media 0. `SoundCeiling-20260903-115722.log.txt` includes source peak −0.797 and loudness −7.734 while route gain is −80. | On this phone, public playback capture can retain the source while original Media is muted. This is the factual basis for Relay. |
| v0.9.1 Relay | Seven PREFLIGHT entries abort in roughly 3–6 ms, with final reason overwritten by `relay_cleanup_complete`; no rendered output established. | Requiring already-proven PRE reference before the mute experiment prevents the experiment that can prove it. Cleanup also hides the failure. |
| v0.9.2 code reproduced locally | At user anchor 3, Samsung gain −43, source loudness −8: 301 ticks over 30 s remain ambiguous with zero writes. | Full PRE/POST interval plus an absolute −20 target cannot track the requested listening level. |
| Short-block PCM reproduced locally | A quiet −42 source at a −18 target stalls near −29.87 at 10 ms cadence. | The command deadband discards small envelope updates. A sample processor needs continuous gain state, independent of actuator command thresholds. |

## v0.10 changes

Ordinary Media now uses source PCM plus the vendor Media curve as an explicitly
estimated output level. Linked target is `profile.targetLoudness + curve(userAnchor)`;
it is not clamped by the UI's −60 dB scale. Own acknowledgements preserve the user
anchor. Adjacent steps require improved error, sustained for 80 ms DOWN / 400 ms UP.
Candidate peak checks use estimated output at the next step, not unattenuated source
peak. Explicit caps, mute, app policy, OFF/zero strength and detected-down pause apply.
The Android write bridge now shares the controller floor policy: the old implicit
anchor-minus-18 dB floor belongs only to historical fallback, not AUTO_MEDIA. A
regression reproduces the old unlinked target stall at index 10 from anchor 15;
the corrected bridge reaches index 3 for source −8 and target −50.

Relay admits UNKNOWN reference only to a fresh proof experiment: persist the Media
lease, acknowledge zero, discard queued samples, then require at least 500 ms fresh
non-silent capture. Only then is a bounded five-second preview rendered. The preview
retains the current Accessibility volume; an unused zero Accessibility stream is
initialized near the saved nonzero Media route gain. Pre-existing Media mute prevents
rendering. The user confirms one clean stream before active output.

The PCM envelope evolves on every block and bounds the actual samples before output.
Linked digital normalization no longer incorporates hardware Accessibility attenuation
into its gain target. Available gain is +24 dB normally and +30 dB in Full; final PCM
peak is at most −6 dBFS, preview peak at most −18 dBFS. Detected Volume Down stops Relay,
keeps original Media muted and latches processing off until explicit restart. Capture
drain is bounded, stop-safe, and failure causes survive cleanup.

## Verification boundary

Focused tests exercise real PCM arrays and the actual coordinator/write bridge and
Relay state machine. Android I/O is simulated in the latter two harnesses. They cover
quiet/loud convergence, sample peaks, source/policy caps, own ACKs, the pause latch,
fresh muted proof, startup volume and cleanup. CI assembles the real Android sources
and checks the stable APK signer. Neither desktop simulation nor compilation proves
Samsung routing, absence of echo, audible leveling or real playback latency. These
remain the next physical field check, recorded in the v0.10 checklist.

## Platform basis

[Android playback capture documentation](https://developer.android.com/media/platform/av-capture)
describes a copy of allowed playback, with MediaProjection consent and capture policy
constraints. Capture does not replace the original. Relay's Media-zero plus separate
Accessibility output is therefore a device-tested engineering approach, not a claim
of unrestricted system-wide interception or support for opted-out/DRM audio.
