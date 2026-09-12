#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
S="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
fail(){ echo "v0.7.6 service wiring contract: $1" >&2; exit 1; }
! grep -Fq 'PRE_VOLUME_ASSUMED' "$S" || fail 'legacy PRE_VOLUME_ASSUMED fallback is forbidden'
! grep -Fq 'assumedPreVolumeFallbackAllowed' "$S" || fail 'legacy assumed PRE-volume fast fallback is forbidden'
! grep -Fq 'globalProbeBefore' "$S" || fail 'legacy 3-sample probe arrays are forbidden'
grep -Fq 'OutputLevelModel.evaluate' "$S" || fail 'service must build explicit output model'
grep -Fq '.outputLevels(actualLevels)' "$S" || fail 'coordinator frame must receive the same output snapshot'
grep -Fq 'controlFrame(now, current, levels' "$S" || fail 'playback path must pass one output snapshot into coordinator wiring'
grep -Fq 'dsp_differential_probe_begin' "$S" || fail 'differential probe begin telemetry missing'
grep -Fq 'dsp_differential_probe_result' "$S" || fail 'differential probe result telemetry missing'
