package dev.soundceiling.app;

final class StatusText {
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
                                    ? "Full experimental +12 dB"
                                    : "Safe +3 dB",
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
