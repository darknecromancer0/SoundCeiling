#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app/src/main"
require(){ local file="$1"; local needle="$2"; grep -Fq "$needle" "$file" || { echo "Missing v0.4 package contract: $file -> $needle" >&2; exit 1; }; }
require "$ROOT/app/build.gradle.kts" 'versionName="0.4.0"'
require "$ROOT/app/build.gradle.kts" 'versionCode=6'
require "$ROOT/README.md" '# Sound Ceiling for Android - v0.4.0'
require "$ROOT/.github/workflows/build-apk.yml" 'SoundCeiling-v0.4.0-debug-apk'
require "$APP/AndroidManifest.xml" 'android:icon="@mipmap/ic_launcher"'
require "$APP/AndroidManifest.xml" 'android:roundIcon="@mipmap/ic_launcher_round"'
require "$APP/java/dev/soundceiling/app/NormalizerService.java" 'ACTION_QUIET'
require "$APP/java/dev/soundceiling/app/NormalizerService.java" 'PendingIntent'
require "$APP/java/dev/soundceiling/app/NormalizerService.java" 'Quiet now'
require "$APP/java/dev/soundceiling/app/NormalizerService.java" 'Stop'
[[ -f "$APP/res/drawable/ic_sound_ceiling_notification.xml" ]] || { echo "Missing notification icon" >&2; exit 1; }
[[ -f "$APP/res/drawable/ic_launcher_foreground.xml" ]] || { echo "Missing launcher foreground" >&2; exit 1; }
[[ -f "$APP/res/mipmap-anydpi-v26/ic_launcher.xml" ]] || { echo "Missing adaptive launcher" >&2; exit 1; }
[[ -f "$APP/res/mipmap-anydpi-v33/ic_launcher.xml" ]] || { echo "Missing monochrome launcher" >&2; exit 1; }
echo "v0.4 package contract: PASS"
