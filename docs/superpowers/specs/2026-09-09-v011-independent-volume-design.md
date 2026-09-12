# v0.11: independent user volume

The nine Samsung SM-A528B / Android 14 v0.10 field logs and the user's report supersede the old requirement that Samsung Media remain the user master. The user explicitly requests a second SoundCeiling slider and authorizes changing the architecture without root.

## Evidence and decision

- In 154207, automatic 4→3→2 is followed by user 2→3, then automatic 3→2→1. Similar cycles recur in 154405. `applyVolumeAuthority` reanchors to the reduced physical index and moves persisted linked ceilings. The target can become quieter after Up.
- Fixed source target −21 dB plus the Samsung curve already requests a large reduction from source levels around −9 dB. A displayed HOLD at 1/15 is therefore internally consistent but fails the listening goal.
- Every Relay start in these logs aborts at preflight with `relay_accessibility_output_unavailable`; the service is disconnected. The old entry point never offers to enable it.
- Down permanently latches `MediaAutoVolumeAuthority`; only starting a new session clears it. This is old intended behavior, now explicitly rejected by the user.
- Stop invalidates neither an in-flight publication nor rebind. A worker can publish running=true after the main thread publishes stopped. The logs do not establish the exact UI interleaving, but the race exists in production code.

## Behavior

1. Persist desired volume and its maximum as separate 0–100 values. Initialize desired volume from the current physical level once. Maximum initially retains an explicitly enabled old Safety Lock, otherwise 100. The old global/linked Media range no longer bounds the ordinary PCM actuator. The visible own maximum is the sole ordinary target cap; old auto-created device/app Media caps remain for the legacy paths. Source exclusions remain.
2. Calibrate the initial source reference over 1.2 s of usable audio, taking the strongest observed loudness to avoid capture warmup becoming the reference. Until ready, hold. The desired output is this fixed reference plus the Samsung route gain corresponding to the independent desired percentage. Capture rebind and actuator ACK never reanchor it; route changes start a new reference.
3. Use a dedicated ordinary Media controller. Compare estimated output to that target, choose only an adjacent step that improves error, apply short asymmetric dwell and an output peak check. Physical Media may use its full range while the app owns it. The original public PCM is an estimate, not certified output SPL. The observed Samsung public PCM is treated consistently as a PRE-volume source estimate; correlation-based PRE/POST telemetry never reinterprets it mid-session.
4. Own slider gestures and explicit Continue resume the existing capture. Hardware Down lowers the desired level and pauses; Up raises the desired level and resumes. Without Accessibility, observed native Media deltas adjust the independent target by a delta, never by the actuator's absolute index. Native Down pauses. Own writes remain acknowledged.
5. Primary card and Accessibility overlay contain desired volume, maximum, Pause/Continue and physical Media telemetry. Show alongside stock volume on intercepted keys and identifiable SystemUI volume events, with a manual Show button. Exact OEM placement/visibility requires phone verification. No screen-content retrieval or root.
6. Serialize Stop with final write/publication boundaries, invalidate worker generations, cancel pending starts. A late frame cannot revive the UI or write after Stop. On leaving ordinary automatic control, reduce any boosted Media to no higher than the nominal user level before returning ownership.
7. Relay prompts for its real Accessibility output prerequisite before projection. Key interception capability alone is not a prerequisite under current user authorization. Keep output separation, duplicate-audio probing and recoverable lease ownership. Ordinary controls are unavailable throughout Relay setup and Media-zero recovery, including phases where Relay does not own hardware keys. A saved own zero is applied at ordinary Start; starting with a nonzero setting does not automatically leave an existing native mute.

## Verification

Reproduce the real 4→2 / user Up conflict with the recorded Samsung curve through the coordinator and actual SafeVolumeController with fake Android I/O. Verify initial-level preservation, loud/quiet movement, independent cap, own ACKs, mute/resume, source policy, live-reference inference isolation, and a deterministically blocked late worker after Stop. Run existing workflow gates, Android assembleDebug and stable-signer verification. No emulator/CI result is described as a successful Samsung listening test.
