import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../core/constants/app_constants.dart';
import '../../data/datasources/api_client.dart';
import '../../data/datasources/secure_local_storage.dart';
import '../../data/repositories/remote_auth_repository.dart';
import '../../data/services/biometric_service.dart';

/// Authentication mode
enum AuthMode { login, register }

/// Authentication screen with email login/registration
/// Requirements: 2.1-2.7 - Email registration, verification, login
class AuthScreen extends StatefulWidget {
  final void Function(String userId)? onAuthenticated;

  const AuthScreen({super.key, this.onAuthenticated});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  // Controllers
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _usernameController = TextEditingController();
  final _displayNameController = TextEditingController();
  final _verificationCodeController = TextEditingController();

  // Services
  late final RemoteAuthRepository _authRepository;
  final _biometricService = BiometricService();

  // State
  AuthMode _authMode = AuthMode.login;
  bool _isLoading = false;
  bool _obscurePassword = true;
  String? _errorMessage;
  bool _biometricAvailable = false;
  bool _showVerification = false;
  String? _pendingEmail;
  Duration? _lockoutDuration;

  @override
  void initState() {
    super.initState();
    _initializeRepository();
    _checkBiometric();
    _checkExistingSession();
  }

  void _initializeRepository() {
    final apiClient = ApiClient();
    final storage = SecureLocalStorage();
    _authRepository = RemoteAuthRepository(
      apiClient: apiClient,
      storage: storage,
    );
  }

  Future<void> _checkBiometric() async {
    final available = await _biometricService.isAvailable();
    if (mounted) setState(() => _biometricAvailable = available);
  }

  Future<void> _checkExistingSession() async {
    final isAuthenticated = await _authRepository.isAuthenticated();
    if (isAuthenticated && mounted) {
      final userId = await _authRepository.getCurrentUserId();
      if (userId != null) {
        await _authRepository.initializeFromStorage();
        widget.onAuthenticated?.call(userId);
      }
    }
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _usernameController.dispose();
    _displayNameController.dispose();
    _verificationCodeController.dispose();
    super.dispose();
  }

  void _toggleAuthMode() {
    setState(() {
      _authMode = _authMode == AuthMode.login ? AuthMode.register : AuthMode.login;
      _errorMessage = null;
      _showVerification = false;
    });
  }

  Future<void> _handleSubmit() async {
    if (_showVerification) {
      await _verifyEmail();
    } else if (_authMode == AuthMode.login) {
      await _login();
    } else {
      await _register();
    }
  }

  /// Requirements: 2.5, 2.6, 2.7 - Login with email
  Future<void> _login() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text;

    if (email.isEmpty || !_isValidEmail(email)) {
      setState(() => _errorMessage = 'Please enter a valid email');
      return;
    }

    if (password.isEmpty || password.length < 6) {
      setState(() => _errorMessage = 'Password must be at least 6 characters');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await _authRepository.loginEmail(
      email: email,
      password: password,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (authResponse) {
        setState(() => _isLoading = false);
        widget.onAuthenticated?.call(authResponse.userId);
      },
      onFailure: (error) {
        setState(() {
          _isLoading = false;
          // Requirements: 2.7 - Display remaining lockout time
          if (error.statusCode == 429) {
            _lockoutDuration = const Duration(minutes: 5);
            _errorMessage = 'Too many attempts. Try again in 5 minutes.';
          } else {
            _errorMessage = error.message;
          }
        });
      },
    );
  }


  /// Requirements: 2.1 - Register with email
  Future<void> _register() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text;
    final username = _usernameController.text.trim();
    final displayName = _displayNameController.text.trim();

    if (email.isEmpty || !_isValidEmail(email)) {
      setState(() => _errorMessage = 'Please enter a valid email');
      return;
    }

    if (password.isEmpty || password.length < 6) {
      setState(() => _errorMessage = 'Password must be at least 6 characters');
      return;
    }

    if (username.isEmpty || username.length < 3) {
      setState(() => _errorMessage = 'Username must be at least 3 characters');
      return;
    }

    if (displayName.isEmpty) {
      setState(() => _errorMessage = 'Please enter your display name');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await _authRepository.registerEmail(
      email: email,
      password: password,
      username: username,
      displayName: displayName,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (_) {
        // Requirements: 2.2 - Display verification code input screen
        setState(() {
          _isLoading = false;
          _showVerification = true;
          _pendingEmail = email;
        });
      },
      onFailure: (error) {
        setState(() {
          _isLoading = false;
          _errorMessage = error.message;
        });
      },
    );
  }

