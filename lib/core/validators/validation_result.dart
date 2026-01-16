/// Represents the result of a validation operation.
class ValidationResult {
  final bool isValid;
  final String? errorMessage;

  /// Creates a successful validation result.
  const ValidationResult.valid()
      : isValid = true,
        errorMessage = null;

  /// Creates a failed validation result with an error message.
  const ValidationResult.invalid(String message)
      : isValid = false,
        errorMessage = message;

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ValidationResult &&
        other.isValid == isValid &&
        other.errorMessage == errorMessage;
  }

  @override
  int get hashCode => isValid.hashCode ^ errorMessage.hashCode;

  @override
  String toString() =>
      'ValidationResult(isValid: $isValid, errorMessage: $errorMessage)';
}
