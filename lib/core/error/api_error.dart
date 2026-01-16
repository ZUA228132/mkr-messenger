import 'dart:developer' as developer;

/// API Error class for parsing and handling API error responses.
/// 
/// Requirements: 10.1 - Parse error message and display it to user
/// Requirements: 10.4 - Log all API errors for debugging
class ApiError {
  final int? statusCode;
  final String message;
  final String? errorCode;
  final String? endpoint;
  final DateTime timestamp;

  ApiError({
    this.statusCode,
    required this.message,
    this.errorCode,
    this.endpoint,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  /// Create ApiError from a response map (typically from Dio Response).
  /// Requirements: 10.1 - Parse error message and display it to user
  factory ApiError.fromResponseData(dynamic data, {int? statusCode, String? endpoint}) {
    String message = 'Unknown error';
    String? errorCode;

    if (data is Map<String, dynamic>) {
      // Try to extract error message from common API response formats
      message = data['error'] as String? ??
          data['message'] as String? ??
          data['detail'] as String? ??
          data['errors']?.toString() ??
          'Unknown error';
      errorCode = data['code'] as String? ?? data['error_code'] as String?;
    } else if (data is String && data.isNotEmpty) {
      message = data;
    }

    final error = ApiError(
      statusCode: statusCode,
      message: message,
      errorCode: errorCode,
      endpoint: endpoint,
    );
    
    // Log the error
    error.log();
    
    return error;
  }

  /// Create ApiError for network unavailable issues.
  /// Requirements: 10.2 - Display offline indicator when network is unavailable
  factory ApiError.network({String? endpoint}) {
    final error = ApiError(
      message: 'Network unavailable. Please check your connection.',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for request timeout.
  /// Requirements: 10.3 - Offer retry option when request times out
  factory ApiError.timeout({String? endpoint}) {
    final error = ApiError(
      message: 'Request timed out. Please try again.',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for cancelled requests.
  factory ApiError.cancelled({String? endpoint}) {
    final error = ApiError(
      message: 'Request was cancelled.',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for unknown/unexpected errors.
  factory ApiError.unknown(dynamic error, {String? endpoint}) {
    final apiError = ApiError(
      message: error?.toString() ?? 'An unexpected error occurred',
      endpoint: endpoint,
    );
    apiError.log();
    return apiError;
  }

  /// Create ApiError for unauthorized (401) responses.
  factory ApiError.unauthorized({String? endpoint}) {
    final error = ApiError(
      statusCode: 401,
      message: 'Authentication required. Please log in again.',
      errorCode: 'UNAUTHORIZED',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for forbidden (403) responses.
  factory ApiError.forbidden({String? endpoint}) {
    final error = ApiError(
      statusCode: 403,
      message: 'Access denied. You do not have permission.',
      errorCode: 'FORBIDDEN',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for not found (404) responses.
  factory ApiError.notFound({String? endpoint, String? resource}) {
    final error = ApiError(
      statusCode: 404,
      message: resource != null ? '$resource not found' : 'Resource not found',
      errorCode: 'NOT_FOUND',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for validation (422) errors.
  factory ApiError.validation(String message, {String? endpoint}) {
    final error = ApiError(
      statusCode: 422,
      message: message,
      errorCode: 'VALIDATION_ERROR',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for rate limiting (429) responses.
  factory ApiError.rateLimited({String? endpoint}) {
    final error = ApiError(
      statusCode: 429,
      message: 'Too many requests. Please wait and try again.',
      errorCode: 'RATE_LIMITED',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Create ApiError for server (5xx) errors.
  factory ApiError.serverError({String? endpoint, String? message}) {
    final error = ApiError(
      statusCode: 500,
      message: message ?? 'Server error. Please try again later.',
      errorCode: 'SERVER_ERROR',
      endpoint: endpoint,
    );
    error.log();
    return error;
  }

  /// Check if this is a network-related error.
  bool get isNetworkError => statusCode == null && message.contains('Network');

  /// Check if this is a timeout error.
  bool get isTimeoutError => message.contains('timed out');

  /// Check if this is an authentication error.
  bool get isAuthError => statusCode == 401;

  /// Check if this is a forbidden error.
  bool get isForbiddenError => statusCode == 403;

  /// Check if this is a not found error.
  bool get isNotFoundError => statusCode == 404;

  /// Check if this is a validation error.
  bool get isValidationError => statusCode == 422;

  /// Check if this is a rate limit error.
  bool get isRateLimitError => statusCode == 429;

  /// Check if this is a server error.
  bool get isServerError => statusCode != null && statusCode! >= 500;

  /// Check if this error should trigger a retry.
  bool get isRetryable => isNetworkError || isTimeoutError || isServerError;

  /// Log this error for debugging.
  /// Requirements: 10.4 - Log all API errors for debugging
  void log() {
    final buffer = StringBuffer();
    buffer.writeln('API Error:');
    buffer.writeln('  Message: $message');
    if (statusCode != null) {
      buffer.writeln('  Status Code: $statusCode');
    }
    if (errorCode != null) {
      buffer.writeln('  Error Code: $errorCode');
    }
    if (endpoint != null) {
      buffer.writeln('  Endpoint: $endpoint');
    }
    buffer.writeln('  Timestamp: ${timestamp.toIso8601String()}');

    developer.log(
      buffer.toString(),
      name: 'ApiError',
      time: timestamp,
    );
  }

  /// Get a user-friendly error message.
  String get userMessage {
    // Return more user-friendly messages for common errors
    if (isNetworkError) {
      return 'No internet connection. Please check your network.';
    }
    if (isTimeoutError) {
      return 'The request took too long. Please try again.';
    }
    if (isAuthError) {
      return 'Your session has expired. Please log in again.';
    }
    if (isForbiddenError) {
      return 'You don\'t have permission to perform this action.';
    }
    if (isRateLimitError) {
      return 'Too many requests. Please wait a moment.';
    }
    if (isServerError) {
      return 'Something went wrong on our end. Please try again later.';
    }
    return message;
  }

  @override
  String toString() =>
      'ApiError(statusCode: $statusCode, message: $message, errorCode: $errorCode, endpoint: $endpoint)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ApiError &&
          runtimeType == other.runtimeType &&
          statusCode == other.statusCode &&
          message == other.message &&
          errorCode == other.errorCode;

  @override
  int get hashCode => Object.hash(statusCode, message, errorCode);

  /// Create a copy of this error with optional overrides.
  ApiError copyWith({
    int? statusCode,
    String? message,
    String? errorCode,
    String? endpoint,
  }) {
    return ApiError(
      statusCode: statusCode ?? this.statusCode,
      message: message ?? this.message,
      errorCode: errorCode ?? this.errorCode,
      endpoint: endpoint ?? this.endpoint,
      timestamp: timestamp,
    );
  }
}
