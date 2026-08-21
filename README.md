# Sound Ceiling for Android - v0.5.0

No-root Android 10+ adaptive audio safety controller focused on fast loud-sound protection, conservative normalization, explicit per-app/system opt-in and fail-safe behavior.

## Что нового в v0.5.0

- Hybrid Engine разделяет пять независимых возможностей: source identity, PCM/metering, volume control, DSP transport и policy confidence.
- Автоматическое повышение запрещено, если источник не определён достаточно уверенно, PCM/capability state не позволяет доверять измерению или одновременно активны конфликтующие источники.
- Per-app правила: `Global`, `On`, `Off`, `Custom`. Samsung/system apps по умолчанию `Off`, пока пользователь явно не разрешит управление.
- Экран «Приложения и системные звуки» содержит поиск и фильтры `Controlled`, `Custom`, `Ignored`, `PCM unavailable`, `System apps`.
- Non-Media streams (`Calls`, `Alarm`, `Ringtone`, `Notifications`, `System`, `DTMF`, `Accessibility`, `Assistant`) по умолчанию не управляются. Каждый поток требует отдельного opt-in и работает только как downward ceiling.
- Device Profiles 2.0 хранят настройки для конкретного audio route: Media ceiling, fallback ceiling, stream policies и app/device overrides.
- PCM evidence может фильтроваться по UID, но durable identity остаётся package name. UID обновляется из `PackageManager` и не считается постоянным идентификатором приложения.
- Multi-source policy выбирает более безопасное ограничение и блокирует авто-повышение при неоднозначности или конфликте `Off`-источника.
- Simple/Advanced/Diagnostics показывают независимые source/metering/control/DSP capability states и downgrade reason.
- Диагностический лог пишет компактные transition events с дедупликацией вместо повторов на каждом audio block.

## Сохранённые safety-механизмы v0.4

- Быстрый raw-peak/transient safety path не ждёт медленное RMS-сглаживание.
- `SafetyGuard` остаётся последним clamp перед обычным Media write.
- Ручное снижение даже на один шаг считается пользовательским override и блокирует неожиданное автоматическое повышение.
- `Safety Lock` жёстко ограничивает максимальную Media-громкость.
- `Quiet now` снижает громкость через защищённый service path и включает manual safety pause.
- LUFS-like нормализация отделена от системного Media index.
- Встроенные профили: Balanced, Safe, Stable loudness, Movie dynamic и Speech; пользовательские профили сохраняются отдельно.
- Diagnostics сохраняет контекст обнаруженных аномалий.
- Логи ограничены общим бюджетом 16 MiB; старые сессии удаляются первыми. PCM/audio payload в лог не записывается.
- Экспериментальный EQ/DSP не входит в критический safety path и не может отключить основной ограничитель.

## Как SoundCeiling понимает громкость

- `Media index` - системная ступень Android/Samsung, например 3/15.
- `dBFS / Raw Peak` - цифровой уровень исходного playback PCM там, где Android разрешает его получить.
- `LUFS-like` - внутренняя оценка средней воспринимаемой громкости для нормализации. Она не заявляется как сертифицированный broadcast LUFS meter.
- `dB SPL` - оценка физической громкости только после калибровки конкретного output device внешним SPL-метром.

Эти величины намеренно показываются отдельно и не считаются взаимозаменяемыми.

## Аудио backends и ограничения Android

- Playback Capture: PCM-анализ через Android AudioPlaybackCapture/MediaProjection, когда воспроизводяющее приложение разрешает capture.
- Targeted PCM evidence: UID-фильтрация используется только как runtime evidence и не доказывает сама по себе, что UID является единственным слышимым источником.
- System Mix: экспериментальный Visualizer output-mix Peak/RMS для fallback safety, когда он доступен.
- Media Guard: системный Media ceiling при отсутствии достоверного анализа. В неопределённом состоянии он не должен автоматически повышать громкость.
- DSP: capability-gated optional path. SoundCeiling не сообщает `DSP active`, пока соответствующий output path не подтверждён.

`AudioPlaybackCapture` не может анализировать защищённый/DRM playback или приложение, запретившее capture. Получение копии PCM также не даёт обычному no-root APK возможности вставить собственный sample-level brickwall limiter обратно в тракт стороннего приложения. Поэтому Standard Engine сохраняет системный Media safety path и fail-closed правила; более глубокий sample-level DSP остаётся отдельной будущей задачей.

## Калибровка

Для абсолютного dB SPL нужен внешний SPL-метр. Если его нет, используйте dBFS/LUFS-like режим и вариант без SPL-калибровки вместо ввода выдуманного значения.

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
./gradlew :app:assembleDebug
```

GitHub Actions публикует `SoundCeiling-v0.5.0-debug-apk` с `app-debug.apk` и `app-debug.apk.sha256`.
