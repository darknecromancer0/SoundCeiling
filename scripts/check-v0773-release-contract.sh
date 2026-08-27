#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.3 release contract missing: $2" >&2; exit 1; }; }
need "$R/app/build.gradle.kts" 'versionCode=27'
need "$R/app/build.gradle.kts" 'versionName="0.7.7.3"'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0773-preenable-sanitization-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/run-v0771-readback-tests.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-readback-wiring-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0773-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'name: SoundCeiling-v0.7.7.3-debug-apk'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java" 'verifyPreEnableSanitized'
need "$R/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java" 'preEnableSanitizedShellVerifies'
need "$R/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java" 'preEnableAlreadyEnabledRejects'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'true, true, 0, 1f, 60f, 10f, -1f, 0f'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'sanitizeEnhancedSessionCandidateBeforeEnable'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'setLimiterByChannelIndex(channel, disabledSamsungLimiter)'
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'enhanced_session_pre_enable_sanitized_unverified'
need "$R/docs/field-tests/2026-08-27-v0.7.7.3-samsung-checklist.md" 'Media to 3/15'
need "$R/docs/field-tests/2026-08-27-v0.7.7.3-samsung-checklist.md" 'session_dsp_readback_result'
need "$R/docs/field-tests/2026-08-27-v0.7.7.3-samsung-checklist.md" 'pre_enable'
need "$R/docs/field-tests/2026-08-27-v0.7.7.3-samsung-checklist.md" 'APK SHA-256'
echo 'v0.7.7.3 release contract: PASS'
