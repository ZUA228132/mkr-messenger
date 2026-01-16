import 'package:flutter/foundation.dart';

import '../datasources/secure_storage_datasource.dart';
import 'secure_wipe_service.dart';

/// Types of disguise for stealth mode
enum DisguiseType {
  calculator,
  notes,
  weather,
}

/// Service for managing stealth mode functionality
/// 
/// Requirements:
/// - 5.1: Enable/disable stealth mode with disguise type
/// - 5.3: Validate secret code to reveal messenger
/// - 5.4: Store secret code securely
/// - 5.5: Trigger Panic Button after 5 failed attempts
class StealthModeService {
  final SecureStorageDatasource _secureStorage;
  final SecureWipeService _wipeService;

  static const String _stealthEnabledKey = 'stealth_enabled';
  static const String _disguiseTypeKey = 'stealth_disguise_type';
  static const String _secretCodeKey = 'stealth_secret_code';
  static const String _failedAttemptsKey = 'stealth_failed_attempts';

  /// Maximum failed attempts before triggering panic button
  static const int maxFailedAttempts = 5;

  int _failedAttempts = 0;

  StealthModeService({
    required SecureStorageDatasource secureStorage,
    required SecureWipeService wipeService,
  })  : _secureStorage = secureStorage,
        _wipeService = wipeService;

  /// Current number of failed code attempts
  int get failedAttempts => _failedAttempts;

  /// Initialize service and load failed attempts from storage
  Future<void> initialize() async {
    final storedAttempts = await _secureStorage.read(_failedAttemptsKey);
    if (storedAttempts != null) {
      _failedAttempts = int.tryParse(storedAttempts) ?? 0;
    }
  }

  /// Check if stealth mode is currently enabled
  Future<bool> isEnabled() async {
    final enabled = await _secureStorage.read(_stealthEnabledKey);
    return enabled == 'true';
  }

  /// Get the current disguise type
  Future<DisguiseType?> getCurrentDisguise() async {
    final typeStr = await _secureStorage.read(_disguiseTypeKey);
    if (typeStr == null) return null;

    return DisguiseType.values.firstWhere(
      (t) => t.name == typeStr,
      orElse: () => DisguiseType.calculator,
    );
  }

  /// Enable stealth mode with specified disguise and secret code
  /// 
  /// Requirements: 5.1, 5.4
  Future<void> enable(DisguiseType type, String secretCode) async {
    if (secretCode.isEmpty) {
      throw ArgumentError('Secret code cannot be empty');
    }

    await Future.wait([
      _secureStorage.write(_stealthEnabledKey, 'true'),
      _secureStorage.write(_disguiseTypeKey, type.name),
      _secureStorage.write(_secretCodeKey, secretCode),
      _secureStorage.write(_failedAttemptsKey, '0'),
    ]);

    _failedAttempts = 0;
  }

  /// Disable stealth mode
  Future<void> disable() async {
    await Future.wait([
      _secureStorage.write(_stealthEnabledKey, 'false'),
      _secureStorage.delete(_secretCodeKey),
      _secureStorage.delete(_failedAttemptsKey),
    ]);

    _failedAttempts = 0;
  }


  /// Validate the secret code input
  /// 
  /// Requirements: 5.3 - Unlock if and only if input exactly matches stored code
  /// Requirements: 5.5 - Trigger Panic Button after 5 failed attempts
  /// 
  /// Returns a [CodeValidationResult] indicating success, failure, or panic triggered
  Future<CodeValidationResult> validateCode(String input) async {
    final storedCode = await _secureStorage.read(_secretCodeKey);

    if (storedCode == null) {
      return CodeValidationResult.error('No secret code configured');
    }

    // Exact match required
    if (input == storedCode) {
      // Reset failed attempts on success
      _failedAttempts = 0;
      await _secureStorage.write(_failedAttemptsKey, '0');
      return CodeValidationResult.success();
    }

    // Record failed attempt
    _failedAttempts++;
    await _secureStorage.write(_failedAttemptsKey, _failedAttempts.toString());

    // Check if panic should be triggered
    if (_failedAttempts >= maxFailedAttempts) {
      debugPrint('Stealth mode: Max failed attempts reached, triggering panic');
      await _triggerPanic();
      return CodeValidationResult.panicTriggered();
    }

    return CodeValidationResult.failure(
      remainingAttempts: maxFailedAttempts - _failedAttempts,
    );
  }

  /// Get the stored secret code (for internal use only)
  Future<String?> getSecretCode() async {
    return await _secureStorage.read(_secretCodeKey);
  }

  /// Update the secret code
  Future<void> updateSecretCode(String newCode) async {
    if (newCode.isEmpty) {
      throw ArgumentError('Secret code cannot be empty');
    }
    await _secureStorage.write(_secretCodeKey, newCode);
  }

  /// Reset failed attempts counter
  Future<void> resetFailedAttempts() async {
    _failedAttempts = 0;
    await _secureStorage.write(_failedAttemptsKey, '0');
  }

  /// Trigger panic button (secure wipe)
  Future<void> _triggerPanic() async {
    try {
      await _wipeService.executeWipe();
    } catch (e) {
      debugPrint('Error triggering panic from stealth mode: $e');
    }
  }
}

/// Result of a secret code validation attempt
class CodeValidationResult {
  final CodeValidationStatus status;
  final int? remainingAttempts;
  final String? errorMessage;

  const CodeValidationResult._({
    required this.status,
    this.remainingAttempts,
    this.errorMessage,
  });

  factory CodeValidationResult.success() => const CodeValidationResult._(
        status: CodeValidationStatus.success,
      );

  factory CodeValidationResult.failure({required int remainingAttempts}) =>
      CodeValidationResult._(
        status: CodeValidationStatus.failure,
        remainingAttempts: remainingAttempts,
      );

  factory CodeValidationResult.panicTriggered() => const CodeValidationResult._(
        status: CodeValidationStatus.panicTriggered,
      );

  factory CodeValidationResult.error(String message) => CodeValidationResult._(
        status: CodeValidationStatus.error,
        errorMessage: message,
      );

  bool get isSuccess => status == CodeValidationStatus.success;
  bool get isPanicTriggered => status == CodeValidationStatus.panicTriggered;
}

enum CodeValidationStatus {
  success,
  failure,
  panicTriggered,
  error,
}
