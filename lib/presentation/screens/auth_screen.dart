import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../core/constants/app_constants.dart';
import '../../core/validators/callsign_validator.dart';
import '../../data/services/biometric_service.dart';
import '../../data/services/lockout_manager.dart';

/// Authentication screen with callsign and PIN
/// Requirements: 2.1, 2.2, 2.4, 2.5
class AuthScreen extends StatefulWidget {
  final void Function(String callsign)? onAuthenticated;

  const AuthScreen({super.key, this.onAuthenticated});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _callsignController = TextEditingController();
  final _pinController = TextEditingController();
  final _lockoutManager = LockoutManager();
  final _biometricService = BiometricService();

  bool _isLoading = false;
  bool _obscurePin = true;
  String? _errorMessage;
  bool _biometricAvailable = false;

  @override
  void initState() {
    super.initState();
    _checkBiometric();
  }

  Future<void> _checkBiometric() async {
    final available = await _biometricService.isAvailable();
    if (mounted) {
      setState(() => _biometricAvailable = available);
    }
  }

  @override
  void dispose() {
    _callsignController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _authenticate() async {
    if (_lockoutManager.isLockedOut) {
      final remaining = _lockoutManager.remainingLockoutDuration;
      setState(() {
        _errorMessage = 'Too many attempts. Try again in ${remaining.inSeconds}s';
      });
      return;
    }

    final callsign = _callsignController.text.trim();
    final pin = _pinController.text;

    final callsignResult = CallsignValidator.validate(callsign);
    if (!callsignResult.isValid) {
      setState(() => _errorMessage = callsignResult.error);
      return;
    }

    if (pin.length < 4 || pin.length > 6 || !RegExp(r'^\d+$').hasMatch(pin)) {
      setState(() => _errorMessage = 'PIN must be 4-6 digits');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    await Future.delayed(const Duration(milliseconds: 500));

    _lockoutManager.reset();

    if (mounted) {
      setState(() => _isLoading = false);
      widget.onAuthenticated?.call(callsign);
    }
  }

  Future<void> _authenticateWithBiometric() async {
    if (!_biometricAvailable) return;

    final success = await _biometricService.authenticate(
      reason: 'Authenticate to access MKR Messenger',
    );

    if (success && mounted) {
      widget.onAuthenticated?.call('user');
    }
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 60),
              _buildLogo(),
              const SizedBox(height: 48),
              _buildCallsignField(),
              const SizedBox(height: 16),
              _buildPinField(),
              if (_errorMessage != null) ...[
                const SizedBox(height: 12),
                _buildError(),
              ],
              const SizedBox(height: 24),
              _buildLoginButton(),
              if (_biometricAvailable) ...[
                const SizedBox(height: 16),
                _buildBiometricButton(),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLogo() {
    return Column(
      children: [
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            color: CupertinoColors.systemBlue.withOpacity(0.1),
            borderRadius: BorderRadius.circular(20),
          ),
          child: const Icon(
            CupertinoIcons.shield_lefthalf_fill,
            size: 48,
            color: CupertinoColors.systemBlue,
          ),
        ),
        const SizedBox(height: 16),
        const Text(
          AppConstants.appName,
          style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 4),
        const Text(
          'Secure. Private. Protected.',
          style: TextStyle(fontSize: 14, color: CupertinoColors.systemGrey),
        ),
      ],
    );
  }

  Widget _buildCallsignField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Callsign', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _callsignController,
          placeholder: 'Enter your callsign',
          prefix: const Padding(
            padding: EdgeInsets.only(left: 12),
            child: Text('@', style: TextStyle(color: CupertinoColors.systemGrey)),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: CupertinoColors.systemGrey6,
            borderRadius: BorderRadius.circular(10),
          ),
          autocorrect: false,
          textInputAction: TextInputAction.next,
        ),
      ],
    );
  }

  Widget _buildPinField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('PIN', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _pinController,
          placeholder: 'Enter your PIN',
          obscureText: _obscurePin,
          keyboardType: TextInputType.number,
          inputFormatters: [
            FilteringTextInputFormatter.digitsOnly,
            LengthLimitingTextInputFormatter(6),
          ],
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: CupertinoColors.systemGrey6,
            borderRadius: BorderRadius.circular(10),
          ),
          suffix: CupertinoButton(
            padding: const EdgeInsets.only(right: 8),
            onPressed: () => setState(() => _obscurePin = !_obscurePin),
            child: Icon(
              _obscurePin ? CupertinoIcons.eye : CupertinoIcons.eye_slash,
              color: CupertinoColors.systemGrey,
              size: 20,
            ),
          ),
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _authenticate(),
        ),
      ],
    );
  }

  Widget _buildError() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: CupertinoColors.systemRed.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          const Icon(CupertinoIcons.exclamationmark_circle, color: CupertinoColors.systemRed, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(_errorMessage!, style: const TextStyle(color: CupertinoColors.systemRed, fontSize: 13)),
          ),
        ],
      ),
    );
  }

  Widget _buildLoginButton() {
    return CupertinoButton.filled(
      onPressed: _isLoading ? null : _authenticate,
      child: _isLoading
          ? const CupertinoActivityIndicator(color: CupertinoColors.white)
          : const Text('Login'),
    );
  }

  Widget _buildBiometricButton() {
    return CupertinoButton(
      onPressed: _authenticateWithBiometric,
      child: const Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(CupertinoIcons.person_crop_circle),
          SizedBox(width: 8),
          Text('Use Face ID / Touch ID'),
        ],
      ),
    );
  }
}
