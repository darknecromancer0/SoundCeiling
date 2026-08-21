from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0 and new in text:
        print(f"already applied: {path}")
        return
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, found {count}: {old!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched: {path}")


replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    "LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,\n"
    "                            loud.lufsLike, blockPeak, false, current,",
    "LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,\n"
    "                            loud.controlLoudnessDb, blockPeak, false, current,",
)

replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """                } else if (transientEvent.severity == TransientGuard.Severity.EMERGENCY) {
                    int extraSteps = Math.max(2,
                            (int) Math.ceil(Math.max(0f, transientEvent.deltaDb
                                    - effectiveProfile.transientWarningDb) / 3f));
                    int floor = effectiveProfile.autoMute ? controlCurve.minIndex()
                            : safetySettings.minIndex;
                    int target = Math.max(floor, current - extraSteps);
                    emergencyTarget = Math.min(emergencyTarget, target);
                    reason = \"transient_emergency\";
                    emergency = true;
                }
""",
    """                } else if (transientEvent.severity == TransientGuard.Severity.EMERGENCY) {
                    int floor = effectiveProfile.autoMute ? controlCurve.minIndex()
                            : safetySettings.minIndex;
                    int target = TransientAttenuationPolicy.safeTarget(current, controlCurve,
                            transientEvent.deltaDb, effectiveProfile.transientEmergencyDb,
                            floor, safetySettings.maxIndex);
                    if (target < emergencyTarget) {
                        emergencyTarget = target;
                        reason = \"transient_emergency\";
                        emergency = true;
                    }
                }
""",
)
