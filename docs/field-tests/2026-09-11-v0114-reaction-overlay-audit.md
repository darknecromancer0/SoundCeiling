# v0.11.4: reaction, recovery, overlay and stopped key filter

## Factual baseline

- Tested APK: v0.11.3/code 43, commit `607f0053d7e3460665c770c0c1b2dd4e381215a3`.
- Device: Samsung SM-A528B, Android 14, speaker, Media indices 0–15.
- Evidence: `SoundCeiling-20260911-160354.log.txt` (2,475 lines) and the two supplied screenshots `1000230723.jpg` and `1000230725.jpg`.
- User says the independent-volume scheme works, but reports brief loud escapes, audible up/down pumping, oversized/misplaced capsules, short interaction timeout, broken expanded header, duplicate Advanced entry in Simple, and delayed hardware keys after Stop.
- PCM Shadow and Relay were OFF. Neither has been credited for these results. Session DSP remains quarantined. This is technical pre-1.0 work; monetization is out of scope.

## Findings

The log contains 59 automatic Media writes: 33 quiet increases, 20 ordinary reductions and 6 fast reductions. A 4→5 increase at t=2044346481 is followed by 5→4 only 27 ms later. Other reversals occur after 81, 83, 137, 156 and 250 ms. Short gaps between loud events allow an increase which the next beat immediately cancels.

Logged detection-to-write times are 6–40 ms (median 13 ms, p95 22 ms); capture-to-write is 15–103 ms (median 28 ms, p95 40 ms). These are internal timestamps for decisions which already happened, **not** end-to-end audible reaction time. The former controller still gates intermediate transients on a smoothed meter and downward dwell unless they reach the 6 dB fast threshold.

The old capsules use a fixed 304 dp height/112 dp pair width and `CENTER_VERTICAL`, independent of Samsung's volume dialog and the keyboard. Screenshot proportions show the own capsules approximately 1.4× taller/wider than Samsung's. Crop/rescaling prevents reliable extraction of absolute phone dp coordinates.

After Stop, Accessibility still requests all hardware keys. Its old callback reads AudioManager and legacy safety preferences before discovering the engine is stopped. The callback runs ahead of normal key dispatch. The supplied log ends at Stop, so it does not measure the reported post-stop lag; this is a concrete code-path defect matching that report.

## Changes

- A finite current-block attack meter can request an immediate one-step reduction below the multi-step fast threshold, while larger peaks retain the existing fast path.
- Upward recovery checks the loudest attack from approximately the preceding 0.8 seconds before starting its ordinary rise dwell. Any reduction also starts the configured raise hold. This avoids raising into a short beat gap; a sustained quieter passage still recovers one step at a time. Explicit user target changes reset the history.
- Ordinary Media remains an integer-index actuator. No alternating-step dithering or fictitious fractional Samsung control was added. See [AudioManager.setStreamVolume](https://developer.android.com/reference/android/media/AudioManager#setStreamVolume(int,%20int,%20int)). Public capture cannot provide advance knowledge of an uncaptured transient. PCM Shadow computes silently; only a successfully isolated audible Relay can process its own PCM output continuously.
- Accessibility key filtering is armed with the in-memory engine/Relay authority and cleared on Stop before preference I/O/audio cleanup. Already queued idle callbacks return without audio, preference or runtime-store reads. Panel creation moves out of the owned key callback. No new debounce delays are applied to physical keys. A release already queued before disarming is consumed without I/O. A release arriving after Android unregisters the filter goes directly to the system; the bridge test does not claim delivery to the removed filter. Holding the filter across synchronous teardown would retain the reported lag path.
- The compact fallback is 216 dp tall with two 36 dp capsules. When Android exposes the native volume nodes, a background query uses only the supplied SystemUI volume event's node family to obtain the native pill bounds. The own pair matches its height/width and sits directly to its left. Screen coordinates and fixed input mode keep the keyboard from re-centering it. Cached bounds are rejected on display-size changes. Visible panels also check for rotation on their refresh timer even if native probing fails. Late native coordinates are deferred until touch release/cancel; actual display rotation cancels an active gesture before repositioning.
- This geometry query requires the declared Accessibility window-content capability and report-view-IDs flag. It does not enumerate global windows or inspect a foreground app root. It reads package/class/resource IDs and bounds, not node text. Work is capped at 64 visited nodes and checks a 250 ms budget; an individual Android Binder call may exceed that budget, so all queries run off the key/UI callback thread. Cancellation prevents late results after Stop.
- Unused overlay: 2 seconds. After a capsule/card action or touch release: 5 seconds (longer Android accessibility timeout is respected). Touch holds suspend dismissal. Native events do not shorten the interaction deadline or continually extend it. Outside tap/close dismiss immediately.
- Expanded header uses a weighted, centered button beside a fixed close target, inset within the rounded panel. Expanded contents scroll; duplicate nested card decoration is removed. The Simple screen's duplicate Advanced button is removed; drawer and expanded-overlay navigation remain.
- Logs add `volume_key_filter active=...`, native geometry and own placement for the next phone test.

## Next phone test

Install v0.11.4 over the current APK. If native alignment remains on the fallback after updating, toggle the SoundCeiling Accessibility service off/on once so Android reloads its changed declared capability.

1. Leave Shadow and Relay off. Play the same track/clip; compare the loud transitions and the short-gap pumping at the same desired/maximum levels.
2. Press Up/Down, including holding the key. Verify independent desired control, Down pause, Up/own slider resume, and the maximum cap.
3. Drag each capsule up/down; check alignment with Samsung both with and without the keyboard and after rotating the phone. Verify approximately five seconds after release and immediate outside dismissal.
4. Open the three-dot panel; check header bounds and Advanced navigation. Simple must have only the drawer Advanced entry.
5. Stop once, then immediately press and hold normal volume keys. Repeat start/stop and key tests. Capture a new log after these presses (the old log stops before them).

All 81 local workflow checks pass. Focused checks cover the real production key service/shared state with boundary fakes, reaction/recovery, native-coordinate placement, rotation fallback and interaction timing. Independent review findings on visible rotation and mid-drag native placement were corrected. Android build and APK identity are recorded in the PR/delivery metadata. Phone acoustic latency, actual Samsung node availability and visual alignment remain unverified until this field test. Do not call this a rootless look-ahead limiter or claim Relay was phone-tested.
