#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7 release contract missing: $2" >&2; exit 1; }; }
python - "$R/app/build.gradle.kts" <<'PY'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text(); m=re.search(r'versionCode=(\d+)',s)
if not m or int(m.group(1)) < 24: raise SystemExit('v0.7.7 release contract: expected versionCode >= 24')
PY
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'android.permission.DUMP'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'adb shell pm grant dev.soundceiling.app android.permission.DUMP'
need "$R/app/src/main/java/dev/soundceiling/app/AudioSessionOwnershipResolver.java" 'record.sessionId <= 0'
need "$R/app/src/main/java/dev/soundceiling/app/AudioSessionOwnershipResolver.java" 'record.uid == exactSource.uid'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java" 'dsp.enhancedSessionId() > 0'
need "$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java" 'session_dsp_apply'
need "$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java" 'session_dsp_apply_failed'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v077-samsung-3of15-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v077-session-telemetry-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v077-android-wiring-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v077-release-contract.sh'
need "$R/README.md" '## v0.7.7 Enhanced Session DSP'
need "$R/README.md" 'non-zero audio session'
need "$R/README.md" '3/15'
need "$R/README.md" 'adb shell pm grant dev.soundceiling.app android.permission.DUMP'
need "$R/docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md" 'session_dsp_apply'
need "$R/docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md" 'HOLD'
need "$R/docs/field-tests/2026-08-25-v0.7.7-samsung-checklist.md" 'APK SHA-256'
echo 'v0.7.7 release contract: PASS'
