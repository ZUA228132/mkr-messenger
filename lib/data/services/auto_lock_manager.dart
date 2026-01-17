import 'dart:async';

import 'package:flutter/foundation.dart';

/// Service for auto-locking the app after period of inactivity
class AutoLockManager {
  static const Duration _inactivityTimeout = Duration(minutes: 7);

  Timer? _lockTimer;
  VoidCallback? _onLockCallback;

  /// Initialize auto-lock and start monitoring
  void init({
    required VoidCallback onLock,
  }) {
    _onLockCallback = onLock;
    _startMonitoring();
  }

  /// Call this when user performs any action
  void reportActivity() {
    _resetLockTimer();
  }

  /// Stop monitoring (call when app is backgrounded)
  void stop() {
    _lockTimer?.cancel();
  }

  /// Resume monitoring (call when app is foregrounded)
  void resume() {
    _startMonitoring();
  }

  void _startMonitoring() {
    _resetLockTimer();
  }

  void _resetLockTimer() {
    _lockTimer?.cancel();
    _lockTimer = Timer(_inactivityTimeout, () {
      _onLockCallback?.call();
    });
  }

  /// Dispose resources
  void dispose() {
    stop();
  }
}
