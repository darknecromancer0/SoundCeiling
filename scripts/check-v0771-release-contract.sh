#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.2 release contract missing: $2" >&2; exit 1; }; }
python - "$R/app/build.gradle.kts" <<'PY'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text(); m=re.search(r'versionCode=(\d+)',s)
if not m or int(m.group(1)) < 26:
    raise SystemExit('v0.7.7.2 historical release contract: expected versionCode >= 26')
PY
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v0771-readback-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-readback-wiring-contract.sh'
need "$R/scripts/run-v0771-readback-tests.sh" 'run-v0771-session-authority-bridge-tests.sh'
need "$R/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java" 'disabledLimiterCompatibilityShellVerifies'
need "$R/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java" 'activeLimiterRejects'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'enhanced_session_custom_candidate_unverified:'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'getLimiterByChannelIndex(channel).isEnabled()'
need "$R/README.md" '## v0.7.7.2 Samsung Session DSP compatibility corrective'
need "$R/README.md" 'disabled limiter compatibility shell'
need "$R/README.md" '0 dB -> -0.5 dB -> 0 dB'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'Media to 3/15'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'session_dsp_readback_result'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'limiter enabled=false'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'APK SHA-256'
echo 'v0.7.7.2 release contract: PASS'
