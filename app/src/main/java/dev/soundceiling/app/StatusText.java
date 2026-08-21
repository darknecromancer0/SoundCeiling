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
        if (s.dspTransportCapability == EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL
                || s.dspTransportCapability == EngineCapabilities.DspTransportCapability.VERIFIED_SOURCE) {
            return "DSP active";
        }
        if (s.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.MIXED) {
            return "Mixed apps · shared down-only control";
        }
        if (s.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.LIKELY
                || s.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.UNKNOWN) {
            return "Source uncertain · Global down-only control";
        }
        if (s.pcmState == PcmAvailabilityState.BLOCKED) {
            return "PCM blocked - safe fallback";
        }
        if (s.pcmState == PcmAvailabilityState.UNCERTAIN
                || s.pcmState == PcmAvailabilityState.ERROR) {
            return "Safe fallback";
        }
        if (s.pcmState == PcmAvailabilityState.ACTIVE
                && s.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT) {
            return "Smart PCM";
        }
        if (s.meteringCapability == EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS) {
            return "System limiter only";
        }
        return "Waiting for audio";
    }

    private StatusText() {}
}
