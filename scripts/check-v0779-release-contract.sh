#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.9 release contract missing: $2" >&2; exit 1; }; }
need "$R/app/build.gradle.kts" 'versionCode=33'
need "$R/app/build.gradle.kts" 'versionName="0.7.7.9"'
need "$R/app/build.gradle.kts" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0779-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'name: SoundCeiling-v0.7.7.9-debug-apk'
need "$R/app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java" 'evidenceWindow = new byte[Math.max(requiredSamples, requiredSamples * 2 + 1)]'
need "$R/app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java" 'preResidual + toleranceDb < postResidual'
need "$R/app/src/test/java/dev/soundceiling/app/V071CaptureReferencePureTest.java" 'noisyMusicAroundSamsungStepsStillConvergesPreVolume'
need "$R/app/src/test/java/dev/soundceiling/app/V071CaptureReferencePureTest.java" 'oneOppositeTransientDoesNotPoisonReferenceForever'
need "$R/app/src/main/java/dev/soundceiling/app/HardCapLatch.java" 'MIN_STABLE_MS_AFTER_OVERSHOOT = 180L'
need "$R/app/src/test/java/dev/soundceiling/app/V0776StrictSafetyPureTest.java" 'hardCapLatchSurvivesSamsungSliderBurst'
need "$R/app/src/test/java/dev/soundceiling/app/V0776StrictSafetyPureTest.java" 'user down must not be counter-written'
need "$R/scripts/check-stable-debug-signing-contract.sh" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
echo 'v0.7.7.9 release contract: PASS'
