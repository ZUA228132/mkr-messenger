# 🎯 MKR Кастомизация - Сводка изменений

## ✅ Выполненные изменения

### 1. Брендинг и идентификация

| Файл | Изменения |
|------|-----------|
| [Info.plist](deltachat-ios/Info.plist) | `CFBundleDisplayName`: Delta Chat → **MKR** |
| [Info.plist](deltachat-ios/Info.plist) | URL схемы: `chat.delta://` → **`mkr://`** |
| [Info.plist](deltachat-ios/Info.plist) | Все упоминания "Delta Chat" → **"MKR"** |
| [Localizable.strings](deltachat-ios/*.lproj/Localizable.strings) | `app_name` = "MKR" (40+ языков) |

### 2. Bundle Identifiers

| Target | Старый ID | Новый ID |
|--------|-----------|----------|
| Main App | `chat.delta` | **`com.mkr.su`** |
| Widget | `chat.delta.DcWidget` | **`com.mkr.su.DcWidget`** |
| Share Extension | `chat.delta.DcShare` | **`com.mkr.su.DcShare`** |
| Notification Service | `chat.delta.DcNotificationService` | **`com.mkr.su.DcNotificationService`** |

### 3. App Groups

| Использование | Старый | Новый |
|---------------|--------|-------|
| Shared Container | `group.chat.delta.ios` | **`group.com.mkr.su`** |

### 4. Цветовая схема MKR

Файл: [DcColors.swift](DcCore/DcCore/Helper/DcColors.swift)

```swift
// Основной брендовый цвет
public static let primary = UIColor(hexString: "0066CC")

// Цвет сообщений
public static let messagePrimaryColor = UIColor.themeColor(
    light: UIColor.rgb(red: 200, green: 220, blue: 255),
    dark: UIColor.init(hexString: "0A2A5C")
)

// Цвет бейджей
public static let unreadBadge = UIColor(hexString: "0066CC")
```

### 5. Конфигурация MKR серверов

Новый файл: [MKRConfig.swift](deltachat-ios/MKRConfig.swift)

```swift
// IMAP/SMTP для @mkr.su
public static let imapServer = "imap.mkr.su"
public static let smtpServer = "smtp.mkr.su"

// WebRTC для звонков
public static let webrtcSignalingServer = "wss://signaling.mkr.su"
public static let webrtcTurnServer = "turn.mkr.su"
```

### 6. GitHub Actions CI/CD

Новые файлы:
- [.github/workflows/build-mkr.yml](.github/workflows/build-mkr.yml) - Workflow для сборки
- [.github/export-options.plist](.github/export-options.plist) - Export options
- [.github/BUILD_SETUP.md](.github/BUILD_SETUP.md) - Инструкция по настройке

### 7. Entitlements

| Файл | Изменения |
|------|-----------|
| [deltachat-ios.entitlements](deltachat-ios/deltachat-ios.entitlements) | App Group → `group.com.mkr.su` |
| [DcShare.entitlements](DcShare/DcShare.entitlements) | App Group → `group.com.mkr.su` |
| [DcNotificationService.entitlements](DcNotificationService/DcNotificationService.entitlements) | App Group → `group.com.mkr.su` |

### 8. Документация

| Файл | Описание |
|------|----------|
| [README_MKR.md](README_MKR.md) | Основное README для MKR проекта |
| [MKR_SERVER_SETUP.md](MKR_SERVER_SETUP.md) | Настройка серверной инфраструктуры |
| [.github/BUILD_SETUP.md](.github/BUILD_SETUP.md) | Настройка GitHub Actions |
| [MKR_CHANGES.md](MKR_CHANGES.md) | Этот файл - сводка изменений |

---

## 📋 Что осталось сделать вручную

### 1. Визуальные ассеты

Замените следующие изображения на MKR бренд:

| Расположение | Что заменить |
|--------------|--------------|
| `Assets.xcassets/AppIcon.appiconset/` | Иконка приложения (все размеры) |
| `Assets.xcassets/dc_logo.imageset/` | Логотип на launch screen |
| `Assets.xcassets/background_light.imageset/` | Фон (светлый) |
| `Assets.xcassets/background_dark.imageset/` | Фон (тёмный) |

**Размеры иконок:**
- iPhone: 60x60, 76x76, 83.5x83.5 (@2x, @3x)
- iPad: 76x76, 83.5x83.5 (@2x)
- App Store: 1024x1024

### 2. Apple Developer Portal

Создайте следующие App IDs:

1. **com.mkr.su** - Main App
   - Push Notifications ✅
   - App Groups ✅
   - Associated Domains ✅

2. **com.mkr.su.DcWidget** - Widget Extension
   - App Groups ✅

3. **com.mkr.su.DcShare** - Share Extension
   - App Groups ✅

4. **com.mkr.su.DcNotificationService** - Notification Service
   - App Groups ✅

### 3. Associated Domains

Добавьте на сервер `mkr.su` файл:
```
/.well-known/apple-app-site-association
```

### 4. GitHub Secrets

Добавьте в GitHub Repository Secrets:

```
APPLE_TEAM_ID=XXXXXXXXX
APPLE_CERTIFICATE_P12=<base64>
APPLE_CERTIFICATE_PASSWORD=********
APPLE_PROVISIONING_PROFILE=<base64>
```

---

## 🚀 Следующие шаги

1. **Создайте репозиторий** на GitHub для MKR
2. **Добавьте Secrets** в GitHub
3. **Замените иконки** на MKR дизайн
4. **Настройте серверы** согласно [MKR_SERVER_SETUP.md](MKR_SERVER_SETUP.md)
5. **Сделайте первый commit** и запустите GitHub Actions

---

## 📁 Структура проекта после кастомизации

```
deltachat-ios-main/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                      # Оригинальный CI
│   │   └── build-mkr.yml              # ✨ MKR Build Workflow
│   ├── export-options.plist            # ✨ Export Options
│   └── BUILD_SETUP.md                  # ✨ Build Setup Guide
├── deltachat-ios/
│   ├── MKRConfig.swift                 # ✨ MKR Config
│   ├── Assets.xcassets/                # Нужно заменить иконки
│   ├── deltachat-ios.entitlements      # ✨ Обновлён
│   ├── Info.plist                      # ✨ Обновлён
│   └── *.lproj/
│       └── Localizable.strings         # ✨ app_name = "MKR"
├── DcCore/DcCore/Helper/
│   └── DcColors.swift                  # ✨ MKR Colors
├── DcShare/
│   ├── DcShare.entitlements            # ✨ Обновлён
│   └── Info.plist                      # ✨ Обновлён
├── DcNotificationService/
│   ├── DcNotificationService.entitlements  # ✨ Обновлён
│   └── Info.plist                      # ✨ Обновлён
├── README_MKR.md                       # ✨ MKR README
├── MKR_SERVER_SETUP.md                 # ✨ Server Setup Guide
└── MKR_CHANGES.md                      # ✨ Этот файл
```

---

## 🔧 Проверка перед первым билдом

```bash
# 1. Проверить Bundle ID
grep -r "com.mkr.su" deltachat-ios.xcodeproj/project.pbxproj

# 2. Проверить App Groups
grep -r "group.com.mkr.su" *.entitlements

# 3. Проверить локализацию
grep "app_name.*=.*\"MKR\"" deltachat-ios/*.lproj/Localizable.strings

# 4. Проверить цвета
grep "0066CC" DcCore/DcCore/Helper/DcColors.swift
```

---

## 📞 Поддержка

При возникновении проблем:
1. Проверьте [BUILD_SETUP.md](.github/BUILD_SETUP.md)
2. Проверьте [MKR_SERVER_SETUP.md](MKR_SERVER_SETUP.md)
3. Обратитесь к: dev@mkr.su

---

**Проект MKR готов к сборке через GitHub Actions! 🎉**
