#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E="$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java"
O="$R/app/src/main/java/dev/soundceiling/app/OptionalDspController.java"
M="$R/app/src/main/java/dev/soundceiling/app/DspTransportManager.java"
T="$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
P="$R/app/src/main/java/dev/soundceiling/app"
S="$P/NormalizerService.java"
Q="$P/QuarantinedAudioSessionDiscovery.java"

python - "$P" "$S" "$Q" <<'PY'
from pathlib import Path
import sys

production, service, quarantine = map(Path, sys.argv[1:])
if not quarantine.is_file():
    raise SystemExit("v0.9 must wire a source-free quarantined session discovery")
if "new QuarantinedAudioSessionDiscovery()" not in service.read_text():
    raise SystemExit("NormalizerService must never construct the legacy DUMP discovery")

forbidden = (
    "android.permission.DUMP",
    "adb shell pm grant",
    "new DumpAudioSessionDiscovery",
    'ProcessBuilder("dumpsys"',
)
for source in production.rglob("*.java"):
    text = source.read_text()
    for token in forbidden:
        if token in text:
            raise SystemExit(f"obsolete privileged discovery remains in {source.name}: {token}")
PY

python - "$E" "$O" "$M" "$T" <<'PY'
from pathlib import Path
import sys

runtime, facade, manager, transport = (Path(value).read_text() for value in sys.argv[1:])
guard = "EnhancedSessionSetup.runtimeAllowed()"

def section(source, start, end):
    begin = source.index(start)
    finish = source.index(end, begin)
    return source[begin:finish]

runtime_update = section(runtime, "    void update(", "    boolean permissionGranted()")
if guard not in runtime_update:
    raise SystemExit("EnhancedSessionDspRuntime must reject quarantine before discovery")
if runtime_update.index(guard) > runtime_update.index("discovery.discover(nowMs)"):
    raise SystemExit("EnhancedSessionDspRuntime quarantine guard is too late")

facade_verify = section(facade,
        "    boolean verifyEnhancedSessionReadback(",
        "    boolean applyGain(")
if guard not in facade_verify:
    raise SystemExit("OptionalDspController must reject quarantine before the manager")
if facade_verify.index(guard) > facade_verify.index("transports.verifyEnhancedSessionReadback"):
    raise SystemExit("OptionalDspController quarantine guard is too late")

manager_verify = section(manager,
        "    boolean verifyEnhancedSessionReadback(",
        "    // -------------------------------------------------------------------------")
if guard not in manager_verify:
    raise SystemExit("DspTransportManager must reject quarantine before matrix iteration")
if manager_verify.index(guard) > manager_verify.index("EnhancedSessionCandidateMatrix.orderedProfiles()"):
    raise SystemExit("DspTransportManager quarantine guard is too late")

factory = section(transport,
        "    static AndroidDynamicsProcessingTransport forEnhancedSessionProbe(\n            DspEndpointHandle handle, EnhancedSessionCandidateMatrix.Profile profile)",
        "    static AndroidDynamicsProcessingTransport forNeutralGlobalProbe")
if guard not in factory:
    raise SystemExit("Enhanced Session Android factory must reject quarantine")
if factory.index(guard) > factory.index("new AndroidDynamicsProcessingTransport("):
    raise SystemExit("Enhanced Session Android factory quarantine guard is too late")

global_factory = section(transport,
        "    static AndroidDynamicsProcessingTransport forNeutralGlobalProbe",
        "    private static AndroidDynamicsProcessingTransport unavailable")
if guard not in global_factory:
    raise SystemExit("legacy session-zero factory must inherit the v0.9 constructor quarantine")
if global_factory.index(guard) > global_factory.index("new AndroidDynamicsProcessingTransport("):
    raise SystemExit("legacy session-zero quarantine guard is too late")

global_prepare = section(manager,
        "    DspTransport.Capability prepareGlobalProbeTransport()",
        "    boolean beginGlobalDifferentialProbe(")
if guard not in global_prepare:
    raise SystemExit("manager must reject legacy global transport before its Android factory")
if global_prepare.index(guard) > global_prepare.index("forNeutralGlobalProbe"):
    raise SystemExit("manager legacy global quarantine guard is too late")

for source, name in ((runtime_update, "runtime"), (facade_verify, "facade"),
                     (manager_verify, "manager"), (factory, "factory")):
    if "EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON" not in source:
        raise SystemExit(f"{name} must expose the canonical quarantine reason")
PY

echo 'v0.9 Session DSP quarantine contract: PASS'
