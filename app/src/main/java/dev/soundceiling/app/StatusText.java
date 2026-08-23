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

    static String engine(RuntimeState s) {
        if (!s.running) return "Sound Ceiling выключен";

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
