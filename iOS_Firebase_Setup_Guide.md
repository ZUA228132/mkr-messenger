# iOS Firebase и CallKit Руководство по настройке

## ✅ Что уже сделано

### 1. Firebase Configuration
- ✅ Добавлены Firebase зависимости в `pubspec.yaml`:
  - `firebase_core: ^3.3.0`
  - `firebase_messaging: ^15.0.4`
- ✅ Создан [ios/Runner/GoogleService-Info.plist](ios/Runner/GoogleService-Info.plist) с вашей конфигурацией Firebase
- ✅ Обновлен [ios/Podfile](ios/Podfile) с Firebase pods
- ✅ Файлы добавлены в Xcode проект через `project.pbxproj`
- ✅ Выполнен `pod install` - все зависимости установлены

### 2. PushNotificationService (Dart)
Полностью переработан [lib/data/services/push_notification_service.dart](lib/data/services/push_notification_service.dart):
- ✅ Поддержка FCM токенов для Android
- ✅ Поддержка APNs токенов для iOS
- ✅ Обработка входящих push уведомлений
- ✅ Специальная обработка для звонков (`type: "call"`)
- ✅ Background message handling
- ✅ Автоматическое обновление токенов

### 3. iOS Native Code
- ✅ Обновлен [ios/Runner/AppDelegate.swift](ios/Runner/AppDelegate.swift):
  - Firebase initialization
  - Firebase Messaging delegate
  - UNUserNotificationCenter delegate
  - Method channel для коммуникации с Flutter
  - APNs токен обработка
  - CallKit интеграция для звонков

- ✅ Создан [ios/Runner/VoipPushManager.swift](ios/Runner/VoipPushManager.swift):
  - PushKit интеграция для VOIP push
  - CallKit интеграция для нативного интерфейса звонков
  - Входящие звонки через нативный iOS UI
  - Исходящие звонки
  - Audio session конфигурация для звонков

### 4. CI/CD Configuration
- ✅ Обновлен [`.github/workflows/ios-build.yml`](.github/workflows/ios-build.yml) для установки CocoaPods

### 5. Info.plist Configuration
- ✅ Все необходимые разрешения уже настроены в [ios/Runner/Info.plist](ios/Runner/Info.plist):
  - `UIBackgroundModes`: `remote-notification`, `voip`
  - Разрешения для камеры, микрофона, Face ID

## 🔧 Что нужно доделать

### 1. Настроить APNs в Firebase Console

