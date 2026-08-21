# Sound Ceiling for Android - v0.6.0

No-root Android 10+ adaptive audio safety controller. v0.6.0 introduces the **One-Way Adaptive Engine**: automatic control may hold or reduce Media volume when audio exceeds the configured safety/loudness target, but it never raises the system Media slider on its own.

## Что изменилось в v0.6.0

- **Никакого automatic-UP.** Ручное снижение Media остаётся решением пользователя. Ни Target, ни Minimum, ни normalization recovery не возвращают ползунок вверх.
- **Target теперь односторонний.** Он задаёт порог, выше которого SoundCeiling может ослабить слишком громкий материал. Более тихий материал не усиливается системным Media slider.
- **Быстрый down-path.** Control loudness и transient/peak safety используют быстрые оценки для реакции на громкие скачки; более медленный LUFS-like meter остаётся отдельным отображаемым измерением.
- **Volume provenance.** Приложение различает ручные изменения, подтверждённые собственные writes и mismatches, чтобы не принимать действия пользователя за сбой или за команду на восстановление громкости.
- **Quiet Now строго downward-only.** Он может только оставить текущую Media ступень или сделать тише.
- **Основное / Расширенные = один engine.** Это два интерфейса к одной и той же логике, а не два конкурирующих режима управления.
- **Калибровочный тон volume-neutral.** ToneController больше не двигает системный Media slider. Если громкость практически нулевая, приложение просит поднять её вручную.
- **Детерминированная калибровка.** Если engine запущен, calibration state machine сначала останавливает его, ждёт подтверждения остановки с timeout и только затем запускает tone.
- **Логи собраны в один UX.** В drawer один пункт `Логи`; Log Sessions умеет открыть папку, выбрать папку, вернуть Default location и поделиться последней сессией.
- **Rotated log parts делятся как один файл.** Части одной logical session объединяются во временный cache-файл и отправляются одним URI через FileProvider.
- **Светлая тема использует semantic palette.** Success/warning/error/neutral карточки получают согласованные surface/text цвета вместо тёмных RGB-констант и белого текста поверх светлого фона.
- **EQ остаётся независимым persistent-модулем.** Настройки и Link Strength сохраняются, application-owned controller восстанавливает EQ при старте, а уход с вкладки EQ не уничтожает effect.
- **Apps & System Sounds остаётся асинхронным.** Package list загружается вне UI thread.

## Почему Android показывает разрешение «захвата экрана»

Для точного playback PCM Android использует `AudioPlaybackCapture`, а его разрешение выдаётся через `MediaProjection`. Поэтому перед новой precise PCM session SoundCeiling сначала объясняет это, а затем Android показывает системное окно, похожее на разрешение записи/трансляции экрана.

SoundCeiling использует полученный доступ для анализа **PCM воспроизводимого аудио**. Приложение не записывает видео экрана. Если пользователь не хочет выдавать MediaProjection или Android/OEM не позволяет precise PCM, можно запустить `Safe fallback`; статус при этом не выдаёт fallback за точный PCM.

## Как SoundCeiling понимает громкость

- `Media index` — системная ступень Android/Samsung, например 3/15.
- `dBFS / Raw Peak` — цифровой уровень playback PCM там, где Android разрешает его получить.
- `RMS` — усреднённая энергия цифрового сигнала за небольшой интервал.
- `LUFS-like` — внутренняя приблизительная realtime-оценка воспринимаемой громкости для нормализации. Это не сертифицированный broadcast LUFS meter.
- `dB SPL` — оценка физической акустической громкости только после калибровки конкретного output device внешним SPL-метром.

Эти величины показываются отдельно и не считаются взаимозаменяемыми.

## Safety model

- `SafetyGuard.clampAutomatic()` — последний automatic clamp перед Media write и не разрешает автоматический результат выше наблюдаемой текущей Media ступени.
- `Safety Lock` остаётся жёстким верхним ограничением.
- Peak/transient protection может быстро снизить Media при опасном projected output.
- Ручное снижение пользователя не превращается в команду вернуть громкость вверх.
- Minimum — нижняя граница для **автоматического снижения**, а не приказ поднять Media, если пользователь уже находится ниже неё.
- При неизвестном source identity или недостоверном PCM engine выбирает HOLD/down-only fallback, а не агрессивное восстановление.
- Microphone input не является анализируемым трактом SoundCeiling; основной precise meter использует playback capture APIs.
- Экспериментальный EQ/DSP не входит в критический safety path и не может отключить основной limiter.

## Калибровка

Калибровка нужна только для приблизительного `dB SPL`. Для обычной работы SoundCeiling она не обязательна. Без внешнего SPL-метра оставьте SPL mode выключенным: dBFS, LUFS-like normalization, peak/transient protection и системные ceilings продолжают работать.

Калибровочный tone не меняет Media volume. При запущенном engine приложение сначала останавливает control loop и ждёт фактической остановки. При timeout или невозможности воспроизвести tone показывается ошибка вместо скрытого изменения громкости.

## Логи

Default location: `Downloads/SoundCeilingLogs`.

Одна работа SoundCeiling = одна логическая session. Если сессия превышает технический размер части, она может храниться в нескольких физических `.log` files, но Log Sessions группирует их вместе. При `Поделиться` все части последовательно объединяются в один временный `.log` и передаются одним URI. Общий retention budget остаётся 16 MiB; старые полные sessions удаляются первыми. PCM/audio payload в лог не записывается и автоматически никуда не отправляется.

## История регрессий v0.5.1

v0.6 сохраняет проверенные field-log fixes из `v0.5.1`: transient re-arm, projected peak calculation, healthy PCM_MIXED handling, async Apps loading, persistent linked-band EQ, logical log sessions и downward-only Quiet Now. Старые CI-gates теперь являются historical regression gates и не фиксируют текущий номер версии или имя release artifact.

## Сборка и проверки

Требуются JDK 17 и Android SDK 35.

```bash
./scripts/run-pure-tests.sh
bash ./scripts/check-v04-storage-contract.sh
bash ./scripts/check-v05-storage-contract.sh
bash ./scripts/check-v05-app-contract.sh
./scripts/check-source-invariants.sh
bash ./scripts/check-v06-one-way-contract.sh
bash ./scripts/check-v05-pcm-contract.sh
bash ./scripts/check-v05-microphone-invariant.sh
bash ./scripts/check-v05-control-adapters.sh
bash ./scripts/check-ui-contract.sh
bash ./scripts/check-v04-ui-contract.sh
bash ./scripts/check-v05-ui-contract.sh
bash ./scripts/check-v04-package-contract.sh
bash ./scripts/check-v05-release-contract.sh
bash ./scripts/check-v051-core-stability-contract.sh
bash ./scripts/check-v06-release-contract.sh
./gradlew --no-daemon :app:assembleDebug
```

GitHub Actions публикует `SoundCeiling-v0.6.0-debug-apk` с `app-debug.apk` и `app-debug.apk.sha256`.
