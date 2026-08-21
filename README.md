# Sound Ceiling for Android - v0.5.1

No-root Android 10+ adaptive audio safety controller focused on fast loud-sound protection, real PCM-driven normalization where available, explicit fallback rules and fail-safe behavior.

## Что исправлено в v0.5.1

Этот patch release основан на полных Samsung SM-A528B field logs v0.5.0 и исправляет причины, из-за которых приложение фактически превращалось в downward-lock системного Media slider.

- `TransientGuard` больше не залипает после первого громкого края: baseline адаптируется, событие re-arm'ится и sustained playback не считается новым emergency каждые несколько миллисекунд.
- Peak ceiling оценивает предполагаемый выход после текущего системного Media gain, а не только raw PCM source peak.
- Healthy `PCM ACTIVE + PCM_MIXED` может использовать Global normalization даже когда package/source identity неизвестна. Exact identity по-прежнему нужна для identity-dependent per-app правил.
- Initial Media state, включая Minimum, является наблюдением, а не ручной командой запретить auto-raise.
- Автоматические limiter/transient reductions больше не сжимают manual user envelope.
- `Quiet Now` строго downward-only: он никогда не повышает Media, даже если сохранённый quiet index выше текущего.
- Target/Normalization strength снова участвуют в реальном PCM → loudness → desired gain → requested → applied pipeline.
- Minimum/Maximum/Safety Lock/Quiet index нормализуются как единый непротиворечивый диапазон.
- `Apps & System Sounds` получает список пакетов в background thread, поэтому открытие экрана не должно блокировать UI на десятки секунд.
- Simple и Apps используют общий `UiTheme`; System/Dark/Light больше не должны расходиться между экранами.
- Advanced сохраняет прежнюю структуру, но основные normalization/status controls подняты выше; добавлены понятные пояснения PCM, RMS, Peak, DSP, dBFS, dB SPL, LUFS и LUFS-like. LUFS-like прямо обозначен как приблизительная realtime-оценка, а не стандартизированный LUFS meter.
- EQ стал независимым persistent-модулем: сохранённые настройки применяются на старте приложения, а вкладка EQ лишь редактирует их. Есть selectable linked bands и `Link Strength`.
- Логи по умолчанию сохраняются в `Downloads/SoundCeilingLogs`. `Open logs` открывает внутренний список логических сессий, а не folder picker. Большие rotated parts показываются как одна session; доступны открыть, поделиться и удалить. Пользователь может выбрать другую папку или вернуть Default location.
- Лог control loop стал структурированнее: реальные изменения Media содержат current/requested/guarded/applied и safety bounds; repeated frame decisions остаются в ring buffer вместо постоянного disk spam.

## Hybrid Engine v0.5

- Hybrid Engine разделяет source identity, PCM/metering, volume control, DSP transport и policy confidence.
- Per-app правила: `Global`, `On`, `Off`, `Custom`. Samsung/system apps по умолчанию `Off`, пока пользователь явно не разрешит управление.
- Non-Media streams (`Calls`, `Alarm`, `Ringtone`, `Notifications`, `System`, `DTMF`, `Accessibility`, `Assistant`) требуют отдельного opt-in и работают только как downward ceiling.
- Device Profiles 2.0 хранят настройки для конкретного audio route: Media ceiling, fallback ceiling, stream policies и app/device overrides.
- PCM evidence может фильтроваться по UID, но durable identity остаётся package name. UID обновляется из `PackageManager` и не считается постоянным идентификатором приложения.
- Multi-source policy выбирает более безопасное ограничение и блокирует auto-raise при доказанном конфликте нескольких источников или `Off`-источника.
- Simple/Advanced/Diagnostics показывают независимые source/metering/control/DSP capability states и downgrade reason.

## Как SoundCeiling понимает громкость

- `Media index` - системная ступень Android/Samsung, например 3/15.
- `dBFS / Raw Peak` - цифровой уровень playback PCM там, где Android разрешает его получить.
- `RMS` - усреднённая энергия цифрового сигнала за небольшой интервал.
- `LUFS-like` - внутренняя приблизительная realtime-оценка воспринимаемой громкости для нормализации. Это не сертифицированный broadcast LUFS meter.
- `dB SPL` - оценка физической акустической громкости только после калибровки конкретного output device внешним SPL-метром.

Эти величины намеренно показываются отдельно и не считаются взаимозаменяемыми.

## Safety

- `SafetyGuard` остаётся последним clamp перед обычным Media write.
- Ручное снижение даже на один шаг считается пользовательским override и предотвращает неожиданное auto-raise.
- `Safety Lock` жёстко ограничивает максимальную Media-громкость.
- При недостоверном PCM/fallback SoundCeiling может удерживать или снижать безопасный потолок, но не должен самостоятельно повышать громкость.
- Microphone input не является анализируемым трактом SoundCeiling; PCM capture использует playback APIs.
- Экспериментальный EQ/DSP не входит в критический safety path и не может отключить основной ограничитель.

## Калибровка

Калибровка нужна только для приблизительного `dB SPL`. Для обычной работы SoundCeiling она не обязательна. Без внешнего SPL-метра оставьте SPL mode выключенным: dBFS, LUFS-like normalization, peak/transient protection и системные ceilings продолжают работать.

## Логи

Default location: `Downloads/SoundCeilingLogs`.

Одна работа SoundCeiling = одна логическая session. Если сессия превышает технический размер части, она может храниться в нескольких физических `.log` files, но внутренний Log Sessions screen группирует их вместе. Общий retention budget остаётся 16 MiB, старые полные sessions удаляются первыми. PCM/audio payload в лог не записывается и автоматически никуда не отправляется.

## Сборка и проверки

Требуются JDK 17 и Android SDK 35.

```bash
./scripts/run-pure-tests.sh
bash ./scripts/check-v04-storage-contract.sh
bash ./scripts/check-v05-storage-contract.sh
bash ./scripts/check-v05-app-contract.sh
./scripts/check-source-invariants.sh
bash ./scripts/check-v05-pcm-contract.sh
bash ./scripts/check-v05-microphone-invariant.sh
bash ./scripts/check-v05-control-adapters.sh
bash ./scripts/check-ui-contract.sh
bash ./scripts/check-v04-ui-contract.sh
bash ./scripts/check-v05-ui-contract.sh
bash ./scripts/check-v04-package-contract.sh
bash ./scripts/check-v05-release-contract.sh
bash ./scripts/check-v051-core-stability-contract.sh
./gradlew :app:assembleDebug
```

GitHub Actions публикует `SoundCeiling-v0.5.1-debug-apk` с `app-debug.apk` и `app-debug.apk.sha256`.
