# Sound Ceiling for Android — v0.3.0

No-root Android 10+ adaptive media-volume normalizer with an audible-floor safety guard, diagnostics and dual-mode UI.

## Режимы v0.3.0

- Простой режим открывается по умолчанию: комфорт и максимальная Media-громкость.
- Расширенный режим содержит dBFS/SPL, скорость реакции, auto-mute и анализ частот.
- Анализ частот только показывает захваченный звук и не является системным EQ.
- Логи находятся в `Download/SoundCeiling/Logs` и не содержат аудио.
- Проверка динамика и SPL-калибровка — разные действия.

## Защита от Media 0

В dBFS-режиме управляющая кривая детерминирована и не зависит от vendor `getStreamVolumeDb()`. Пока обнаружен сигнал и `Разрешать авто-mute` выключен, сервис не запрашивает mute: минимальный автоматический индекс остаётся слышимым. В логах записываются raw/requested/applied индексы для диагностики внешнего сброса громкости.

## Ограничения Android

`AudioPlaybackCapture` может анализировать только разрешённый приложением звук. Защищённый/DRM-звук может запрещать захват. Обычное APK не является системным EQ и не может гарантировать посэмпловый brickwall limiter финального микса.

## Сборка

Требуются JDK 17 и Android SDK 35.

```bash
./scripts/run-pure-tests.sh
./scripts/check-source-invariants.sh
./gradlew :app:assembleDebug
```

GitHub Actions публикует `SoundCeiling-v0.3.0-debug-apk` с APK и SHA-256.
