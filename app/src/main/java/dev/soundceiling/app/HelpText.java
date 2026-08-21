package dev.soundceiling.app;

final class HelpText {
    static final String MIN_MEDIA = "MIN_MEDIA";
    static final String MAX_MEDIA = "MAX_MEDIA";
    static final String SAFETY_LOCK = "SAFETY_LOCK";
    static final String SOURCE_PEAK = "SOURCE_PEAK";
    static final String TRANSIENT_WARNING = "TRANSIENT_WARNING";
    static final String TRANSIENT_EMERGENCY = "TRANSIENT_EMERGENCY";
    static final String TARGET_LOUDNESS = "TARGET_LOUDNESS";
    static final String NORMALIZATION_STRENGTH = "NORMALIZATION_STRENGTH";
    static final String TOLERANCE = "TOLERANCE";
    static final String DOWN_ATTACK = "DOWN_ATTACK";
    static final String UP_RELEASE = "UP_RELEASE";
    static final String HOLD = "HOLD";
    static final String RECOVERY = "RECOVERY";
    static final String AUTO_MUTE = "AUTO_MUTE";

    static String forKey(String key) {
        if (MIN_MEDIA.equals(key)) return "Нижняя граница Media. При ручном уходе к минимуму авто-повышение приостанавливается.";
        if (MAX_MEDIA.equals(key)) return "Обычный верхний предел системной Media-громкости. Нормализатор не должен поднимать выше него.";
        if (SAFETY_LOCK.equals(key)) return "Независимый финальный блокиратор. Любая запись Media проходит через него последней.";
        if (SOURCE_PEAK.equals(key)) return "Порог raw peak в dBFS. Горячий первый блок может сразу запросить снижение, не ожидая RMS/LUFS.";
        if (TRANSIENT_WARNING.equals(key)) return "Рост быстрого уровня относительно недавней базы, после которого защита заранее сужает громкость.";
        if (TRANSIENT_EMERGENCY.equals(key)) return "Более сильный скачок уровня. Вызывает более агрессивное аварийное снижение.";
        if (TARGET_LOUDNESS.equals(key)) return "Цель LUFS-like для доступного PCM. Это уровень источника, а не положение ползунка Samsung.";
        if (NORMALIZATION_STRENGTH.equals(key)) return "Насколько сильно Sound Ceiling стремится выравнивать тихие и громкие участки.";
        if (TOLERANCE.equals(key)) return "Мёртвая зона вокруг целевой громкости. Больше значение означает меньше мелких коррекций.";
        if (DOWN_ATTACK.equals(key)) return "Обычная задержка между снижениями нормализатора. Emergency peak работает отдельно и не ждёт её.";
        if (UP_RELEASE.equals(key)) return "Насколько медленно разрешено автоматическое повышение тихого материала.";
        if (HOLD.equals(key)) return "Пауза после громкого события перед тем, как снова разрешить нормализатору поднимать громкость.";
        if (RECOVERY.equals(key)) return "Интервал постепенного открытия ручного safety-envelope после того, как пользователь снова поднял громкость.";
        if (AUTO_MUTE.equals(key)) return "Разрешает алгоритму самому ставить Media в 0. По умолчанию выключено для защиты от случайного исчезновения звука.";
        return "Параметр Sound Ceiling.";
    }

    private HelpText() {}
}
