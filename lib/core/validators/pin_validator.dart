import 'validation_result.dart';

/// Validates PIN format according to requirements.
///
/// PIN rules:
/// - Must be 4-6 characters long
/// - Must contain only digits (0-9)
///
/// **Feature: mkr-flutter-ios, Property 2: PIN Validation**
/// **Validates: Requirements 2.2**
class PinValidator {
  /// Minimum allowed PIN length.
  static const int minLength = 4;

  /// Maximum allowed PIN length.
  static const int maxLength = 6;

  /// Regular expression for digits only.
  static final RegExp _digitsOnlyRegex = RegExp(r'^[0-9]+$');

  /// Validates a PIN string.
  ///
  /// Returns [ValidationResult.valid] if the PIN meets all requirements:
  /// - 4-6 characters long
  /// - Contains only digits
  ///
  /// Returns [ValidationResult.invalid] with an appropriate error message otherwise.
  static ValidationResult validate(String pin) {
    // Check for empty input
    if (pin.isEmpty) {
      return const ValidationResult.invalid('PIN cannot be empty');
    }

    // Check minimum length
    if (pin.length < minLength) {
      return ValidationResult.invalid(
        'PIN must be at least $minLength digits',
      );
    }

    // Check maximum length
    if (pin.length > maxLength) {
      return ValidationResult.invalid(
        'PIN must be at most $maxLength digits',
      );
    }

    // Check for digits only
    if (!_digitsOnlyRegex.hasMatch(pin)) {
      return const ValidationResult.invalid('PIN must contain only digits');
    }

    return const ValidationResult.valid();
  }

  /// Convenience method to check if a PIN is valid.
  ///
  /// Returns true if the PIN passes all validation rules.
  static bool isValid(String pin) {
    return validate(pin).isValid;
  }

  /// Validates that two PINs match.
  ///
  /// Returns true if both PINs are equal.
  static bool pinsMatch(String pin, String confirmPin) {
    return pin == confirmPin;
  }
}
