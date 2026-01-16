import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../core/validators/callsign_validator.dart';
import '../../data/datasources/api_client.dart';
import '../../data/datasources/secure_local_storage.dart';
import '../../data/repositories/remote_auth_repository.dart';
import '../../data/services/biometric_service.dart';

enum AuthMode { login, register }

/// Экран авторизации MKR — чистый Apple стиль
class AuthScreen extends StatefulWidget {
  final void Function(String userId)? onAuthenticated;
  const AuthScreen({super.key, this.onAuthenticated});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _callsignController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _displayNameController = TextEditingController();

  late final RemoteAuthRepository _authRepository;
  final _biometricService = BiometricService();

  AuthMode _authMode = AuthMode.login;
  bool _isLoading = false;
  bool _obscurePassword = true;
  String? _errorMessage;
  bool _biometricAvailable = false;

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
    _authRepository = RemoteAuthRepository(apiClient: apiClient, storage: storage);
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
    _authMode == AuthMode.login ? await _login() : await _register();
  }

  Future<void> _login() async {
    final callsign = _callsignController.text.trim().toLowerCase();
    final password = _passwordController.text;

    if (!callsign.contains('@')) {
      final validation = CallsignValidator.validate(callsign);
      if (!validation.isValid) {
        setState(() => _errorMessage = validation.errorMessage ?? 'Неверный позывной');
        return;
      }
    }

    if (password.length < 6) {
      setState(() => _errorMessage = 'Пароль минимум 6 символов');
      return;
    }

    setState(() { _isLoading = true; _errorMessage = null; });

    final result = await _authRepository.loginSimple(callsign: callsign, password: password);
    if (!mounted) return;

    result.fold(
      onSuccess: (auth) { setState(() => _isLoading = false); widget.onAuthenticated?.call(auth.userId); },
      onFailure: (e) {
        setState(() {
          _isLoading = false;
          _errorMessage = e.statusCode == 429 ? 'Слишком много попыток' : e.statusCode == 401 ? 'Неверные данные' : e.message;
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
    if (displayName.isEmpty) { setState(() => _errorMessage = 'Введите имя'); return; }
    if (password.length < 6) { setState(() => _errorMessage = 'Пароль минимум 6 символов'); return; }
    if (password != confirmPassword) { setState(() => _errorMessage = 'Пароли не совпадают'); return; }

    setState(() { _isLoading = true; _errorMessage = null; });

    final result = await _authRepository.registerSimple(callsign: callsign, displayName: displayName, password: password);
    if (!mounted) return;

    result.fold(
      onSuccess: (auth) { setState(() => _isLoading = false); widget.onAuthenticated?.call(auth.userId); },
      onFailure: (e) { setState(() { _isLoading = false; _errorMessage = e.statusCode == 409 ? 'Позывной занят' : e.message; }); },
    );
  }

  Future<void> _authenticateWithBiometric() async {
    if (!_biometricAvailable) return;
    final result = await _biometricService.authenticate(reason: 'Войти в MKR');
    if (result == BiometricResult.success && mounted) {
      final userId = await _authRepository.getCurrentUserId();
      if (userId != null) { await _authRepository.initializeFromStorage(); widget.onAuthenticated?.call(userId); }
    }
  }

  @override
  Widget build(BuildContext context) {
    final brightness = MediaQuery.platformBrightnessOf(context);
    final isDark = brightness == Brightness.dark;
    
    return CupertinoPageScaffold(
      backgroundColor: isDark ? CupertinoColors.black : CupertinoColors.systemGroupedBackground,
      child: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox(height: 60),
                // Logo - красивый градиентный блок
                Container(
                  width: 100,
                  height: 100,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(24),
                    boxShadow: [
                      BoxShadow(
                        color: const Color(0xFF6366F1).withAlpha(80),
                        blurRadius: 30,
                        offset: const Offset(0, 10),
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Text(
                      'M',
                      style: TextStyle(
                        fontSize: 52,
                        fontWeight: FontWeight.w800,
                        color: CupertinoColors.white,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 24),
                Text('MKR', style: TextStyle(fontSize: 36, fontWeight: FontWeight.w700, color: isDark ? CupertinoColors.white : CupertinoColors.black, letterSpacing: 6)),
                const SizedBox(height: 4),
                Text('Secure Messenger', style: TextStyle(fontSize: 14, color: CupertinoColors.systemGrey, fontWeight: FontWeight.w500, letterSpacing: 2)),
                const SizedBox(height: 8),
                Text(_authMode == AuthMode.login ? 'Вход' : 'Регистрация', style: TextStyle(fontSize: 17, color: CupertinoColors.systemGrey, fontWeight: FontWeight.w500)),
                const SizedBox(height: 48),
                
                // Form
                CupertinoListSection.insetGrouped(
                  backgroundColor: isDark ? CupertinoColors.black : CupertinoColors.systemGroupedBackground,
                  children: [
                    _field(_callsignController, 'Позывной', CupertinoIcons.person, formatters: [FilteringTextInputFormatter.allow(RegExp(r'[a-zA-Z0-9@._\-]'))]),
                    if (_authMode == AuthMode.register) _field(_displayNameController, 'Имя', CupertinoIcons.textformat),
                    _passwordField(_passwordController, 'Пароль'),
                    if (_authMode == AuthMode.register) _passwordField(_confirmPasswordController, 'Подтвердите пароль'),
                  ],
                ),
                
                if (_errorMessage != null) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(color: CupertinoColors.systemRed.withOpacity(0.1), borderRadius: BorderRadius.circular(10)),
                    child: Row(children: [
                      const Icon(CupertinoIcons.exclamationmark_circle_fill, color: CupertinoColors.systemRed, size: 18),
                      const SizedBox(width: 8),
                      Expanded(child: Text(_errorMessage!, style: const TextStyle(color: CupertinoColors.systemRed, fontSize: 14))),
                    ]),
                  ),
                ],
                
                const SizedBox(height: 24),
                
                // Submit button
                SizedBox(
                  width: double.infinity,
                  child: CupertinoButton.filled(
                    onPressed: _isLoading ? null : _handleSubmit,
                    child: _isLoading 
                      ? const CupertinoActivityIndicator(color: CupertinoColors.white)
                      : Text(_authMode == AuthMode.login ? 'Войти' : 'Создать аккаунт'),
                  ),
                ),
                
                const SizedBox(height: 16),
                
                // Toggle
                CupertinoButton(
                  onPressed: _toggleAuthMode,
                  child: Text(
                    _authMode == AuthMode.login ? 'Создать аккаунт' : 'Уже есть аккаунт? Войти',
                    style: const TextStyle(fontSize: 15),
                  ),
                ),
                
                if (_biometricAvailable && _authMode == AuthMode.login) ...[
                  const SizedBox(height: 8),
                  CupertinoButton(
                    onPressed: _authenticateWithBiometric,
                    child: Row(mainAxisSize: MainAxisSize.min, children: [
                      Icon(CupertinoIcons.person_crop_circle, color: CupertinoColors.activeBlue),
                      const SizedBox(width: 8),
                      const Text('Face ID / Touch ID'),
                    ]),
                  ),
                ],
                
                const SizedBox(height: 48),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      width: 20,
                      height: 20,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                        ),
                        borderRadius: BorderRadius.circular(5),
                      ),
                      child: const Center(
                        child: Text('M', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: CupertinoColors.white)),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text('MKR by Makarov', style: TextStyle(color: CupertinoColors.systemGrey, fontSize: 13)),
                  ],
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  CupertinoListTile _field(TextEditingController ctrl, String placeholder, IconData icon, {List<TextInputFormatter>? formatters}) {
    return CupertinoListTile(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      leading: Icon(icon, color: CupertinoColors.activeBlue),
      title: CupertinoTextField.borderless(
        controller: ctrl,
        placeholder: placeholder,
        inputFormatters: formatters,
        style: const TextStyle(fontSize: 17),
      ),
    );
  }

  CupertinoListTile _passwordField(TextEditingController ctrl, String placeholder) {
    return CupertinoListTile(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      leading: const Icon(CupertinoIcons.lock, color: CupertinoColors.activeBlue),
      title: CupertinoTextField.borderless(
        controller: ctrl,
        placeholder: placeholder,
        obscureText: _obscurePassword,
        style: const TextStyle(fontSize: 17),
      ),
      trailing: CupertinoButton(
        padding: EdgeInsets.zero,
        onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
        child: Icon(_obscurePassword ? CupertinoIcons.eye : CupertinoIcons.eye_slash, color: CupertinoColors.systemGrey, size: 20),
      ),
    );
  }
}
