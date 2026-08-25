#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7 Android wiring missing: $(basename "$1") -> $2" >&2; exit 1; }; }
S="$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
SM="$R/app/src/main/java/dev/soundceiling/app/SimpleModeView.java"
AD="$R/app/src/main/java/dev/soundceiling/app/AdvancedModeView.java"
need "$S" 'session_dsp_apply'
need "$S" 'enhancedSessionDsp.onApplyFailed("session_dsp_apply_failed")'
need "$S" '.enhancedSession(enhancedSessionDsp != null && enhancedSessionDsp.permissionGranted()'
need "$SM" 'EnhancedSessionSetup.ADB_GRANT_COMMAND'
need "$SM" 'copyEnhancedSessionCommand()'
need "$SM" 'runtime.sessionDspActive'
need "$SM" 'StatusText.sessionDsp(runtime)'
need "$AD" 'EnhancedSessionSetup.ADB_GRANT_COMMAND'
need "$AD" 'copyEnhancedSessionCommand()'
need "$AD" 'runtime.sessionDspActive'
need "$AD" 'StatusText.sessionDsp(runtime)'
echo 'v0.7.7 Android wiring contract: PASS'
