#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
mapfile -t files < <(grep -RIl 'setStreamVolume(' "$PKG" || true)
for file in "${files[@]}"; do
  case "$(basename "$file")" in
    VolumeApplier.java|ToneController.java|SystemStreamController.java|VolumeKeySafetyService.java) ;;
    *) echo "Unexpected setStreamVolume call: $file" >&2; exit 1 ;;
  esac
done
# v0.7.7.7 exception: Strict Safety owns hardware Volume-Up and writes one bounded Media step itself.
# The service is the only accessibility writer; its dedicated contract locks Volume-Down pass-through
# and targetIndexOnVolumeUp(current, hardMax) before setStreamVolume.
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
# Approved v0.7.1 design §11 and plan Task 5 Steps 3/4/7: the service gathers immutable
# evidence, then NormalizerControlCoordinator owns OutputGainPlanner and TransientGuard. The
# legacy detectors remain helpers only; a service-side control call would reintroduce a writer.
for token in 'NormalizerControlCoordinator' 'VolumeWriteTracker' 'LoudnessMeter' 'ACTION_QUIET' 'safeVolume'; do
  grep -q "$token" "$PKG/NormalizerService.java" || { echo "NormalizerService missing control integration: $token" >&2; exit 1; }
done
grep -Fq 'controlCoordinator.onFrame(controlFrame(' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must route control ticks through NormalizerControlCoordinator" >&2; exit 1; }
grep -Fq 'OutputGainPlanner.plan(' "$PKG/NormalizerControlCoordinator.java" || {
  echo "NormalizerControlCoordinator must own OutputGainPlanner" >&2; exit 1; }
grep -Fq 'TransientGuard transientGuard' "$PKG/NormalizerControlCoordinator.java" || {
  echo "NormalizerControlCoordinator must own TransientGuard" >&2; exit 1; }
grep -Fq 'transientGuard.onPlaybackState' "$PKG/NormalizerControlCoordinator.java" || {
  echo "NormalizerControlCoordinator must feed playback state to TransientGuard" >&2; exit 1; }
grep -Fq 'calibrationProfileValid' "$PKG/NormalizerControlCoordinator.java" || {
  echo "NormalizerControlCoordinator must own calibration-profile evidence" >&2; exit 1; }
grep -Fq '|| !frame.calibrationProfileValid' "$PKG/NormalizerControlCoordinator.java" || {
  echo "NormalizerControlCoordinator must gate positive control on calibration evidence" >&2; exit 1; }
grep -Fq 'missing_spl_profile' "$PKG/NormalizerControlCoordinator.java" || {
  echo "NormalizerControlCoordinator must publish missing_spl_profile fail-closed reason" >&2; exit 1; }
grep -Fq '.calibrationProfileValid(!Prefs.splMode(this) || currentProfile != null)' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must frame real SPL calibration-profile evidence" >&2; exit 1; }
for forbidden in 'PeakSafetyDetector.safeTargetForSourcePeak' 'ManualThresholdFollower' \
                 'AdaptiveVolumeEnvelope' 'TransientGuard'; do
  if grep -Fq "$forbidden" "$PKG/NormalizerService.java"; then
    echo "NormalizerService must not directly own legacy control: $forbidden" >&2; exit 1
  fi
done
grep -q 'DiagnosticLog.anomaly' "$PKG/RuntimeStateStore.java" || {
  echo "RuntimeStateStore must feed automatic anomalies to the black-box logger" >&2; exit 1;
}
grep -q 'withDiagnostics' "$PKG/RuntimeStateStore.java" || {
  echo "RuntimeStateStore must publish diagnostics in RuntimeState" >&2; exit 1;
}
# Approved v0.7.1 design §11 and plan Task 5 Steps 3/4/7 move the old service HOLD branch into
# the coordinator frame: only a real currentProfile proves SPL calibration, and the coordinator
# publishes missing_spl_profile while keeping hard-peak attenuation available.
grep -Fq 'profileForPolicy(hybridSnapshot.policy)' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must supply resolved policy/profile evidence to coordinator control" >&2; exit 1;
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
grep -Fq 'hybridSnapshot.policy' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must pass resolved policy evidence into coordinator control" >&2; exit 1;
}

# Verified per-app/DSP claims may only originate from the dedicated capability resolver.
mapfile -t per_app_claims < <(grep -RIl 'VolumeControlCapability.PER_APP_VERIFIED' "$PKG" || true)
for file in "${per_app_claims[@]}"; do
  [[ "$(basename "$file")" == "CapabilityResolver.java" ]] || {
    echo "PER_APP_VERIFIED claim outside CapabilityResolver: $file" >&2; exit 1;
  }
done
# v0.7.1 Global DSP UI may consume RuntimeState's typed VERIFIED_* value, but only
# CapabilityResolver/NormalizerService may derive it from the transport boundary.
mapfile -t dsp_claims < <(grep -RIl 'DspTransportCapability.VERIFIED_' "$PKG" || true)
for file in "${dsp_claims[@]}"; do
  case "$(basename "$file")" in
    CapabilityResolver.java|StatusText.java|NormalizerService.java|SimpleModeView.java|AdvancedModeView.java|AppsSystemView.java) ;;
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
