/// Manages progressive lockout for failed authentication attempts
/// 
/// Implements progressive lockout delays:
/// - 5 failures: 30 seconds
/// - 6 failures: 1 minute
/// - 7 failures: 2 minutes
/// - 8 failures: 5 minutes
/// - 9 failures: 15 minutes
/// - 10+ failures: 30 minutes
class LockoutManager {
  int _failedAttempts = 0;
  DateTime? _lockoutUntil;

  /// Number of failed attempts before lockout starts
  static const int lockoutThreshold = 5;

  /// Current number of failed attempts
  int get failedAttempts => _failedAttempts;

  /// Time until lockout ends, null if not locked out
  DateTime? get lockoutUntil => _lockoutUntil;

  /// Whether the account is currently locked out
  bool get isLockedOut {
    if (_lockoutUntil == null) return false;
    if (DateTime.now().isAfter(_lockoutUntil!)) {
      // Lockout has expired
      return false;
    }
    return true;
  }

  /// Remaining lockout duration, or Duration.zero if not locked
  Duration get remainingLockoutDuration {
    if (_lockoutUntil == null) return Duration.zero;
    final remaining = _lockoutUntil!.difference(DateTime.now());
    return remaining.isNegative ? Duration.zero : remaining;
  }

  /// Calculate lockout duration based on number of failed attempts
  /// 
  /// Returns Duration.zero for attempts below threshold
  Duration getLockoutDuration(int attempts) {
    if (attempts < lockoutThreshold) {
      return Duration.zero;
    }

    // Progressive lockout durations
    return switch (attempts) {
      5 => const Duration(seconds: 30),
      6 => const Duration(minutes: 1),
      7 => const Duration(minutes: 2),
      8 => const Duration(minutes: 5),
      9 => const Duration(minutes: 15),
      _ => const Duration(minutes: 30), // 10+ attempts
    };
  }

  /// Record a failed authentication attempt
  /// 
  /// Returns true if the account is now locked out
  bool recordFailure() {
    _failedAttempts++;
    
    final lockoutDuration = getLockoutDuration(_failedAttempts);
    
    if (lockoutDuration > Duration.zero) {
      _lockoutUntil = DateTime.now().add(lockoutDuration);
      return true;
    }
    
    return false;
  }

  /// Reset the lockout state after successful authentication
  void reset() {
    _failedAttempts = 0;
    _lockoutUntil = null;
  }

  /// Check if authentication can be attempted
  /// 
  /// Returns null if allowed, or the remaining lockout duration if locked
  Duration? canAttemptAuth() {
    if (!isLockedOut) return null;
    return remainingLockoutDuration;
  }
}
