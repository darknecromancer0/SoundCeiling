#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mapfile -t files < <(grep -RIl 'setStreamVolume(' "$ROOT/app/src/main/java" || true)
for file in "${files[@]}"; do case "$(basename "$file")" in VolumeApplier.java|ToneController.java) ;; *) echo "Unexpected setStreamVolume call: $file" >&2; exit 1;; esac; done
echo "Source invariants: PASS"
