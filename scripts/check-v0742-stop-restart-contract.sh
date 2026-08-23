#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
fail(){ echo "v0.7.4.2 stop/restart contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }
require "$SERVICE" 'pcmCapture.requestStop();'
require "$SERVICE" 'waitForWorkerExit(activeWorker'
require "$SERVICE" 'capture_stop_ack'
require "$SERVICE" 'teardown_deferred_worker_alive'
require_order "$SERVICE" 'pcmCapture.requestStop();' 'waitForWorkerExit(activeWorker'
require_order "$SERVICE" 'waitForWorkerExit(activeWorker' 'pcmCapture.close();'
require_order "$SERVICE" 'waitForWorkerExit(activeWorker' 'p.stop();'
require_order "$SERVICE" 'waitForWorkerExit(activeWorker' 'visualizer.close();'
echo "v0.7.4.2 stop/restart contract: PASS"
