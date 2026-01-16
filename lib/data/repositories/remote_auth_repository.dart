import 'dart:developer' as developer;

import '../../core/errors/failures.dart';
import '../../core/result/result.dart';
import '../datasources/api_client.dart';
import '../datasources/secure_local_storage.dart';
import '../models/auth_models.dart';

/// Remote Auth Repository for MKR Backend integration
/// 
/// Requirements: 2.1-2.7 - Email registration, verification, login
/// Requirements: 3.4 - Logout and clear credentials
class RemoteAuthRepository {
  final ApiClient _apiClient;
  final SecureLocalStorage _storage;

  RemoteAuthRepository({
    required ApiClient apiClient,
    required SecureLocalStorage storage,
  })  : _apiClient = apiClient,
        _storage = storage;

  /// Register a new user with email
  /// Requirements: 2.1 - POST /api/auth/register/email
  Future<Result<void>> registerEmail({
    required String email,
    required String password,
    required String username,
    required String displayName,
  }) async {
    final request = RegisterEmailRequest(
      email: email,
      password: password,
      username: username,
      displayName: displayName,
    );

    developer.log(
      'Registering user: ${request.email}',
      name: 'RemoteAuthRepository',
    );

    final result = await _apiClient.post(
      '/api/auth/register/email',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (_) {
        developer.log(
          'Registration successful for: ${request.email}',
          name: 'RemoteAuthRepository',
        );
        return const Success(null);
      },
      onFailure: (apiError) {
        developer.log(
          'Registration failed: ${apiError.message}',
          name: 'RemoteAuthRepository',
        );
        return Error(apiError.toFailure());
      },
    );
  }

  /// Verify email with code
  /// Requirements: 2.3 - POST /api/auth/verify/email
  /// Requirements: 2.4 - Store JWT token after verification
  Future<Result<AuthResponse>> verifyEmail({
    required String email,
    required String code,
  }) async {
    final request = VerifyEmailRequest(
      email: email,
      code: code,
    );

    developer.log(
      'Verifying email: ${request.email}',
      name: 'RemoteAuthRepository',
    );

    final result = await _apiClient.post(
      '/api/auth/verify/email',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (response) async {
        try {
          final authResponse = AuthResponse.fromJson(
            response.data as Map<String, dynamic>,
          );

          // Store token and userId securely
          // Requirements: 2.4 - Store JWT token securely
          await _storage.saveToken(authResponse.token);
          await _storage.saveUserId(authResponse.userId);

          // Set token in API client for subsequent requests
          _apiClient.setAuthToken(authResponse.token);

          developer.log(
            'Email verified successfully for: $email',
            name: 'RemoteAuthRepository',
          );

          return Success(authResponse);
        } catch (e) {
          developer.log(
            'Failed to parse verify response: $e',
            name: 'RemoteAuthRepository',
          );
          return Error(ServerFailure('Failed to parse response: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Email verification failed: ${apiError.message}',
          name: 'RemoteAuthRepository',
        );
        return Error(apiError.toFailure());
      },
    );
  }


  /// Login with email and password
  /// Requirements: 2.5 - POST /api/auth/login/email
  /// Requirements: 2.6 - Store JWT token and userId securely
  /// Requirements: 2.7 - Handle lockout error
  Future<Result<AuthResponse>> loginEmail({
    required String email,
    required String password,
  }) async {
    final request = LoginEmailRequest(
      email: email,
      password: password,
    );

    developer.log(
      'Logging in user: ${request.email}',
      name: 'RemoteAuthRepository',
    );

    final result = await _apiClient.post(
      '/api/auth/login/email',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (response) async {
        try {
          final authResponse = AuthResponse.fromJson(
            response.data as Map<String, dynamic>,
          );

          // Store token and userId securely
          // Requirements: 2.6 - Store JWT token and userId securely
          await _storage.saveToken(authResponse.token);
          await _storage.saveUserId(authResponse.userId);

          // Set token in API client for subsequent requests
          _apiClient.setAuthToken(authResponse.token);

          developer.log(
            'Login successful for: $email',
            name: 'RemoteAuthRepository',
          );

          return Success(authResponse);
        } catch (e) {
          developer.log(
            'Failed to parse login response: $e',
            name: 'RemoteAuthRepository',
          );
          return Error(ServerFailure('Failed to parse response: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Login failed: ${apiError.message}',
          name: 'RemoteAuthRepository',
        );
        
        // Check for lockout error
        // Requirements: 2.7 - Display remaining lockout time
        if (apiError.statusCode == 429) {
          return Error(const AccountLockedFailure(Duration(minutes: 5)));
        }
        
        return Error(apiError.toFailure());
      },
    );
  }

  /// Logout and clear all credentials
  /// Requirements: 3.4 - Clear all stored credentials on logout
  Future<Result<void>> logout() async {
    developer.log(
      'Logging out user',
      name: 'RemoteAuthRepository',
    );

    try {
      // Try to notify server about logout (optional, may fail if offline)
      await _apiClient.post('/api/auth/logout');
    } catch (e) {
      // Ignore server logout errors - we'll clear local data anyway
      developer.log(
        'Server logout failed (continuing with local cleanup): $e',
        name: 'RemoteAuthRepository',
      );
    }

    // Clear all stored credentials
    // Requirements: 3.4 - Clear all stored credentials
    await _storage.clearCredentials();

    // Clear token from API client
    _apiClient.clearAuthToken();

    developer.log(
      'Logout completed - credentials cleared',
      name: 'RemoteAuthRepository',
    );

    return const Success(null);
  }

  /// Check if user is authenticated
  /// Returns true if valid token and userId exist in storage
  Future<bool> isAuthenticated() async {
    return await _storage.isAuthenticated();
  }

  /// Get current user ID from storage
  Future<String?> getCurrentUserId() async {
    return await _storage.getUserId();
  }

  /// Get current token from storage
  Future<String?> getCurrentToken() async {
    return await _storage.getToken();
  }

  /// Initialize API client with stored token (call on app start)
  /// Requirements: 3.2 - Include Authorization header with Bearer token
  Future<void> initializeFromStorage() async {
    final token = await _storage.getToken();
    if (token != null && token.isNotEmpty) {
      _apiClient.setAuthToken(token);
      developer.log(
        'API client initialized with stored token',
        name: 'RemoteAuthRepository',
      );
    }
  }
}
