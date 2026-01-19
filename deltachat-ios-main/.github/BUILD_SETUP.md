# MKR iOS App - Enterprise Signature Setup

Это руководство по настройке автоматической сборки MKR iOS приложения с использованием **Enterprise сертификата** (企业签名) от Esign.

## Overview

GitHub Actions workflow автоматически собирает MKR iOS приложение при каждом push в репозиторий.

## Особенности Enterprise сертификата (Esign)

- ✅ Сертификат действует **1 год**
- ✅ Установка на **любое количество устройств**
- ✅ **Нет ограничения 7 дней** - приложение работает постоянно
- ✅ Можно устанавливать напрямую без iTunes/Sideloadly
- ✅ Подходит для дистрибуции внутри компании или публичной раздачи
- ⚠️ Нельзя публиковать в App Store
- ⚠️ Push уведомления требуют дополнительно настроек

---

## Настройка GitHub Variables

Перейдите в ваш репозиторий на GitHub: **Settings** → **Secrets and variables** → **Actions** → **Variables**

### Обязательные Variables:

| Variable | Описание | Пример |
|----------|----------|--------|
| `ESIGN_CERT_P12` | Сертификат .p12 в Base64 | (см. ниже как получить) |
| `ESIGN_CERT_PASSWORD` | Пароль сертификата | Ваш пароль |

---

## Экспорт сертификата из Esign

### Способ 1: Через Esign Desktop/App

1. Откройте приложение Esign
2. Найдите ваш Enterprise сертификат
3. Экспортируйте его как `.p12` файл
4. Запомните/задайте пароль экспорта

### Способ 2: Из файла сертификата

Если у вас уже есть файл `.p12`:

```bash
# Конвертировать в Base64 для GitHub
base64 -i your_certificate.p12 | pbcopy
```

### Добавление в GitHub

1. Скопируйте Base64 строку сертификата
2. GitHub → Settings → Secrets and variables → Actions → Variables
3. New repository variable:
   - Name: `ESIGN_CERT_P12`
   - Value: (вставить Base64)
4. Repeat для пароля:
   - Name: `ESIGN_CERT_PASSWORD`
   - Value: (ваш пароль)

---

## GitHub Actions Workflow

### Job 1: build-mkr-simulator (Автоматический)

Запускается при каждом push и PR.
- Собирает для iOS Simulator
- Запускает тесты
- Не требует сертификата

### Job 2: build-mkr-enterprise (Автоматический)

Запускается автоматически, если указан `ESIGN_CERT_P12`.
- Собирает для реальных устройств
- Подписывает Enterprise сертификатом
- Создаёт `.ipa` файл
- Загружает как артефакт (хранится 90 дней)

---

## Установка приложения на устройства

### Вариант 1: Прямая установка (самый простой)

1. Скачайте `.ipa` из GitHub Actions Artifacts
2. Отправьте файл на устройство (Telegram, Email, AirDrop)
3. На iPhone: откройте файл → **Установить**
4. **Настройки → Основные → VPN и управление устройствами** → Разрешить

> Enterprise сертификат позволяет установку напрямую без компьютера!

### Вариант 2: Через Sideloadly (с компьютера)

