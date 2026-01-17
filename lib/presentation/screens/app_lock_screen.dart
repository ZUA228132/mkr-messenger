import 'dart:async';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// App Lock Screen with PIN and Biometric authentication
class AppLockScreen extends StatefulWidget {
  final VoidCallback? onUnlocked;
  final bool enableSetup;
  final VoidCallback? onSetupComplete;

  const AppLockScreen({
    super.key,
    this.onUnlocked,
    this.enableSetup = false,
    this.onSetupComplete,
  });

  @override
  State<AppLockScreen> createState() => _AppLockScreenState();
}

class _AppLockScreenState extends State<AppLockScreen> {
  static const _storage = FlutterSecureStorage();
  static const _pinKey = 'app_lock_pin';
  static const _biometricKey = 'app_lock_biometric_enabled';

  final _pinController = TextEditingController();
  final _localAuth = LocalAuthentication();

  String? _storedPin;
  List<int> _enteredPin = [];
  bool _isBiometricAvailable = false;
  bool _isBiometricEnabled = false;
  bool _showError = false;
  int _failedAttempts = 0;
  Timer? _errorTimer;
  Timer? _blockTimer;
  bool _isBlocked = false;
  int _blockTimeRemaining = 0;

  // Setup mode state
  bool _isConfirmingPin = false;
  String? _newPin;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  @override
  void dispose() {
    _pinController.dispose();
    _errorTimer?.cancel();
    _blockTimer?.cancel();
    super.dispose();
  }

  Future<void> _initialize() async {
    // Check if PIN is set
    _storedPin = await _storage.read(key: _pinKey);

    // Check biometric availability
    final isAvailable = await _localAuth.canCheckBiometrics;
    final isEnabled = await _storage.read(key: _biometricKey);

    if (mounted) {
      setState(() {
        _isBiometricAvailable = isAvailable;
        _isBiometricEnabled = isEnabled == 'true';
      });
    }

    // If biometric is enabled and available, try biometric auth first
    if (_isBiometricEnabled && _isBiometricAvailable && !widget.enableSetup) {
      _tryBiometricAuth();
    }
  }

