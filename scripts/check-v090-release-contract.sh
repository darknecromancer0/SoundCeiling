#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
G="$R/app/build.gradle.kts"
W="$R/.github/workflows/build-apk.yml"
README="$R/README.md"
CHECKLIST="$R/docs/field-tests/2026-08-29-v0.9.0-samsung-checklist.md"
MANIFEST="$R/app/src/main/AndroidManifest.xml"

fail(){ echo "v0.9 release contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject(){ if grep -Fq -- "$2" "$1"; then fail "forbidden $(basename "$1") -> $2"; fi; }

need "$G" 'versionCode=36'
need "$G" 'versionName="0.9.0"'
need "$G" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'

need "$W" 'run: bash ./scripts/run-v090-session-quarantine-tests.sh'
need "$W" 'run: bash ./scripts/check-v090-session-quarantine-contract.sh'
need "$W" 'run: bash ./scripts/run-v090-pcm-feasibility-tests.sh'
need "$W" 'run: bash ./scripts/run-v090-pcm-shadow-tests.sh'
need "$W" 'run: bash ./scripts/check-v090-runtime-wiring-contract.sh'
need "$W" 'run: bash ./scripts/check-v090-release-contract.sh'
need "$W" 'name: SoundCeiling-v0.9.0-debug-apk'
need "$W" 'name: SoundCeiling-v0.9.0-debug-apk-checksum'
need "$W" 'path: app/build/outputs/apk/debug/app-debug.apk.sha256'
need "$W" 'name: SoundCeiling-v0.9-source-snapshot'

need "$README" '# Sound Ceiling for Android - v0.9.0'
need "$README" '## v0.9.0 PCM shadow feasibility and Session DSP quarantine'
need "$README" 'public_playback_capture_keeps_original_audio'
need "$CHECKLIST" 'Install-over from v0.8.0'
need "$CHECKLIST" 'field_quarantined_neutral_media_bypass'
need "$CHECKLIST" 'pcm_dsp_feasibility'
need "$CHECKLIST" 'pcm_dsp_shadow'
need "$CHECKLIST" 'audibleOutputAllowed=false'
need "$CHECKLIST" 'Volume Down'
need "$CHECKLIST" 'Safety Maximum'

need "$R/scripts/check-stable-debug-signing-contract.sh" \
  '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
reject "$MANIFEST" 'android.permission.DUMP'

echo 'v0.9 release contract: PASS'
