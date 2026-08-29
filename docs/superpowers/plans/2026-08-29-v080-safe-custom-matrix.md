# SoundCeiling v0.8 Safe Custom Matrix Implementation Plan

**Goal:** Ship a Samsung field APK that can authorize only an explicit, topology-verified
non-zero-session DSP candidate, with a bounded positive-gain pilot and fail-closed output guard.

**Architecture:** Keep the existing exact-source/session ownership, policy arbiter, Media anchor,
Strict Safety and lifecycle manager. Replace the global Enhanced Session emergency gate with an
ordered pure candidate description consumed by the Android transport manager. Candidate authority
still enters the coordinator only as `VERIFIED_POLICY_SCOPED`.

## Task 1: Lock the pure safety policy

- Add failing tests for ordered explicit candidates, permanent OEM-default quarantine, exact
  profile invariants, `+3 dB` Enhanced Session clamp, retained negative range and output anomaly
  guard thresholds.
- Implement `EnhancedSessionCandidateMatrix`, `EnhancedSessionGainPolicy` and
  `EnhancedSessionOutputGuard`.
- Add the test to `scripts/run-pure-tests.sh` and a focused v0.8 test runner.

## Task 2: Make readback topology-specific

- Add failing readback tests for variant, frame duration, channel count, stage-in-use and band-count
  mismatches.
- Extend `EnhancedSessionReadbackVerifier.Snapshot` and add profile-aware pre-enable and handshake
  verification while retaining historical overloads.
- Run the focused readback suite.

## Task 3: Wire the custom Android candidate matrix

- Build every Enhanced Session candidate only with `DynamicsProcessing(priority, sessionId, config)`.
- Instantiate all declared optional stages disabled from construction.
- Remove every Enhanced Session path to `DynamicsProcessing(sessionId)`.
- Iterate candidates in `DspTransportManager`, neutralize/release failures, honor verification epoch,
  select the first verified candidate, and expose its profile ID.
- Preserve historical trusted-handle/global diagnostic paths without expanding their authority.

## Task 4: Add the field safety envelope and telemetry

- Apply the Enhanced Session `+3 dB` positive clamp inside its transport instance.
- Evaluate the output anomaly guard before each normal control decision; on trip suppress and release
  the session before any further gain command.
- Log candidate attempts/results/selection, clamp status and guard inputs/result.
- Expose the selected profile in runtime diagnostics and add a v0.8 Samsung field checklist.

## Task 5: Release and verify

- Set version `0.8.0` / versionCode `35`, update README and workflow artifact name.
- Replace the v0.7.7.10 emergency contract with a historical OEM-default-quarantine invariant and
  add a v0.8 release/wiring contract.
- Run focused RED/GREEN tests, the complete pure suite, every workflow contract, Android debug
  assembly, signer verification and checksum.
- Commit and push the existing PR #8 branch, wait for GitHub Actions, and deliver the signed APK
  artifact for the next Samsung field run.
