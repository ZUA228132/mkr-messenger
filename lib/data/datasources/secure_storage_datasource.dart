import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Data source for secure storage operations
class SecureStorageDatasource {
  final FlutterSecureStorage _storage;

  SecureStorageDatasource({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage(
          aOptions: AndroidOptions(encryptedSharedPreferences: true),
          iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
        );

  static const String _accessTokenKey = 'jwt_token';
  static const String _refreshTokenKey = 'refresh_token';
  static const String _tokenExpiryKey = 'token_expiry';

  /// Store a value securely
  Future<void> write(String key, String value) async {
    await _storage.write(key: key, value: value);
  }

  /// Read a value from secure storage
  Future<String?> read(String key) async {
    return await _storage.read(key: key);
  }

  /// Delete a value from secure storage
  Future<void> delete(String key) async {
    await _storage.delete(key: key);
  }

  /// Delete all values from secure storage
  Future<void> deleteAll() async {
    await _storage.deleteAll();
  }

  /// Store authentication tokens
  Future<void> storeTokens({
    required String accessToken,
    required String refreshToken,
    required DateTime expiresAt,
  }) async {
    await Future.wait([
      write(_accessTokenKey, accessToken),
      write(_refreshTokenKey, refreshToken),
      write(_tokenExpiryKey, expiresAt.toIso8601String()),
    ]);
  }

  /// Get stored access token
  Future<String?> getAccessToken() => read(_accessTokenKey);

  /// Get stored refresh token
  Future<String?> getRefreshToken() => read(_refreshTokenKey);

  /// Get token expiry date
  Future<DateTime?> getTokenExpiry() async {
    final expiry = await read(_tokenExpiryKey);
    return expiry != null ? DateTime.parse(expiry) : null;
  }

  /// Clear all authentication tokens
  Future<void> clearTokens() async {
    await Future.wait([
      delete(_accessTokenKey),
      delete(_refreshTokenKey),
      delete(_tokenExpiryKey),
    ]);
  }
}
