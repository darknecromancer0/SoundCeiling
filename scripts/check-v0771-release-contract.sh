#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.2 release contract missing: $2" >&2; exit 1; }; }
need "$R/app/build.gradle.kts" 'versionCode=26'
need "$R/app/build.gradle.kts" 'versionName="0.7.7.2"'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-neutral-session-probe-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v0771-readback-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-readback-wiring-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'name: SoundCeiling-v0.7.7.2-debug-apk'
need "$R/scripts/run-v0771-readback-tests.sh" 'run-v0771-session-authority-bridge-tests.sh'
need "$R/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java" 'disabledLimiterCompatibilityShellVerifies'
need "$R/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java" 'activeLimiterRejects'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'enhanced_session_input_gain_with_disabled_limiter_shell_unverified'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'getLimiterByChannelIndex(channel).isEnabled()'
need "$R/README.md" '# Sound Ceiling for Android - v0.7.7.2'
need "$R/README.md" 'disabled limiter compatibility shell'
need "$R/README.md" '0 dB -> -0.5 dB -> 0 dB'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'Media to 3/15'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'session_dsp_readback_result'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'limiter enabled=false'
need "$R/docs/field-tests/2026-08-26-v0.7.7.2-samsung-checklist.md" 'APK SHA-256'
echo 'v0.7.7.2 release contract: PASS'
