#!/usr/bin/env bash
set -euo pipefail
BRANCH='feature/v0.7-adaptive-envelope'
PATCH_B64='.ci/v0761-source.patch.gz.b64'
EXPECTED_B64='cafd35930c735fdaf2dd5676dcd6f8711a9ae90e2e83dcad8202058bde9e55eb'
EXPECTED_PATCH='18f9b5c253fdcea5dab18af22227d17c79d95434a9b0448bc10397366a4ec6b1'
actual_b64="$(sha256sum "$PATCH_B64" | awk '{print $1}')"
[[ "$actual_b64" == "$EXPECTED_B64" ]] || { echo "base64 SHA mismatch" >&2; exit 1; }
base64 -d "$PATCH_B64" | gzip -dc > /tmp/v0761-source.patch
actual_patch="$(sha256sum /tmp/v0761-source.patch | awk '{print $1}')"
[[ "$actual_patch" == "$EXPECTED_PATCH" ]] || { echo "patch SHA mismatch" >&2; exit 1; }

git fetch origin "$BRANCH"
git checkout -B "$BRANCH" "origin/$BRANCH"
git apply --check /tmp/v0761-source.patch
git apply /tmp/v0761-source.patch
rm -rf .ci

./scripts/run-pure-tests.sh
for script in scripts/check-*.sh; do bash "$script"; done

git config user.name 'darknecromancer0'
git config user.email '127566984+darknecromancer0@users.noreply.github.com'
git add -A
git commit -m 'fix: harden Samsung Global DSP probe v0.7.6.1'
git push origin HEAD:"$BRANCH"
