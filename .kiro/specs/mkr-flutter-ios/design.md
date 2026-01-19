# Design Document: MKR Flutter iOS

## Overview

MKR Flutter - **отдельный** кроссплатформенный проект в папке `mkr_flutter/`, позволяющий собирать iOS приложение без Mac через облачные CI/CD сервисы. 

**Важно:**
- Kotlin Android проект (`app/`) остаётся без изменений
- Flutter проект создаётся в новой папке `mkr_flutter/`
- Оба проекта используют один и тот же backend (`backend/`)
- Flutter версия - это порт функционала для iOS, не замена Android версии

### Ключевые решения:
- **Flutter 3.x** - стабильная версия с полной поддержкой iOS/Android
- **Codemagic** - CI/CD для сборки iOS без Mac (500 бесплатных минут/месяц)
- **flutter_secure_storage** - безопасное хранение на обеих платформах
- **pointycastle** - криптография (AES-256-GCM, X25519)

### Структура проекта:
```
/                           # Корень репозитория
├── app/                    # Kotlin Android (НЕ ТРОГАЕМ)
├── backend/                # Kotlin Ktor backend (общий)
├── mkr_flutter/            # НОВЫЙ Flutter проект
│   ├── lib/
│   ├── ios/
│   ├── android/
│   ├── codemagic.yaml
│   └── pubspec.yaml
└── ...
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MKR Flutter App                          │
├─────────────────────────────────────────────────────────────┤
│  Presentation Layer (UI)                                    │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │   Screens   │ │   Widgets   │ │  Providers  │           │
│  │  (Cupertino │ │  (Reusable) │ │  (Riverpod) │           │
│  │   + Material)│ │             │ │             │           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer (Business Logic)                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │  Use Cases  │ │  Entities   │ │ Repositories│           │
│  │             │ │             │ │ (Interfaces)│           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
├─────────────────────────────────────────────────────────────┤
│  Data Layer                                                 │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │   API       │ │   Local     │ │   Crypto    │           │
│  │  (Dio/WS)   │ │  (SQLite)   │ │  (Signal)   │           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
├─────────────────────────────────────────────────────────────┤
│  Platform Layer                                             │
│  ┌─────────────────────┐ ┌─────────────────────┐           │
│  │       iOS           │ │      Android        │           │
│  │  - Keychain         │ │  - Keystore         │           │
│  │  - Face ID          │ │  - Fingerprint      │           │
│  │  - APNs             │ │  - FCM              │           │
│  └─────────────────────┘ └─────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. Authentication Module

```dart
abstract class AuthRepository {
  Future<AuthResult> login(String callsign, String pin);
  Future<void> logout();
  Future<bool> validateBiometric();
  Future<void> storeTokens(AuthTokens tokens);
  Future<AuthTokens?> getStoredTokens();
}

class CallsignValidator {
  /// Validates callsign: 3-16 alphanumeric, starts with letter
  static ValidationResult validate(String callsign);
}

class PinValidator {
  /// Validates PIN: 4-6 digits only
  static ValidationResult validate(String pin);
}

class LockoutManager {
  int failedAttempts = 0;
  DateTime? lockoutUntil;
  
  /// Returns lockout duration based on failed attempts
  Duration getLockoutDuration(int attempts);
  
  /// Records failed attempt, returns if locked out
  bool recordFailure();
  
  /// Resets on successful auth
  void reset();
}
```

### 2. Encryption Module

```dart
abstract class SignalProtocol {
  /// Generate identity key pair
  Future<KeyPair> generateIdentityKeys();
  
  /// Perform X25519 key exchange
  Future<SharedSecret> performKeyExchange(PublicKey theirKey);
  
  /// Encrypt message with AES-256-GCM
  Future<EncryptedMessage> encrypt(String plaintext, SessionKey key);
  
  /// Decrypt message
  Future<String> decrypt(EncryptedMessage ciphertext, SessionKey key);
  
  /// Serialize keys to JSON
  String serializeKeys(KeyPair keys);
  
  /// Deserialize keys from JSON
  KeyPair deserializeKeys(String json);
}

class EncryptedMessage {
  final Uint8List ciphertext;
  final Uint8List nonce;  // Unique per message
  final Uint8List tag;    // Authentication tag
}
```

### 3. Panic Button Module

```dart
abstract class SecureWipeService {
  /// Execute complete data wipe
  Future<WipeResult> executeWipe();
  