  /// Requirements: 2.3, 2.4 - Verify email with code
  Future<void> _verifyEmail() async {
    final code = _verificationCodeController.text.trim();

    if (code.isEmpty || code.length != 6) {
      setState(() => _errorMessage = 'Please enter the 6-digit code');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await _authRepository.verifyEmail(
      email: _pendingEmail!,
      code: code,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (authResponse) {
        setState(() => _isLoading = false);
        widget.onAuthenticated?.call(authResponse.userId);
      },
      onFailure: (error) {
        setState(() {
          _isLoading = false;
          _errorMessage = error.message;
        });
      },
    );
  }

  Future<void> _authenticateWithBiometric() async {
    if (!_biometricAvailable) return;

    final result = await _biometricService.authenticate(
      reason: 'Authenticate to access MKR Messenger',
    );

    if (result == BiometricResult.success && mounted) {
      final userId = await _authRepository.getCurrentUserId();
      if (userId != null) {
        await _authRepository.initializeFromStorage();
        widget.onAuthenticated?.call(userId);
      }
    }
  }

  bool _isValidEmail(String email) {
    return RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$').hasMatch(email);
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
              const SizedBox(height: 40),
              _buildLogo(),
              const SizedBox(height: 40),
              if (_showVerification)
                _buildVerificationForm()
              else
                _buildAuthForm(),
              if (_errorMessage != null) ...[
                const SizedBox(height: 12),
                _buildError(),
              ],
              const SizedBox(height: 24),
              _buildSubmitButton(),
              if (!_showVerification) ...[
                const SizedBox(height: 16),
                _buildToggleButton(),
                if (_biometricAvailable && _authMode == AuthMode.login) ...[
                  const SizedBox(height: 16),
                  _buildBiometricButton(),
                ],
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
        Text(
          _showVerification
              ? 'Enter verification code'
              : (_authMode == AuthMode.login ? 'Welcome back' : 'Create account'),
          style: const TextStyle(fontSize: 14, color: CupertinoColors.systemGrey),
        ),
      ],
    );
  }


  Widget _buildAuthForm() {
    return Column(
      children: [
        _buildEmailField(),
        const SizedBox(height: 16),
        _buildPasswordField(),
        if (_authMode == AuthMode.register) ...[
          const SizedBox(height: 16),
          _buildUsernameField(),
          const SizedBox(height: 16),
          _buildDisplayNameField(),
        ],
      ],
    );
  }

  Widget _buildVerificationForm() {
    return Column(
      children: [
        Text(
          'We sent a verification code to $_pendingEmail',
          textAlign: TextAlign.center,
          style: const TextStyle(color: CupertinoColors.systemGrey),
        ),
        const SizedBox(height: 24),
        _buildVerificationCodeField(),
        const SizedBox(height: 16),
        CupertinoButton(
          onPressed: () {
            setState(() {
              _showVerification = false;
              _verificationCodeController.clear();
            });
          },
          child: const Text('Back to registration'),
        ),
      ],
    );
  }

  Widget _buildEmailField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Email',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _emailController,
          placeholder: 'Enter your email',
          keyboardType: TextInputType.emailAddress,
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

  Widget _buildPasswordField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Password',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _passwordController,
          placeholder: 'Enter your password',
          obscureText: _obscurePassword,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: CupertinoColors.systemGrey6,
            borderRadius: BorderRadius.circular(10),
          ),
          suffix: CupertinoButton(
            padding: const EdgeInsets.only(right: 8),
            onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
            child: Icon(
              _obscurePassword ? CupertinoIcons.eye : CupertinoIcons.eye_slash,
              color: CupertinoColors.systemGrey,
              size: 20,
            ),
          ),
          textInputAction: _authMode == AuthMode.login
              ? TextInputAction.done
              : TextInputAction.next,
          onSubmitted: _authMode == AuthMode.login ? (_) => _handleSubmit() : null,
        ),
      ],
    );
  }

  Widget _buildUsernameField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Username',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _usernameController,
          placeholder: 'Choose a username',
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

  Widget _buildDisplayNameField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Display Name',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _displayNameController,
          placeholder: 'Your display name',
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: CupertinoColors.systemGrey6,
            borderRadius: BorderRadius.circular(10),
          ),
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _handleSubmit(),
        ),
      ],
    );
  }

  Widget _buildVerificationCodeField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Verification Code',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        CupertinoTextField(
          controller: _verificationCodeController,
          placeholder: 'Enter 6-digit code',
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
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 24, letterSpacing: 8),
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _handleSubmit(),
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
          const Icon(
            CupertinoIcons.exclamationmark_circle,
            color: CupertinoColors.systemRed,
            size: 18,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              _errorMessage!,
              style: const TextStyle(
                color: CupertinoColors.systemRed,
                fontSize: 13,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSubmitButton() {
    String buttonText;
    if (_showVerification) {
      buttonText = 'Verify';
    } else if (_authMode == AuthMode.login) {
      buttonText = 'Login';
    } else {
      buttonText = 'Register';
    }

    return CupertinoButton.filled(
      onPressed: _isLoading ? null : _handleSubmit,
      child: _isLoading
          ? const CupertinoActivityIndicator(color: CupertinoColors.white)
          : Text(buttonText),
    );
  }

  Widget _buildToggleButton() {
    return CupertinoButton(
      onPressed: _toggleAuthMode,
      child: Text(
        _authMode == AuthMode.login
            ? "Don't have an account? Register"
            : 'Already have an account? Login',
      ),
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
