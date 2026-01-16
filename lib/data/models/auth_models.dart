// Auth API Models for MKR Backend integration
// Requirements: 2.1, 2.3, 2.5 - Auth request/response models

/// Response from authentication endpoints (login, verify)
/// Requirements: 2.4, 2.6 - Contains userId and token for secure storage
class AuthResponse {
  final String userId;
  final String token;
  final int accessLevel;

  const AuthResponse({
    required this.userId,
    required this.token,
    required this.accessLevel,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      userId: json['userId'] as String,
      token: json['token'] as String,
      accessLevel: json['accessLevel'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'token': token,
      'accessLevel': accessLevel,
    };
  }

  @override
  String toString() =>
      'AuthResponse(userId: $userId, accessLevel: $accessLevel)';
}

/// Request body for email registration
/// Requirements: 2.1 - POST /api/auth/register/email
class RegisterEmailRequest {
  final String email;
  final String password;
  final String username;
  final String displayName;

  const RegisterEmailRequest({
    required this.email,
    required this.password,
    required this.username,
    required this.displayName,
  });

  Map<String, dynamic> toJson() {
    return {
      'email': email,
      'password': password,
      'username': username,
      'displayName': displayName,
    };
  }

  @override
  String toString() =>
      'RegisterEmailRequest(email: $email, username: $username, displayName: $displayName)';
}

/// Request body for email login
/// Requirements: 2.5 - POST /api/auth/login/email
class LoginEmailRequest {
  final String email;
  final String password;

  const LoginEmailRequest({
    required this.email,
    required this.password,
  });

  Map<String, dynamic> toJson() {
    return {
      'email': email,
      'password': password,
    };
  }

  @override
  String toString() => 'LoginEmailRequest(email: $email)';
}

/// Request body for email verification
/// Requirements: 2.3 - POST /api/auth/verify/email
class VerifyEmailRequest {
  final String email;
  final String code;

  const VerifyEmailRequest({
    required this.email,
    required this.code,
  });

  Map<String, dynamic> toJson() {
    return {
      'email': email,
      'code': code,
    };
  }

  @override
  String toString() => 'VerifyEmailRequest(email: $email, code: $code)';
}

/// Lockout error response when login fails too many times
/// Requirements: 2.7 - Display remaining lockout time
class LockoutError {
  final int remainingSeconds;
  final String message;

  const LockoutError({
    required this.remainingSeconds,
    required this.message,
  });

  factory LockoutError.fromJson(Map<String, dynamic> json) {
    return LockoutError(
      remainingSeconds: json['remainingSeconds'] as int? ?? 0,
      message: json['message'] as String? ?? 'Account locked',
    );
  }

  Duration get remainingDuration => Duration(seconds: remainingSeconds);

  @override
  String toString() =>
      'LockoutError(remainingSeconds: $remainingSeconds, message: $message)';
}
