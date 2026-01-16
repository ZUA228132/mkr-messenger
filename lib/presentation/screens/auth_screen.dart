import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../core/constants/app_constants.dart';
import '../../core/validators/callsign_validator.dart';
import '../../data/datasources/api_client.dart';
import '../../data/datasources/secure_local_storage.dart';
import '../../data/repositories/remote_auth_repository.dart';
import '../../data/services/biometric_service.dart';

/// Режим авторизации
enum AuthMode { login, register }

/// Экран авторизации MKR с liquid glass дизайном
class AuthScreen extends StatefulWidget {
  final void Function(String userId)? onAuthenticated;

  const AuthScreen({super.key, this.onAuthenticated});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> with SingleTickerProviderStateMixin {
  // Controllers
  final _callsignController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _displayNameController = TextEditingController();

  // Services
  late final RemoteAuthRepository _authRepository;
  final _biometricService = BiometricService();

  // State
  AuthMode _authMode = AuthMode.login;
  bool _isLoading = false;
  bool _obscurePassword = true;
  String? _errorMessage;
  bool _biometricAvailable = false;

  // Animation
  late AnimationController _animController;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _initializeRepository();
    _checkBiometric();
    _checkExistingSession();
    
    _animController = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );
    _fadeAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animController, curve: Curves.easeOut),
    );
    _animController.forward();
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
    _callsignController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _displayNameController.dispose();
    _animController.dispose();
    super.dispose();
  }

  void _toggleAuthMode() {
    setState(() {
      _authMode = _authMode == AuthMode.login ? AuthMode.register : AuthMode.login;
      _errorMessage = null;
      _passwordController.clear();
      _confirmPasswordController.clear();
    });
  }

  Future<void> _handleSubmit() async {
    if (_authMode == AuthMode.login) {
      await _login();
    } else {
      await _register();
    }
  }

  Future<void> _login() async {
    final callsign = _callsignController.text.trim().toLowerCase();
    final password = _passwordController.text;

    final isEmail = callsign.contains('@');
    if (!isEmail) {
      final validation = CallsignValidator.validate(callsign);
      if (!validation.isValid) {
        setState(() => _errorMessage = validation.errorMessage ?? 'Неверный позывной');
        return;
      }
    }

    if (password.isEmpty || password.length < 6) {
      setState(() => _errorMessage = 'Пароль должен быть минимум 6 символов');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await _authRepository.loginSimple(
      callsign: callsign,
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
          if (error.statusCode == 429) {
            _errorMessage = 'Слишком много попыток. Попробуйте через 5 минут.';
          } else if (error.statusCode == 401) {
            _errorMessage = 'Неверный позывной или пароль';
          } else {
            _errorMessage = error.message;
          }
        });
      },
    );
  }

  Future<void> _register() async {
    final callsign = _callsignController.text.trim().toLowerCase();
    final password = _passwordController.text;
    final confirmPassword = _confirmPasswordController.text;
    final displayName = _displayNameController.text.trim();

    final validation = CallsignValidator.validate(callsign);
    if (!validation.isValid) {
      setState(() => _errorMessage = validation.errorMessage ?? 'Неверный позывной');
      return;
    }

    if (displayName.isEmpty) {
      setState(() => _errorMessage = 'Введите ваше имя');
      return;
    }

    if (password.isEmpty || password.length < 6) {
      setState(() => _errorMessage = 'Пароль должен быть минимум 6 символов');
      return;
    }

    if (password != confirmPassword) {
      setState(() => _errorMessage = 'Пароли не совпадают');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await _authRepository.registerSimple(
      callsign: callsign,
      displayName: displayName,
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
          if (error.statusCode == 409) {
            _errorMessage = 'Этот позывной уже занят';
          } else {
            _errorMessage = error.message;
          }
        });
      },
    );
  }

  Future<void> _authenticateWithBiometric() async {
    if (!_biometricAvailable) return;

    final result = await _biometricService.authenticate(
      reason: 'Войти в MKR Messenger',
    );

    if (result == BiometricResult.success && mounted) {
      final userId = await _authRepository.getCurrentUserId();
      if (userId != null) {
        await _authRepository.initializeFromStorage();
        widget.onAuthenticated?.call(userId);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Color(0xFF0A0A1A),
              Color(0xFF1A1A3E),
              Color(0xFF0F0F2A),
            ],
          ),
        ),
        child: SafeArea(
          child: FadeTransition(
            opacity: _fadeAnim,
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 48),
                  _buildLogo(),
                  const SizedBox(height: 48),
                  _buildGlassCard(),
                  if (_errorMessage != null) ...[
                    const SizedBox(height: 16),
                    _buildError(),
                  ],
                  const SizedBox(height: 24),
                  _buildSubmitButton(),
                  const SizedBox(height: 16),
                  _buildToggleButton(),
                  if (_biometricAvailable && _authMode == AuthMode.login) ...[
                    const SizedBox(height: 16),
                    _buildBiometricButton(),
                  ],
                  const SizedBox(height: 48),
                  _buildFooter(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLogo() {
    return Column(
      children: [
        // Glowing MKR text
        ShaderMask(
          shaderCallback: (bounds) => const LinearGradient(
            colors: [
              Color(0xFF00D4FF),
              Color(0xFF7B68EE),
              Color(0xFF00D4FF),
            ],
          ).createShader(bounds),
          child: const Text(
            'MKR',
            style: TextStyle(
              fontSize: 56,
              fontWeight: FontWeight.w900,
              color: Colors.white,
              letterSpacing: 12,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          _authMode == AuthMode.login ? 'Вход в систему' : 'Регистрация',
          style: TextStyle(
            fontSize: 16,
            color: Colors.white.withOpacity(0.6),
            letterSpacing: 2,
          ),
        ),
      ],
    );
  }

  Widget _buildGlassCard() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(24),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Colors.white.withOpacity(0.15),
                Colors.white.withOpacity(0.05),
              ],
            ),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(
              color: Colors.white.withOpacity(0.2),
              width: 1,
            ),
          ),
          child: Column(
            children: [
              _buildTextField(
                controller: _callsignController,
                placeholder: _authMode == AuthMode.login 
                    ? 'позывной или email' 
                    : 'ваш_позывной',
                label: _authMode == AuthMode.login ? 'Позывной или Email' : 'Позывной',
                icon: CupertinoIcons.person_badge_plus,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[a-zA-Z0-9@._\-]')),
                  LengthLimitingTextInputFormatter(50),
                ],
              ),
              if (_authMode == AuthMode.register) ...[
                const SizedBox(height: 20),
                _buildTextField(
                  controller: _displayNameController,
                  placeholder: 'Как вас называть?',
                  label: 'Ваше имя',
                  icon: CupertinoIcons.person,
                ),
              ],
              const SizedBox(height: 20),
              _buildTextField(
                controller: _passwordController,
                placeholder: _authMode == AuthMode.login 
                    ? 'Введите пароль' 
                    : 'Минимум 6 символов',
                label: 'Пароль',
                icon: CupertinoIcons.lock,
                isPassword: true,
              ),
              if (_authMode == AuthMode.register) ...[
                const SizedBox(height: 20),
                _buildTextField(
                  controller: _confirmPasswordController,
                  placeholder: 'Повторите пароль',
                  label: 'Подтвердите пароль',
                  icon: CupertinoIcons.lock_rotation,
                  isPassword: true,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String placeholder,
    required String label,
    required IconData icon,
    bool isPassword = false,
    List<TextInputFormatter>? inputFormatters,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: Colors.white.withOpacity(0.7),
            letterSpacing: 0.5,
          ),
        ),
        const SizedBox(height: 8),
        Container(
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.08),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: Colors.white.withOpacity(0.1),
            ),
          ),
          child: CupertinoTextField(
            controller: controller,
            placeholder: placeholder,
            obscureText: isPassword && _obscurePassword,
            prefix: Padding(
              padding: const EdgeInsets.only(left: 14),
              child: Icon(
                icon,
                color: const Color(0xFF00D4FF),
                size: 20,
              ),
            ),
            suffix: isPassword
                ? CupertinoButton(
                    padding: const EdgeInsets.only(right: 8),
                    onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                    child: Icon(
                      _obscurePassword ? CupertinoIcons.eye : CupertinoIcons.eye_slash,
                      color: Colors.white.withOpacity(0.4),
                      size: 20,
                    ),
                  )
                : null,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 16),
            decoration: const BoxDecoration(),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 16,
            ),
            placeholderStyle: TextStyle(
              color: Colors.white.withOpacity(0.3),
              fontSize: 16,
            ),
            inputFormatters: inputFormatters,
          ),
        ),
      ],
    );
  }

  Widget _buildError() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: CupertinoColors.systemRed.withOpacity(0.15),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: CupertinoColors.systemRed.withOpacity(0.3),
            ),
          ),
          child: Row(
            children: [
              const Icon(
                CupertinoIcons.exclamationmark_circle_fill,
                color: CupertinoColors.systemRed,
                size: 20,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  _errorMessage!,
                  style: const TextStyle(
                    color: CupertinoColors.systemRed,
                    fontSize: 14,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSubmitButton() {
    return Container(
      height: 56,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          colors: [
            Color(0xFF00D4FF),
            Color(0xFF7B68EE),
          ],
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF00D4FF).withOpacity(0.4),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        borderRadius: BorderRadius.circular(16),
        onPressed: _isLoading ? null : _handleSubmit,
        child: _isLoading
            ? const CupertinoActivityIndicator(color: Colors.white)
            : Text(
                _authMode == AuthMode.login ? 'Войти' : 'Создать аккаунт',
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
      ),
    );
  }

  Widget _buildToggleButton() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          _authMode == AuthMode.login ? 'Нет аккаунта?' : 'Уже есть аккаунт?',
          style: TextStyle(
            color: Colors.white.withOpacity(0.6),
            fontSize: 15,
          ),
        ),
        CupertinoButton(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          onPressed: _toggleAuthMode,
          child: Text(
            _authMode == AuthMode.login ? 'Создать' : 'Войти',
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              color: Color(0xFF00D4FF),
              fontSize: 15,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildBiometricButton() {
    return CupertinoButton(
      onPressed: _authenticateWithBiometric,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            CupertinoIcons.person_crop_circle,
            color: Colors.white.withOpacity(0.7),
          ),
          const SizedBox(width: 8),
          Text(
            'Face ID / Touch ID',
            style: TextStyle(
              color: Colors.white.withOpacity(0.7),
              fontSize: 15,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFooter() {
    return Column(
      children: [
        Text(
          'MKR by Makarov',
          style: TextStyle(
            color: Colors.white.withOpacity(0.4),
            fontSize: 13,
            letterSpacing: 1,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Защищённый мессенджер',
          style: TextStyle(
            color: Colors.white.withOpacity(0.25),
            fontSize: 11,
          ),
        ),
      ],
    );
  }
}

// Colors class for compatibility
class Colors {
  static const Color white = Color(0xFFFFFFFF);
  static const Color black = Color(0xFF000000);
  static const Color transparent = Color(0x00000000);
}
