# v0.11.2: restore useful Advanced controls

The user asked to restore Advanced mode before testing the v0.11.1 reaction/capsule build.
The screen still existed, but v0.11 collapsed nearly every old control into one legacy group.
Old dynamics do not tune IndependentMediaController, whose timing was fixed in code.

The visible Advanced screen now exposes the current controller's dynamics, normalization on/off,
the projected peak ceiling, the independent desired volume/maximum, and existing live meters.
There is no additional controller or output transport.

| Setting | Default, identical to v0.11.1 | Editable range |
| --- | --- | --- |
| Small downward correction dwell | 40 ms | 0–500 ms |
| Upward recovery dwell, one step | 150 ms | 50–5000 ms |
| Hold following fast reduction | 300 ms | 0–5000 ms |
| Ordinary tolerance | 1.5 dB | 0.5–6 dB |
| Overshoot for immediate multi-step reduction | 6 dB | 3–18 dB |

The fast branch still bypasses ordinary downward dwell. Absolute peak violations can trigger it
independently of the user-selected overshoot threshold. It retains the fresh-read/user-pause write
boundary and a nonzero ordinary floor. Upward recovery remains one step. Holds already started
retain their scheduled deadline; a changed hold value applies to subsequent loud reductions.

New settings have a separate versioned preference value. Old fallback or DSP values cannot silently
change their defaults on install. Every ordinary service frame passes the current settings through
NormalizerControlCoordinator; the existing user target is not recalibrated. Target log entries now
include the effective dynamics. The standard reset also resets these dynamics.

Reaction presets and named profiles contain the five dynamics values only. Loading them preserves
desired volume, maximum, calibration, app rules and old profiles. The existing Shadow/Relay and
legacy compatibility controls remain explicitly grouped; they do not become newly supported DSP
transports. Shadow itself and its current status are visible without opening that group.

Advanced is reachable from the navigation menu, a button in Simple, and the expanded capsule header.
The header action opens the same Activity/screen and dismisses the overlay without restarting sound.
The previous two-second capsule dismissal and manual close paths remain.

Regression tests exercise configurable correction/recovery/hold/tolerance through the production
controller and coordinator, fixed target and pause preservation, profile round trips, invalid stored
values, unchanged v0.11.1 defaults, and profile persistence without changing listening volume.
All 78 local workflow check scripts pass, including the existing fast-attack and lifecycle suites.

## Phone comparison

1. Install over v0.11.1 (or v0.11.0). Open Advanced using either the Simple button or ellipsis panel.
2. Keep the "Как в v0.11.1" reaction settings for the first ordinary-mode audio comparison; there is
   no need to enable Shadow or Relay. Check the same loud fragment and capsule placement.
3. Change recovery time to a clearly larger value, then restore the default preset. Save a profile,
   modify its settings and load it; desired volume and maximum must stay unchanged.
4. Confirm volume Down still pauses, Continue resumes, and Stop works once. Export logs if either
   the audible timing or the UI behavior differs from expectations.

Physical listening latency and final Samsung overlay placement remain unverified in this workspace.
