#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
for file in PackageSourceRepository.java AppPolicyStore.java AppClassifier.java; do
  [[ -f "$PKG/$file" ]] || { echo "Missing v0.5 app file: $file" >&2; exit 1; }
done
grep -Fq 'android.permission.QUERY_ALL_PACKAGES' "$MANIFEST" || { echo "Engineering build requires QUERY_ALL_PACKAGES" >&2; exit 1; }
grep -Fq 'getInstalledApplications' "$PKG/PackageSourceRepository.java" || { echo "Installed app discovery missing" >&2; exit 1; }
grep -Fq 'applicationInfo.uid' "$PKG/PackageSourceRepository.java" || { echo "Runtime UID resolution missing" >&2; exit 1; }
grep -Fq 'packageName' "$PKG/AppPolicyStore.java" || { echo "App policy persistence must use package name" >&2; exit 1; }
grep -Fq 'app_policies_v05' "$PKG/AppPolicyStore.java" || { echo "Versioned app policy store key missing" >&2; exit 1; }
grep -Fq 'AppClassifier.defaultMode' "$PKG/PackageSourceRepository.java" || { echo "Central app classifier must drive defaults" >&2; exit 1; }
if grep -Eq 'KEY_.*UID|uid_policies|policies_by_uid' "$PKG/AppPolicyStore.java"; then
  echo "UID must not be the persistent policy identity" >&2; exit 1
fi
echo "v0.5 app contract: PASS"
