# v0.11 Samsung field check

Device of the report: Samsung SM-A528B, Android 14. Install over v0.10 using the same development signer. Desktop tests and Android compilation do not establish audible success.

1. Play Yandex Music or a video already recognized by the app. Use ordinary Start and allow audio capture. Leave PCM Shadow off; it is not needed for ordinary normalization.
2. The primary SoundCeiling card shows desired volume and its maximum. Wait about 1–2 s for the initial sound reference. Ordinary stable material should remain near its initial level instead of immediately draining to 1/15.
3. Change the **SoundCeiling** desired slider up and down. The desired value remains fixed while physical Samsung Media reacts to louder/quieter material. Increase the maximum if it clamps your desired setting. The physical Media value may legitimately exceed the own desired percentage during quiet material.
4. Enable SoundCeiling in Accessibility using the panel button. Press a volume key: the stock Samsung panel and the own panel should appear together. Check overlap, both sliders, finger tracking, 5 s dismissal and return from settings. Exact OEM window-event matching remains a field check; manual Show is available.
5. Hardware Down must decrease volume and pause automatic movement. Continue, own-slider movement, or Up must resume without another capture permission request. At zero, explicit Up must be able to leave mute. Repeat with Accessibility disabled using the stock slider; pause occurs when the actual downward movement is observed.
6. While normalization is writing, tap Stop **once**. The UI must remain stopped, the foreground notification disappear, and Media must stop moving. Repeat a few times, including during a source/capture transition. A boosted Media level must not remain above the nominal user volume after ordinary Stop; Stop must never increase an already lower level.
7. Separately try Relay. If Accessibility is disabled, Relay should name the prerequisite and open its settings; returning after enabling should continue setup. Run only on the built-in speaker. Confirm the quiet probe only if one clean stream is heard. If it aborts, record its displayed reason rather than repeatedly accepting/restarting.
8. Export a fresh log for the shortest failing sequence. Say whether the slider, physical sound, Pause/Continue and Stop agreed. For Relay, note whether mute, quiet preview or confirmation was reached.

New log events: `user_volume_intent`, `user_volume_target`, and generation/lifecycle diagnostics. The target is a PCM/route estimate, not measured speaker SPL. Coarse Media cannot remove every transient before it is heard or interpolate between Samsung's hardware steps.
