#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/app/src/main/java/dev/soundceiling/app"
OBSERVER="$PACKAGE/PlaybackObserver.java"
RUNTIME="$PACKAGE/AccessibilityRelayRuntime.java"
RESOLVER="$PACKAGE/HybridRuntimeResolver.java"
OWNERSHIP="$PACKAGE/RelayPlaybackOwnership.java"

fail() { echo "v0.9.1 playback ownership contract: $*" >&2; exit 1; }
need() { grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject() { if grep -Fq -- "$2" "$1"; then fail "forbidden $(basename "$1") -> $2"; fi; }

need "$OBSERVER" 'RelayPlaybackOwnership.uniqueNewByStableKey('
need "$OBSERVER" 'RelayPlaybackOwnership.excludeOwnedByStableKey('
need "$OBSERVER" 'AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY'
need "$OBSERVER" 'AudioAttributes.CONTENT_TYPE_MUSIC'
need "$OBSERVER" 'filtered.ownershipProven()'
need "$OWNERSHIP" 'owned.equals(item)'
need "$RESOLVER" 'beginRelayRendererOwnership()'
need "$RESOLVER" 'claimRelayRendererOwnership('
need "$RESOLVER" 'clearRelayRendererOwnership()'
need "$RUNTIME" 'beginRelayRendererOwnership()'
need "$RUNTIME" 'claimRelayRendererOwnership(baseline)'
need "$RUNTIME" 'clearRelayRendererOwnership()'
reject "$OBSERVER" 'getAudioSessionId()'

echo 'v0.9.1 playback ownership contract: PASS'
