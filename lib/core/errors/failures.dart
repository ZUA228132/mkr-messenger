/// Base class for all failures in the application
abstract class Failure {
  final String message;
  const Failure(this.message);
}

/// Authentication related failures
class AuthenticationFailure extends Failure {
  const AuthenticationFailure(super.message);
}

class InvalidCallsignFailure extends AuthenticationFailure {
  const InvalidCallsignFailure() : super('Invalid callsign format');
}

class InvalidPinFailure extends AuthenticationFailure {
  const InvalidPinFailure() : super('Invalid PIN format');
}

class AccountLockedFailure extends AuthenticationFailure {
  final Duration lockoutDuration;
  const AccountLockedFailure(this.lockoutDuration) 
      : super('Account locked due to too many failed attempts');
}

class BiometricNotAvailableFailure extends AuthenticationFailure {
  const BiometricNotAvailableFailure() : super('Biometric authentication not available');
}

/// Encryption related failures
class EncryptionFailure extends Failure {
  const EncryptionFailure(super.message);
}

class KeyGenerationFailure extends EncryptionFailure {
  const KeyGenerationFailure() : super('Failed to generate encryption keys');
}

class DecryptionFailure extends EncryptionFailure {
  const DecryptionFailure() : super('Failed to decrypt message');
}

class KeySerializationFailure extends EncryptionFailure {
  const KeySerializationFailure() : super('Failed to serialize/deserialize keys');
}

/// Network related failures
class NetworkFailure extends Failure {
  const NetworkFailure(super.message);
}

class NetworkUnavailableFailure extends NetworkFailure {
  const NetworkUnavailableFailure() : super('No internet connection');
}

class ServerFailure extends NetworkFailure {
  const ServerFailure(super.message);
}

class WebSocketDisconnectedFailure extends NetworkFailure {
  const WebSocketDisconnectedFailure() : super('WebSocket connection lost');
}

class TokenExpiredFailure extends NetworkFailure {
  const TokenExpiredFailure() : super('Authentication token expired');
}

/// Storage related failures
class StorageFailure extends Failure {
  const StorageFailure(super.message);
}

class SecureStorageFailure extends StorageFailure {
  const SecureStorageFailure() : super('Secure storage operation failed');
}

class DatabaseFailure extends StorageFailure {
  const DatabaseFailure() : super('Database operation failed');
}

class FileSystemFailure extends StorageFailure {
  const FileSystemFailure() : super('File system operation failed');
}
