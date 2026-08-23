#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCM="$ROOT/app/src/main/java/dev/soundceiling/app/PcmCaptureBackend.java"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
fail(){ echo "v0.7.4.1 stop contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require "$PCM" 'private boolean readInFlight;'
require "$PCM" 'private boolean releaseRequested;'
require "$PCM" 'read = record.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);'
require "$PCM" 'releaseNow = releaseRequested && !released;'
require "$PCM" 'if (released || readInFlight) return;'
require "$PCM" 'try { record.stop(); } catch (RuntimeException ignored) {}'
require "$PCM" 'try { record.release(); } catch (RuntimeException ignored) {}'
require "$SERVICE" 'workerRunning.set(false);'
require "$SERVICE" 'if (!workerRunning.get()) return;'
require "$BUILD" 'versionCode=15'
require "$BUILD" 'versionName="0.7.4.1"'
require "$WF" 'run: bash ./scripts/check-v0741-stop-contract.sh'
require "$WF" 'name: SoundCeiling-v0.7.4.1-debug-apk'
python - "$PCM" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
close=s[s.index('    @Override public void close()'):]
if close.index('requestStop();') > close.index('releaseRecordOnce();'):
    raise SystemExit('v0.7.4.1 stop contract: stop must precede release decision')
release=s[s.index('    private void releaseRecordOnce()'):]
if release.index('if (released || readInFlight) return;') > release.index('record.release()'):
    raise SystemExit('v0.7.4.1 stop contract: in-flight guard must precede native release')
PY
echo "v0.7.4.1 stop contract: PASS"