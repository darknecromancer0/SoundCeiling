#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail(){ echo "v0.7.7.10 emergency DSP quarantine contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
need "$R/app/build.gradle.kts" 'versionCode=34'
need "$R/app/build.gradle.kts" 'versionName="0.7.7.10"'
need "$R/app/build.gradle.kts" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v07710-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v07710-emergency-dsp-quarantine-test.sh'
need "$R/.github/workflows/build-apk.yml" 'name: SoundCeiling-v0.7.7.10-debug-apk'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'static final boolean RUNTIME_QUARANTINED = true;'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java" 'session_dsp_emergency_quarantine'
need "$R/app/src/test/java/dev/soundceiling/app/V07710EmergencyDspQuarantinePureTest.java" 'enhancedSessionRuntimeMustBeQuarantined'
python - "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" <<'PY2'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
safe='"enhanced_session_samsung_constructor_shell_unverified",\n                false, true);'
unsafe='"enhanced_session_samsung_constructor_shell_unverified",\n                true, true);'
if safe not in s:
    raise SystemExit('Enhanced Session OEM default fallback is not quarantined')
if unsafe in s:
    raise SystemExit('unsafe Enhanced Session OEM default fallback re-enabled')
PY2
need "$R/scripts/check-stable-debug-signing-contract.sh" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
echo 'v0.7.7.10 emergency DSP quarantine contract: PASS'
