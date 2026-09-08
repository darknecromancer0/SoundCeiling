# v0.10 Audible Normalization Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for independent
> units, with a final review; the user already authorized implementation and CI builds.

**Goal:** deliver an install-over no-root APK with functioning ordinary Media control
and a bootstrappable audible PCM path.

**Architecture:** source-relative PCM normalization and source-feedforward Media
control share captured evidence but have mutually exclusive output ownership.

**Tech Stack:** existing Java Android API 29+, pure Java tests, GitHub Actions API 35.

**Spec:** `docs/superpowers/specs/2026-09-07-v010-audible-normalization-design.md`

## Global Constraints

- Baseline 7a3e7c45c3826791b414769a3062b6cfecd3f220, same branch and draft PR #8.
- No root, no private capture-policy bypass, no Session DSP reactivation.
- Explicit cap, mute, final PCM ceiling and detected Volume Down remain effective.
- Physical Samsung acceptance is distinct from desktop and CI verification.

## Task 1: PCM envelope and linked target

Files: `PcmNormalizer.java`, `RelayPcmDsp.java`, focused PCM tests/scripts.
Consumes the existing `process` API; preserves its result fields for runtime.

- [x] Add failing 10 ms PCM tests: alternate sustained -42 and -8 dB source loudness,
  target -18, verify convergence within tolerance with sufficient gain allowance;
  vary Accessibility route attenuation and verify linked digital gain is unchanged.
  Include short loud transients, silence and stereo preservation.
- [x] Implement per-block gain evolution without discarded 0.2 dB increments:
  `desired = gain + strength * (target - projectedLoudness)`;
  `next = gain + (1 - exp(-dt/tau)) * (desired-gain)`.
  Clamp/ramp conversion under the actual block peak limit; no step-command deadband.
- [x] In linked Relay, use `targetLoudness` with a zero route for digital control;
  actual projected telemetry adds the Accessibility route separately. Default +24,
  Full +30 dB, final samples <= -6 dBFS. Explicit unlinked bounds remain supported.
- [x] Run focused tests and relevant earlier PCM tests, document semantic updates,
  commit only this unit.

## Task 2: Media source-feedforward controller

Files: new `MediaNormalizationController.java` if needed, coordinator, focused tests.
Consumes source levels, route, user anchor, profile, bounds, cap, activity/policy;
produces adjacent Media command plus meaningful reason. Service write bridge retained.

- [x] Reproduce old HOLD with Samsung gain at index 3 = -43, raw loudness -8, peak -1.
  Cover source -30, cap4, silence, mute, own acknowledgments and manual-down latch.
- [x] Compute linked runtime target without OutputCeilingState's -60 clamp:
  `target = profile.targetLoudness + curve.gainDbForIndex(userAnchor)`.
  Source estimate is public-capture PCM + route, explicitly identified as estimated.
- [x] Choose next adjacent step only if it improves interval error by hysteresis;
  use 80 ms sustained DOWN and 400 ms sustained UP, profile OFF/zero disables writes.
  Peak UP checks use estimated next OUTPUT peak. Bound to audible floor, configured
  cap and hard cap. Own steps never redefine linked target.
- [x] Verify actual coordinator and write bridge, amend superseded full-interval
  tests, commit the isolated unit.

## Task 3: Relay startup, diagnostics and user-down

Files: RelayPreflightPolicy, AccessibilityRelayRuntime, NormalizerService,
RelayCardView, targeted lifecycle tests/scripts.

- [x] Add a failing preflight regression with UNKNOWN capture reference and otherwise
  valid facts. It must permit the muted proof phase while still rejecting bad route,
  policy, generations and unavailable output.
- [x] Replace prior-reference admission with per-epoch fresh PCM-after-mute proof.
  Discard queued pre-mute blocks, require >=500 ms live non-silent samples before
  output. Timeout and failure retain the actual reason through cleanup.
- [x] Keep explicit preview confirmation but make the preview level usable; preserve
  output ownership and recovery ordering. Temporary warmup waits within a deadline.
- [x] On detected down, stop output and keep Media muted with a visible latched reason;
  explicit Relay restart rearms, no automatic restart. Add runtime regression harness
  for admission, PCM loss, original suppression and pause where practical.
- [x] Verify relevant Relay tests and update only tests for intentionally superseded
  PRE admission/+3 dB pilot semantics. Commit unit.

## Task 4: Integration, review and APK

Files: version metadata, workflow, README, field checklist and audit notes.

- [x] Make UI name the actual mode and show pending/blocked/paused reasons and cap
  saturation, preserve existing approved simple/advanced layout.
- [x] Add focused new tests to CI, set versionName 0.10.0 and increment versionCode.
- [x] Run required matrix once; fix concrete failures. Obtain independent code review
  of lifecycle and controller boundaries, address material findings.
- [ ] Push a fast-forward commit to the existing feature branch, wait for successful
  assembleDebug and signer verification, download and verify the matching APK hash.
- [ ] Deliver APK, short alternating quiet/loud + Volume Down scenario and honest
  limitations; leave store publication and physical acceptance unclaimed.
