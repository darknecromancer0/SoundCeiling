#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7 Android wiring missing: $(basename "$1") -> $2" >&2; exit 1; }; }
reject(){ if grep -Fq -- "$2" "$1"; then echo "v0.9 quarantine forbids: $(basename "$1") -> $2" >&2; exit 1; fi; }
S="$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
SM="$R/app/src/main/java/dev/soundceiling/app/SimpleModeView.java"
AD="$R/app/src/main/java/dev/soundceiling/app/AdvancedModeView.java"
M="$R/app/src/main/AndroidManifest.xml"
need "$S" 'session_dsp_apply'
need "$S" 'enhancedSessionDsp.onApplyFailed("session_dsp_apply_failed")'
need "$S" '.enhancedSession(enhancedSessionDsp != null && enhancedSessionDsp.permissionGranted()'
need "$SM" 'runtime.sessionDspActive'
need "$SM" 'StatusText.sessionDsp(runtime)'
need "$SM" 'StatusText.pcmDsp(runtime)'
need "$AD" 'runtime.sessionDspActive'
need "$AD" 'StatusText.sessionDsp(runtime)'
need "$AD" 'StatusText.pcmDsp(runtime)'
reject "$SM" 'EnhancedSessionSetup.ADB_GRANT_COMMAND'
reject "$AD" 'EnhancedSessionSetup.ADB_GRANT_COMMAND'
reject "$M" 'android.permission.DUMP'
echo 'v0.7.7 historical Android wiring under v0.9 quarantine: PASS'
