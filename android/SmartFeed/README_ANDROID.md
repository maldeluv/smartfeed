# SmartFeed Android

## Требования

- Android Studio с Android SDK 36 и JDK 17.
- Android Emulator либо устройство с Android 6.0+.
- Docker Desktop для локального backend.

## Запуск backend

Из каталога `smartfeed`:

```powershell
docker compose up -d
docker compose ps
Invoke-RestMethod http://localhost:8000/health
```

Перед первым запуском дождитесь статуса `healthy` у `smartfeed-backend`.

Для обновления аналитики новыми Android-событиями должен работать Spark Structured
Streaming. Команда запуска и ALS batch для пересчёта рекомендаций описаны в
[`../../README.md`](../../README.md) в разделах **Spark Structured Streaming** и
**Spark MLlib ALS Recommendations**.

## Настройка API URL

Для Android Emulator по умолчанию используется:

```text
http://10.0.2.2:8000/
```

`10.0.2.2` — адрес хост-компьютера из стандартного Android Emulator. Не используйте
`localhost`: внутри эмулятора он указывает на сам эмулятор.

URL можно переопределить Gradle-параметром:

```powershell
.\gradlew.bat assembleDebug -PsmartfeedApiBaseUrl=http://192.168.1.20:8000/
```

Для постоянной настройки добавьте в `gradle.properties`:

```properties
smartfeedApiBaseUrl=http://192.168.1.20:8000/
```

Для физического устройства укажите LAN-адрес компьютера. Устройство и компьютер
должны находиться в одной сети, а порт `8000` должен быть разрешён брандмауэром.

## Сборка и запуск

Откройте `smartfeed/android/SmartFeed` в Android Studio либо выполните:

```powershell
cd smartfeed\android\SmartFeed
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Demo-аккаунты

| Роль | Email | Пароль |
|---|---|---|
| Пользователь | `demo@smartfeed.local` | `demo12345` |
| Администратор | `admin@smartfeed.local` | `admin12345` |

Android-клиент использует пользовательские endpoints; для основного сценария входите
под `demo@smartfeed.local`.

## Проверка сценария

1. Войдите и дождитесь загрузки ленты.
2. Откройте статью, поставьте лайк и сохраните её.
3. Проверьте разделы «Сохраненные», «Категории» и «Рекомендации».
4. Откройте «Аналитику»: события обновляются после синхронизации backend.
5. В профиле отображается количество ожидающих событий; кнопка синхронизации
   запускает WorkManager вручную.
6. Для проверки offline cache сначала загрузите данные онлайн, затем отключите сеть и
   перезапустите приложение. Лента, категории и сохранённые статьи берутся из Room.

## Диагностика

```powershell
adb devices
adb logcat | Select-String "com.smartfeed|FATAL EXCEPTION"
docker compose logs backend --tail 100
```

Если после смены backend или аккаунта осталась старая сессия:

```powershell
adb shell pm clear com.smartfeed
```
