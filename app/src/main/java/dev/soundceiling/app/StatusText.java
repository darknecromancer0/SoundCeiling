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
        if (!s.enhancedSessionPermissionGranted) {
            return EnhancedSessionSetup.REQUIRED_STATUS + " · "
                    + EnhancedSessionSetup.ADB_GRANT_COMMAND;
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

    static String engine(RuntimeState s) {
        if (!s.running) return "Sound Ceiling выключен";
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