  Future<void> _tryBiometricAuth() async {
    try {
      final didAuthenticate = await _localAuth.authenticate(
        localizedReason: 'Разблокируйте MKR Messenger',
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
        ),
      );

      if (didAuthenticate && mounted) {
        widget.onUnlocked?.call();
      }
    } catch (e) {
      // Biometric failed, show PIN pad
    }
  }

  void _onPinNumber(int number) {
    if (_isBlocked) return;

    setState(() {
      _showError = false;
      _enteredPin.add(number);
    });

    if (_enteredPin.length == 4) {
      _verifyPin();
    }

    // Haptic feedback
    HapticFeedback.lightImpact();
  }

  void _onPinDelete() {
    if (_isBlocked || _enteredPin.isEmpty) return;

    setState(() {
      _enteredPin.removeLast();
      _showError = false;
    });

    HapticFeedback.lightImpact();
  }

  Future<void> _verifyPin() async {
    final enteredPin = _enteredPin.join();

    if (widget.enableSetup) {
      if (!_isConfirmingPin) {
        // First entry - save and ask to confirm
        setState(() {
          _newPin = enteredPin;
          _isConfirmingPin = true;
        });
        // Clear after a short delay for UX
        await Future.delayed(const Duration(milliseconds: 300));
        if (mounted) {
          setState(() => _enteredPin = []);
        }
      } else {
        // Confirming - check if matches
        if (enteredPin == _newPin) {
          // Save PIN
          await _storage.write(key: _pinKey, value: enteredPin);
          widget.onSetupComplete?.call();
        } else {
          _showPinError();
          setState(() {
            _isConfirmingPin = false;
            _newPin = null;
          });
          // Clear after delay
          await Future.delayed(const Duration(milliseconds: 500), () {
            if (mounted) {
              setState(() => _enteredPin = []);
            }
          });
        }
      }
    } else {
      // Verify mode
      if (enteredPin == _storedPin) {
        // Success!
        _failedAttempts = 0;
        widget.onUnlocked?.call();
      } else {
        _failedAttempts++;
        _showPinError();

        // Block after 3 failed attempts
        if (_failedAttempts >= 3) {
          _blockForTime(30);
        } else {
          // Clear PIN after delay
          await Future.delayed(const Duration(milliseconds: 500), () {
            if (mounted) {
              setState(() => _enteredPin = []);
            }
          });
        }
      }
    }
  }

  void _showPinError() {
    setState(() => _showError = true);
    HapticFeedback.heavyImpact();

    _errorTimer?.cancel();
    _errorTimer = Timer(const Duration(seconds: 1), () {
      if (mounted) {
        setState(() => _showError = false);
      }
    });
  }

  void _blockForTime(int seconds) {
    setState(() {
      _isBlocked = true;
      _blockTimeRemaining = seconds;
      _enteredPin = [];
    });

    _blockTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_blockTimeRemaining > 1) {
        setState(() => _blockTimeRemaining--);
      } else {
        timer.cancel();
        setState(() {
          _isBlocked = false;
          _failedAttempts = 0;
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.enableSetup
        ? (_isConfirmingPin ? 'Подтвердите PIN-код' : 'Придумайте PIN-код')
        : 'Введите PIN-код';

    final subtitle = widget.enableSetup
        ? '4 цифры для блокировки приложения'
        : 'Для разблокировки MKR Messenger';

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: CupertinoPageScaffold(
        backgroundColor: CupertinoColors.systemBackground.resolveFrom(context),
        child: SafeArea(
          child: Column(
            children: [
              const SizedBox(height: 60),
              // App Icon
              Center(
                child: Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Center(
                    child: Text(
                      'M',
                      style: TextStyle(
                        fontSize: 36,
                        fontWeight: FontWeight.w800,
                        color: CupertinoColors.white,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 32),
              // Title
              Text(
                title,
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 15,
                  color: CupertinoColors.secondaryLabel.resolveFrom(context),
                ),
              ),
              const SizedBox(height: 40),
              // PIN Dots
              _buildPinDots(),
              const SizedBox(height: 40),
              // Number Pad
              Expanded(
                child: _buildNumberPad(),
              ),
              const SizedBox(height: 20),
              // Biometric Button
              if (_isBiometricAvailable && !widget.enableSetup && !_isBlocked)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: CupertinoButton(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    color: CupertinoColors.systemGrey5.resolveFrom(context),
                    borderRadius: BorderRadius.circular(12),
                    onPressed: _tryBiometricAuth,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          CupertinoIcons.hand_draw_fill,
                          color: CupertinoColors.systemGrey.resolveFrom(context),
                          size: 20,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Face ID / Touch ID',
                          style: TextStyle(
                            color: CupertinoColors.systemGrey.resolveFrom(context),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              const SizedBox(height: 20),
              // Blocked message
              if (_isBlocked)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Text(
                    'Попробуйте через $_blockTimeRemaining сек.',
                    style: const TextStyle(
                      color: CupertinoColors.systemRed,
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPinDots() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(4, (index) {
        final isFilled = index < _enteredPin.length;
        final isError = _showError && index == _enteredPin.length - 1 && _enteredPin.length == 4;

        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          margin: const EdgeInsets.symmetric(horizontal: 8),
          width: 16,
          height: 16,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isError
                ? CupertinoColors.systemRed
                : isFilled
                    ? CupertinoColors.activeBlue
                    : CupertinoColors.systemGrey5.resolveFrom(context),
            border: isFilled
                ? null
                : Border.all(
                    color: CupertinoColors.systemGrey4.resolveFrom(context),
                    width: 1,
                  ),
          ),
        );
      }),
    );
  }

  Widget _buildNumberPad() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 40),
      child: Column(
        children: [
          // Rows 1-3
          for (int row = 0; row < 3; row++)
            Expanded(
              child: Row(
                children: List.generate(3, (col) {
                  final number = row * 3 + col + 1;
                  return Expanded(
                    child: _buildNumberButton(number),
                  );
                }),
              ),
            ),
          const SizedBox(height: 12),
          // Row 4
          Expanded(
            child: Row(
              children: [
                // Biometric or empty
                Expanded(
                  child: _buildNumberButton(0),
                ),
                const SizedBox(width: 12),
                // Clear/Delete button
                Expanded(
                  child: _buildDeleteButton(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNumberButton(int number) {
    return GestureDetector(
      onTap: () => _onPinNumber(number),
      child: Container(
        decoration: BoxDecoration(
          color: CupertinoColors.systemGrey5.resolveFrom(context),
          shape: BoxShape.circle,
        ),
        child: Center(
          child: Text(
            number.toString(),
            style: const TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDeleteButton() {
    return GestureDetector(
      onTap: _onPinDelete,
      onLongPress: () {
        // Clear all
        if (!_isBlocked && _enteredPin.isNotEmpty) {
          setState(() => _enteredPin.clear());
        }
      },
      child: Container(
        decoration: BoxDecoration(
          color: CupertinoColors.systemGrey5.resolveFrom(context),
          shape: BoxShape.circle,
        ),
        child: Center(
          child: Icon(
            CupertinoIcons.delete_left_fill,
            size: 28,
            color: CupertinoColors.systemGrey.resolveFrom(context),
          ),
        ),
      ),
    );
  }
}

/// Service to manage app lock state
class AppLockService {
  static const _storage = FlutterSecureStorage();
  static const _pinKey = 'app_lock_pin';
  static const _biometricKey = 'app_lock_biometric_enabled';
  static const _lockEnabledKey = 'app_lock_enabled';

  static Future<bool> isLockEnabled() async {
    final enabled = await _storage.read(key: _lockEnabledKey);
    return enabled == 'true';
  }

  static Future<void> setLockEnabled(bool enabled) async {
    if (enabled) {
      // Check if PIN is set
      final pin = await _storage.read(key: _pinKey);
      if (pin == null || pin.isEmpty) {
        throw Exception('PIN-код не установлен. Сначала создайте PIN-код.');
      }
    }
    await _storage.write(key: _lockEnabledKey, value: enabled ? 'true' : 'false');
  }

  static Future<bool> hasPin() async {
    final pin = await _storage.read(key: _pinKey);
    return pin != null && pin.isNotEmpty;
  }

  static Future<void> clearPin() async {
    await _storage.delete(key: _pinKey);
    await _storage.delete(key: _biometricKey);
    await _storage.delete(key: _lockEnabledKey);
  }

  static Future<bool> isBiometricEnabled() async {
    final enabled = await _storage.read(key: _biometricKey);
    return enabled == 'true';
  }

  static Future<void> setBiometricEnabled(bool enabled) async {
    await _storage.write(key: _biometricKey, value: enabled ? 'true' : 'false');
  }

  static Future<bool> verifyAndUnlock() async {
    final pin = await _storage.read(key: _pinKey);
    return pin != null && pin.isNotEmpty;
  }
}