  /// Delete all encryption keys
  Future<void> wipeKeys();
  
  /// Overwrite and delete messages
  Future<void> wipeMessages();
  
  /// Overwrite and delete media files
  Future<void> wipeMedia();
  
  /// Revoke server tokens
  Future<void> revokeServerTokens();
  
  /// Reset app to initial state
  Future<void> resetAppState();
}
```

### 4. Stealth Mode Module

```dart
abstract class StealthModeService {
  /// Enable stealth mode with disguise type
  Future<void> enable(DisguiseType type, String secretCode);
  
  /// Disable stealth mode
  Future<void> disable();
  
  /// Validate secret code
  bool validateCode(String input);
  
  /// Get current disguise type
  DisguiseType? getCurrentDisguise();
  
  /// Track failed code attempts
  int failedAttempts = 0;
}

enum DisguiseType {
  calculator,
  notes,
  weather,
}
```

### 5. Chat Module

```dart
abstract class ChatRepository {
  /// Send encrypted message
  Future<void> sendMessage(String chatId, Message message);
  
  /// Get messages for chat
  Stream<List<Message>> getMessages(String chatId);
  
  /// Queue message for offline sending
  Future<void> queueOfflineMessage(Message message);
  
  /// Sync queued messages when online
  Future<void> syncQueuedMessages();
  
  /// Delete messages by auto-delete policy
  Future<void> applyAutoDelete(AutoDeletePolicy policy);
}

abstract class MediaEncryption {
  /// Encrypt media file before upload
  Future<EncryptedFile> encryptMedia(File file);
  
  /// Decrypt downloaded media
  Future<File> decryptMedia(EncryptedFile encrypted);
}
```

### 6. Security Check Module

```dart
abstract class SecurityChecker {
  /// Run all security checks
  Future<SecurityReport> runChecks();
  
  /// Check for jailbreak/root
  Future<bool> isDeviceCompromised();
  
  /// Check for debugger
  Future<bool> isDebuggerAttached();
  
  /// Check for emulator
  Future<bool> isEmulator();
  
  /// Calculate risk level from checks
  RiskLevel calculateRiskLevel(SecurityReport report);
}

enum RiskLevel {
  safe,
  low,
  medium,
  high,
  critical,
}
```

### 7. Local Storage Module

```dart
abstract class SecureLocalStorage {
  /// Store encrypted message in local DB
  Future<void> storeMessage(Message message);
  
  /// Get cached messages
  Future<List<Message>> getCachedMessages(String chatId);
  
  /// Encrypt entire database
  Future<void> encryptDatabase(String key);
  
  /// Clear all local data
  Future<void> clearAll();
}
```

## Data Models

```dart
class User {
  final String id;
  final String callsign;
  final String? displayName;
  final String? avatarUrl;
  final bool isVerified;
  final DateTime createdAt;
}

class Message {
  final String id;
  final String chatId;
  final String senderId;
  final String content;
  final MessageType type;
  final DateTime timestamp;
  final MessageStatus status;
  final AutoDeletePolicy? autoDelete;
}

class Chat {
  final String id;
  final ChatType type;
  final List<String> participantIds;
  final Message? lastMessage;
  final DateTime updatedAt;
}

class AuthTokens {
  final String accessToken;
  final String refreshToken;
  final DateTime expiresAt;
  
  String toJson();
  factory AuthTokens.fromJson(String json);
}

class KeyPair {
  final Uint8List publicKey;
  final Uint8List privateKey;
  
  String toJson();
  factory KeyPair.fromJson(String json);
}

enum MessageType { text, image, video, audio, file }
enum MessageStatus { sending, sent, delivered, read, failed }
enum ChatType { direct, group, channel }
enum AutoDeletePolicy { oneDay, threeDays, sevenDays, thirtyDays, afterRead, onExit }
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Callsign Validation
*For any* string input, the callsign validator SHALL accept it if and only if it is 3-16 characters long, contains only alphanumeric characters, and starts with a letter.
**Validates: Requirements 2.1**

### Property 2: PIN Validation
*For any* string input, the PIN validator SHALL accept it if and only if it is 4-6 characters long and contains only digits.
**Validates: Requirements 2.2**

