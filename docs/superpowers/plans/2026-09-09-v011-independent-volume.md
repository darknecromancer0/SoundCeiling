# v0.11 implementation

Design: ../specs/2026-09-09-v011-independent-volume-design.md

- [x] Add independent target calibration and ordinary Media controller with failing Samsung regression tests first. Integrate explicit target into the coordinator; bypass legacy linked anchor persistence only for the new path.
- [x] Add persistent user volume bridge, explicit resume and own actuator range; use the visible own maximum for desired output and retain source exclusions. Integrate service actions and log the independent target.
- [x] Add primary card and Accessibility overlay; intercept own Up/Down; explain and open required Accessibility setup for Relay.
- [x] Serialize worker lifetime, publications and writes with Stop; cancel pending start flows; test the reported interleaving.
- [ ] Run meaningful integration and workflow gates. Update version, field checklist and PR #8, build signed APK through existing Actions, verify and deliver.

Independent UI and lifecycle tasks run in bounded parallel agents under the dispatching-parallel-agents skill. Root owns audio model/control integration and verifies combined behavior. User has authorized implementation, continued development and APK delivery; no additional design approval is needed.

Integration review: serialized Relay start on the main lifecycle queue; blocked ordinary user actions throughout Relay preflight, Media-zero ownership and recovery, including phases with no Relay key ownership; applied saved own zero at ordinary Start. Added write-bridge regressions for native Down not being applied twice, leaving own mute, initial mute and lease exclusion. Samsung listening and OEM overlay placement remain the field acceptance step.
