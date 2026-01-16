# Руководство по сборке iOS для MKR Flutter

Это руководство объясняет, как собрать и распространить iOS приложение MKR Messenger **без платного Apple Developer аккаунта ($99/год)**.

## Варианты распространения

| Способ | Стоимость | Ограничения | Подходит для |
|--------|-----------|-------------|--------------|
| **AltStore / Sideloadly** | Бесплатно | Переподпись каждые 7 дней | Личное использование |
| **Esign / GBox** | Бесплатно | Нужен сертификат | Тестирование |
| **TrollStore** | Бесплатно | iOS 14.0-16.6.1 | Постоянная установка |
| **Scarlet** | Бесплатно | Периодические отзывы | Распространение |

---

## Содержание

1. [Сборка IPA без подписи](#1-сборка-ipa-без-подписи)
2. [Установка через AltStore](#2-установка-через-altstore)
3. [Установка через Sideloadly](#3-установка-через-sideloadly)
4. [Установка через Esign](#4-установка-через-esign)
5. [Установка через TrollStore](#5-установка-через-trollstore)
6. [Настройка Codemagic для сборки](#6-настройка-codemagic-для-сборки)
7. [Решение проблем](#7-решение-проблем)

---

## 1. Сборка IPA без подписи

### Локальная сборка (нужен Mac)

```bash
cd mkr_flutter

# Установить зависимости
flutter pub get

# Собрать iOS без подписи
flutter build ios --release --no-codesign

# IPA будет в build/ios/iphoneos/
```

### Сборка через Codemagic (без Mac)

Codemagic может собрать неподписанный IPA:

```yaml
# В codemagic.yaml
scripts:
  - name: Build unsigned IPA
    script: |
      cd mkr_flutter
      flutter build ios --release --no-codesign
      
      # Создать Payload директорию
      mkdir -p Payload
      cp -r build/ios/iphoneos/Runner.app Payload/
      
      # Запаковать в IPA
      zip -r MKR-Messenger-unsigned.ipa Payload
```

---

## 2. Установка через AltStore

**AltStore** - бесплатный способ установки IPA на iPhone с компьютера.

### Требования
- Windows или Mac
- iPhone с iOS 12.2+
- iTunes (Windows) или Finder (Mac)
- Бесплатный Apple ID

### Установка AltStore

1. Скачайте AltStore с [altstore.io](https://altstore.io/)
2. Установите на компьютер
3. Подключите iPhone по USB
4. В AltStore выберите "Install AltStore" → ваш iPhone
5. Введите Apple ID (создайте бесплатный если нет)

### Установка MKR Messenger

1. Скачайте `MKR-Messenger.ipa` на компьютер
2. Откройте AltStore на компьютере
3. Нажмите "Install App" → выберите IPA
4. Приложение установится на iPhone

### ⚠️ Важно
- Переподпись нужна **каждые 7 дней**
- Держите AltStore на компьютере
- Подключайте iPhone к той же Wi-Fi сети для автообновления

---

## 3. Установка через Sideloadly

**Sideloadly** - альтернатива AltStore с большим функционалом.

### Установка

1. Скачайте с [sideloadly.io](https://sideloadly.io/)
2. Установите на Windows/Mac
3. Подключите iPhone по USB

### Установка IPA

1. Откройте Sideloadly
2. Перетащите `MKR-Messenger.ipa` в окно
3. Введите Apple ID
4. Нажмите "Start"
5. На iPhone: Настройки → Основные → Управление устройством → Доверять

### Преимущества Sideloadly
- Можно менять Bundle ID (обход лимита 3 приложений)
- Удаление ограничений на поддерживаемые устройства
- Инъекция tweaks

---

## 4. Установка через Esign

**Esign** - подпись IPA прямо на iPhone с использованием сертификата.

### Получение сертификата

Варианты получения .p12 сертификата:

1. **Бесплатные сертификаты** (часто отзываются):
   - Telegram каналы с сертификатами
   - Сайты типа udidregistrations.com (осторожно!)

2. **Покупка сертификата** (~$10-20):
   - [udid.tech](https://udid.tech)
   - [signulous.com](https://signulous.com)
   - Действует ~1 год

### Установка Esign

1. Скачайте Esign IPA
2. Установите через AltStore/Sideloadly
3. Откройте Esign на iPhone

### Импорт сертификата

1. В Esign: Настройки → Импорт сертификата
2. Загрузите .p12 файл и .mobileprovision
3. Введите пароль сертификата

### Подпись MKR Messenger

1. Скачайте `MKR-Messenger.ipa` на iPhone
2. В Esign: Библиотека → Импорт → выберите IPA
3. Нажмите на IPA → Подписать
4. Выберите сертификат → Подписать
5. Установить

---

## 5. Установка через TrollStore

**TrollStore** - постоянная установка без переподписи (только определённые версии iOS).

### Поддерживаемые версии iOS

| Версия iOS | Поддержка |
|------------|-----------|
| 14.0 - 14.8.1 | ✅ Полная |
| 15.0 - 15.4.1 | ✅ Полная |
| 15.5 - 15.6.1 | ✅ Полная |
| 15.7 - 15.8.2 | ✅ Полная |
| 16.0 - 16.6.1 | ✅ Полная |
| 16.7+ | ❌ Нет |
| 17.0+ | ❌ Нет |

### Установка TrollStore

1. Проверьте версию iOS: Настройки → Основные → Об этом устройстве
2. Следуйте инструкции на [ios.cfw.guide/installing-trollstore](https://ios.cfw.guide/installing-trollstore/)

### Установка MKR Messenger

1. Скачайте `MKR-Messenger.ipa` на iPhone
2. Откройте в TrollStore
3. Нажмите "Install"
4. Готово! Переподпись не нужна

---

## 6. Настройка Codemagic для сборки

### Бесплатная сборка без подписи

Codemagic даёт 500 бесплатных минут/месяц для сборки.

### Конфигурация codemagic.yaml

```yaml
workflows:
  ios-unsigned:
    name: iOS Unsigned Build
    instance_type: mac_mini_m2
    max_build_duration: 60
    
    environment:
      flutter: 3.24.0
      xcode: latest
      cocoapods: default
    
    triggering:
      events:
        - push
      branch_patterns:
        - pattern: main
          include: true
    
    scripts:
      - name: Get Flutter packages
        script: |
          cd mkr_flutter
          flutter pub get
      
      - name: Build unsigned iOS
        script: |
          cd mkr_flutter
          flutter build ios --release --no-codesign
      
      - name: Create IPA
        script: |
          cd mkr_flutter
          mkdir -p Payload
          cp -r build/ios/iphoneos/Runner.app Payload/
          zip -r MKR-Messenger-unsigned.ipa Payload
          mv MKR-Messenger-unsigned.ipa build/
    
    artifacts:
      - mkr_flutter/build/*.ipa
    
    publishing:
      email:
        recipients:
          - your-email@example.com
```

### Настройка Codemagic

1. Зарегистрируйтесь на [codemagic.io](https://codemagic.io/)
2. Подключите GitHub/GitLab репозиторий
3. Выберите "Flutter App"
4. Codemagic найдёт `codemagic.yaml` автоматически
5. Запустите сборку

### Скачивание IPA

После успешной сборки:
1. Перейдите в Builds
2. Нажмите на успешную сборку
3. Скачайте артефакт `MKR-Messenger-unsigned.ipa`

---

## 7. Решение проблем

### "Unable to install" при установке

**Причина**: Сертификат отозван или истёк

**Решение**:
- Получите новый сертификат
- Используйте AltStore (бесплатный Apple ID)

### "App is not available" после 7 дней

**Причина**: Истёк срок подписи бесплатного Apple ID

**Решение**:
- Переподпишите через AltStore/Sideloadly
- Используйте TrollStore (если iOS поддерживается)

### Приложение вылетает при запуске

**Причина**: Неправильная подпись или повреждённый IPA

**Решение**:
1. Удалите приложение
2. Пересоберите IPA
3. Переподпишите заново

### "Your device is not supported"

**Причина**: IPA собран для другой архитектуры

**Решение**: Убедитесь что собираете для arm64:
```bash
flutter build ios --release --no-codesign
```

---

## Чек-лист

### Для личного использования (AltStore)
- [ ] Установлен AltStore на компьютер
- [ ] Установлен AltStore на iPhone
- [ ] Есть бесплатный Apple ID
- [ ] Скачан MKR-Messenger.ipa
- [ ] Приложение установлено
- [ ] Напоминание о переподписи через 7 дней

### Для распространения (Esign)
- [ ] Получен .p12 сертификат
- [ ] Получен .mobileprovision профиль
- [ ] Установлен Esign на iPhone
- [ ] Импортирован сертификат
- [ ] IPA подписан и установлен

### Для постоянной установки (TrollStore)
- [ ] iOS версия 14.0-16.6.1
- [ ] TrollStore установлен
- [ ] IPA установлен через TrollStore

---

## Полезные ссылки

- [AltStore](https://altstore.io/) - бесплатная установка IPA
- [Sideloadly](https://sideloadly.io/) - альтернатива AltStore
- [TrollStore Guide](https://ios.cfw.guide/installing-trollstore/) - постоянная установка
- [Codemagic](https://codemagic.io/) - облачная сборка Flutter

---

## Заметки по безопасности

⚠️ **Важно**:

1. Не используйте основной Apple ID для подписи - создайте отдельный
2. Не скачивайте сертификаты с непроверенных источников
3. Проверяйте IPA на вирусы перед установкой
4. TrollStore безопасен только на поддерживаемых версиях iOS
