# v0.11.5: desired-only keys, current intent and native panel geometry

## Factual baseline

User test: `SoundCeiling-20260912-121254.log.txt`, 595 lines, approximately 124 seconds,
Samsung SM-A528B / Android 14, ordinary v0.11.4. Media range 0–15, speaker route.
Delivered v0.11.4 source tree: `c974678ffcbdc6766fd5eed0f4364580f4b1551a`, remote commit
`dba5f4f4050fb37af65b6316da1006c5c7b52b9d`, APK SHA-256
`416cf62cce6ef10817b310db8bba23e7e673da92ae500b4c42e25a5f2a53dbf6`.
The current screenshot is `1000230785.jpg`; it shows the own capsules above the native pill
and the speaker's outer wave clipped at the right edge.

The user's September 12 instruction supersedes the earlier hardware-Down pause rule:
ordinary keys change only the own desired slider; automatic pause/mute occurs at own zero.
The explicit Pause button remains available. Ordinary control remains separate from the
optional Relay path, and Session DSP remains quarantined.

## What the log establishes

| Observation | Evidence | Implication |
| --- | --- | --- |
| No native geometry was obtained | Line 40: `native=null`, screen 1080×2400, density 2.8125; seven panel shows use fallback `708,180,933,788` | Reducing default capsule size in v0.11.4 did not fix Samsung alignment. |
| Down paused with a positive own target | Down at elapsed 2116644852 ms, resume at 2116663311 ms | About 18.46 seconds without ordinary writes was an intentional old pause, now contrary to the requested behavior. |
| A key could mute Media while the own slider remained positive | Early Down operations reach Media 0 with own values still above zero | Two controls applied the same intent: desired-level change plus a separate physical Down. |
| Recovery could be armed during a rising PCM block | For example line 174: Media 4→5, slow control −11.20 dB, current attack −8.40 dB; line 220: 4→5, −11.78 / −8.05 dB | Recent history alone did not veto the current rising edge. |
| A later attack can reverse a previous quiet recovery | Line 197: 4→5; line 206: 5→4 about 107 ms later | A reactive Media controller cannot anticipate an audio block it has not received. |
| Shadow and Relay are off | Entire session | Neither is evidence for the reported audible behavior. |
| Stop disarms filtering promptly in this log | Key filter off at 2116729813 ms; service stop at 2116729818 ms | No new post-Stop key delay is demonstrated by this session. |

The old geometry log did not record source availability, event window ID or query failure reason.
It proves that the result was null, not which Android call failed. The new query specifically
handles source-less events, including window ID −1, and records its result reason and capability.

The independent source reference stays at approximately −13.165 dB. Capture PRE/POST correlation
classification changes during playback, but the independent controller does not reinterpret its
control signal from that diagnostic classification. These transitions are not evidence of a
control-direction bug. The exact user-described loud increase followed by a freeze cannot be
uniquely located without its timestamp; the positive-level pause and rising-edge writes are
separately confirmed facts.

## Implementation

- Positive own key/slider actions synchronously retain or resume ordinary authority. They only
  update the desired target. Own zero pauses and requests an acknowledged Media-zero write.
  An explicit positive action can bootstrap Media zero to step 1; Continue at own zero stays muted.
- Each desired/maximum/Pause action changes a target revision. Ordinary commands calculated before
  that revision cannot reach the actuator. Final effects follow session gate → target lock →
  Media authority. Target read-modify-write operations use the same lock.
- Delayed service intents apply the latest target and current pause state. They cannot revive an
  older positive action after a newer explicit Pause, or reapply an old pause after a newer positive
  gesture. Current-intent application is serialized with target changes.
- With leveling strength above zero, an attack block more than `max(1 dB, tolerance)` above the
  smoothed control level cancels upward dwell if the next step would bring it within tolerance
  below the goal or above it. A deliberate higher target remains responsive while the whole
  rising block stays well below that goal. It does not delay downward attacks. The existing
  recent-attack history and quiet recovery remain. At strength zero, nominal-gain behavior remains.
- Geometry selection first uses SystemUI event nodes, then volume-window metadata and eligible
  roots. Exact event window, a system volume title, or a narrow verified SystemUI window can identify
  the capsule. Full-screen bounds are rejected. The foreground app root is not used as a fallback;
  no node text is read. Nodes/windows are recycled and Stop cancels publication.
- The query is off the main thread with a 64-node and 250 ms traversal budget. A single Android
  Binder call cannot be forcibly preempted by that budget. Interactive-window retrieval is enabled
  in both XML and runtime service flags. Failure retains the existing compact fallback position.
- Native dimensions drive matching width, height and top, with an 8 dp gap. Existing rotation,
  drag stability, keyboard-independent placement, dismissal and Stop behavior are retained.
  Centered 24 dp vector icons replace paths that overflowed the capsule width.

## Verification and remaining phone work

`scripts/run-v0115-target-tests.sh` exercises the real own-target control and Android write bridge
against a fake Media actuator, a log-derived rising-attack sequence, and window selection with
missing event nodes. The stale positive-intent/Pause case and rising-attack case failed before
their corresponding fixes. Historical tests with the previous Down contract are updated to the
user's new rule; independent old Relay/legacy safety coverage remains.

Vector paths were rendered in a 36 dp capsule preview and visually checked for clipping and
centering. This is asset verification, not a screenshot of Android execution. All workflow checks
and the real SDK 35 APK build are required before delivery. A separate reviewer was requested but
could not run because its execution quota was exhausted; no independent review verdict is claimed.

Next Samsung test, using ordinary mode with Shadow/Relay off:

1. Press Up/Down repeatedly and hold each key. Only the own desired percentage should change from
   the key intent; normalization keeps running above zero. The maximum should not move.
2. Reach own zero: audio mutes and the status says to raise the own slider. Raise it again:
   normalization resumes. Press explicit Pause, then move the own slider: the newest action wins.
3. Show the native pill at different screen/keyboard states. Both own pills should align beside it,
   have unclipped icons, stay open during touch, and dismiss about five seconds after interaction.
   If only fallback placement appears, toggle the Accessibility service off/on once and capture a log.
4. Replay a known quiet/loud sequence. If a loud increase or long hold occurs, note the approximate
   time or make a short recording, then export the log. Distinguish a later new transient from an
   upward command issued while the current measured block was already rising.
5. Stop, then use normal hardware keys. Filtering must stay off and the ordinary volume controls
   must respond normally.

No v0.11.5 physical listening/geometry acceptance is claimed before that test. Ordinary Media still
has integer vendor steps and capture/output latency; Shadow does not make audible output smoother.
