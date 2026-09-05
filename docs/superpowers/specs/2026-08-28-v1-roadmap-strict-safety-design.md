# SoundCeiling Roadmap to 1.0 and Strict Safety Design

## Goal

Ship SoundCeiling 1.0 only after the core promise is trustworthy on the Samsung SM-A528B field device: the user remains master of Samsung Media volume, the configured hard ceiling cannot be bypassed by normal user interaction, volume-down is never fought, DSP raises quiet material and attenuates loud material without moving the user's Samsung Media anchor, and stopping/reconfiguring the service cannot leave stale audio authority behind.

After 1.0, use 1.0.x for corrective fixes. Use 1.1 for the previously planned secondary tabs, controls, polish and additional features that are not required for the core 1.0 normalization contract.

## Current evidence

The v0.7.7.5 field log proves three separate facts:

1. Exact source identity and targeted capture work for Yandex Music: package `ru.yandex.music`, UID `10292`, non-zero session `76737`.
2. Enhanced Session `DynamicsProcessing` still fails during custom construction with `IllegalArgumentException`; therefore DSP gain remains unavailable even when the planner requests positive gain.
3. The current Media hard cap is a reactive writer, not a strict authority boundary. During a held Volume-Up gesture or Samsung panel drag, observed Media can race from the configured hard max `4` through `5`, `7`, `11`, `14/15` before the app write wins. The tracker can also classify a write mismatch as external/user authority, contaminating the media anchor with an illegal overshoot.

## Release sequence

### v0.7.7.6 Strict Safety Authority

Add a dedicated Android AccessibilityService safety gate for hardware volume keys. When enabled and SoundCeiling is running, Volume-Up is consumed before the system when the next Media step would exceed the active hard ceiling. Volume-Down is never consumed and always remains user-authoritative.

Samsung panel/other external volume writes cannot be intercepted before they occur, so NormalizerService adds a strict overshoot latch. If Media is observed above the hard ceiling, the service repeatedly writes the ceiling until multiple consecutive readbacks confirm the legal index. During this latch, no overshoot may update `MediaAnchorState`, Linked Lock ceilings, recovery debt, or user-authority state.

`VolumeWriteTracker` gains an explicit rejected-overshoot observation instead of treating above-hard-cap mismatches as generic USER authority. The coordinator treats this observation as safety-only and never persists it as the user's anchor.

Strict hard-cap status is truthful: guaranteed only when the accessibility key filter is active plus the service safety latch is healthy. Without accessibility, the app still clamps observed slider writes but must not claim that held hardware Volume-Up is impossible to race.

### v0.8 Samsung Session DSP Compatibility Matrix

Keep OEM default topology disabled after the v0.7.7.4 safety incident. Probe a small ordered matrix of documented custom `DynamicsProcessing.Config` candidates against the exact non-zero session. Every candidate starts globally disabled and neutral. Authority is granted only after deterministic topology and input-gain readback plus `0 -> -0.5 -> 0 dB` handshake. Any unexpected processing stage, lost control, non-neutral state, constructor failure or restore mismatch releases that candidate and advances to the next. No candidate may remain attached after stop, route change, capture replacement or epoch invalidation.

### v0.9 PCM DSP feasibility and fallback

If Samsung rejects all safe session candidates, test an explicit PlaybackCapture -> SoundCeiling DSP -> AudioTrack path. This path may ship only if the field device proves that source capture semantics, latency, routing and duplicate-audio prevention are acceptable. Session DSP remains preferred when available.

### v0.9.5 / 1.0 hardening

No new feature scope. Stress and regression work only: hardware-key hammering, Samsung slider dragging, stop/start races, route changes, source changes, silent sections, transient peaks, long-running normalization and restart behavior.

## 1.0 acceptance contract

- A configured Media hard ceiling cannot be exceeded persistently through held hardware Volume-Up or Samsung panel dragging.
- Volume-Down always works immediately and is never raised back by SoundCeiling as a response to the user's downward action.
- Illegal external overshoots never become media anchors or move Linked Lock ceilings.
- Stopping SoundCeiling removes all DSP/control authority and no stale verification can attach afterwards.
- Supported exact sources receive real non-zero DSP gain changes while the Samsung Media anchor remains fixed during ordinary normalization.
- Quiet material is raised toward target loudness; loud material is attenuated; hard output peaks remain below the configured ceiling.
- No RED safety-cap violations, stale DSP attach, uncontrolled oscillation or runaway auto-volume appears in the acceptance stress logs.

## Post-1.0 roadmap

- `1.0.x`: corrective fixes only for regressions, device compatibility and safety issues.
- `1.1`: resume the previously planned secondary tabs and additional functions, including richer per-app/system-app controls, advanced diagnostics and the deferred UI/feature backlog, while preserving the 1.0 safety and normalization contracts as non-regression gates.