### Property 3: Progressive Lockout
*For any* sequence of N failed authentication attempts where N >= 5, the lockout duration SHALL increase progressively with each additional failure.
**Validates: Requirements 2.5**

### Property 4: Encryption Uniqueness
*For any* plaintext message encrypted twice with the same session key, the resulting ciphertexts SHALL be different (due to unique nonces).
**Validates: Requirements 3.2**

### Property 5: Encryption Round-Trip
*For any* valid plaintext message, encrypting and then decrypting with the same session key SHALL return the original plaintext.
**Validates: Requirements 3.3**

### Property 6: Key Serialization Round-Trip
*For any* valid KeyPair, serializing to JSON and then deserializing SHALL return an equivalent KeyPair.
**Validates: Requirements 3.4, 3.5**

### Property 7: Secure Wipe Completeness
*For any* app state with stored data, after executing secure wipe, the storage SHALL contain no encryption keys, no messages, and no media files.
**Validates: Requirements 4.2, 4.3, 4.5**

### Property 8: Stealth Code Validation
*For any* secret code and input attempt, the stealth mode SHALL unlock if and only if the input exactly matches the stored secret code.
**Validates: Requirements 5.3**

### Property 9: Stealth Lockout Trigger
*For any* sequence of 5 consecutive incorrect code attempts in stealth mode, the system SHALL trigger the Panic Button.
**Validates: Requirements 5.5**

### Property 10: Media Encryption
*For any* media file, the encrypted output SHALL not contain any recognizable content from the original file (no plaintext leakage).
**Validates: Requirements 6.2**

### Property 11: Auto-Delete Enforcement
*For any* message with an auto-delete policy, after the specified interval has elapsed, the message SHALL no longer exist in storage.
**Validates: Requirements 6.4**

### Property 12: Risk Level Calculation
*For any* combination of security check results, the calculated risk level SHALL be deterministic and SHALL be higher when more security issues are detected.
**Validates: Requirements 7.4**

### Property 13: Offline Message Caching
*For any* message stored in local cache, retrieving cached messages SHALL return all stored messages for that chat.
**Validates: Requirements 9.1**

### Property 14: Offline Queue Integrity
*For any* message queued while offline, the message SHALL remain in the queue until successfully synced or explicitly removed.
**Validates: Requirements 9.2**

### Property 15: Local Storage Encryption
*For any* message stored locally, the raw database content SHALL not contain the plaintext message content.
**Validates: Requirements 9.4**

## Error Handling

### Authentication Errors
- `InvalidCallsignError` - callsign format invalid
- `InvalidPinError` - PIN format invalid
- `AuthenticationFailedError` - credentials incorrect
- `AccountLockedError` - too many failed attempts
- `BiometricNotAvailableError` - biometric hardware unavailable

### Encryption Errors
- `KeyGenerationError` - failed to generate keys
- `EncryptionError` - encryption operation failed
- `DecryptionError` - decryption failed (wrong key or corrupted)
- `KeySerializationError` - failed to serialize/deserialize keys

### Network Errors
- `NetworkUnavailableError` - no internet connection
- `ServerError` - backend returned error
- `WebSocketDisconnectedError` - real-time connection lost
- `TokenExpiredError` - auth token needs refresh

### Storage Errors
- `SecureStorageError` - platform secure storage failed
- `DatabaseError` - local database operation failed
- `FileSystemError` - file operation failed

## Testing Strategy

### Property-Based Testing Library
**fast_check** (Dart) - property-based testing library for Flutter/Dart

### Unit Tests
- Validator functions (callsign, PIN)
- Encryption/decryption operations
- Serialization/deserialization
- Risk level calculation
- Auto-delete logic

### Property-Based Tests
Each correctness property will be implemented as a property-based test using fast_check:
- Generate random inputs matching the property's domain
- Verify the property holds for all generated inputs
- Minimum 100 iterations per property
- Tag format: `**Feature: mkr-flutter-ios, Property {N}: {description}**`

### Integration Tests
- Authentication flow with backend
- WebSocket message delivery
- Secure storage on both platforms
- Push notification handling

### Platform-Specific Tests
- iOS: Keychain storage, Face ID integration
- Android: Keystore storage, fingerprint integration
