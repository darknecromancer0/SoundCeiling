#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
fail(){ echo "v0.7.4.2 stop/restart contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
STOP_BLOCK="$(awk '/private synchronized void stopSafe/{flag=1} /@Override public void onDestroy/{flag=0} flag' "$SERVICE")"
[[ -n "$STOP_BLOCK" ]] || fail "stopSafe block not found"
for needle in 'pcmCapture.requestStop();' 'waitForWorkerExit(activeWorker' 'capture_stop_ack' 'teardown_deferred_worker_alive'; do
  require "$SERVICE" "$needle"
done
line_in_stop(){ printf '%s\n' "$STOP_BLOCK" | grep -Fn -- "$1" | head -1 | cut -d: -f1; }
for needle in 'pcmCapture.requestStop();' 'waitForWorkerExit(activeWorker' 'pcmCapture.close();' 'p.stop();' 'visualizer.close();'; do
  printf '%s\n' "$STOP_BLOCK" | grep -Fq -- "$needle" || fail "stopSafe missing -> $needle"
done
request=$(line_in_stop 'pcmCapture.requestStop();')
wait=$(line_in_stop 'waitForWorkerExit(activeWorker')
close=$(line_in_stop 'pcmCapture.close();')
projection=$(line_in_stop 'p.stop();')
visualizer=$(line_in_stop 'visualizer.close();')
[[ $request -lt $wait ]] || fail "requestStop must precede worker join"
[[ $wait -lt $close && $wait -lt $projection && $wait -lt $visualizer ]] || fail "worker join must precede native resource teardown"
echo "v0.7.4.2 stop/restart contract: PASS"
