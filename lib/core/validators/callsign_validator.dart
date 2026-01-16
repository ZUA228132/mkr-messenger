import 'validation_result.dart';

/// Validates callsign format according to requirements.
///
/// Callsign rules:
/// - Must be 3-16 characters long
/// - Must contain only alphanumeric characters (a-z, A-Z, 0-9)
/// - Must start with a letter
///
/// **Feature: mkr-flutter-ios, Property 1: Callsign Validation**
/// **Validates: Requirements 2.1**
class CallsignValidator {
  /// Minimum allowed callsign length.
  static const int minLength = 3;

  /// Maximum allowed callsign length.
  static const int maxLength = 16;

  /// Regular expression for alphanumeric characters only.
  static final RegExp _alphanumericRegex = RegExp(r'^[a-zA-Z0-9]+$');

  /// Regular expression for letter at start.
  static final RegExp _startsWithLetterRegex = RegExp(r'^[a-zA-Z]');

  /// Validates a callsign string.
  ///
  /// Returns [ValidationResult.valid] if the callsign meets all requirements:
  /// - 3-16 characters long
  /// - Contains only alphanumeric characters
  /// - Starts with a letter
  ///
  /// Returns [ValidationResult.invalid] with an appropriate error message otherwise.
  static ValidationResult validate(String callsign) {
    // Check for empty input
    if (callsign.isEmpty) {
      return const ValidationResult.invalid('Callsign cannot be empty');
    }

    // Check minimum length
    if (callsign.length < minLength) {
      return ValidationResult.invalid(
        'Callsign must be at least $minLength characters',
      );
    }

    // Check maximum length
    if (callsign.length > maxLength) {
      return ValidationResult.invalid(
        'Callsign must be at most $maxLength characters',
      );
    }

    // Check that it starts with a letter
    if (!_startsWithLetterRegex.hasMatch(callsign)) {
      return const ValidationResult.invalid(
        'Callsign must start with a letter',
      );
    }

    // Check for alphanumeric characters only
    if (!_alphanumericRegex.hasMatch(callsign)) {
      return const ValidationResult.invalid(
        'Callsign must contain only letters and numbers',
      );
    }

    return const ValidationResult.valid();
  }

  /// Convenience method to check if a callsign is valid.
  ///
  /// Returns true if the callsign passes all validation rules.
  static bool isValid(String callsign) {
    return validate(callsign).isValid;
  }
}
