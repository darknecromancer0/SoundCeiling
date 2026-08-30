package dev.soundceiling.app;

final class HelpText {
    static final String MIN_MEDIA="MIN_MEDIA", MAX_MEDIA="MAX_MEDIA", SAFETY_LOCK="SAFETY_LOCK", QUIET_NOW="QUIET_NOW",
            QUIET_LEVEL="QUIET_LEVEL", MAX_DOWN_STEPS="MAX_DOWN_STEPS", CEILING_BASIS="CEILING_BASIS",
            SOURCE_PEAK="SOURCE_PEAK", TRANSIENT_WARNING="TRANSIENT_WARNING", TRANSIENT_EMERGENCY="TRANSIENT_EMERGENCY",
            TARGET_LOUDNESS="TARGET_LOUDNESS", NORMALIZATION_STRENGTH="NORMALIZATION_STRENGTH", TOLERANCE="TOLERANCE",
            DOWN_ATTACK="DOWN_ATTACK", UP_RELEASE="UP_RELEASE", HOLD="HOLD", RECOVERY="RECOVERY", AUTO_MUTE="AUTO_MUTE",
            PCM="PCM", DSP="DSP", RMS="RMS", DBFS="DBFS", DBSPL="DBSPL", LUFS="LUFS", LUFS_LIKE="LUFS_LIKE",
            CALIBRATION="CALIBRATION", GLOBAL_DSP="GLOBAL_DSP", DEFAULT_LINKED_LOCK="DEFAULT_LINKED_LOCK",
            OUTPUT_CEILINGS="OUTPUT_CEILINGS", RAW_PEAK="RAW_PEAK", PROJECTED_PEAK="PROJECTED_PEAK",
            CONTROL_LOUDNESS="CONTROL_LOUDNESS", CAPTURE_REFERENCE="CAPTURE_REFERENCE", VERIFIED_SOURCE="VERIFIED_SOURCE",
            VERIFIED_POLICY_DSP="VERIFIED_POLICY_DSP", VERIFIED_GLOBAL_DSP="VERIFIED_GLOBAL_DSP",
            WHOLE_OUTPUT_DSP="WHOLE_OUTPUT_DSP", MEDIA_STEP_PERCENT="MEDIA_STEP_PERCENT", DSP_FALLBACK="DSP_FALLBACK";

    private static final String[] VISIBLE_DIAGNOSTIC_TERMS = {
            PCM, LUFS_LIKE, DBFS, RMS, DSP, DSP_FALLBACK, DBSPL, RAW_PEAK, PROJECTED_PEAK,
            CONTROL_LOUDNESS, CAPTURE_REFERENCE, VERIFIED_SOURCE, VERIFIED_POLICY_DSP,
            VERIFIED_GLOBAL_DSP, WHOLE_OUTPUT_DSP, MEDIA_STEP_PERCENT, DEFAULT_LINKED_LOCK, GLOBAL_DSP
    };

    static String[] visibleDiagnosticTermIds() { return VISIBLE_DIAGNOSTIC_TERMS.clone(); }

