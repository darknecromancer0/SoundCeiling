#!/usr/bin/env bash
set -euo pipefail

R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE="$R/app/build.gradle.kts"
MANIFEST="$R/app/src/main/AndroidManifest.xml"
WORKFLOW="$R/.github/workflows/build-apk.yml"
README="$R/README.md"
CHECKLIST="$R/docs/field-tests/2026-08-31-v0.9.1-samsung-relay-checklist.md"
CAPTURE="$R/app/src/main/java/dev/soundceiling/app/PcmCaptureBackend.java"
SERVICE="$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
SIGNING="$R/scripts/check-stable-debug-signing-contract.sh"

fail() { echo "v0.9.1 release contract: $*" >&2; exit 1; }
need_file() { [[ -f "$1" ]] || fail "missing $(basename "$1")"; }
need() { grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject() {
  if grep -Fqi -- "$2" "$1"; then
    fail "forbidden $(basename "$1") -> $2"
  fi
}

need "$GRADLE" 'versionCode=37'
need "$GRADLE" 'versionName="0.9.1"'
need "$GRADLE" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'

need "$MANIFEST" 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'
need "$MANIFEST" 'mediaProjection|mediaPlayback|specialUse'
need "$SERVICE" 'type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK'
need "$MANIFEST" 'Field-only engineering build: QUERY_ALL_PACKAGES'
need "$MANIFEST" 'Field-only engineering build: specialUse'
reject "$MANIFEST" 'android.permission.DUMP'
reject "$CAPTURE" 'USAGE_ASSISTANCE_ACCESSIBILITY'

need "$WORKFLOW" 'run: bash ./scripts/run-v091-accessibility-relay-tests.sh'
need "$WORKFLOW" 'run: bash ./scripts/check-v091-release-contract.sh'
need "$WORKFLOW" 'name: SoundCeiling-v0.9.1-source-snapshot'
need "$WORKFLOW" 'name: SoundCeiling-v0.9.1-debug-apk'
need "$WORKFLOW" 'name: SoundCeiling-v0.9.1-debug-apk-checksum'
need "$WORKFLOW" 'path: app/build/outputs/apk/debug/app-debug.apk.sha256'

need_file "$CHECKLIST"
need "$CHECKLIST" 'Install-over from v0.9.0'
need "$CHECKLIST" 'Media 0'
need "$CHECKLIST" 'Один чистый тихий поток'
need "$CHECKLIST" 'Safety Maximum'
need "$CHECKLIST" '10 минут'
need "$CHECKLIST" 'median <= 120 ms'
need "$CHECKLIST" 'p95 <= 200 ms'

need "$README" '# Sound Ceiling for Android - v0.9.1'
need "$README" 'experimental field path'
need "$README" 'built-in speaker only'
need "$README" 'field_quarantined_neutral_media_bypass'
need "$README" 'SHADOW_ONLY'
need "$README" 'PR #8 remains draft'
reject "$README" 'store-ready'
reject "$README" 'готово для Google Play'
reject "$README" 'готово для RuStore'

need "$SIGNING" '5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
need "$WORKFLOW" '5aa109027b8ae7675ce543eaf26402a2890bca97510bc2018661ea2231516be2'

echo 'v0.9.1 release contract: PASS'
