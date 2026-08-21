#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
mapfile -t files < <(grep -RIl 'setStreamVolume(' "$PKG" || true)
for file in "${files[@]}"; do
  case "$(basename "$file")" in
    VolumeApplier.java|ToneController.java|SystemStreamController.java) ;;
    *) echo "Unexpected setStreamVolume call: $file" >&2; exit 1 ;;
  esac
done
for file in SafeVolumeController.java GlobalVisualizerBackend.java AudioBackendStatus.java OptionalDspController.java; do
  [[ -f "$PKG/$file" ]] || { echo "Missing v0.4 service component: $file" >&2; exit 1; }
done
# Downward writes still use the proven automatic clamp; v0.7 recovery has a separate explicit clamp.
grep -q 'SafetyGuard.clampAutomatic' "$PKG/SafeVolumeController.java" || {
  echo "SafeVolumeController must retain downward SafetyGuard" >&2; exit 1;
}
grep -q 'SafetyGuard.clampRecovery' "$PKG/SafeVolumeController.java" || {
  echo "SafeVolumeController must use explicit v0.7 recovery SafetyGuard" >&2; exit 1;
}
grep -q 'applier.applyIndex' "$PKG/SafeVolumeController.java" || {
  echo "SafeVolumeController must write only after final clamp" >&2; exit 1;
}
mapfile -t direct_apply_index < <(grep -RIl 'applier\.applyIndex' "$PKG" || true)
for file in "${direct_apply_index[@]}"; do
  [[ "$(basename "$file")" == "SafeVolumeController.java" ]] || {
    echo "VolumeApplier.applyIndex bypass outside SafeVolumeController: $file" >&2; exit 1;
  }
done
if [[ -f "$PKG/SystemStreamController.java" ]]; then
  grep -q 'policy.enabled' "$PKG/SystemStreamController.java" || {
    echo "System stream writes must be explicitly enabled" >&2; exit 1; }
  if grep -q 'STREAM_MUSIC' "$PKG/SystemStreamController.java"; then
    echo "SystemStreamController must not write Media stream" >&2; exit 1
  fi
fi
for token in 'PeakSafetyDetector' 'TransientGuard' 'ManualThresholdFollower' 'VolumeWriteTracker' 'LoudnessMeter' 'ACTION_QUIET' 'safeVolume'; do
  grep -q "$token" "$PKG/NormalizerService.java" || { echo "NormalizerService missing control integration: $token" >&2; exit 1; }
done
grep -q 'DiagnosticLog.anomaly' "$PKG/RuntimeStateStore.java" || {
  echo "RuntimeStateStore must feed automatic anomalies to the black-box logger" >&2; exit 1;
}
grep -q 'withDiagnostics' "$PKG/RuntimeStateStore.java" || {
  echo "RuntimeStateStore must publish diagnostics in RuntimeState" >&2; exit 1;
}
grep -q 'missing_spl_profile' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must publish/log missing_spl_profile HOLD" >&2; exit 1;
}
grep -q 'updateNotification(state)' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must update notification from RuntimeState" >&2; exit 1;
}

# v0.5 fail-closed source/policy invariants remain. v0.7 adds only explicit, owned recovery.
grep -Fq 'return AppRule.Mode.OFF' "$PKG/AppClassifier.java" || {
  echo "Samsung/system classifier must have an OFF default path" >&2; exit 1;
}
grep -Fq 'kind == SystemStreamPolicy.Kind.MEDIA' "$PKG/SystemStreamPolicies.java" || {
  echo "Only Media may default enabled in system stream policies" >&2; exit 1;
}
grep -Fq 'v0.6 compatibility overload remains one-way' "$PKG/HybridEngineCoordinator.java" || {
  echo "Hybrid coordinator must preserve the legacy one-way overload" >&2; exit 1;
}
grep -Fq 'adaptive_recovery' "$PKG/HybridEngineCoordinator.java" || {
  echo "Hybrid coordinator must expose explicit adaptive recovery" >&2; exit 1;
}
grep -Fq 'return new ControlPlan(current, true' "$PKG/HybridEngineCoordinator.java" || {
  echo "Hybrid coordinator must still HOLD upward requests without recovery authority" >&2; exit 1;
}
grep -Fq 'HybridEngineCoordinator.plan' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must route hybrid requests through HybridEngineCoordinator" >&2; exit 1;
}
grep -Fq 'hybridSnapshot.policy' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must pass resolved policy into hybrid control" >&2; exit 1;
}

# Verified per-app/DSP claims may only originate from the dedicated capability resolver.
mapfile -t per_app_claims < <(grep -RIl 'VolumeControlCapability.PER_APP_VERIFIED' "$PKG" || true)
for file in "${per_app_claims[@]}"; do
  [[ "$(basename "$file")" == "CapabilityResolver.java" ]] || {
    echo "PER_APP_VERIFIED claim outside CapabilityResolver: $file" >&2; exit 1;
  }
done
mapfile -t dsp_claims < <(grep -RIl 'DspTransportCapability.VERIFIED_' "$PKG" || true)
for file in "${dsp_claims[@]}"; do
  case "$(basename "$file")" in
    CapabilityResolver.java|StatusText.java) ;;
    *) echo "Verified DSP claim outside capability/status boundary: $file" >&2; exit 1 ;;
  esac
done

# Unsupported non-Media writes must be suppressed at the adapter boundary, not merely log-deduplicated.
for token in 'SystemStreamAttemptGate' 'attempts.shouldAttempt' 'attempts.markUnsupported'; do
  grep -Fq "$token" "$PKG/SystemStreamController.java" || {
    echo "SystemStreamController missing unsupported-write suppression: $token" >&2; exit 1;
  }
done
grep -Fq 'system_stream_unavailable' "$PKG/DiagnosticLog.java" || {
  echo "system_stream_unavailable must use compact transition logging" >&2; exit 1;
}

# v0.7 does not resurrect the ambiguous legacy RAISING state; owned recovery is explicit.
if grep -Fq 'RAISING' "$PKG/RuntimeState.java" "$PKG/StatusText.java" "$PKG/NormalizerService.java"; then
  echo "Production runtime must not publish the ambiguous legacy RAISING state" >&2; exit 1
fi
# Zero attribution must come from write provenance rather than a bare index transition heuristic.
grep -Fq 'UnexpectedZeroPolicy.isUnexpectedZero' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must classify zero through write provenance" >&2; exit 1;
}
grep -Fq 'next.unexpectedZero' "$PKG/RuntimeStateStore.java" || {
  echo "RuntimeStateStore must trust explicit zero provenance" >&2; exit 1;
}
if grep -Fq 'lastVolume > 0 && next.volumeIndex == 0' "$PKG/RuntimeStateStore.java"; then
  echo "RuntimeStateStore must not infer unexpected zero from index transition alone" >&2; exit 1
fi

echo "Source invariants: PASS"