    static String forKey(String key) {
        if (GLOBAL_DSP.equals(key) || WHOLE_OUTPUT_DSP.equals(key)) return tri(
                "PCM Shadow v0.9 рассчитывает gain/leveling на отдельной копии разрешённого targeted PCM только в памяти.",
                "Влияет только на feasibility-метрики и логи; Samsung Media и слышимый аудиовыход не изменяются.",
                "Режим всегда SHADOW_ONLY: public playback capture сохраняет исходный звук, поэтому audibleOutputAllowed=false и AudioTrack не создаётся.");
        if (DEFAULT_LINKED_LOCK.equals(key)) return tri(
                "Default Linked Lock связывает Minimum Output Ceiling и Maximum Output Ceiling в одну точку.",
                "При ON оба sliders видимы, но заблокированы в Simple и Advanced; пользовательское движение Samsung Media slider сдвигает связанную точку, app-owned Media write её не двигает.",
                "Точно, когда изменение Media классифицировано VolumeWriteTracker как USER, а не APP_ACK/STALE/MISMATCH.");
        if (OUTPUT_CEILINGS.equals(key)) return tri(
                "Output ceilings задают нижнюю и верхнюю цель цифрового уровня.",
                "Verified DSP использует их как непрерывный dB range; fallback показывает ближайшую реальную Media step.",
                "DSP dB точны при verified transport; fallback-процент точен только как фактическая дискретная Media step.");
        if (PCM.equals(key)) return tri("PCM — цифровые аудиосэмплы воспроизводимого сигнала.", "Даёт измерение уровня и может подтвердить UID-targeted source.", "Точно для source identity только после стабильного non-silent targeted PCM.");
        if (LUFS_LIKE.equals(key) || LUFS.equals(key)) return tri("LUFS-like — приближённая loudness-оценка воспринимаемой громкости; это не сертифицированное измерение настоящего LUFS.", "Используется нормализатором для сравнения тихих и громких участков.", "Надёжнее на устойчивом программном материале; это приблизительная метрика.");
        if (DBFS.equals(key)) return tri("dBFS — цифровая шкала сигнала, где 0 dBFS является цифровым максимумом.", "Используется для peak и цифровых ceilings.", "Точно для измеренного цифрового сигнала, но не означает dB SPL в комнате.");
        if (RMS.equals(key)) return tri("RMS — средняя мощность короткого участка сигнала.", "Помогает быстро оценивать уровень и probe attenuation.", "Точно для текущего measurement window, но не равно воспринимаемой loudness.");
        if (DSP.equals(key)) return tri("DSP — изменение самого аудиосигнала без обязательного движения системного volume slider.", "Может применять gain, EQ и limiter.", "Считать доступным для управления можно только при verified capability и корректном scope.");
        if (DSP_FALLBACK.equals(key)) return tri("DSP-like/fallback — совместимый путь, когда verified DSP недоступен.", "Использует безопасные Media writes и доступные измерения.", "Точно отражает fallback только когда RuntimeState не помечает Global DSP active.");
        if (DBSPL.equals(key)) return tri("dB SPL — приблизительная акустическая громкость у уха или в комнате.", "Используется только для калиброванного SPL-представления.", "Точно лишь настолько, насколько актуальна калибровка конкретного route.");
        if (RAW_PEAK.equals(key) || SOURCE_PEAK.equals(key)) return tri("Raw peak — краткий максимальный уровень измеренного сигнала.", "Запускает peak safety и помогает вычислять headroom.", "Точен для текущего capture source; при degraded capture помечается как приблизительный.");
        if (PROJECTED_PEAK.equals(key)) return tri("Projected peak — ожидаемый peak после текущего Media/DSP gain.", "Не даёт нормализатору поднять сигнал выше hard peak ceiling.", "Точен при корректной route curve и известном applied DSP gain.");
        if (CONTROL_LOUDNESS.equals(key)) return tri("Control loudness — значение громкости, которое видит normalizer decision loop.", "По нему рассчитывается обычная positive/negative correction.", "Наиболее надёжно при стабильном PCM и корректном capture reference.");
        if (CAPTURE_REFERENCE.equals(key)) return tri("Capture pre/post-volume показывает, измерен сигнал до или после системной громкости.", "Определяет математическую интерпретацию gain correction.", "Точно только после подтверждённого capture-reference probe; UNKNOWN блокирует опасное повышение.");
        if (VERIFIED_SOURCE.equals(key)) return tri("Verified source — приложение-кандидат, подтверждённое targeted PCM, а не только MediaSession.", "Разрешает selective per-app policy там, где она технически применима.", "Точно только после стабильного UID-targeted non-silent PCM.");
        if (VERIFIED_POLICY_DSP.equals(key)) return tri("Verified policy-scoped DSP — DSP handle с доверенным provenance для конкретной policy scope.", "Позволяет selective DSP без обработки чужих endpoints.", "Точно только для APP_OWNED или DOCUMENTED_PROVIDER handle с совпадающей policy.");
        if (VERIFIED_GLOBAL_DSP.equals(key)) return tri(
                "Исторический global-mix DSP использовал session-zero DynamicsProcessing; после Samsung field-регрессии v0.8 он помещён в полный карантин до любого Android constructor.",
                "В v0.9 не является active actuator и не меняет Samsung Media или слышимый аудиовыход; нормализация рассчитывается только отдельным PCM Shadow.",
                "Карантин fail-closed: никакой constructor, attach, probe или gain Session DSP не разрешён.");
        if (MEDIA_STEP_PERCENT.equals(key)) return tri("Media step — реальная дискретная ступень Android/Samsung; percent — её отображение на 0–100%.", "Fallback может выбирать только существующие steps, а не произвольный плавный процент.", "Точно, когда UI показывает фактический index и snapped percent текущего route.");
        if (MIN_MEDIA.equals(key)) return "Minimum Media — нижняя граница fallback Media actuator. Пользовательское ручное снижение ниже неё не должно автоматически возвращаться вверх.";
        if (MAX_MEDIA.equals(key)) return "Maximum Media — жёсткая верхняя граница fallback/safety Media actuator.";
        if (SAFETY_LOCK.equals(key)) return "Safety Lock — дополнительный hard Media ceiling. Он остаётся рабочим независимо от Global DSP.";
        if (QUIET_NOW.equals(key)) return "Quiet Now — одноразовое снижение Media до настроенного уровня. Доступно только в Расширенном режиме.";
        if (QUIET_LEVEL.equals(key)) return "Quiet Now level — предел Media для Quiet Now; команда никогда не повышает громкость.";
        if (CEILING_BASIS.equals(key)) return "Шкала управления меняет представление output ceilings: Media %, Digital dB или калиброванный dB SPL. Без действующей калибровки dB SPL используется Safe fallback. Смена шкалы не создаёт права на повышение и не создаёт отдельный controller.";
        if (TARGET_LOUDNESS.equals(key)) return "Target — цель вычисления нормализации в PCM Shadow. В v0.9 он не разрешает слышимый gain и не создаёт нового права повышать Samsung Media; fallback-восстановление возвращает только ранее сделанное SoundCeiling снижение. Hard safety остаётся отдельным путём.";
        if (NORMALIZATION_STRENGTH.equals(key)) return "Normalization strength — доля рассчитанной обычной коррекции. Hard safety остаётся отдельным приоритетным путём.";
        if (TOLERANCE.equals(key)) return "Tolerance — зона вокруг target, где normalizer удерживает текущий уровень.";
        if (DOWN_ATTACK.equals(key)) return "Downward attack — скорость обычного снижения; hard peak/transient safety работает отдельно.";
        if (MAX_DOWN_STEPS.equals(key)) return "Max down steps — максимум шагов fallback Media, на которые обычный контроллер может снизить громкость за один цикл решения.";
        if (UP_RELEASE.equals(key)) return "Upward release — минимальная пауза между шагами ограниченного восстановления ранее сделанного SoundCeiling снижения.";
        if (HOLD.equals(key)) return "Hold after loud — защитная пауза после громкого участка; пока она активна, восстановление не начинается.";
        if (RECOVERY.equals(key)) return "Recovery — ограниченное восстановление собственного снижения SoundCeiling. Оно не отменяет ручное снижение пользователя и остаётся в пределах output ceilings и hard safety.";
        if (AUTO_MUTE.equals(key)) return "Разрешить автоматический mute — позволяет hard safety поставить Media в 0 при разрешённой аварийной ситуации.";
        if (TRANSIENT_WARNING.equals(key)) return "Transient warning — порог внезапного скачка относительно недавнего уровня.";
        if (TRANSIENT_EMERGENCY.equals(key)) return "Transient emergency — аварийный порог резкого нового скачка.";
        if (CALIBRATION.equals(key)) return "Калибровка нужна только для приблизительного dB SPL; digital normalization и hard safety от неё не зависят.";
        return "Параметр SoundCeiling.";
    }

    private static String tri(String what, String affects, String accurate) {
        return "Что это: " + what + "\nВлияет: " + affects + "\nТочно: " + accurate;
    }

    private HelpText() {}
}
