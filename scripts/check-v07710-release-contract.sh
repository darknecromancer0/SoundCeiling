#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail(){ echo "v0.7.7.10 historical OEM-default quarantine contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
python - "$R/app/build.gradle.kts" <<'PY'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text(); m=re.search(r'versionCode=(\d+)',s)
if not m or int(m.group(1)) < 34:
    raise SystemExit('v0.7.7.10 historical contract: expected versionCode >= 34')
PY
need "$R/app/build.gradle.kts" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v07710-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v07710-emergency-dsp-quarantine-test.sh'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'OEM_DEFAULT_RUNTIME_QUARANTINED = true'
need "$R/app/src/test/java/dev/soundceiling/app/V07710EmergencyDspQuarantinePureTest.java" 'enhancedSessionOemDefaultMustStayQuarantined'
python - "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" <<'PY2'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
start=s.index('static AndroidDynamicsProcessingTransport forEnhancedSessionProbe')
end=s.index('static AndroidDynamicsProcessingTransport forNeutralGlobalProbe', start)
factory=s[start:end]
if 'new DynamicsProcessing(audioSessionId)' in factory or 'allowDefaultConfigFallback' in factory:
    raise SystemExit('unsafe Enhanced Session OEM-default fallback re-enabled')
if 'EnhancedSessionCandidateMatrix.Profile profile' not in factory:
    raise SystemExit('explicit custom Enhanced Session replacement missing')
PY2
need "$R/scripts/check-stable-debug-signing-contract.sh" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
echo 'v0.7.7.10 historical OEM-default quarantine contract: PASS'
