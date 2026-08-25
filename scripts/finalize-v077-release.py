from pathlib import Path

readme = Path('README.md')
s = readme.read_text()
s = s.replace('# Sound Ceiling for Android - v0.7.6.3', '# Sound Ceiling for Android - v0.7.7', 1)
old_intro = 'SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.6.3 is a Samsung DSP attach-evidence corrective built on the **Control Architecture Reset** from v0.7.6, the DSP attach safety work from v0.7.6.1, and the output-domain correction from v0.7.6.2.'
new_intro = 'SoundCeiling is a no-root Android 10+ adaptive audio controller. v0.7.7 moves ordinary normalization to verified **Enhanced Session DSP** on an exact non-zero audio session while keeping the Samsung Media slider as the user master anchor.'
if old_intro in s:
    s = s.replace(old_intro, new_intro, 1)
section = '''## v0.7.7 Enhanced Session DSP

- **Non-zero session authority.** Ordinary normalization may use DSP only after SoundCeiling discovers exactly one active non-zero audio session owned by the exact playback UID and verifies its audible differential response. Session 0 is not the v0.7.7 normalizer target.
- **Samsung Media remains user authority.** Media **3/15** is a first-class user anchor. Ordinary normalization must not pull the Samsung slider away from 3/15, 2/15, or another manual step. With verified Session DSP, correction is continuous DSP gain; without it, ordinary normalization holds.
- **One-time no-root setup.** Enhanced Session DSP discovery requires the Android DUMP permission granted once from ADB: `adb shell pm grant dev.soundceiling.app android.permission.DUMP`. Simple and Advanced modes expose setup status and a copyable command.
- **Fail-closed transport.** A stale, ambiguous, non-neutral, unresponsive, or failed session transport is released. A failed gain apply revokes Session DSP authority and returns ordinary control to HOLD rather than reviving target-chasing Media fallback.
- **Diagnostics expose the real actuator.** Runtime state and logs include session ID, UID, package, requested/applied gain, reason, and `session_dsp_apply`.
- Historical Safety Maximum, Quiet Now, source-policy gates, user-master anchor, debt-only recovery, stop/restart protection, and v0.7.6.x output-domain safety contracts remain intact.

Samsung physical acceptance: `docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md`.

'''
marker = '## v0.7.6.3 Samsung DSP attach evidence corrective\n'
if '## v0.7.7 Enhanced Session DSP' not in s:
    if marker not in s:
        raise SystemExit('README v0.7.6.3 marker missing')
    s = s.replace(marker, section + marker, 1)
s = s.replace('SoundCeiling-v0.7.6.3-debug-apk', 'SoundCeiling-v0.7.7-debug-apk')
readme.write_text(s)

wf = Path('.github/workflows/build-apk.yml')
w = wf.read_text()
old = '''      - name: Check v0.7.7 Session DSP telemetry
        run: bash ./scripts/run-v077-session-telemetry-tests.sh
      - uses: android-actions/setup-android@v4
'''
new = '''      - name: Check v0.7.7 Session DSP telemetry
        run: bash ./scripts/run-v077-session-telemetry-tests.sh
      - name: Check v0.7.7 Android wiring contract
        run: bash ./scripts/check-v077-android-wiring-contract.sh
      - name: Check v0.7.7 release contract
        run: bash ./scripts/check-v077-release-contract.sh
      - uses: android-actions/setup-android@v4
'''
if 'run: bash ./scripts/check-v077-release-contract.sh' not in w:
    if old not in w:
        raise SystemExit('build workflow telemetry anchor missing')
    w = w.replace(old, new, 1)
w = w.replace('name: SoundCeiling-v0.7.6.3-debug-apk', 'name: SoundCeiling-v0.7.7-debug-apk')
wf.write_text(w)
