#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PREFS="$ROOT/app/src/main/java/dev/soundceiling/app/Prefs.java"
STORE="$ROOT/app/src/main/java/dev/soundceiling/app/ControlProfileStore.java"
[[ -f "$STORE" ]] || { echo "Missing ControlProfileStore.java" >&2; exit 1; }
for token in MIN_MEDIA_INDEX SAFETY_LOCK_ENABLED SAFETY_LOCK_PERCENT QUIET_INDEX NORMALIZATION_PRESET TARGET_LOUDNESS LOUDNESS_TOLERANCE NORMALIZATION_STRENGTH DOWNWARD_ATTACK_MS UPWARD_RELEASE_MS HOLD_AFTER_LOUD_MS MAX_DOWN_STEPS MAX_UP_STEPS SOURCE_PEAK_THRESHOLD TRANSIENT_WARNING TRANSIENT_EMERGENCY RECOVERY_INTERVAL_MS ACTIVE_PROFILE THEME_MODE; do
  grep -q "$token" "$PREFS" || { echo "Missing Prefs key: $token" >&2; exit 1; }
done
for token in 'save(Context' 'load(Context' 'delete(Context' 'rename(Context' 'duplicate(Context' 'isModified(Context'; do
  grep -q "$token" "$STORE" || { echo "Missing profile store API: $token" >&2; exit 1; }
done
