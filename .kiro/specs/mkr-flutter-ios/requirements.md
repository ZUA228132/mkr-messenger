# Requirements Document

## Introduction

MKR Messenger Flutter - кроссплатформенная версия защищённого мессенджера MKR для iOS и Android. Проект позволяет собирать iOS приложение без Mac'а через облачные CI/CD сервисы (Codemagic, GitHub Actions). Использует существующий backend и реализует все ключевые функции безопасности оригинального Android приложения.

## Glossary

- **MKR_Flutter**: Кроссплатформенное Flutter приложение для iOS и Android
- **Signal_Protocol**: Криптографический протокол с Perfect Forward Secrecy для E2E шифрования
- **Panic_Button**: Функция экстренного удаления всех данных за 3 секунды
- **Stealth_Mode**: Режим маскировки приложения под калькулятор/заметки/погоду
- **Callsign**: Уникальный позывной пользователя в формате @username
- **E2E**: End-to-End шифрование, при котором только отправитель и получатель могут прочитать сообщения
- **Codemagic**: Облачный CI/CD сервис для сборки Flutter приложений под iOS без Mac

## Requirements

### Requirement 1: Project Setup

**User Story:** As a developer, I want to set up a Flutter project structure, so that I can build MKR Messenger for both iOS and Android from a single codebase.

#### Acceptance Criteria

1. WHEN the project is initialized THEN MKR_Flutter SHALL create a Flutter project with iOS and Android targets
2. WHEN configuring dependencies THEN MKR_Flutter SHALL include packages for cryptography, secure storage, and networking
3. WHEN setting up CI/CD THEN MKR_Flutter SHALL include Codemagic configuration for iOS builds without Mac
4. WHEN the project structure is created THEN MKR_Flutter SHALL follow clean architecture with separation of data, domain, and presentation layers

### Requirement 2: Authentication System

**User Story:** As a user, I want to authenticate securely with callsign and PIN, so that I can access my account with military-style credentials.

#### Acceptance Criteria

1. WHEN a user enters a callsign THEN MKR_Flutter SHALL validate the format as 3-16 alphanumeric characters starting with a letter
2. WHEN a user enters a PIN THEN MKR_Flutter SHALL validate the length as 4-6 digits
3. WHEN authentication succeeds THEN MKR_Flutter SHALL store tokens securely using platform-specific secure storage
4. WHEN a user enables biometric auth THEN MKR_Flutter SHALL support Face ID on iOS and fingerprint on Android
5. IF authentication fails 5 times THEN MKR_Flutter SHALL implement progressive lockout delays

### Requirement 3: End-to-End Encryption

**User Story:** As a user, I want all my messages encrypted with Signal Protocol, so that only the intended recipient can read them.

#### Acceptance Criteria

1. WHEN a new chat session starts THEN MKR_Flutter SHALL perform X25519 key exchange
2. WHEN sending a message THEN MKR_Flutter SHALL encrypt using AES-256-GCM with a unique key per message
3. WHEN receiving a message THEN MKR_Flutter SHALL decrypt and verify message integrity
4. WHEN serializing encryption keys THEN MKR_Flutter SHALL use JSON encoding for key storage
5. WHEN deserializing encryption keys THEN MKR_Flutter SHALL parse JSON and restore key objects

### Requirement 4: Panic Button

**User Story:** As a user, I want to instantly delete all data in an emergency, so that my information cannot be compromised.

#### Acceptance Criteria

1. WHEN a user holds the Panic Button for 3 seconds THEN MKR_Flutter SHALL initiate secure data wipe
2. WHEN secure wipe executes THEN MKR_Flutter SHALL delete all encryption keys from secure storage
3. WHEN secure wipe executes THEN MKR_Flutter SHALL overwrite and delete all local messages and media
4. WHEN secure wipe completes THEN MKR_Flutter SHALL revoke all server tokens via API call
5. WHEN secure wipe completes THEN MKR_Flutter SHALL reset the app to initial state

### Requirement 5: Stealth Mode

**User Story:** As a user, I want to disguise the app as a calculator or notes app, so that the messenger is hidden from casual observers.

#### Acceptance Criteria

1. WHEN Stealth Mode is enabled THEN MKR_Flutter SHALL change the app icon to the selected disguise
2. WHEN the disguised app opens THEN MKR_Flutter SHALL display a functional fake calculator interface
3. WHEN a user enters the secret code in the fake calculator THEN MKR_Flutter SHALL reveal the messenger
4. WHEN Stealth Mode is configured THEN MKR_Flutter SHALL store the secret code securely
5. IF an incorrect code is entered 5 times THEN MKR_Flutter SHALL trigger Panic Button automatically

### Requirement 6: Chat Functionality

**User Story:** As a user, I want to send and receive encrypted messages, so that I can communicate securely.

#### Acceptance Criteria

1. WHEN a user sends a text message THEN MKR_Flutter SHALL encrypt and transmit via WebSocket
2. WHEN a user sends media THEN MKR_Flutter SHALL encrypt the file before upload
3. WHEN messages arrive THEN MKR_Flutter SHALL display them in real-time with delivery status
4. WHEN auto-delete is configured THEN MKR_Flutter SHALL delete messages after the specified interval
5. WHEN a user views a chat THEN MKR_Flutter SHALL mark messages as read and sync status

### Requirement 7: Security Checks

**User Story:** As a user, I want the app to detect compromised devices, so that I am warned about security risks.

#### Acceptance Criteria

1. WHEN the app launches THEN MKR_Flutter SHALL check for jailbreak/root on the device
2. WHEN a debugger is detected THEN MKR_Flutter SHALL warn the user about the security risk
3. WHEN running on an emulator THEN MKR_Flutter SHALL display a warning and limit functionality
4. WHEN security checks complete THEN MKR_Flutter SHALL display a risk level indicator

### Requirement 8: iOS-Specific Features

**User Story:** As an iOS user, I want native iOS experience, so that the app feels like a proper iOS application.

#### Acceptance Criteria

1. WHEN running on iOS THEN MKR_Flutter SHALL use Cupertino widgets for native look
2. WHEN Face ID is available THEN MKR_Flutter SHALL integrate with iOS biometric APIs
3. WHEN building for iOS THEN MKR_Flutter SHALL support iOS 14.0 and above
4. WHEN handling notifications THEN MKR_Flutter SHALL use APNs for push notifications

### Requirement 9: Offline Support

**User Story:** As a user, I want to access my messages offline, so that I can read conversations without internet.

#### Acceptance Criteria

1. WHEN the device is offline THEN MKR_Flutter SHALL display cached messages from local storage
2. WHEN composing a message offline THEN MKR_Flutter SHALL queue the message for sending when online
3. WHEN connection is restored THEN MKR_Flutter SHALL sync queued messages and fetch new ones
4. WHEN storing messages locally THEN MKR_Flutter SHALL encrypt the local database

### Requirement 10: CI/CD for iOS Builds

**User Story:** As a developer, I want to build iOS apps without a Mac, so that I can distribute to iOS users using cloud services.

#### Acceptance Criteria

1. WHEN code is pushed to main branch THEN MKR_Flutter SHALL trigger Codemagic build pipeline
2. WHEN building iOS THEN MKR_Flutter SHALL sign the app with provided certificates
3. WHEN build succeeds THEN MKR_Flutter SHALL generate IPA file for distribution
4. WHEN IPA is ready THEN MKR_Flutter SHALL upload to TestFlight or generate Ad Hoc distribution
