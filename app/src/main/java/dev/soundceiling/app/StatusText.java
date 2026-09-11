package dev.soundceiling.app;

final class StatusText {
    static String independentLevels(RuntimeState s) {
        if (!s.running) return "Показатели появятся после запуска и захвата звука.";
        if (s.relayAudible) return "Активен Relay — его выход и усиление показаны в карточке Relay.";
        if (!s.signalPresent || s.meterAgeMs > 1500L) return "Нет свежего звука для измерения.";
        if (s.meteringCapability == EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS) {
            return "Резервный измеритель общего выхода\nRMS: " + db(s.rmsDbfs)
                    + " · Peak: " + db(s.rawPeakDbfs) + " dBFS\nРаздельное измерение источника недоступно.";
        }
        if (s.meteringCapability != EngineCapabilities.MeteringCapability.PCM_EXACT
                && s.meteringCapability != EngineCapabilities.MeteringCapability.PCM_MIXED) {
            return "Измерение уровня PCM недоступно.";
        }
        String meters = "LUFS-like (≈3 с): " + db(s.sourceLoudness)
                + " · RMS: " + db(s.rmsDbfs) + " dBFS"
                + "\nPeak вход / расчётный выход: " + db(s.rawPeakDbfs) + " / "
                + db(s.projectedPeakDbfs) + " dBFS";
        if (s.meteringCapability == EngineCapabilities.MeteringCapability.PCM_MIXED) {
            return "PCM содержит смесь источников.\n" + meters;
        }
        if (!Float.isFinite(s.independentTargetDb)) {
            return "Вход (быстрый): " + db(s.controlLoudnessDb)
                    + " LUFS-like\nЦель обычной автогромкости ещё не определена.\n" + meters;
        }
        return "Вход → расчётный выход: " + db(s.controlLoudnessDb) + " → "
                + db(s.independentOutputDb) + " LUFS-like"
                + "\nЦель ползунка: " + db(s.independentTargetDb)
                + " · текущая цель: " + db(s.independentEffectiveTargetDb) + " LUFS-like"
                + "\nКоррекция Media: " + signedDb(s.independentGainDb) + " дБ"
                + " · ступень " + s.volumeIndex + "/" + s.volumeMax + "\n" + meters;
    }

    private static String db(float value) {
        return Float.isFinite(value) ? String.format(java.util.Locale.US, "%.1f", value) : "—";
    }
    private static String signedDb(float value) {
        return Float.isFinite(value) ? String.format(java.util.Locale.US, "%+.1f", value) : "—";
    }

    static String capture(RuntimeState s) {
        return switch (s.captureStatus) {
            case RUNNING -> "Захват работает";
            case STARTING -> "Запуск захвата…";
            case WAITING_SIGNAL -> "Ожидание звука";
            case STOPPED -> "Захват остановлен";
            case ERROR -> s.message;
        };
    }

    static String signal(RuntimeState s) {
        return s.signalPresent ? "Звук обнаружен" : "Нет захватываемого звука";
    }

    static String controller(RuntimeState s) {
        if (s.manualSafetyPause) {
            return "Автогромкость на паузе"
                    + (s.lastControllerReason.contains("user_down") ? " — Volume Down" : "")
                    + ". Нажмите «Продолжить» или измените громкость SoundCeiling.";
        }
        if (s.lastControllerReason.startsWith("user_volume_")) {
            return switch (s.lastControllerReason) {
                case "user_volume_learning" -> "Запоминает начальную громкость · 1–2 сек";
                case "user_volume_loud_down", "user_volume_down_dwell", "user_volume_fast_down" -> "Снижает громкий фрагмент";
                case "user_volume_attack_hold" -> "Удерживает уровень после громкого звука";
                case "user_volume_quiet_up", "user_volume_up_dwell" -> "Повышает тихий фрагмент";
                case "user_volume_at_target" -> "Громкость близка к выбранной";
                case "user_volume_nearest_step" -> "Ближайшая ступень Samsung к выбранной громкости";
                case "user_volume_waiting_audio" -> "Ждёт звук для регулировки";
                case "user_volume_waiting_source" -> "Ждёт доступный источник PCM";
                case "user_volume_muted" -> "Громкость SoundCeiling: 0";
                case "user_volume_lowest_step" -> "Достигнута минимальная ступень Samsung";
                case "user_volume_highest_step" -> "Достигнута максимальная ступень Samsung";
                case "user_volume_peak_limit" -> "Повышение ограничено пиком звука";
                case "user_volume_raise_policy_blocked" -> "Повышение ограничено правилом источника";
                case "user_volume_normalization_off" -> "Нормализация выключена в настройках";
                default -> "Регулирует громкость SoundCeiling";
            };
        }
        if (s.lastControllerReason.startsWith("media_auto_")) {
            if (s.controlActivity == RuntimeState.ControlActivity.RECOVERING)
                return "Samsung Media: повышает тихий звук на одну ступень";
            if (s.controlActivity == RuntimeState.ControlActivity.DECREASING)
                return "Samsung Media: снижает громкий звук на одну ступень";
            if (s.lastControllerReason.contains("no_output_loudness"))
                return "Samsung Media: ждёт измерение PCM; автозаписей нет";
            if (s.lastControllerReason.contains("reference_ambiguous"))
                return "Samsung Media: безопасное направление неоднозначно — удержание";
            if (s.lastControllerReason.contains("next_step_exceeds_ceiling"))
                return "Samsung Media: следующая ступень превысит потолок — удержание";
            return "Samsung Media: удержание · " + s.lastControllerReason;
        }
        return switch (s.controlActivity) {
            case HOLDING -> "Регулятор: удерживает";
            case DECREASING -> "Регулятор: снижает";
            case RECOVERING -> "Recovery: плавно возвращает только снижение, ранее сделанное SoundCeiling";
            case MINIMUM_LIMIT -> "Регулятор: ограничен слышимым минимумом";
            case MAXIMUM_LIMIT -> "Регулятор: ограничен максимумом";
            case ERROR -> "Регулятор: ошибка";
            case IDLE -> "Регулятор: не активен";
        };
    }