#### В Firebase Console:
1. Перейдите в [Firebase Console](https://console.firebase.google.com/)
2. Выберите проект `patriot-app-mess`
3. Project Settings → Cloud Messaging → iOS app configuration

#### Настройка APNs (Apple Push Notification service):
1. Сгенерируйте APNs Key в [Apple Developer Portal](https://developer.apple.com/account/resources/authkeys/list)
   - Key Type: **APNs Authentication Key**
   - Сохраните Key ID (10 символов)
2. Скачайте ключ (только один раз!) - файл `.p8`
3. В Firebase Console:
   - Project Settings → Cloud Messaging
   - Вкладка "iOS app configuration"
   - Upload APNs authentication key
   - Введите:
     - APNs Key ID (из шага 1)
     - Team ID (из Apple Developer Portal)
     - Upload `.p8` файл

### 2. Backend Integration

Ваш backend должен отправлять push уведомления в следующем формате:

#### Для входящего звонка (через FCM):
```json
{
  "message": {
    "token": "FCM_TOKEN_DEVICE",
    "notification": {
      "title": "Входящий звонок",
      "body": "Имя звонящего"
    },
    "data": {
      "type": "call",
      "caller_id": "123",
      "caller_name": "Иван Иванов",
      "call_type": "video",
      "room_id": "livekit-room-id"
    },
    "apns": {
      "payload": {
        "aps": {
          "alert": {
            "title": "Входящий звонок",
            "body": "Иван Иванов"
          },
          "sound": "default",
          "badge": 1
        }
      }
    }
  }
}
```

#### Для VOIP push (специальный тип для звонков - приоритетный):
```json
{
  "message": {
    "token": "VOAPNS_TOKEN_DEVICE",
    "data": {
      "type": "incoming_call",
      "caller_id": "123",
      "caller_name": "Иван Иванов",
      "is_video": true,
      "room_id": "livekit-room-id"
    },
    "apns": {
      "headers": {
        "apns-push-type": "voip",
        "apns-priority": "10"
      },
      "payload": {
        "aps": {
          "alert": "Входящий звонок",
          "sound": "default",
          "badge": 1
        }
      }
    }
  }
}
```

**Важно:** APNs токен и FCM токен - это **разные** токены! Для VOIP push нужен отдельный APNs токен.

### 3. Flutter Integration

Добавьте обработчики method channel в вашем Flutter коде:

```dart
import 'package:flutter/services.dart';

static const _pushChannel = MethodChannel('com.mkr.messenger/push_notification');

@override
void initState() {
  super.initState();
  _setupPushHandlers();
}

void _setupPushHandlers() {
  _pushChannel.setMethodCallHandler((call) async {
    switch (call.method) {
      case 'onIncomingCall':
        final callerId = call.arguments['callerId'] as String;
        final callerName = call.arguments['callerName'] as String;
        final isVideo = call.arguments['isVideo'] as bool;
        final roomId = call.arguments['roomId'] as String?;

        // Показать UI входящего звонка или перейти на экран звонка
        _handleIncomingCall(callerId, callerName, isVideo, roomId);
        break;

      case 'onCallAnswered':
        // Пользователь ответил на звонок через CallKit UI
        _startCall();
        break;

      case 'onCallEnded':
        // Звонок завершен
        _endCall();
        break;

      case 'onFCMTokenReceived':
        final token = call.arguments['token'] as String;
        // Обновить токен на backend
        await _updateTokenOnBackend(token);
        break;

      case 'onAPNsTokenReceived':
        final token = call.arguments['token'] as String;
        // APNs токен для VOIP
        await _updateApnsTokenOnBackend(token);
        break;

      case 'onVoipTokenReceived':
        final token = call.arguments['token'] as String;
        // VOIP токен для push уведомлений звонков
        await _updateVoipTokenOnBackend(token);
        break;
    }
  });
}
```

### 4. Инициализация PushNotificationService

В `main.dart`:

```dart
import 'package:mkr_flutter/data/services/push_notification_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Инициализируем push notifications
  final pushService = PushNotificationService();
  await pushService.initialize();

  // Получаем токен для отправки на backend
  final fcmToken = pushService.fcmToken;
  if (fcmToken != null) {
    // Отправить токен на ваш backend
    // await apiService.updatePushToken(fcmToken);
    print('FCM Token: $fcmToken');
  }

  runApp(MyApp());
}
```

## 🧪 Тестирование

### Локальное тестирование (без Xcode):

Так как у вас нет Xcode локально, тестируйте через GitHub Actions:

1. Сделайте commit и push изменений
2. GitHub Actions автоматически соберет iOS билд
3. Скачайте IPA из Actions artifacts
4. Установите на устройство через:
   - Sideloadly (для установки без сертификата)
   - AltStore
   - Или напрямую на устройство с сертификатом разработчика

### Тестирование FCM на iOS:
1. Установите приложение на iOS устройство
2. Запустите приложение
3. Разрешите уведомления когда спросят
4. В логах Flutter вы должны увидеть:
   ```
   PushNotificationService: Firebase initialized successfully
   PushNotificationService initialized successfully
   FCM Token: XXX...
   ```
5. Скопируйте FCM токен из логов
6. Отправьте тестовое уведомление через Firebase Console или ваш backend

### Тестирование входящего звонка:
1. Получите APNs токен из логов (должен появиться после первого запуска)
2. Отправьте VOIP push уведомление на этот токен
3. Должен появиться нативный интерфейс входящего звонка iOS (CallKit)
4. При ответе - приложение откроется на экране звонка

## ⚠️ Возможные проблемы

### Проблема: "Firebase is not configured"
**Решение:**
- Убедитесь, что `GoogleService-Info.plist` существует в `ios/Runner/`
- Проверьте, что Bundle ID в plist совпадает с настройками проекта

### Проблема: "No APNs token"
**Решение:**
- Убедитесь, что вы запускаете на реальном устройстве (не симулятор)
- Проверьте, что Push Notifications включены в Apple Developer Portal
- Проверьте, что provisioning profile включает Push Notifications capability

### Проблема: "VOIP push not received"
**Решение:**
- Убедитесь, что используете правильный VOIP токен (отличается от FCM токена)
- Проверьте, что backend отправляет на правильный endpoint
- APNs для VOIP использует другой порт: `https://api.development.push.apple.com:443`

### Проблема: "CocoaPods not installed in CI"
**Решение:**
- Убедитесь, что `.github/workflows/ios-build.yml` содержит шаг установки CocoaPods (уже добавлено)

## 📁 Структура файлов

```
ios/
├── Runner/
│   ├── AppDelegate.swift              # Firebase + Messaging + CallKit методы
│   ├── VoipPushManager.swift          # VOIP push + CallKit менеджер
│   ├── GoogleService-Info.plist       # Firebase конфигурация
│   └── Info.plist                     # Разрешения приложения
├── Runner.xcodeproj/
│   └── project.pbxproj                # Xcode проект (с нашими файлами)
├── Podfile                            # CocoaPods зависимости
└── Podfile.lock                       # Заблокированные версии pods

lib/
└── data/
    └── services/
        └── push_notification_service.dart  # FCM/APNs сервис

.github/
└── workflows/
    └── ios-build.yml                  # CI/CD для iOS билда
```

## 📚 Полезные ссылки

- [Firebase Cloud Messaging для Flutter](https://firebase.flutter.dev/docs/messaging/overview)
- [Apple Push Notification Service](https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server)
- [PushKit Framework](https://developer.apple.com/documentation/pushkit)
- [CallKit Framework](https://developer.apple.com/documentation/callkit)

## 🎯 Следующие шаги

1. ✅ Все файлы добавлены в проект
2. ⏳ Настроить APNs в Firebase Console (вручную)
3. ⏳ Интегрировать отправку FCM токена на backend при старте приложения
4. ⏳ Добавить обработчики method channel в Flutter коде
5. ⏳ Реализовать backend отправку push уведомлений для звонков
6. ⏳ Тестировать на реальном iOS устройстве

## 🚀 CI/CD

GitHub Actions автоматически:
- Установит CocoaPods
- Установит Firebase pods
- Соберет iOS приложение
- Создаст IPA файл

Просто сделайте push в ветку `main` или `develop`, и билд начнется автоматически.
