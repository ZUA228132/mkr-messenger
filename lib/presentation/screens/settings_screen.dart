import 'package:flutter/cupertino.dart';

import '../../core/constants/app_constants.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/user.dart';

/// Экран настроек — чистый Apple стиль
class SettingsScreen extends StatefulWidget {
  final User? user;
  final RemoteUserRepository? userRepository;
  final VoidCallback? onLogout;
  final VoidCallback? onProfileUpdated;

  const SettingsScreen({
    super.key,
    this.user,
    this.userRepository,
    this.onLogout,
    this.onProfileUpdated,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _notificationsEnabled = true;
  bool _biometricEnabled = false;
  int _autoDeleteIndex = 1;
  int _themeIndex = 2; // 0=light, 1=dark, 2=system
  User? _loadedUser;
  bool _isLoading = false;
  bool _isSaving = false;

  final _autoDeleteOptions = ['1 час', '24 часа', '7 дней', 'Никогда'];
  final _themeOptions = ['Светлая', 'Тёмная', 'Системная'];

  User? get _effectiveUser => widget.user ?? _loadedUser;

  @override
  void initState() {
    super.initState();
    _loadUserIfNeeded();
  }

  @override
  void didUpdateWidget(SettingsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.user != oldWidget.user) {
      setState(() {});
    }
  }

  Future<void> _loadUserIfNeeded() async {
    if (_effectiveUser != null) return;
    if (widget.userRepository == null) return;

    setState(() => _isLoading = true);

    final result = await widget.userRepository!.getCurrentUser();
    if (!mounted) return;

    result.fold(
      onSuccess: (user) => setState(() {
        _loadedUser = user;
        _isLoading = false;
      }),
      onFailure: (_) => setState(() => _isLoading = false),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = _effectiveUser;
    final displayName = user?.displayName ?? user?.callsign ?? 'Пользователь';
    final callsign = user?.callsign ?? 'user';
    final firstLetter = displayName.isNotEmpty ? displayName[0].toUpperCase() : '?';

    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Настройки'),
      ),
      child: SafeArea(
        child: _isLoading
            ? const Center(child: CupertinoActivityIndicator())
            : CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Column(
                      children: [
                        const SizedBox(height: 16),
                        // Profile Section
                        _buildProfileSection(displayName, callsign, firstLetter),
                        const SizedBox(height: 24),
                        // Settings Section
                        _buildSettingsSection(),
                        const SizedBox(height: 24),
                        // About Section
                        _buildAboutSection(),
                        const SizedBox(height: 24),
                        // Logout Button
                        _buildLogoutButton(),
                        const SizedBox(height: 16),
                        // Footer
                        _buildFooter(),
                        const SizedBox(height: 32),
                      ],
                    ),
                  ),
                ],
              ),
      ),
    );
  }

  Widget _buildProfileSection(String displayName, String callsign, String firstLetter) {
    return GestureDetector(
      onTap: () => _showProfileEditor(),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: CupertinoColors.systemBackground.resolveFrom(context),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            // Avatar
            Container(
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [CupertinoColors.systemBlue, CupertinoColors.systemIndigo],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  firstLetter,
                  style: const TextStyle(
                    fontSize: 26,
                    fontWeight: FontWeight.w600,
                    color: CupertinoColors.white,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 16),
            // Info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    displayName,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '@$callsign',
                    style: TextStyle(
                      fontSize: 15,
                      color: CupertinoColors.secondaryLabel.resolveFrom(context),
                    ),
                  ),
                ],
              ),
            ),
            // Chevron
            Icon(
              CupertinoIcons.chevron_right,
              color: CupertinoColors.tertiaryLabel.resolveFrom(context),
              size: 20,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSettingsSection() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemBackground.resolveFrom(context),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(
              'НАСТРОЙКИ',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: CupertinoColors.secondaryLabel.resolveFrom(context),
              ),
            ),
          ),
          _buildSettingRow(
            icon: CupertinoIcons.bell_fill,
            iconColor: CupertinoColors.systemRed,
            title: 'Уведомления',
            trailing: CupertinoSwitch(
              value: _notificationsEnabled,
              onChanged: (v) => setState(() => _notificationsEnabled = v),
            ),
          ),
          _buildDivider(),
          _buildSettingRow(
            icon: CupertinoIcons.lock_fill,
            iconColor: CupertinoColors.systemBlue,
            title: 'Face ID / Touch ID',
            trailing: CupertinoSwitch(
              value: _biometricEnabled,
              onChanged: (v) => setState(() => _biometricEnabled = v),
            ),
          ),
          _buildDivider(),
          _buildSettingRow(
            icon: CupertinoIcons.paintbrush_fill,
            iconColor: CupertinoColors.systemPurple,
            title: 'Оформление',
            value: _themeOptions[_themeIndex],
            onTap: _showThemePicker,
          ),
          _buildDivider(),
          _buildSettingRow(
            icon: CupertinoIcons.clock_fill,
            iconColor: CupertinoColors.systemOrange,
            title: 'Автоудаление',
            value: _autoDeleteOptions[_autoDeleteIndex],
            onTap: _showAutoDeletePicker,
          ),
        ],
      ),
    );
  }

  Widget _buildAboutSection() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemBackground.resolveFrom(context),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(
              'О ПРИЛОЖЕНИИ',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: CupertinoColors.secondaryLabel.resolveFrom(context),
              ),
            ),
          ),
          _buildSettingRow(
            icon: CupertinoIcons.info_circle_fill,
            iconColor: CupertinoColors.systemGrey,
            title: 'Версия',
            value: AppConstants.appVersion,
            showChevron: false,
          ),
          _buildDivider(),
          _buildSettingRow(
            icon: CupertinoIcons.doc_text_fill,
            iconColor: CupertinoColors.systemGrey,
            title: 'Условия использования',
            onTap: () => _showInfo('Условия использования'),
          ),
          _buildDivider(),
          _buildSettingRow(
            icon: CupertinoIcons.hand_raised_fill,
            iconColor: CupertinoColors.systemGrey,
            title: 'Конфиденциальность',
            onTap: () => _showInfo('Политика конфиденциальности'),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingRow({
    required IconData icon,
    required Color iconColor,
    required String title,
    String? value,
    Widget? trailing,
    VoidCallback? onTap,
    bool showChevron = true,
  }) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            // Icon
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: iconColor,
                borderRadius: BorderRadius.circular(7),
              ),
              child: Icon(icon, color: CupertinoColors.white, size: 18),
            ),
            const SizedBox(width: 12),
            // Title
            Expanded(
              child: Text(
                title,
                style: const TextStyle(fontSize: 17),
              ),
            ),
            // Value or Trailing
            if (trailing != null)
              trailing
            else if (value != null) ...[
              Text(
                value,
                style: TextStyle(
                  fontSize: 17,
                  color: CupertinoColors.secondaryLabel.resolveFrom(context),
                ),
              ),
              if (showChevron && onTap != null) ...[
                const SizedBox(width: 8),
                Icon(
                  CupertinoIcons.chevron_right,
                  color: CupertinoColors.tertiaryLabel.resolveFrom(context),
                  size: 18,
                ),
              ],
            ] else if (onTap != null && showChevron)
              Icon(
                CupertinoIcons.chevron_right,
                color: CupertinoColors.tertiaryLabel.resolveFrom(context),
                size: 18,
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildDivider() {
    return Padding(
      padding: const EdgeInsets.only(left: 58),
      child: Container(
        height: 0.5,
        color: CupertinoColors.separator.resolveFrom(context),
      ),
    );
  }

  Widget _buildLogoutButton() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: SizedBox(
        width: double.infinity,
        child: CupertinoButton(
          padding: const EdgeInsets.symmetric(vertical: 14),
          color: CupertinoColors.systemRed.withAlpha(25),
          borderRadius: BorderRadius.circular(12),
          onPressed: _showLogoutConfirmation,
          child: const Text(
            'Выйти из аккаунта',
            style: TextStyle(
              color: CupertinoColors.systemRed,
              fontSize: 17,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildFooter() {
    return Text(
      'MKR by Makarov',
      style: TextStyle(
        fontSize: 13,
        color: CupertinoColors.tertiaryLabel.resolveFrom(context),
      ),
    );
  }

  // === Actions ===

  void _showProfileEditor() {
    final user = _effectiveUser;
    final nameController = TextEditingController(text: user?.displayName ?? '');

    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        height: 320,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: CupertinoColors.systemBackground.resolveFrom(context),
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                CupertinoButton(
                  padding: EdgeInsets.zero,
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Отмена'),
                ),
                const Text(
                  'Редактировать',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
                ),
                CupertinoButton(
                  padding: EdgeInsets.zero,
                  onPressed: _isSaving
                      ? null
                      : () async {
                          final newName = nameController.text.trim();
                          if (newName.isEmpty) return;

                          setState(() => _isSaving = true);
                          Navigator.pop(ctx);

                          if (widget.userRepository != null) {
                            final result = await widget.userRepository!
                                .updateProfile(displayName: newName);

                            if (!mounted) return;

                            result.fold(
                              onSuccess: (updatedUser) {
                                setState(() {
                                  _loadedUser = updatedUser;
                                  _isSaving = false;
                                });
                                widget.onProfileUpdated?.call();
                              },
                              onFailure: (error) {
                                setState(() => _isSaving = false);
                                _showError(error.message);
                              },
                            );
                          } else {
                            setState(() => _isSaving = false);
                          }
                        },
                  child: _isSaving
                      ? const CupertinoActivityIndicator()
                      : const Text(
                          'Готово',
                          style: TextStyle(fontWeight: FontWeight.w600),
                        ),
                ),
              ],
            ),
            const SizedBox(height: 24),
            // Avatar
            Center(
              child: Stack(
                children: [
                  Container(
                    width: 80,
                    height: 80,
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        colors: [CupertinoColors.systemBlue, CupertinoColors.systemIndigo],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: Text(
                        (user?.displayName ?? user?.callsign ?? '?')[0].toUpperCase(),
                        style: const TextStyle(
                          fontSize: 32,
                          fontWeight: FontWeight.w600,
                          color: CupertinoColors.white,
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: CupertinoColors.systemGrey5.resolveFrom(context),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        CupertinoIcons.camera_fill,
                        size: 14,
                        color: CupertinoColors.systemGrey,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
            // Name field
            CupertinoTextField(
              controller: nameController,
              placeholder: 'Имя',
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey6.resolveFrom(context),
                borderRadius: BorderRadius.circular(12),
              ),
              style: const TextStyle(fontSize: 17),
              autofocus: true,
            ),
          ],
        ),
      ),
    );
  }

  void _showThemePicker() {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: const Text('Оформление'),
        actions: List.generate(_themeOptions.length, (i) {
          final isSelected = i == _themeIndex;
          return CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(ctx);
              setState(() => _themeIndex = i);
            },
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(_themeOptions[i]),
                if (isSelected) ...[
                  const SizedBox(width: 8),
                  const Icon(CupertinoIcons.checkmark, size: 18),
                ],
              ],
            ),
          );
        }),
        cancelButton: CupertinoActionSheetAction(
          isDestructiveAction: true,
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Отмена'),
        ),
      ),
    );
  }

  void _showAutoDeletePicker() {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: const Text('Автоудаление сообщений'),
        message: const Text('Сообщения будут автоматически удалены через выбранное время'),
        actions: List.generate(_autoDeleteOptions.length, (i) {
          final isSelected = i == _autoDeleteIndex;
          return CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(ctx);
              setState(() => _autoDeleteIndex = i);
            },
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(_autoDeleteOptions[i]),
                if (isSelected) ...[
                  const SizedBox(width: 8),
                  const Icon(CupertinoIcons.checkmark, size: 18),
                ],
              ],
            ),
          );
        }),
        cancelButton: CupertinoActionSheetAction(
          isDestructiveAction: true,
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Отмена'),
        ),
      ),
    );
  }

  void _showInfo(String title) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(title),
        content: const Padding(
          padding: EdgeInsets.only(top: 16),
          child: Text('Информация будет добавлена позже.'),
        ),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  void _showLogoutConfirmation() {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Выход'),
        content: const Text('Вы уверены, что хотите выйти из аккаунта?'),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Отмена'),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () {
              Navigator.pop(ctx);
              widget.onLogout?.call();
            },
            child: const Text('Выйти'),
          ),
        ],
      ),
    );
  }

  void _showError(String message) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Ошибка'),
        content: Text(message),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }
}
