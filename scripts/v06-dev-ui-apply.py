from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0 and new in text:
        print(f"already applied: {path}")
        return
    if count != 1:
        raise SystemExit(f"expected one match in {path}, found {count}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched: {path}")


DRAWER = "app/src/main/java/dev/soundceiling/app/DrawerLayoutController.java"
HELP = "app/src/main/java/dev/soundceiling/app/HelpText.java"
SIMPLE = "app/src/main/java/dev/soundceiling/app/SimpleModeView.java"
ADV = "app/src/main/java/dev/soundceiling/app/AdvancedModeView.java"
APPS = "app/src/main/java/dev/soundceiling/app/AppsSystemView.java"

# Main navigation names the two surfaces, not two independent running modes.
replace_once(DRAWER, 'addNav("Простой режим", AppDestination.SIMPLE);',
             'addNav("Основное", AppDestination.SIMPLE);')
replace_once(DRAWER, 'addNav("Расширенный режим", AppDestination.ADVANCED);',
             'addNav("Расширенные", AppDestination.ADVANCED);')

# Plain-language one-way help. Legacy profile keys remain readable but are no longer active controls.
replace_once(HELP,
    'static final String MIN_MEDIA="MIN_MEDIA", MAX_MEDIA="MAX_MEDIA", SAFETY_LOCK="SAFETY_LOCK",',
    'static final String MIN_MEDIA="MIN_MEDIA", MAX_MEDIA="MAX_MEDIA", SAFETY_LOCK="SAFETY_LOCK", QUIET_NOW="QUIET_NOW",')
replace_once(HELP,
    'if (MIN_MEDIA.equals(key)) return "Minimum — только нижняя граница системной Media-громкости. Нормализатор может работать выше неё. Если пользователь сам опускает ползунок до Minimum, автоматическое повышение временно останавливается.";',
    'if (MIN_MEDIA.equals(key)) return "Minimum — нижняя граница для автоматического снижения. Если пользователь сам ставит Media ниже неё или в 0, SoundCeiling не возвращает ползунок вверх. Обычная нормализация ждёт, пока пользователь сам повысит Media.";')
replace_once(HELP,
    'if (SAFETY_LOCK.equals(key)) return "Safety Lock — последний жёсткий потолок перед записью громкости в Android. Он ограничивает все автоматические повышения независимо от остальных настроек.";',
    'if (SAFETY_LOCK.equals(key)) return "Safety Lock — дополнительный жёсткий потолок перед записью Media. Он может только удержать или снизить громкость и никогда не служит целью для повышения.";\n        if (QUIET_NOW.equals(key)) return "Quiet Now — одноразовая команда сделать текущую Media-громкость не выше настроенного Quiet Now level. Если звук уже тише, кнопка ничего не повышает.";')
replace_once(HELP,
    'if (TARGET_LOUDNESS.equals(key)) return "Target — к какой средней воспринимаемой громкости стремится нормализатор. Выше Target = SoundCeiling охотнее поднимает тихий материал; ниже = держит его тише. Это не положение ползунка Samsung.";',
    'if (TARGET_LOUDNESS.equals(key)) return "Target — верхняя цель воспринимаемой громкости. Если звук выше Target, SoundCeiling может его снизить. Если звук ниже Target, контроллер удерживает Media как есть. Target никогда не повышает Media.";')
replace_once(HELP,
    'if (NORMALIZATION_STRENGTH.equals(key)) return "Normalization strength — насколько сильно алгоритм пытается приблизить звук к Target. 0% выключает выравнивание, 100% стремится к цели наиболее активно, но Ceiling и safety всё равно имеют приоритет.";',
    'if (NORMALIZATION_STRENGTH.equals(key)) return "Normalization strength — какая доля рассчитанного снижения применяется, когда звук выше Target. 0% отключает обычную нормализацию, 100% запрашивает полное безопасное снижение. Тихий материал этот параметр не усиливает.";')
replace_once(HELP,
    'if (UP_RELEASE.equals(key)) return "Upward release — насколько осторожно возвращается громкость вверх, когда материал стал тихим. Больше значение = медленнее и спокойнее повышение.";',
    'if (UP_RELEASE.equals(key)) return "Legacy-параметр старых профилей. В one-way engine 0.6 автоматического движения Media вверх нет, поэтому эта величина не показывается в активных настройках.";')
replace_once(HELP,
    'if (HOLD.equals(key)) return "Hold after loud — пауза после громкого события перед следующим автоматическим повышением. Она нужна, чтобы контроллер не дёргал системный ползунок вверх-вниз.";',
    'if (HOLD.equals(key)) return "Legacy-параметр старых профилей. One-way engine 0.6 не использует его для восстановления Media вверх.";')
replace_once(HELP,
    'if (RECOVERY.equals(key)) return "Manual recovery — как быстро открывается разрешённый диапазон после того, как пользователь сам уменьшил громкость. Автоматические limiter-снижения этот manual envelope больше не меняют.";',
    'if (RECOVERY.equals(key)) return "Legacy-параметр старого manual envelope. В 0.6 ручное снижение меняет dB-пороги, а восстановление этих порогов никогда не двигает Media вверх.";')

