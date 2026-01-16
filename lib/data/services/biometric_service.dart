import 'package:local_auth/local_auth.dart';

/// Service for biometric authentication (Face ID / Fingerprint)
class BiometricService {
  final LocalAuthentication _localAuth;

  BiometricService({LocalAuthentication? localAuth})
      : _localAuth = localAuth ?? LocalAuthentication();

  /// Check if biometric authentication is available on this device
  Future<bool> isAvailable() async {
    try {
      final canCheckBiometrics = await _localAuth.canCheckBiometrics;
      final isDeviceSupported = await _localAuth.isDeviceSupported();
      return canCheckBiometrics && isDeviceSupported;
    } catch (e) {
      return false;
    }
  }

  /// Get available biometric types on this device
  Future<List<BiometricType>> getAvailableBiometrics() async {
    try {
      return await _localAuth.getAvailableBiometrics();
    } catch (e) {
      return [];
    }
  }

  /// Check if Face ID is available (iOS)
  Future<bool> hasFaceId() async {
    final biometrics = await getAvailableBiometrics();
    return biometrics.contains(BiometricType.face);
  }

  /// Check if fingerprint is available
  Future<bool> hasFingerprint() async {
    final biometrics = await getAvailableBiometrics();
    return biometrics.contains(BiometricType.fingerprint);
  }

  /// Authenticate using biometrics
  /// 
  /// Returns true if authentication succeeded, false otherwise
  Future<BiometricResult> authenticate({
    String reason = 'Authenticate to access MKR Messenger',
    bool biometricOnly = true,
  }) async {
    try {
      final isAvailable = await this.isAvailable();
      if (!isAvailable) {
        return BiometricResult.notAvailable;
      }

      final didAuthenticate = await _localAuth.authenticate(
        localizedReason: reason,
        options: AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: biometricOnly,
          useErrorDialogs: true,
        ),
      );

      return didAuthenticate 
          ? BiometricResult.success 
          : BiometricResult.failed;
    } catch (e) {
      return BiometricResult.error;
    }
  }

  /// Cancel any ongoing authentication
  Future<bool> cancelAuthentication() async {
    try {
      return await _localAuth.stopAuthentication();
    } catch (e) {
      return false;
    }
  }
}

/// Result of biometric authentication attempt
enum BiometricResult {
  /// Authentication succeeded
  success,
  
  /// Authentication failed (user cancelled or wrong biometric)
  failed,
  
  /// Biometric authentication not available on device
  notAvailable,
  
  /// An error occurred during authentication
  error,
}