1. Скачайте [Sideloadly](https://sideloadly.io/)
2. Подключите iPhone к компьютеру
3. Откройте `.ipa` файл в Sideloadly
4. Нажмите "Start"

### Вариант 3: Через AltStore

1. Установите [AltStore](https://altstore.io/) на iPhone
2. Откройте `.ipa` через Safari
3. Нажмите "Скачать в AltStore"

### Вариант 4: Публичная ссылка (для распространения)

Загрузите `.ipa` на свой сервер и создайте QR-код или ссылку вида:
```
itms-services://?action=download-manifest&url=https://your-server.com/manifest.plist
```

Пример manifest.plist:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>items</key>
    <array>
        <dict>
            <key>assets</key>
            <array>
                <dict>
                    <key>kind</key>
                    <string>software-package</string>
                    <key>url</key>
                    <string>https://your-server.com/MKR.ipa</string>
                </dict>
            </array>
            <key>metadata</key>
            <dict>
                <key>bundle-identifier</key>
                <string>com.mkr.su</string>
                <key>bundle-version</key>
                <string>1.0.0</string>
                <key>kind</key>
                <string>software</string>
                <key>title</key>
                <string>MKR Messenger</string>
            </dict>
        </dict>
    </array>
</dict>
</plist>
```

---

## Возобновление сертификата

Enterprise сертификат от Esign действует **1 год**. После истечения:

### Вариант 1: Продление через Esign

1. Свяжитесь с вашим провайдером Esign
2. Получите новый сертификат
3. Обновите `ESIGN_CERT_P12` в GitHub Variables
4. Пересоберите приложение

### Вариант 2: Покупка нового сертификата

Если старый истёк:
- Купите новый Enterprise сертификат
- Обновите Variables
- Пересоберите

### Важно: Пользователям нужно переустановить

После обновления сертификата пользователям нужно:
1. Удалить старую версию приложения
2. Установить новую версию с новым сертификатом

---

## Ограничения Enterprise сертификата

| Функция | Доступно |
|---------|----------|
| Установка на любые устройства | ✅ |
| Срок действия | ✅ 1 год |
| Количество устройств | ✅ Безлимит |
| Прямая установка | ✅ |
| Публичная дистрибуция | ✅ |
| App Store | ❌ |
| TestFlight | ❌ |
| Push уведомления | ⚠️ Требует настройки |
| In-App Purchases | ❌ |

---

## Настройка Push уведомлений (опционально)

Enterprise сертификат МОЖЕТ поддерживать push уведомления, но требует:

1. **Создать App ID в Apple Developer Portal** (нужна платная учётная запись)
2. **Сгенерировать Push Notification Certificate**
3. **Настроить сервер push уведомлений**

Без платного Apple Developer Program ($99/год) push уведомления не будут работать.

---

## Локальная сборка

### Сборка с Enterprise сертификатом

```bash
# 1. Импортировать сертификат
security import certificate.p12 -k ~/Library/Keychains/login.keychain-db -P "PASSWORD"

# 2. Собрать
xcodebuild -workspace deltachat-ios.xcworkspace \
  -scheme deltachat-ios \
  -destination 'generic/platform=iOS' \
  -configuration Release \
  PRODUCT_BUNDLE_IDENTIFIER=com.mkr.su \
  CODE_SIGN_IDENTITY="iPhone Distribution" \
  CODE_SIGNING_REQUIRED=YES \
  archive -archivePath build/MKR.xcarchive

# 3. Экспорт IPA
xcodebuild -exportArchive \
  -archivePath build/MKR.xcarchive \
  -exportPath build/export \
  -exportOptionsPlist .github/export-options-enterprise.plist
```

---

## Troubleshooting

### Ошибка: "Certificate expired"

**Решение**: Получите новый сертификат от Esign и обновите `ESIGN_CERT_P12`.

### Ошибка: "No provisioning profile"

**Решение**: Enterprise сертификат не требует provisioning profiles. Проверьте `CODE_SIGNING_REQUIRED=YES`.

### Приложение не запускается

**Проверьте**:
```bash
codesign -dvvv MKR.ipa
```

Должно показать: `Authority=iPhone Distribution`

### Ошибка: "Profile doesn't match"

**Решение**: Убедитесь, что Bundle ID в проекте совпадает с тем, на который выдан сертификат.

---

## Полезные команды

### Проверка сертификата
```bash
security find-identity -v -p codesigning
```

### Проверка подписи IPA
```bash
codesign -dvvv MKR.ipa
```

### Проверка даты истечения сертификата
```bash
security find-certificate -c "iPhone Distribution" -p | openssl x509 -noout -dates
```

---

## Сравнение типов сертификатов

| Характеристика | Free (7 дней) | Enterprise (1 год) | Apple Developer ($99/год) |
|----------------|---------------|---------------------|---------------------------|
| Срок действия | 7 дней | 1 год | 1 год |
| Устройства | Только свои | Любые | Любые |
| App Store | ❌ | ❌ | ✅ |
| Push | ❌ | ⚠️* | ✅ |
| Стоимость | Бесплатно | ~$200-500/год | $99/год |

*Push требует дополнительно платный Apple Developer

---

## Дополнительные ресурсы

- [Esign](https://esign.yeah) - Покупка Enterprise сертификатов
- [Sideloadly](https://sideloadly.io/) - Установка IPA с компьютера
- [AltStore](https://altstore.io/) - Установка IPA на iPhone
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

---

## Поддержка

Для вопросов: dev@mkr.su

---

**Преимущества Enterprise сертификата**: Нет ограничения 7 дней, можно устанавливать на любые устройства, подходит для публичной дистрибуции!
