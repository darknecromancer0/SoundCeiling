# v0.11.3 — useful Advanced controls after the independent-volume rebuild

## Evidence and scope

The user asked to review the earlier Advanced settings and restore useful ones before the next
phone test. The audit compared all 23 revisions of `AdvancedModeView.java` in the available Git
history, the current controller wiring, and the recorded v0.10/v0.11 field conclusions. The latest
delivered baseline is v0.11.2 (remote `2593ca73fc552b0cb2bd97110db58c0364fbbd6a`, local
`5424ad10afa33166a365b9d39600d0d3ad790cb7`, identical tree). This is a source and regression-test
audit, not a new Samsung listening result.

Useful history landmarks:

- `2c430253`: the first full Advanced screen, profiles, normalization strength/target/tolerance,
  attack/release/hold, Media bounds, Quiet Now, transient thresholds, SPL and meters.
- `0f9dc16d`: Fast / Balanced / Gentle speed presets.
- `e40a8436`: clearer names, projected peak, inline help and optional SPL.
- `77289cd9`, `b2a86c46`: downward-only behavior and subsequent recovery changes.
- `38c17073`, `4c44625f`: ceiling basis and selectable display scales.
- `1417503d`: output ceilings, Linked Lock, global DSP and fallback boundaries.
- `86dfcac8`: PCM feasibility and non-audible Shadow.
- `64add213`: independent user volume and collapsed legacy controls.
- `5424ad10`: v0.11.2 restores ordinary dynamics, named reaction profiles, peak and Shadow access.

## Setting-by-setting decision

| Earlier control / indicator | Current role and decision |
| --- | --- |
| LUFS / LUFS-like | Keep as an approximate K-weighted loudness readout. Show the fast control input and the slower approximately 3-second display separately; neither is certified EBU LUFS. |
| RMS | Keep as input diagnostics, not a competing target controller. |
| Raw peak / projected peak / peak threshold | Keep measured input and estimated output peaks. The projected peak ceiling is already directly accessible and remains independent of leveling strength. |
| Frequency bands | Keep the live spectrum; label it as measurement rather than an equalizer. |
| Target loudness | Restore a truthful **readout** of the current independent slider target. A second legacy absolute Target control would compete with the user's working volume control. |
| Normalization strength | **Restore real partial leveling** in the ordinary Media controller, 0–100%. The old 35/65/100 values only passed the ordinary enable gate and produced identical ordinary correction. |
| Off / Light / Medium / Strict | Keep the common enable switch. Adapt Light/Medium/Strict as **Мягко / Баланс / Строго** presets for the current six settings. No separate historical `Strike` control was found in Advanced. |
| Tolerance | Already restored as **Допустимое отклонение**. It controls the no-change zone around the current goal. |
| Downward attack / upward release / hold | Already restored as ordinary down dwell, one-step recovery dwell, and hold after fast reduction. |
| Fast / Balanced / Gentle speed presets | Their role is covered by the new presets plus individually editable timings. Named v0.11.2 snapshots remain loadable. |
| Transient warning / emergency | The current fast-attack threshold and projected peak ceiling cover ordinary audible intervention. The old thresholds remain for compatibility; reconnecting a second transient controller would add conflicting decisions. |
| Max down steps / max up steps | Keep the current immediate multi-step reduction and one-step recovery policy. Reintroducing an ordinary downward step cap would recreate avoidable attack delay. |
| Minimum / Maximum Media | Legacy fallback boundaries remain in compatibility. Ordinary Media needs its full actuator range to bring quiet passages up; the independent user maximum bounds the output goal. |
| Safety Lock / independent ceilings / Linked Lock | Preserve stored compatibility controls. Ordinary desired volume and maximum replace their previous target-authority role and do not follow actuator movement. |
| Manual recovery / pause | Existing Down → pause, own slider / Up / Continue → resume. Keep these working controls. |
| Quiet Now / Quiet Now level | Existing one-shot downward command remains in compatibility. It is not a normalization metric or another recovery controller. |
| Allow automatic mute | Remains legacy/Relay configuration. Ordinary correction retains a nonzero floor; user mute remains available. |
| Media % / Digital dB / Calibrated SPL | Keep compatibility display choices. Show the ordinary target in estimated digital loudness alongside the own percent slider; do not add a fictitious room SPL target. |
| SPL target / ceiling / calibration | Retain legacy access and diagnostic estimate. Calibration is not required for the independent controller and cannot make public PCM a physical microphone measurement. |
| User profiles | Extend ordinary reaction profiles to include strength. Preserve old profile storage and named v0.11.2 dynamics snapshots. |
| Global DSP / PCM Shadow / Relay | Keep separate capability-aware controls. Shadow has no audible output. Relay remains an optional experiment without a new field claim. Session DSP stays quarantined. |
| Gain / attenuation / last decision | Restore signed actual Media correction relative to nominal user gain, estimated output, and the current controller reason. Move stale legacy-envelope readouts under compatibility. |

