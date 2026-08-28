#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.7 release contract missing: $2" >&2; exit 1; }; }
need "$R/app/build.gradle.kts" 'versionCode=31'
need "$R/app/build.gradle.kts" 'versionName="0.7.7.7"'
need "$R/app/build.gradle.kts" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0777-preemptive-volume-up-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0777-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'name: SoundCeiling-v0.7.7.7-debug-apk'
need "$R/scripts/check-stable-debug-signing-contract.sh" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
echo 'v0.7.7.7 release contract: PASS'
