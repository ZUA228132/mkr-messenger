import 'package:dio/dio.dart';
import '../../domain/entities/auth_tokens.dart';
import '../../domain/entities/user.dart';
import '../../domain/repositories/auth_repository.dart';

/// Data source for authentication API calls
class AuthApiDatasource {
  final Dio _dio;

  AuthApiDatasource({required Dio dio}) : _dio = dio;

  /// Login with callsign and PIN
  Future<AuthResult> login(String callsign, String pin) async {
    try {
      final response = await _dio.post(
        '/auth/login',
        data: {
          'callsign': callsign,
          'pin': pin,
        },
      );

      final data = response.data as Map<String, dynamic>;
      
      return AuthResult(
        user: User(
          id: data['user']['id'] as String,
          callsign: data['user']['callsign'] as String,
          displayName: data['user']['displayName'] as String?,
          avatarUrl: data['user']['avatarUrl'] as String?,
          isVerified: data['user']['isVerified'] as bool? ?? false,
          createdAt: DateTime.parse(data['user']['createdAt'] as String),
        ),
        tokens: AuthTokens(
          accessToken: data['tokens']['accessToken'] as String,
          refreshToken: data['tokens']['refreshToken'] as String,
          expiresAt: DateTime.parse(data['tokens']['expiresAt'] as String),
        ),
      );
    } on DioException {
      rethrow;
    }
  }

  /// Logout and invalidate tokens on server
  Future<void> logout(String accessToken) async {
    try {
      await _dio.post(
        '/auth/logout',
        options: Options(
          headers: {'Authorization': 'Bearer $accessToken'},
        ),
      );
    } on DioException {
      // Ignore logout errors - we'll clear local tokens anyway
    }
  }

  /// Refresh authentication tokens
  Future<AuthTokens> refreshTokens(String refreshToken) async {
    final response = await _dio.post(
      '/auth/refresh',
      data: {'refreshToken': refreshToken},
    );

    final data = response.data as Map<String, dynamic>;
    
    return AuthTokens(
      accessToken: data['accessToken'] as String,
      refreshToken: data['refreshToken'] as String,
      expiresAt: DateTime.parse(data['expiresAt'] as String),
    );
  }
}
