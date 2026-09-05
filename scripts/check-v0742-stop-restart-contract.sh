#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/app/src/main/java/dev/soundceiling/app/PcmCaptureBackend.java"
fail(){ echo "v0.7.4.2 stop/restart contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
CLOSE_BLOCK="$(awk '/@Override public void close\(\)/{flag=1} /private void releaseRecordOnce/{flag=0} flag' "$BACKEND")"
[[ -n "$CLOSE_BLOCK" ]] || fail "close block not found"
require "$BACKEND" 'readLock.notifyAll();'
require "$BACKEND" 'while (readInFlight)'
require "$BACKEND" 'readLock.wait('
require "$BACKEND" 'if (closed || stopRequested || releaseRequested) return 0;'
require "$BACKEND" 'if (closed || stopRequested) return 0;'
printf '%s\n' "$CLOSE_BLOCK" | grep -Fq 'requestStop();' || fail 'close must request AudioRecord stop'
printf '%s\n' "$CLOSE_BLOCK" | grep -Fq 'while (readInFlight)' || fail 'close must drain an in-flight read'
request=$(printf '%s\n' "$CLOSE_BLOCK" | grep -Fn 'requestStop();' | head -1 | cut -d: -f1)
wait=$(printf '%s\n' "$CLOSE_BLOCK" | grep -Fn 'while (readInFlight)' | head -1 | cut -d: -f1)
[[ $request -lt $wait ]] || fail 'AudioRecord stop must precede waiting for blocking read'
echo "v0.7.4.2 stop/restart contract: PASS"
