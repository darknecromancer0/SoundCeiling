package dev.soundceiling.app;

final class HelpText {
    static final String MIN_MEDIA="MIN_MEDIA", MAX_MEDIA="MAX_MEDIA", SAFETY_LOCK="SAFETY_LOCK",
            SOURCE_PEAK="SOURCE_PEAK", TRANSIENT_WARNING="TRANSIENT_WARNING", TRANSIENT_EMERGENCY="TRANSIENT_EMERGENCY",
            TARGET_LOUDNESS="TARGET_LOUDNESS", NORMALIZATION_STRENGTH="NORMALIZATION_STRENGTH", TOLERANCE="TOLERANCE",
            DOWN_ATTACK="DOWN_ATTACK", UP_RELEASE="UP_RELEASE", HOLD="HOLD", RECOVERY="RECOVERY", AUTO_MUTE="AUTO_MUTE",
            PCM="PCM", DSP="DSP", RMS="RMS", DBFS="DBFS", DBSPL="DBSPL", LUFS="LUFS", LUFS_LIKE="LUFS_LIKE", CALIBRATION="CALIBRATION";

    static String forKey(String key) {
        if (MIN_MEDIA.equals(key)) return "Minimum — только нижняя граница системной Media-громкости. Нормализатор может работать выше неё. Если пользователь сам опускает ползунок до Minimum, автоматическое повышение временно останавливается.";
        if (MAX_MEDIA.equals(key)) return "Maximum — обычная верхняя граница Media. Ни нормализация, ни Quiet Now не должны поднимать звук выше неё.";
        if (SAFETY_LOCK.equals(key)) return "Safety Lock — последний жёсткий потолок перед записью громкости в Android. Он ограничивает все автоматические повышения независимо от остальных настроек.";
        if (SOURCE_PEAK.equals(key)) return "Peak — самый высокий короткий всплеск сигнала. Порог задаётся в dBFS. SoundCeiling сравнивает не только исходный peak, а ожидаемый peak после текущего положения системной громкости.";
        if (TRANSIENT_WARNING.equals(key)) return "Transient — внезапный скачок уровня относительно недавнего звука. Warning реагирует на заметный скачок, но не должен срабатывать непрерывно на уже установившейся громкости.";
        if (TRANSIENT_EMERGENCY.equals(key)) return "Emergency transient — очень резкий новый скачок. Это аварийная реакция, а не постоянный режим limiter. После события база адаптируется и защита перевооружается.";
        if (TARGET_LOUDNESS.equals(key)) return "Target — к какой средней воспринимаемой громкости стремится нормализатор. Выше Target = SoundCeiling охотнее поднимает тихий материал; ниже = держит его тише. Это не положение ползунка Samsung.";
        if (NORMALIZATION_STRENGTH.equals(key)) return "Normalization strength — насколько сильно алгоритм пытается приблизить звук к Target. 0% выключает выравнивание, 100% стремится к цели наиболее активно, но Ceiling и safety всё равно имеют приоритет.";
        if (TOLERANCE.equals(key)) return "Tolerance — зона около Target, где громкость не трогается. Большая зона уменьшает мелкие движения ползунка, маленькая делает нормализацию точнее и активнее.";
        if (DOWN_ATTACK.equals(key)) return "Downward attack — задержка обычного снижения слишком громкого материала. Аварийный peak/transient путь работает отдельно и быстрее.";
        if (UP_RELEASE.equals(key)) return "Upward release — насколько осторожно возвращается громкость вверх, когда материал стал тихим. Больше значение = медленнее и спокойнее повышение.";
        if (HOLD.equals(key)) return "Hold after loud — пауза после громкого события перед следующим автоматическим повышением. Она нужна, чтобы контроллер не дёргал системный ползунок вверх-вниз.";
        if (RECOVERY.equals(key)) return "Manual recovery — как быстро открывается разрешённый диапазон после того, как пользователь сам уменьшил громкость. Автоматические limiter-снижения этот manual envelope больше не меняют.";
        if (AUTO_MUTE.equals(key)) return "Разрешает автоматике ставить Media в абсолютный 0. Выключено по умолчанию, чтобы приложение не могло неожиданно полностью убрать звук.";
        if (PCM.equals(key)) return "PCM — реальные цифровые аудиосэмплы воспроизводимого звука. Когда PCM ACTIVE, SoundCeiling может измерять сам сигнал и выполнять нормализацию. Для Global-профиля точное имя приложения не обязательно.";
        if (DSP.equals(key)) return "DSP — обработка самого аудиосигнала, например Equalizer. Это отдельный модуль: если DSP недоступен на устройстве, limiter/нормализатор системной Media-громкости продолжают работать.";
        if (RMS.equals(key)) return "RMS — средняя физическая мощность короткого участка сигнала. Полезна для быстрых измерений, но хуже отражает воспринимаемую человеком громкость, чем loudness-оценка.";
        if (DBFS.equals(key)) return "dBFS — цифровая шкала внутри аудиофайла/PCM. 0 dBFS — максимально возможный цифровой уровень, поэтому обычные значения отрицательные. Это не громкость в комнате и не dB SPL.";
        if (DBSPL.equals(key)) return "dB SPL — приблизительная реальная звуковая громкость у уха/в комнате. Её нельзя корректно получить только из PCM: нужна калибровка конкретного телефона, наушников или другого выхода.";
        if (LUFS.equals(key)) return "LUFS — стандартная метрика воспринимаемой громкости, учитывающая частоты и время. Полноценный LUFS требует стандартизированной обработки и окон измерения.";
        if (LUFS_LIKE.equals(key)) return "LUFS-like в SoundCeiling — приближённая loudness-оценка для realtime-контроллера, а не сертифицированное измерение настоящего LUFS. Она нужна для сравнения тихих и громких участков и работы Target.";
        if (CALIBRATION.equals(key)) return "Калибровка нужна только если вы хотите использовать режим dB SPL. Обычные Peak/RMS/LUFS-like, Ceiling и нормализация работают без неё. Если SPL не нужен, калибровку можно вообще не трогать.";
        return "Параметр SoundCeiling.";
    }

    private HelpText() {}
}
