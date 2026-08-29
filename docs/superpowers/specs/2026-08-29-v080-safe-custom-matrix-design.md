# SoundCeiling v0.8 Safe Custom Session DSP Matrix

## Purpose

Resume real Enhanced Session normalization on the Samsung SM-A528B without restoring the
unsafe OEM-default `DynamicsProcessing(sessionId)` path. Samsung Media remains the user's master
volume. Ordinary normalization may change only verified non-zero-session DSP gain; it must not
move the Media anchor.

## Field evidence and boundary

- v0.7.7.9 showed that a deterministic readback of Samsung's unknown OEM-default topology was
  not enough: positive input gain was followed by output behaving like maximum volume.
- v0.7.7.10 correctly quarantined that path. The 2026-08-29 log confirms the runtime stayed
  `SAFETY_ONLY`, Yandex Music still reached exact targeted PCM identity, and illegal Samsung panel
  overshoots never contaminated the Media anchor.
- The same log also confirms that panel dragging is a reactive clamp boundary: most corrective
  writes completed in a few milliseconds, but SystemUI can briefly win a write race. v0.8 does
  not weaken or reinterpret Strict Safety.

## Candidate matrix

Every candidate uses the three-argument Android constructor with an explicit custom `Config`.
The one-argument OEM-default constructor is permanently forbidden for Enhanced Session.

Candidates are attempted in this order:

1. `cts_frequency_full_bypass_stereo`: frequency-resolution, two channels, PreEQ/MBC/PostEQ
   declared with two bands each, limiter declared, every optional stage disabled, 9.5 ms frame.
   This mirrors the public Android CTS architecture while keeping processing bypassed.
2. `frequency_limiter_bypass_stereo`: frequency-resolution, two channels, only a disabled
   limiter shell, 9.5 ms frame.
3. `frequency_input_gain_only_stereo`: frequency-resolution, two channels, no optional stages,
   9.5 ms frame.

No mono candidate is included in the first field build because a channel-topology mismatch must
not risk asymmetric stereo gain. No time-resolution candidate is included because the field device
already rejected the historical time-resolution limiter shell.

## Verification and lifecycle

For each candidate:

1. Construct the effect disabled with input gain `0 dB` and optional stages disabled in the
   `Config` itself.
2. Read back and require the exact expected variant, frame duration, channel count, stage-in-use
   flags, band counts, disabled state, control ownership and neutral per-channel input gain.
3. Run the bounded `0 -> -0.5 -> 0 dB` readback handshake.
4. Promote only the first candidate that passes all checks to verified policy-scoped authority.

A constructor error, topology mismatch, lost control, non-neutral state, gain mismatch, restore
failure or lifecycle epoch change neutralizes, disables and releases that candidate before the next
candidate is considered. Stop, route change, capture replacement, policy change and session change
retain the existing neutralize-before-release ordering.

## First-field positive-gain pilot

The generic planner remains capable of requesting its historical range, but a verified Enhanced
Session transport clamps positive applied gain to `+3 dB` for this field pilot. Negative gain keeps
the existing `-48 dB` bound and hard-safety attenuation remains immediate. This limits exposure
while proving that quiet material can receive real non-zero gain without moving Samsung Media.

## Output anomaly guard

While a verified Enhanced Session has positive applied gain, a fail-closed guard compares the
PRE_VOLUME projection with the independently readable session-zero Visualizer level. It trips only
when all of these are true:

- actual output peak is above the configured hard ceiling plus `1 dB`;
- projected output peak is at least `6 dB` below that ceiling;
- actual-minus-projected residual is at least `12 dB`.

On a trip the session is immediately neutralized, disabled, released and suppressed until session
or route identity changes. Visualizer semantics can vary on OEM devices; therefore this guard may
false-disable DSP, but it never grants authority or moves Media. Missing or non-finite evidence does
not trigger and does not weaken the normal output-domain peak limiter.

## Telemetry and field acceptance

Logs must identify each candidate attempt/result, the selected profile, the `+3 dB` pilot cap and
any output-guard trip with actual/projected peaks and residual. The v0.8 field run is accepted only
if an explicit candidate attaches, non-zero DSP gain is observed, Samsung Media remains at its user
anchor, quiet/loud behavior follows the requested direction, and no output anomaly or stale effect
survives a lifecycle boundary. If every candidate fails, the truthful outcome remains
`session_dsp_unavailable`; the next roadmap step is v0.9 PCM DSP feasibility, not OEM default.