# Main: one shared status card, Target/Quiet help, no duplicate status surfaces.
replace_once(SIMPLE,
    'private final TextView comfortLabel, minLabel, maxLabel, normalizeLabel, engineStatus, safetyBadge;',
    'private final TextView comfortLabel, minLabel, maxLabel, normalizeLabel;')
replace_once(SIMPLE, 'TextView title = text("Простой режим", 28, true);',
             'TextView title = text("Основное", 28, true);')
replace_once(SIMPLE,
    'TextView intro = secondary("Target задаёт желаемую громкость, Minimum только нижнюю границу, Maximum верхнюю. Аварийный limiter работает отдельно.", 14);',
    'TextView intro = secondary("Один one-way движок: SoundCeiling удерживает или снижает Media, но никогда не повышает системный ползунок автоматически. Target — верхняя цель, а не положение ползунка.", 14);')
replace_once(SIMPLE,
'''        engineStatus = text("Waiting for audio", 16, true);
        engineStatus.setPadding(0, 0, 0, dp(8));
        root.addView(engineStatus);
        safetyBadge = secondary("", 14);
        safetyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        safetyBadge.setPadding(0, 0, 0, dp(12));
        root.addView(safetyBadge);

        comfortLabel = section(); root.addView(comfortLabel);
''',
'''        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.bottomMargin = dp(14); root.addView(statusCard, statusLp);

        LinearLayout targetRow = new LinearLayout(context);
        targetRow.setOrientation(LinearLayout.HORIZONTAL);
        comfortLabel = section();
        targetRow.addView(comfortLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        targetRow.addView(helpButton(HelpText.TARGET_LOUDNESS), new LinearLayout.LayoutParams(dp(46), dp(42)));
        root.addView(targetRow);
''')
replace_once(SIMPLE,
'''        Button quiet = new Button(context);
        quiet.setAllCaps(false); quiet.setText("Quiet now · только сделать тише"); quiet.setTextSize(16);
        quiet.setOnClickListener(v -> this.listener.onQuietNow());
        LinearLayout.LayoutParams quietLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        quietLp.topMargin = dp(18); root.addView(quiet, quietLp);
''',
'''        LinearLayout quietRow = new LinearLayout(context);
        quietRow.setOrientation(LinearLayout.HORIZONTAL);
        Button quiet = new Button(context);
        quiet.setAllCaps(false); quiet.setText("Quiet Now"); quiet.setTextSize(16);
        quiet.setOnClickListener(v -> this.listener.onQuietNow());
        quietRow.addView(quiet, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams quietHelpLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        quietHelpLp.leftMargin = dp(8); quietRow.addView(helpButton(HelpText.QUIET_NOW), quietHelpLp);
        LinearLayout.LayoutParams quietLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        quietLp.topMargin = dp(18); root.addView(quietRow, quietLp);
''')
replace_once(SIMPLE,
'''        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(18); root.addView(statusCard, statusLp);
''', '')
replace_once(SIMPLE,
'''        startStop.setText(state.running ? "Остановить" : "Запустить");
        engineStatus.setText(StatusText.engine(state));
        String lock = state.safetyLockEnabled
                ? "Safety Lock: ON · до " + state.safetyLockIndex + "/" + state.volumeMax
                : "Safety Lock: OFF · основной потолок активен";
        if (state.manualSafetyPause) lock += "\\nРучная пауза: автоматическое повышение остановлено";
        safetyBadge.setText(lock);
        statusCard.render(state);
''',
'''        startStop.setText(state.running ? "Остановить" : "Запустить");
        statusCard.render(state);
''')
replace_once(SIMPLE,
    'normalizeLabel.setText("Normalization: " + percent + "% · " + word);',
    'normalizeLabel.setText("Normalization: " + percent + "% · " + word + " · только вниз");')
replace_once(SIMPLE,
    '    private TextView section() { return text("", 16, true); }\n',
'''    private TextView section() { return text("", 16, true); }
    private Button helpButton(String key) {
        Button b = new Button(getContext()); b.setAllCaps(false); b.setText("?"); b.setTextSize(16);
        b.setOnClickListener(v -> new android.app.AlertDialog.Builder(getContext())
                .setTitle("Что это значит?").setMessage(HelpText.forKey(key))
                .setPositiveButton("Понятно", null).show());
        return b;
    }
''')

# Advanced: profiles/actions first, only downward-relevant reaction controls remain visible.
replace_once(ADV,
'''            transientWarning, transientEmergency, targetLoudness, tolerance, strength,
            downAttack, upRelease, holdAfterLoud, maxDownSteps, maxUpSteps, recovery,
            targetSpl, splCeiling;''',
'''            transientWarning, transientEmergency, targetLoudness, tolerance, strength,
            downAttack, maxDownSteps, targetSpl, splCeiling;''')
