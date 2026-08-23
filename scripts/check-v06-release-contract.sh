#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
GRADLE="$ROOT/app/build.gradle.kts"
WORKFLOW="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
V06="$ROOT/scripts/check-v06-one-way-contract.sh"

require(){ local file="$1" needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.6 release file: $file" >&2; exit 1; }; grep -Fq -- "$needle" "$file" || { echo "Missing v0.6 release regression: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1" needle="$2"; if grep -Fq -- "$needle" "$file"; then echo "Forbidden v0.6 release regression: $(basename "$file") -> $needle" >&2; exit 1; fi; }

# Historical v0.6 release gate deliberately does not pin the current app identity.
# v0.7 must keep the proven v0.6 safety/build checks alive while owning release naming.
require "$README" 'v0.5.1'
require "$README" 'volume-neutral calibration'
require "$README" 'non-raising Quiet Now'

# Historical/safety gates and Android build remain wired.
for gate in \
  './scripts/run-pure-tests.sh' \
  './scripts/check-v04-storage-contract.sh' \
  './scripts/check-v05-storage-contract.sh' \
  './scripts/check-v05-app-contract.sh' \
  './scripts/check-source-invariants.sh' \
  './scripts/check-v06-one-way-contract.sh' \
  './scripts/check-v07-adaptive-contract.sh' \
  './scripts/check-v05-pcm-contract.sh' \
  './scripts/check-v05-microphone-invariant.sh' \
  './scripts/check-v05-control-adapters.sh' \
  './scripts/check-ui-contract.sh' \
  './scripts/check-v04-ui-contract.sh' \
  './scripts/check-v05-ui-contract.sh' \
  './scripts/check-v04-package-contract.sh' \
  './scripts/check-v05-release-contract.sh' \
  './scripts/check-v051-core-stability-contract.sh' \
  './scripts/check-v06-release-contract.sh' \
  './scripts/check-v07-ui-contract.sh' \
  './scripts/check-v07-release-contract.sh' \
  ':app:assembleDebug' \
  'sha256sum app-debug.apk'; do
  require "$WORKFLOW" "$gate"
done

# v0.6 downward safety remains present while v0.7 adds a separately bounded recovery path.
require "$PKG/SafetyGuard.java" 'clampAutomatic'
require "$PKG/SafetyGuard.java" 'clampRecovery'
require "$PKG/HybridEngineCoordinator.java" 'v0.6 compatibility overload remains one-way'
require "$PKG/HybridEngineCoordinator.java" 'adaptive_recovery'
require "$PKG/MainActivity.java" 'private void showProjectionExplanation()'
require "$PKG/MainActivity.java" 'SoundCeiling не записывает видео экрана'
# Preserve the v0.6 fallback guarantees, but v0.7 names the user-visible capability honestly.
require "$PKG/StatusText.java" 'Safe fallback'
require "$PKG/StatusText.java" 'System-only protection'
reject "$PKG/StatusText.java" 'System limiter only'

# Calibration must never touch Media, and runtime must not resurrect ambiguous RAISING state.
reject "$PKG/ToneController.java" 'setStreamVolume('
for file in "$PKG/RuntimeState.java" "$PKG/StatusText.java" "$PKG/NormalizerService.java"; do
  reject "$file" 'RAISING'
done
require "$PKG/CalibrationToneStateMachine.java" 'WAITING_STOPPED'

# Reviewed runtime metadata/cleanup from v0.6 stays protected.
reject "$PKG/NormalizerService.java" 'HEADER version=0.5.0'
reject "$PKG/NormalizerService.java" 'Sound Ceiling v0.5.0'
require "$PKG/NormalizerService.java" '.setContentTitle("Sound Ceiling v" + BuildConfig.VERSION_NAME)'
control_profile_fallbacks="$(grep -Fc '} else if (controlProfile != null) {' "$PKG/NormalizerService.java")"
[[ "$control_profile_fallbacks" -eq 1 ]] || { echo "Expected exactly one controlProfile threshold fallback; found $control_profile_fallbacks" >&2; exit 1; }

# Semantic theme and logical single-file log sharing remain release regressions.
require "$PKG/UiTheme.java" 'successSurface(Context context)'
require "$PKG/LogAccess.java" 'mergeSessionForShare'
require "$PKG/LogAccess.java" 'FileProvider.getUriForFile'
reject "$PKG/LogAccess.java" 'ACTION_SEND_MULTIPLE'
require "$MANIFEST" 'androidx.core.content.FileProvider'
require "$MANIFEST" '${applicationId}.fileprovider'
require "$ROOT/app/src/main/res/xml/file_paths.xml" '<cache-path name="shared_logs" path="shared_logs/" />'
require "$ROOT/gradle.properties" 'android.useAndroidX=true'

# Independent EQ survives navigation and process-owned settings remain persisted.
require "$PKG/SoundCeilingApplication.java" 'EqController.get(this).applySaved()'
require "$PKG/EqController.java" 'private static volatile EqController instance;'
require "$PKG/EqSettings.java" 'linkStrengthPercent'
reject "$PKG/EqView.java" 'onDetachedFromWindow'

# Detailed v0.6 behavior is retained as a historical regression gate.
require "$V06" 'v0.6 historical runtime/UI regressions: PASS'

echo "v0.6 historical release regression: PASS"
