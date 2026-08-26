#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.1 release contract missing: $2" >&2; exit 1; }; }
need "$R/app/build.gradle.kts" 'versionCode=25'
need "$R/app/build.gradle.kts" 'versionName="0.7.7.1"'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-neutral-session-probe-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'run: bash ./scripts/check-v0771-release-contract.sh'
need "$R/.github/workflows/build-apk.yml" 'name: SoundCeiling-v0.7.7.1-debug-apk'
need "$R/.github/workflows/build-apk.yml" 'key: soundceiling-debug-keystore-v1'
need "$R/.github/workflows/build-apk.yml" 'path: ~/.android/debug.keystore'
need "$R/.github/workflows/build-apk.yml" 'alias androiddebugkey'
need "$R/README.md" '# Sound Ceiling for Android - v0.7.7.1'
need "$R/README.md" '## v0.7.7.1 neutral Session DSP corrective'
need "$R/README.md" 'input-gain-only topology'
need "$R/README.md" 'Unstable PCM/Visualizer residuals are inconclusive, not unsafe'
need "$R/README.md" 'SoundCeiling-v0.7.7.1-debug-apk'
need "$R/docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md" 'Media to 3/15'
need "$R/docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md" 'attach_unstable_residuals'
need "$R/docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md" 'session_dsp_apply'
need "$R/docs/field-tests/2026-08-26-v0.7.7.1-samsung-checklist.md" 'APK SHA-256'
echo 'v0.7.7.1 release contract: PASS'