replace_once(ADV, 'TextView title = text("Расширенный режим", 28, true);',
             'TextView title = text("Расширенные", 28, true);')
profile = '''        section("Профили");
        profileInfo = secondary("", 14); root.addView(profileInfo); addPresetButtons();
        LinearLayout profileRow = horizontal(); Button save = button("Сохранить профиль"); Button load = button("Загрузить");
        profileRow.addView(save, weight()); profileRow.addView(load, weight()); root.addView(profileRow);
        Button reset = button("По умолчанию"); root.addView(reset, fullButton());
        save.setOnClickListener(v -> promptSaveProfile()); load.setOnClickListener(v -> promptLoadProfile());
        reset.setOnClickListener(v -> applyBuiltIn("Balanced", BuiltInProfiles.balanced()));

'''
replace_once(ADV, profile, '')
replace_once(ADV,
'''        statusCard = new StatusCardView(context); root.addView(statusCard,
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        section("Главное");
''',
'''        statusCard = new StatusCardView(context); root.addView(statusCard,
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

''' + profile + '''        startStop = button("Запустить"); startStop.setOnClickListener(v -> listener.onStartStop()); root.addView(startStop, fullButton());
        LinearLayout quietRow = horizontal();
        Button quiet = button("Quiet Now"); quiet.setOnClickListener(v -> listener.onQuietNow());
        Button quietHelp = button("?"); quietHelp.setOnClickListener(v -> showHelp(HelpText.QUIET_NOW));
        quietRow.addView(quiet, weight()); quietRow.addView(quietHelp, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(quietRow);

        section("Главное");
''')
replace_once(ADV,
    'root.addView(secondary("Сначала Target и Normalization, затем границы. Minimum не является рабочей громкостью: это только нижний предел.", 13));',
    'root.addView(secondary("Target — верхняя цель: тихий материал ниже неё не усиливается. Здесь настраиваются только удержание и снижение Media.", 13));')
replace_once(ADV,
'''        Button quiet = button("Quiet Now · только понизить"); quiet.setOnClickListener(v -> listener.onQuietNow()); root.addView(quiet, fullButton());
        startStop = button("Запустить"); startStop.setOnClickListener(v -> listener.onStartStop()); root.addView(startStop, fullButton());

''', '')
replace_once(ADV, 'section("Поведение");', 'section("Поведение · только снижение");')
for old in (
'''        upRelease = addSlider("Upward release", HelpText.UP_RELEASE, 100, 3000,
                Prefs.upwardReleaseMs(context), p -> p + " ms", p -> editBehavior(Prefs.UPWARD_RELEASE_MS, p));
''',
'''        holdAfterLoud = addSlider("Hold after loud", HelpText.HOLD, 0, 3000,
                Prefs.holdAfterLoudMs(context), p -> p + " ms", p -> editBehavior(Prefs.HOLD_AFTER_LOUD_MS, p));
''',
'''        maxUpSteps = addSlider("Max up steps", HelpText.UP_RELEASE, 0, 3,
                Prefs.maxUpSteps(context), Integer::toString, p -> editBehavior(Prefs.MAX_UP_STEPS, p));
''',
'''        recovery = addSlider("Manual recovery", HelpText.RECOVERY, 100, 3000,
                (int) Prefs.recoveryIntervalMs(context), p -> p + " ms", p -> edit(Prefs.RECOVERY_INTERVAL_MS, (long) p));
'''):
    replace_once(ADV, old, '')
replace_once(ADV,
'''        strength.setProgress(Math.round(Prefs.normalizationStrength(getContext()) * 100f)); downAttack.setProgress(Prefs.downwardAttackMs(getContext()));
        upRelease.setProgress(Prefs.upwardReleaseMs(getContext())); holdAfterLoud.setProgress(Prefs.holdAfterLoudMs(getContext()));
        maxDownSteps.setProgress(Prefs.maxDownSteps(getContext())); maxUpSteps.setProgress(Prefs.maxUpSteps(getContext())); recovery.setProgress((int) Prefs.recoveryIntervalMs(getContext()));
''',
'''        strength.setProgress(Math.round(Prefs.normalizationStrength(getContext()) * 100f)); downAttack.setProgress(Prefs.downwardAttackMs(getContext()));
        maxDownSteps.setProgress(Prefs.maxDownSteps(getContext()));
''')

# Apps/System: system sounds first; app search sits immediately below the Applications heading.
replace_once(APPS,
'''        addHeader();
        search = new EditText(getContext());
        search.setHint("Поиск приложений");
''',
'''        addHeader();
        addSystemStreams();
        TextView appTitle = text("Приложения", 20, true);
        appTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(appTitle);
        search = new EditText(getContext());
        search.setHint("Поиск приложений");
''')
replace_once(APPS,
'''        addFilters();
        addSystemStreams();
        TextView appTitle = text("Приложения", 20, true);
        appTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(appTitle);
        appsHost = new LinearLayout(getContext());
''',
'''        addFilters();
        appsHost = new LinearLayout(getContext());
''')
