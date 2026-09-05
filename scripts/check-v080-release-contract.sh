#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail(){ echo "v0.8 release contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }

python - "$R/app/build.gradle.kts" <<'PY'
from pathlib import Path
import re, sys
s = Path(sys.argv[1]).read_text()
m = re.search(r'versionCode=(\d+)', s)
if not m or int(m.group(1)) < 35:
    raise SystemExit('v0.8 historical contract: expected versionCode >= 35')
PY
need "$R/app/build.gradle.kts" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v080-safe-custom-matrix-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v080-session-matrix-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v080-release-contract.sh'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'OEM_DEFAULT_RUNTIME_QUARANTINED = true'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'SAFE_CUSTOM_MATRIX_ENABLED = true'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'RUNTIME_QUARANTINED = true'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'field_quarantined_neutral_media_bypass'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionGainPolicy.java" 'MAX_POSITIVE_GAIN_DB = 3f'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionOutputGuard.java" 'MIN_CONTRADICTORY_RESIDUAL_DB = 12f'
need "$R/README.md" '## v0.8.0 safe Samsung Session DSP matrix'
need "$R/docs/field-tests/2026-08-29-v0.8.0-samsung-checklist.md" 'session_dsp_candidate_selected'
need "$R/docs/field-tests/2026-08-29-v0.8.0-samsung-checklist.md" 'Media 3/15'
need "$R/scripts/check-stable-debug-signing-contract.sh" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
echo 'v0.8 historical release contract under v0.9 quarantine: PASS'
