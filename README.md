# Sound Ceiling for Android — v0.2.1

No-root Android 10+ adaptive media-volume normalizer with a hard user volume cap and optional calibrated dB SPL mode.

## v0.2.1 highlights

- **dBFS mode**: target RMS plus peak ceiling.
- **Calibrated dB SPL mode**: set an approximate physical target and ceiling after calibrating the current audio output.
- **Per-output calibration profiles**: profiles are stored separately for detected speakers/headphones/USB/Bluetooth outputs.
- **Built-in 1 kHz calibration tone**: peak −30 dBFS, RMS ≈ −33.01 dBFS, 3 seconds.
- **Adaptive normalizer**: fast RMS (~250 ms), short RMS (~2.5 s), peak hold, hysteresis, fast reductions and slow upward recovery.
- **Normalization strength**: 0–100%.
- **Absolute Max Media Volume**: a system-volume guardrail that remains active even when captured PCM is silent.
- Foreground service and live RMS / peak / estimated SPL display.
- **Android 10/11 compatibility fix**: BLE-only output constants are no longer resolved on pre-Android-12 devices.
- **Pure logic CI tests** run before every APK build.

## How it works

Android 10+ `AudioPlaybackCapture` can copy permitted `MEDIA`, `GAME` and `UNKNOWN` playback after the user grants a `MediaProjection` token. Sound Ceiling measures that PCM and controls Android's global `STREAM_MUSIC` volume index.

For each Android volume index the app asks `AudioManager.getStreamVolumeDb()` for the platform-provided attenuation curve. This is used both for level control and for calibrated SPL estimation.

### Control logic

1. Capture ~50 ms stereo blocks at 48 kHz.
2. Track a fast RMS, a ~2.5 s RMS and a decaying peak hold.
3. Compute the volume gain that would move the program toward the target.
4. Compute the maximum volume gain permitted by the ceiling.
5. The ceiling always wins.
6. Loudness reductions are fast; a ceiling violation jumps directly to a safe Android volume step.
7. Upward recovery is intentionally slow and disabled during silence, preventing pauses from cranking the volume up.
8. `Max Media Volume` is independently enforced on every processing cycle.

## dB SPL calibration

The calibration is intentionally explicit because a phone cannot infer real acoustic SPL from digital samples alone.

1. Connect/select the speaker or headphones you want to calibrate.
2. Stop the normalizer.
3. Tap the built-in **1 kHz / 3 s** test tone.
4. Measure the acoustic level with an external SPL meter.
5. Enter that measured value in the app without changing Android media volume.
6. Save the profile.
7. Enable **Calibrated dB SPL mode**.

The app stores an offset satisfying approximately:

`estimated SPL = captured dBFS + Android volume attenuation dB + calibration offset`

This lets the controller translate a target such as `70 dB SPL` and a ceiling such as `80 dB SPL` into Android volume steps for that profile.

### Calibration accuracy warning

- Phone speaker: a second device running a sound-meter app can provide a rough calibration.
- Headphones: accurate SPL at the ear requires a suitable acoustic coupler / measurement rig. Holding a phone microphone near an earcup is only an approximation.
- The SPL estimate is not a certified dosimeter and is not intended for medical/occupational compliance measurements.

## Important Android limitation

A regular no-root APK cannot transparently insert a sample-by-sample DSP into the final system mix of every app. Sound Ceiling therefore changes the system media-volume index rather than modifying the outgoing PCM itself.

Consequences:

- An extremely short first transient can occur before Android applies the new volume step.
- Apps may opt out of playback capture.
- Only allowed playback usages can be captured.
- Protected/DRM content may not be analyzable.
- The independent `Max Media Volume` cap still works while the service is running, even if the current captured stream is silence.

A true brickwall limiter for every system sound requires system/root/vendor-level audio integration.

## Defaults

### dBFS
- Target RMS: `−18 dBFS`
- Peak ceiling: `−3 dBFS`

### Calibrated SPL
- Target: `70 dB SPL`
- Ceiling: `80 dB SPL`

### Common
- Max Media Volume: `70%`
- Normalization strength: `100%`
- Quiet normalization: enabled

## Build locally

Requirements:

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1

From the repository root:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build with GitHub Actions

`.github/workflows/build-apk.yml` builds the APK on pushes to `main` / `master`, pull requests, or manual `workflow_dispatch` runs. The downloadable artifact is named:

`SoundCeiling-v2-debug-apk`
