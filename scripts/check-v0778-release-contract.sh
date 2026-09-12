#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.8+ historical release contract missing: $2" >&2; exit 1; }; }
python - "$R/app/build.gradle.kts" <<'PY'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text()
m=re.search(r'versionCode=(\d+)',s)
if not m or int(m.group(1)) < 32:
    raise SystemExit('v0.7.7.8+ historical release contract: expected versionCode >= 32')
PY
need "$R/app/build.gradle.kts" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0773-preenable-sanitization-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v0771-readback-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0777-preemptive-volume-up-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0778-release-contract.sh'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'OEM_DEFAULT_RUNTIME_QUARANTINED = true'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'EnhancedSessionCandidateMatrix.Profile profile'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'preEq.setEnabled(false)'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'mbc.setEnabled(false)'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'postEq.setEnabled(false)'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'limiter.setEnabled(false)'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java" 'pre_enable_stage_still_enabled'
need "$R/scripts/check-stable-debug-signing-contract.sh" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
echo 'v0.7.7.8+ historical release contract: PASS'
