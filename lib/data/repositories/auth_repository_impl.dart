import 'package:dio/dio.dart';
import 'package:local_auth/local_auth.dart';

import '../../core/errors/failures.dart';
import '../../core/utils/result.dart';
import '../../domain/entities/auth_tokens.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_api_datasource.dart';
import '../datasources/secure_storage_datasource.dart';

/// Implementation of AuthRepository
class AuthRepositoryImpl implements AuthRepository {
  final AuthApiDatasource _apiDatasource;
  final SecureStorageDatasource _storageDatasource;
  final LocalAuthentication _localAuth;

  AuthRepositoryImpl({
    required AuthApiDatasource apiDatasource,
    required SecureStorageDatasource storageDatasource,
    LocalAuthentication? localAuth,
  })  : _apiDatasource = apiDatasource,
        _storageDatasource = storageDatasource,
        _localAuth = localAuth ?? LocalAuthentication();

  @override
  Future<Result<AuthResult>> login(String callsign, String pin) async {
    try {
      final result = await _apiDatasource.login(callsign, pin);
      
      // Store tokens securely
      await _storageDatasource.storeTokens(
        accessToken: result.tokens.accessToken,
        refreshToken: result.tokens.refreshToken,
        expiresAt: result.tokens.expiresAt,
      );
      
      return Success(result);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        return const Error(AuthenticationFailure('Invalid callsign or PIN'));
      }
      if (e.response?.statusCode == 423) {
        return const Error(AccountLockedFailure(Duration(minutes: 5)));
      }
      return Error(NetworkFailure(e.message ?? 'Network error'));
    } catch (e) {
      return Error(AuthenticationFailure(e.toString()));
    }
  }

  @override
  Future<Result<void>> logout() async {
    try {
      final accessToken = await _storageDatasource.getAccessToken();
      
      if (accessToken != null) {
        await _apiDatasource.logout(accessToken);
      }
      
      await _storageDatasource.clearTokens();
      
      return const Success(null);
    } catch (e) {
      // Even if server logout fails, clear local tokens
      await _storageDatasource.clearTokens();
      return const Success(null);
    }
  }

  @override
  Future<Result<bool>> validateBiometric() async {
    try {
      final canAuthenticate = await _localAuth.canCheckBiometrics;
      final isDeviceSupported = await _localAuth.isDeviceSupported();
      
      if (!canAuthenticate || !isDeviceSupported) {
        return const Error(BiometricNotAvailableFailure());
      }

      final didAuthenticate = await _localAuth.authenticate(
        localizedReason: 'Authenticate to access MKR Messenger',
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: true,
        ),
      );

      return Success(didAuthenticate);
    } catch (e) {
      return const Error(BiometricNotAvailableFailure());
    }
  }

  @override
  Future<Result<void>> storeTokens(AuthTokens tokens) async {
    try {
      await _storageDatasource.storeTokens(
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresAt: tokens.expiresAt,
      );
      return const Success(null);
    } catch (e) {
      return const Error(SecureStorageFailure());
    }
  }

  @override
  Future<Result<AuthTokens?>> getStoredTokens() async {
    try {
      final accessToken = await _storageDatasource.getAccessToken();
      final refreshToken = await _storageDatasource.getRefreshToken();
      final expiresAt = await _storageDatasource.getTokenExpiry();

      if (accessToken == null || refreshToken == null || expiresAt == null) {
        return const Success(null);
      }

      return Success(AuthTokens(
        accessToken: accessToken,
        refreshToken: refreshToken,
        expiresAt: expiresAt,
      ));
    } catch (e) {
      return const Error(SecureStorageFailure());
    }
  }

  @override
  Future<Result<void>> clearTokens() async {
    try {
      await _storageDatasource.clearTokens();
      return const Success(null);
    } catch (e) {
      return const Error(SecureStorageFailure());
    }
  }

  @override
  Future<Result<AuthTokens>> refreshTokens(String refreshToken) async {
    try {
      final tokens = await _apiDatasource.refreshTokens(refreshToken);
      
      // Store new tokens
      await _storageDatasource.storeTokens(
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresAt: tokens.expiresAt,
      );
      
      return Success(tokens);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        return const Error(TokenExpiredFailure());
      }
      return Error(NetworkFailure(e.message ?? 'Network error'));
    } catch (e) {
      return Error(AuthenticationFailure(e.toString()));
    }
  }
}
