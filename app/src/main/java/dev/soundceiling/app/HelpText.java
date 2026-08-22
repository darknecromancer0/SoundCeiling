package dev.soundceiling.app;

final class HelpText {
    static final String MIN_MEDIA="MIN_MEDIA", MAX_MEDIA="MAX_MEDIA", SAFETY_LOCK="SAFETY_LOCK", QUIET_NOW="QUIET_NOW",
            QUIET_LEVEL="QUIET_LEVEL", MAX_DOWN_STEPS="MAX_DOWN_STEPS",
            SOURCE_PEAK="SOURCE_PEAK", TRANSIENT_WARNING="TRANSIENT_WARNING", TRANSIENT_EMERGENCY="TRANSIENT_EMERGENCY",
            TARGET_LOUDNESS="TARGET_LOUDNESS", NORMALIZATION_STRENGTH="NORMALIZATION_STRENGTH", TOLERANCE="TOLERANCE",
            DOWN_ATTACK="DOWN_ATTACK", UP_RELEASE="UP_RELEASE", HOLD="HOLD", RECOVERY="RECOVERY", AUTO_MUTE="AUTO_MUTE",
            PCM="PCM", DSP="DSP", RMS="RMS", DBFS="DBFS", DBSPL="DBSPL", LUFS="LUFS", LUFS_LIKE="LUFS_LIKE", CALIBRATION="CALIBRATION";

    static String forKey(String key) {
        if (MIN_MEDIA.equals(key)) return "Minimum — нижняя граница для автоматического снижения. Если пользователь сам ставит Media ниже неё или в 0, SoundCeiling не возвращает ползунок вверх. Обычная нормализация ждёт, пока пользователь сам повысит Media.";
        if (MAX_MEDIA.equals(key)) return "Maximum — обычная верхняя граница Media. Ни нормализация, ни Quiet Now не должны поднимать звук выше неё.";
        if (SAFETY_LOCK.equals(key)) return "Safety Lock — дополнительный жёсткий потолок перед записью Media. Он может только удержать или снизить громкость и никогда не служит целью для повышения.";
        if (QUIET_NOW.equals(key)) return "Quiet Now — одноразовая команда сделать текущую Media-громкость не выше настроенного Quiet Now level. Если звук уже тише, кнопка ничего не повышает.";
        if (QUIET_LEVEL.equals(key)) return "Quiet Now level — верхний предел Media для кнопки Quiet Now. Команда может только снизить текущую громкость до этого предела; если Media уже ниже, она не повышает её.";
        if (SOURCE_PEAK.equals(key)) return "Peak — самый высокий короткий всплеск сигнала. Порог задаётся в dBFS. SoundCeiling сравнивает не только исходный peak, а ожидаемый peak после текущего положения системной громкости.";
        if (TRANSIENT_WARNING.equals(key)) return "Transient — внезапный скачок уровня относительно недавнего звука. Warning реагирует на заметный скачок, но не должен срабатывать непрерывно на уже установившейся громкости.";
        if (TRANSIENT_EMERGENCY.equals(key)) return "Emergency transient — очень резкий новый скачок. Это аварийная реакция, а не постоянный режим limiter. После события база адаптируется и защита перевооружается.";
        if (TARGET_LOUDNESS.equals(key)) return "Target — верхняя цель воспринимаемой громкости. Если звук выше Target, SoundCeiling может его снизить. Если звук ниже Target, контроллер удерживает Media как есть. Target никогда не повышает Media.";
        if (NORMALIZATION_STRENGTH.equals(key)) return "Normalization strength — какая доля рассчитанного снижения применяется, когда звук выше Target. 0% отключает обычную нормализацию, 100% запрашивает полное безопасное снижение. Тихий материал этот параметр не усиливает.";
        if (TOLERANCE.equals(key)) return "Tolerance — зона около Target, где громкость не трогается. Большая зона уменьшает мелкие движения ползунка, маленькая делает нормализацию точнее и активнее.";
        if (DOWN_ATTACK.equals(key)) return "Downward attack — задержка обычного снижения слишком громкого материала. Аварийный peak/transient путь работает отдельно и быстрее.";
        if (MAX_DOWN_STEPS.equals(key)) return "Max down steps — максимум шагов системного Media, на которые обычный контроллер может снизить громкость за один цикл решения. Меньше — плавнее, больше — быстрее. Аварийная peak/transient защита работает отдельным путём.";
        if (UP_RELEASE.equals(key)) return "Legacy-параметр старых профилей. В one-way engine 0.6 автоматического движения Media вверх нет, поэтому эта величина не показывается в активных настройках.";
        if (HOLD.equals(key)) return "Legacy-параметр старых профилей. One-way engine 0.6 не использует его для восстановления Media вверх.";
        if (RECOVERY.equals(key)) return "Legacy-параметр старого manual envelope. В 0.6 ручное снижение меняет dB-пороги, а восстановление этих порогов никогда не двигает Media вверх.";
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
