import '../../core/utils/result.dart';
import '../entities/auth_tokens.dart';
import '../entities/user.dart';

/// Authentication result containing user and tokens
class AuthResult {
  final User user;
  final AuthTokens tokens;

  const AuthResult({required this.user, required this.tokens});
}

/// Abstract repository for authentication operations
abstract class AuthRepository {
  /// Login with callsign and PIN
  Future<Result<AuthResult>> login(String callsign, String pin);
  
  /// Logout current user
  Future<Result<void>> logout();
  
  /// Validate biometric authentication
  Future<Result<bool>> validateBiometric();
  
  /// Store authentication tokens securely
  Future<Result<void>> storeTokens(AuthTokens tokens);
  
  /// Get stored authentication tokens
  Future<Result<AuthTokens?>> getStoredTokens();
  
  /// Clear stored tokens
  Future<Result<void>> clearTokens();
  
  /// Refresh authentication tokens
  Future<Result<AuthTokens>> refreshTokens(String refreshToken);
}
