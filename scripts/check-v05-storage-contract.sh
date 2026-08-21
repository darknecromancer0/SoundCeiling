#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
for file in DeviceProfileV2.java DeviceProfileMigrator.java DeviceProfileV2Store.java; do
  [[ -f "$PKG/$file" ]] || { echo "Missing v0.5 storage file: $file" >&2; exit 1; }
done
grep -Fq 'SCHEMA_VERSION = 2' "$PKG/DeviceProfileV2.java" || { echo "DeviceProfileV2 schema must be 2" >&2; exit 1; }
grep -Fq 'old.calibrationOffsetDb' "$PKG/DeviceProfileMigrator.java" || { echo "v0.4 calibration must migrate" >&2; exit 1; }
grep -Fq 'SystemStreamPolicies.defaults()' "$PKG/DeviceProfileMigrator.java" || { echo "migrated stream defaults required" >&2; exit 1; }
grep -Fq 'DeviceProfileMigrator.fromV04' "$PKG/DeviceProfileV2Store.java" || { echo "legacy profile migration path required" >&2; exit 1; }
grep -Fq 'device_profiles_hybrid_v2' "$PKG/DeviceProfileV2Store.java" || { echo "versioned v0.5 profile key required" >&2; exit 1; }
grep -Fq 'One corrupt route must never disable the limiter' "$PKG/DeviceProfileV2Store.java" || { echo "per-entry corruption isolation required" >&2; exit 1; }
echo "v0.5 storage contract: PASS"
