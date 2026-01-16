import 'package:flutter/cupertino.dart';

import '../../core/constants/app_constants.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/user.dart';

/// Экран настроек с редактированием профиля
/// Requirements: 7.1-7.3 - User profile view and edit
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
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
  bool _isUpdating = false;

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Настройки'),
      ),
      child: SafeArea(
        child: ListView(
          children: [
            _buildProfileSection(),
            const SizedBox(height: 24),
            _buildSettingsSection(context),
            const SizedBox(height: 24),
            _buildAboutSection(),
            const SizedBox(height: 24),
            _buildLogoutButton(context),
          ],
        ),
      ),
    );
  }

  Widget _buildProfileSection() {
    final displayName = widget.user?.displayName ?? widget.user?.callsign ?? 'Пользователь';
    final callsign = widget.user?.callsign ?? 'user';

    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemGrey6,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          _buildAvatar(),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  displayName,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '@$callsign',
                  style: const TextStyle(
                    color: CupertinoColors.systemGrey,
                    fontSize: 14,
                  ),
                ),
                if (widget.user?.isVerified == true) ...[
                  const SizedBox(height: 4),
                  const Row(
                    children: [
                      Icon(
                        CupertinoIcons.checkmark_seal_fill,
                        size: 14,
                        color: CupertinoColors.systemBlue,
                      ),
                      SizedBox(width: 4),
                      Text(
                        'Подтверждён',
                        style: TextStyle(
                          color: CupertinoColors.systemBlue,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
          CupertinoButton(
            padding: EdgeInsets.zero,
            onPressed: () => _showEditProfileDialog(context),
            child: const Icon(CupertinoIcons.pencil),
          ),
        ],
      ),
    );
  }

  Widget _buildAvatar() {
    final avatarUrl = widget.user?.avatarUrl;
    final displayName = widget.user?.displayName ?? widget.user?.callsign ?? 'U';

    return Container(
      width: 60,
      height: 60,
      decoration: BoxDecoration(
        color: CupertinoColors.systemBlue.withOpacity(0.2),
        shape: BoxShape.circle,
      ),
      child: avatarUrl != null
          ? ClipOval(
              child: Image.network(
                avatarUrl,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _buildAvatarPlaceholder(displayName),
              ),
            )
          : _buildAvatarPlaceholder(displayName),
    );
  }

  Widget _buildAvatarPlaceholder(String name) {
    return Center(
      child: Text(
        name.isNotEmpty ? name[0].toUpperCase() : '?',
        style: const TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.bold,
          color: CupertinoColors.systemBlue,
        ),
      ),
    );
  }

  void _showEditProfileDialog(BuildContext context) {
    final displayNameController = TextEditingController(
      text: widget.user?.displayName ?? '',
    );

    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Редактировать профиль'),
        content: Padding(
          padding: const EdgeInsets.only(top: 16),
          child: Column(
            children: [
              CupertinoTextField(
                controller: displayNameController,
                placeholder: 'Отображаемое имя',
                padding: const EdgeInsets.all(12),
              ),
            ],
          ),
        ),
        actions: [
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () async {
              Navigator.pop(context);
              await _updateProfile(displayNameController.text.trim());
            },
            child: const Text('Сохранить'),
          ),
        ],
      ),
    );
  }

  Future<void> _updateProfile(String displayName) async {
    if (widget.userRepository == null || displayName.isEmpty) return;

    setState(() => _isUpdating = true);

    final result = await widget.userRepository!.updateProfile(
      displayName: displayName,
    );

    if (!mounted) return;

    setState(() => _isUpdating = false);

    result.fold(
      onSuccess: (_) {
        widget.onProfileUpdated?.call();
        _showSuccess('Профиль обновлён');
      },
      onFailure: (error) {
        _showError(error.message);
      },
    );
  }

  void _showSuccess(String message) {
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Успешно'),
        content: Text(message),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  void _showError(String message) {
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Ошибка'),
        content: Text(message),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsSection(BuildContext context) {
    return CupertinoListSection.insetGrouped(
      header: const Text('НАСТРОЙКИ'),
      children: [
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.bell, color: CupertinoColors.systemBlue),
          title: const Text('Уведомления'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.lock, color: CupertinoColors.systemBlue),
          title: const Text('Приватность'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.paintbrush, color: CupertinoColors.systemBlue),
          title: const Text('Оформление'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.clock, color: CupertinoColors.systemBlue),
          title: const Text('Автоудаление'),
          subtitle: const Text('Сообщения удаляются через 24ч'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
      ],
    );
  }

  Widget _buildAboutSection() {
    return CupertinoListSection.insetGrouped(
      header: const Text('О ПРИЛОЖЕНИИ'),
      children: [
        const CupertinoListTile(
          leading: Icon(CupertinoIcons.info, color: CupertinoColors.systemGrey),
          title: Text('Версия'),
          additionalInfo: Text(AppConstants.appVersion),
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.doc_text, color: CupertinoColors.systemGrey),
          title: const Text('Условия использования'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.hand_raised, color: CupertinoColors.systemGrey),
          title: const Text('Политика конфиденциальности'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
      ],
    );
  }

  Widget _buildLogoutButton(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: CupertinoButton(
        color: CupertinoColors.systemRed.withOpacity(0.1),
        onPressed: _isUpdating ? null : () => _showLogoutConfirmation(context),
        child: _isUpdating
            ? const CupertinoActivityIndicator()
            : const Text(
                'Выйти',
                style: TextStyle(color: CupertinoColors.systemRed),
              ),
      ),
    );
  }

  void _showLogoutConfirmation(BuildContext context) {
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Выход'),
        content: const Text('Вы уверены, что хотите выйти?'),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(context),
            child: const Text('Отмена'),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () {
              Navigator.pop(context);
              widget.onLogout?.call();
            },
            child: const Text('Выйти'),
          ),
        ],
      ),
    );
  }
}