    static String media(RuntimeState s) {
        return "Media " + s.volumeIndex + "/" + s.volumeMax;
    }

    static String sessionDsp(RuntimeState s) {
        if (EnhancedSessionSetup.RUNTIME_QUARANTINED) {
            return "Session DSP quarantined · "
                    + EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON;
        }
        if (s.sessionDspActive && s.sessionId > 0) {
            String pkg = s.sessionPackage.isEmpty() ? "unknown" : s.sessionPackage;
            String activePrefix = "session_dsp_active:";
            String profile = s.sessionDspReason != null
                    && s.sessionDspReason.startsWith(activePrefix)
                    ? s.sessionDspReason.substring(activePrefix.length()) : "unknown_profile";
            return String.format(java.util.Locale.US,
                    "Session DSP %d · %s · profile %s · requested %+.2f dB · applied %+.2f dB"
                            + " · pilot max %+.2f dB",
                    s.sessionId, pkg, profile, s.sessionDspRequestedGainDb,
                    s.sessionDspAppliedGainDb, EnhancedSessionGainPolicy.MAX_POSITIVE_GAIN_DB);
        }
        String reason = s.sessionDspReason == null || s.sessionDspReason.isEmpty()
                ? "session_dsp_unavailable" : s.sessionDspReason;
        return "Session DSP unavailable · " + reason;
    }

    static String pcmDsp(RuntimeState s) {
        String reason = s.pcmDspReason == null || s.pcmDspReason.isEmpty()
                ? "public_playback_capture_keeps_original_audio" : s.pcmDspReason;
        String mode = s.pcmDspMode == null || s.pcmDspMode.isEmpty()
                ? "SHADOW_ONLY" : s.pcmDspMode;
        String base = "SHADOW_ONLY".equals(mode) && !s.pcmDspAudibleOutputAllowed
                ? "PCM DSP: Shadow only · audible output blocked"
                : "PCM DSP: " + mode;
        if (!s.pcmShadowActive) return base + " · " + reason;
        return String.format(java.util.Locale.US,
                "%s · requested %+.2f dB · shadow %+.2f dB · %s · %s",
                base, s.pcmShadowRequestedGainDb, s.pcmShadowAppliedGainDb,
                reason, s.pcmShadowReason);
    }

    static String relay(RuntimeState s) {
        String reason = s.relayReason == null || s.relayReason.isEmpty()
                ? "relay_off" : s.relayReason;
        return switch (s.relayState) {
            case "PREFLIGHT" -> "Relay: проверка условий · " + reason;
            case "CAPTURE_PROVEN" -> "Relay: точный PCM подтверждён · " + reason;
            case "MEDIA_MUTING" -> "Relay: временно выключает Samsung Media · " + reason;
            case "MEDIA_MUTED" -> "Relay: Media 0, проверка PCM · " + reason;
            case "QUIET_PROBE" -> String.format(java.util.Locale.US,
                    "Тихая проба Relay · осталось %.1f с · %s",
                    s.relayProbeRemainingMs / 1000f, reason);
            case "AWAITING_CONFIRMATION" ->
                    "Relay: ждёт подтверждения тихой пробы · " + reason;
            case "ACTIVE" -> s.relayAudible
                    ? String.format(java.util.Locale.US,
                            "Relay активен · %s · gain %+.2f dB · output %.1f dBFS · %s",
                            s.relayFullExperimental
                                    ? "Full +30 dB"
                                    : "Normal +24 dB",
                            s.relayAppliedGainDb,
                            s.relayOutputPeakDbfs, reason)
                    : "Relay: запуск подтверждённого выхода · " + reason;
            case "ABORTING" -> "Relay: безопасная остановка · " + reason;
            case "RECOVERY_REQUIRED" ->
                    "Relay: нужно восстановление Media · " + reason;
            default -> "Relay выключен · " + reason;
        };
    }

    static String engine(RuntimeState s) {
        if (!s.running) return "Sound Ceiling выключен";
        if (s.relayAudible) return "Accessibility Relay";
        if (s.manualSafetyPause) return "Samsung Media: автогромкость на паузе";
        if (s.lastControllerReason.startsWith("user_volume_"))
            return "SoundCeiling · собственная громкость";
        if (s.lastControllerReason.startsWith("media_auto_"))
            return "Samsung Media Auto Volume";
        if (s.sessionDspActive && s.sessionId > 0) return "Session DSP";

        boolean precisePcm = s.pcmState == PcmAvailabilityState.ACTIVE
                && s.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT
                && s.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.EXACT;
        if (precisePcm) return "Precise PCM";

        boolean signalFallback = s.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT
                || s.meteringCapability == EngineCapabilities.MeteringCapability.PCM_MIXED
                || s.meteringCapability == EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS;
        if (signalFallback) return "Safe fallback";

        return "System-only protection";
    }

    private StatusText() {}
}