## Ordinary strength semantics

Let `R` be the fixed learned source reference, `T` the independent user target, `S` the current
source estimate, `a` strength in 0–1, and `M` the independent maximum target. The controller uses:

```
currentGoal = min(T + (1 - a) * (S - R), M)
```

At 100%, behavior is the existing fixed-target normalization. At 50%, half the source contrast
remains in dB after settling. At 0%, the controller returns toward nominal user gain, subject to
the independent maximum, peak ceiling, nonzero floor, pause and source/raise permissions. The
master normalization switch remains separate. Hardware steps and the configured tolerance limit
accuracy; this formula bounds the requested goal, not an exact physical sound-pressure ceiling.

The fast attack computes its goal from the fast input, while ordinary correction uses the smoother
control input. Only a change of the fixed user target resets target-related dwell. A changing partial
goal must not repeatedly reset recovery. Scaling each frame's error was rejected because it would
eventually produce full normalization at every nonzero strength.

| Preset | Strength | Down | Up | Hold | Tolerance | Fast threshold |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Мягко | 35% | 60 ms | 400 ms | 700 ms | 2.5 dB | 6 dB |
| Баланс | 65% | 40 ms | 200 ms | 400 ms | 1.5 dB | 6 dB |
| Строго | 100% | 20 ms | 150 ms | 400 ms | 1 dB | 4 dB |
| Existing field default / reset | 100% | 40 ms | 150 ms | 300 ms | 1.5 dB | 6 dB |

Presets are initial tuning choices for testing, not phone-verified acoustic optimization. They
change these six settings only. They do not change user volume, maximum, peak ceiling, engine
start/stop, or the master enable switch.

`v2` profile encoding adds strength to the existing five values. Storage keys remain unchanged.
The decoder accepts `v1` with strength 100%, so an update or old-profile load preserves the previous
behavior. Invalid named profiles do not replace the active configuration. Full reset preserves
saved profile names as before.

## Live telemetry

The service publishes input-related output estimates using the **applied** Media index, not the
requested next index or optional DSP gain. Signed Media correction is `routeGain - (T - R)`.
The readout distinguishes the fixed slider target from the current partial-strength goal. It shows
no ordinary result for stopped/stale capture or an active audible Relay. Diagnostic/Relay snapshot
copies preserve the new fields. The once-per-second `user_volume_target` event now includes
`effectiveTargetDb`, `estimatedOutputDb`, `mediaCorrectionDb`, and encoded strength with timings.
The readout also checks the actual metering capability. Fallback Visualizer values are labeled as
shared output RMS/Peak, never as source LUFS-like; activity-only mode claims no PCM measurement.
Mixed PCM exposes its meters without an ordinary single-source target. The duplicated historical
meter row was removed from compatibility so it cannot reintroduce the fallback labeling error.

## Validation and phone check

Targeted Java regression checks cover settled 0/50/100% quiet/loud passages, fast partial attack,
continuously changing source with completed recovery dwell, maximum/peak constraints at 0%,
pause/inactive/mute/raise authority, coordinator wiring, original defaults, v1/v2 profile migration,
corrupt-profile retention, signed telemetry, snapshot enrichment and stale/Relay display handling.
All 79 local workflow check scripts passed, including the affected help/UI rechecks and the new
fallback-meter regression. Android assembly and the stable
signing certificate are checked in the PR's GitHub Actions build.

Next Samsung check, using the same repeatable quiet/loud passage:

1. Install over v0.11.2; confirm saved own volume/maximum and existing reaction profile are retained.
2. Keep the existing default first. Compare attacks/recovery with v0.11.2 before changing settings.
3. Change only strength between 35%, 65% and 100%. Give each setting time to settle; confirm that
   partial values retain more contrast, subject to the own maximum and 15-step quantization.
4. Try Мягко / Баланс / Строго and save/reload a custom profile. Check displayed values and actual
   effect, including the old profile restoring its original timings at 100%.
5. Watch input → estimated output, fixed target and signed Media correction. Down must pause once;
   own slider / Up / Continue must resume without restarting; Stop must take effect on one press.
6. Confirm the compact capsules still dismiss after about two seconds or an outside touch and that
   opening Advanced does not move volume. Export logs with the chosen preset/strength noted.

Ordinary Media is still reactive to captured audio. These restored controls do not create lookahead
or prove removal of the first audible transient. Shadow cannot alter that sound; a Relay comparison
would be a separate real-device test.
