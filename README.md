# Sound Ceiling for Android - v0.4.0

No-root Android 10+ adaptive media-volume controller focused on fast loud-sound protection, conservative normalization and explicit fail-safe behavior.

## Что нового в v0.4.0

- Быстрый raw-peak/transient safety path не ждёт медленное RMS-сглаживание.
- `SafetyGuard` является последним clamp перед любым обычным Media write.
- Ручное снижение даже на один шаг считается пользовательским override и блокирует неожиданное автоматическое повышение.
- `Safety Lock` может жёстко ограничить максимальную Media-громкость.
- `Quiet now` снижает громкость через тот же защищённый service path и включает manual safety pause.
- LUFS-like нормализация отделена от системного Media index.
- Встроенные профили: Balanced, Safe, Stable loudness, Movie dynamic и Speech; пользовательские профили можно сохранять отдельно.
- Simple mode содержит комфорт, minimum/maximum Media, силу нормализации и Quiet now.
- Advanced mode содержит Media range, peak/transient controls, normalization parameters, SpeedPreset, SPL, профили, inline help и live meters.
- Diagnostics показывает GREEN/YELLOW/RED состояния и автоматически сохраняет контекст обнаруженных аномалий.
- Логи ограничены общим бюджетом 16 MiB; старые сессии удаляются первыми. PCM-аудио в лог не записывается.
- Отдельные экраны EQ, Calibration, Diagnostics и Appearance.
- Theme mode: System / Dark / Light.
- Экспериментальный global EQ является optional и не входит в критический safety path.
- При отказе от точного MediaProjection capture SoundCeiling может перейти в Safe fallback: Visualizer output-mix meter, если он доступен, либо Media-only guard без автоматического повышения.

## Как SoundCeiling понимает громкость

- `Media index` - системная ступень Android/Samsung, например 3/15.
- `dBFS / Raw Peak` - цифровой уровень исходного playback PCM там, где Android разрешает его получить.
- `LUFS-like` - внутренняя оценка средней воспринимаемой громкости для нормализации. Она пока не заявляется как сертифицированный broadcast LUFS meter.
- `dB SPL` - оценка физической громкости только после калибровки конкретного output device внешним SPL-метром.

Эти величины намеренно показываются отдельно и не считаются взаимозаменяемыми.

## Аудио backends

- Playback Capture - точный PCM-анализ через Android AudioPlaybackCapture/MediaProjection.
- System Mix - экспериментальный Visualizer(0) Peak/RMS для быстрого fallback safety.
- Media Guard - системный volume ceiling, когда достоверного анализа сигнала нет. В неопределённом состоянии этот fallback не должен автоматически повышать громкость.
- DSP - capability-gated experimental path. SoundCeiling не утверждает, что глобальный DSP активен, пока relevant output path не подтверждён.

## Ограничения Android

`AudioPlaybackCapture` может анализировать только playback, который Android и воспроизводящее приложение разрешают захватывать. Защищённый/DRM-звук или приложение с запрещённым capture может быть недоступно для PCM-анализа.

Получение копии PCM через AudioPlaybackCapture само по себе не даёт обычному APK возможности вставить собственный sample-level brickwall limiter обратно в тракт стороннего приложения. Поэтому v0.4 сохраняет системный Media safety path как гарантированный fallback, а optional EQ/DSP не может отключить основной ограничитель.

## Калибровка

Для абсолютного dB SPL нужен внешний SPL-метр. Если его нет, используйте dBFS/LUFS-like режим и выберите в мастере вариант без SPL-калибровки вместо ввода выдуманного значения.

## Сборка и проверки

Требуются JDK 17 и Android SDK 35.

```bash
./scripts/run-pure-tests.sh
bash ./scripts/check-v04-storage-contract.sh
./scripts/check-source-invariants.sh
bash ./scripts/check-ui-contract.sh
bash ./scripts/check-v04-ui-contract.sh
bash ./scripts/check-v04-package-contract.sh
./gradlew :app:assembleDebug
```

GitHub Actions публикует `SoundCeiling-v0.4.0-debug-apk` с APK и SHA-256.
