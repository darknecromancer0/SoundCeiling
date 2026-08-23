# Sound Ceiling for Android - v0.7.0

No-root Android 10+ adaptive audio safety controller. v0.7.0 introduces the **Adaptive Envelope**: SoundCeiling may reduce Media when playback is too loud and may later restore only attenuation that SoundCeiling itself created. A deliberate manual Media reduction immediately becomes the new automation ceiling, while Maximum and Safety Lock remain hard upper bounds.

## Что изменилось в v0.7.0

- **Bounded recovery вместо глобального automatic-UP.** Recovery существует только как возврат доказанного собственного снижения SoundCeiling. Тихий материал сам по себе не даёт права повышать Media.
- **Ручное управление главнее автоматики.** Manual down сужает User ceiling, manual up расширяет его в пределах Safety ceiling. App-write acknowledgement не выдаётся за действие пользователя.
- **Transient Guard 2.0.** Старт playback и возврат после тишины получают короткий warmup, delta-emergency требует подтверждения, а обычный transient-down ограничен небольшим числом Samsung steps за решение. Абсолютный projected-peak escape hatch остаётся быстрым.
- **Три шкалы одного engine.** `Media %`, `Digital dB` и `Calibrated dB SPL` меняют представление настроек, но не создают отдельные конкурирующие контроллеры.
- **Диагностика envelope.** Advanced показывает User ceiling, Safety ceiling, recoverable reference и auto attenuation, чтобы по одному скриншоту было видно, почему Media двигался или не двигался.
- **Logs index-first.** Каждый успешно созданный log part сразу сохраняется в durable index. Sessions объединяет index и MediaStore/SAF discovery, поэтому пустой discovery больше не делает только что созданную сессию невидимой.
- **EQ и meters.** EQ показывает live spectrum, correction response и силу коррекции; light theme использует semantic meter colors.
- **UI cleanup.** Long slider labels получают нормальную высоту и font padding, Minimum показывается в процентах и реальной Media ступени, recovery timing снова доступен в Advanced.
- **Source/DSP truthfulness.** Source permission/candidate/PCM confidence и verified DSP transport не смешиваются с фактом наличия PCM analysis или Android Equalizer object.

## Safety model

Главный контракт v0.7:

`automatic target <= User ceiling <= Safety ceiling`

- Automatic DOWN может идти ниже User ceiling, если материал слишком громкий.
- Automatic RECOVER_UP может вернуть только ранее созданный SoundCeiling attenuation debt и останавливается на текущем User ceiling / Safety ceiling / recoverable reference.
- Manual DOWN никогда не исправляется автоматикой вверх.
- `Minimum` остаётся нижней границей автоматического снижения, а не приказом поднять вручную установленный тихий Media.
- `Quiet Now` остаётся строго non-raising.
- Peak/transient protection и hard cap clamp используют отдельный безопасный downward path.
- На stock Android/Samsung приложение не может физически изменить диапазон системного volume slider до обработки нажатия системой, поэтому запрещённая ступень может мелькнуть визуально, но должна быть быстро clamped back.
- Microphone/call audio не используется как production analysis source.
- Наличие EQ не считается доказательством verified global/source DSP transport.

## Почему Android показывает разрешение «захвата экрана»

Для precise playback PCM Android использует `AudioPlaybackCapture`, а разрешение выдаётся через `MediaProjection`. SoundCeiling использует его для анализа PCM воспроизводимого аудио и не записывает видео экрана. Если precise capture недоступен или пользователь его не разрешил, статус должен честно показать fallback вместо выдуманного exact PCM.

## Шкалы громкости

- `Media %` — удобное представление системной Android/Samsung шкалы.
- `Digital dB` — Target LUFS-like, Tolerance и Projected Peak dBFS. Это цифровые оценки, не физическая громкость в комнате.
- `Calibrated dB SPL` — приблизительная акустическая оценка только для текущего калиброванного output route. Без калибровки режим не включается молча и используется Safe fallback.

## Калибровка

Калибровка нужна только для приблизительного `dB SPL`. Обычные Media ceilings, PCM/dBFS/LUFS-like normalization и transient/peak protection продолжают работать без неё. Calibration tone volume-neutral и отменяется при изменении Media или output route; protection после calibration state machine восстанавливается явно.

## Логи

Default location: `Downloads/SoundCeilingLogs`.

Одна работа SoundCeiling = одна логическая session. Rotated parts одной session группируются вместе и при Share/Open объединяются в один временный `.log`. Durable index записывает URI сразу после успешного создания part; MediaStore/SAF discovery затем только дополняет и обновляет эти данные. Indexed entry удаляется не потому, что discovery вернул пусто, а после фактической невозможности прочитать URI или успешного удаления пользователем.

## Samsung field-test checklist для v0.7.0

1. **Auto down 7 -> 5:** после собственного автоматического снижения и достаточно тихого материала SoundCeiling должен плавно recover toward 7, не выше User/Safety ceiling.
2. **User manual down 7 -> 4:** после ручного снижения автоматика не должна выбрать значение выше 4, пока пользователь сам снова не поднимет Media.
3. **Silence / song onset:** начало песни или возврат из тишины не должны обрушать Media `4 -> 1` только из-за transient delta.
4. **Hard Maximum:** на Samsung запрещённая ступень может кратко мелькнуть в системном UI, но SoundCeiling должен promptly clamp её обратно к разрешённому ceiling.
5. **Yandex Music:** UI должен показать состояние source permission, candidate package/UID и затем EXACT только если targeted PCM действительно подтвердил UID; иначе остаётся truthful MIXED/UNKNOWN.
6. **Logs:** экран Sessions должен показывать только что созданную сессию даже если MediaStore folder discovery временно пуст.
7. **Light theme / EQ:** meter labels, spectrum и EQ response остаются читаемыми в светлой теме и не клипуются.

## Исторические регрессии

v0.7 сохраняет проверенные fixes из `v0.5.1` и v0.6: transient re-arm, projected peak calculation, healthy PCM_MIXED global control, async Apps loading, persistent linked-band EQ, logical one-file log sharing, volume-neutral calibration и non-raising Quiet Now. Historical gates не фиксируют текущий release artifact или номер версии.

## Сборка и проверки

Требуются JDK 17 и Android SDK 35.

```bash
./scripts/run-pure-tests.sh
./scripts/check-source-invariants.sh
bash ./scripts/check-v07-adaptive-contract.sh
bash ./scripts/check-v07-ui-contract.sh
bash ./scripts/check-v07-release-contract.sh
./gradlew --no-daemon --stacktrace :app:assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions также запускает historical regression gates v0.4/v0.5/v0.5.1/v0.6 и публикует `SoundCeiling-v0.7.0-debug-apk` с `app-debug.apk` и `app-debug.apk.sha256`.
