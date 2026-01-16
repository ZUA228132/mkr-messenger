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

  const SettingsScreen({super.key, this.user, this.userRepository, this.onLogout, this.onProfileUpdated});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _notificationsEnabled = true;
  bool _biometricEnabled = false;
  int _autoDeleteIndex = 1; // 0=1ч, 1=24ч, 2=7д, 3=никогда
  User? _loadedUser;
  bool _isLoadingUser = false;

  String get _autoDeleteText => ['1 час', '24 часа', '7 дней', 'Никогда'][_autoDeleteIndex];

  User? get _effectiveUser => widget.user ?? _loadedUser;

  @override
  void initState() {
    super.initState();
    if (widget.user == null && widget.userRepository != null) {
      _loadUser();
    }
  }

  Future<void> _loadUser() async {
    if (widget.userRepository == null) return;
    setState(() => _isLoadingUser = true);
    
    final result = await widget.userRepository!.getCurrentUser();
    if (!mounted) return;
    
    result.fold(
      onSuccess: (user) => setState(() {
        _loadedUser = user;
        _isLoadingUser = false;
      }),
      onFailure: (_) => setState(() => _isLoadingUser = false),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = _effectiveUser;
    final displayName = user?.displayName ?? user?.callsign ?? 'Пользователь';
    final callsign = user?.callsign ?? 'user';

    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(middle: Text('Настройки')),
      child: SafeArea(
        child: ListView(
          children: [
            // Profile
            CupertinoListSection.insetGrouped(
              children: [
                CupertinoListTile(
                  leading: Container(
                    width: 50, height: 50,
                    decoration: BoxDecoration(color: CupertinoColors.activeBlue.withOpacity(0.2), shape: BoxShape.circle),
                    child: Center(child: Text(displayName[0].toUpperCase(), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: CupertinoColors.activeBlue))),
                  ),
                  title: Text(displayName, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('@$callsign'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => _showEditProfile(context),
                ),
              ],
            ),

            // Settings
            CupertinoListSection.insetGrouped(
              header: const Text('НАСТРОЙКИ'),
              children: [
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.bell_fill, CupertinoColors.systemRed),
                  title: const Text('Уведомления'),
                  trailing: CupertinoSwitch(
                    value: _notificationsEnabled,
                    onChanged: (v) => setState(() => _notificationsEnabled = v),
                  ),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.lock_fill, CupertinoColors.activeBlue),
                  title: const Text('Face ID / Touch ID'),
                  trailing: CupertinoSwitch(
                    value: _biometricEnabled,
                    onChanged: (v) => setState(() => _biometricEnabled = v),
                  ),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.paintbrush_fill, CupertinoColors.systemPurple),
                  title: const Text('Оформление'),
                  additionalInfo: const Text('Системная'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => _showAppearance(context),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.clock_fill, CupertinoColors.systemOrange),
                  title: const Text('Автоудаление'),
                  additionalInfo: Text(_autoDeleteText),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => _showAutoDelete(context),
                ),
              ],
            ),

            // About
            CupertinoListSection.insetGrouped(
              header: const Text('О ПРИЛОЖЕНИИ'),
              children: [
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.info_circle_fill, CupertinoColors.systemGrey),
                  title: const Text('Версия'),
                  additionalInfo: const Text(AppConstants.appVersion),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.doc_text_fill, CupertinoColors.systemGrey),
                  title: const Text('Условия использования'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => _showInfo(context, 'Условия использования'),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.hand_raised_fill, CupertinoColors.systemGrey),
                  title: const Text('Конфиденциальность'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => _showInfo(context, 'Политика конфиденциальности'),
                ),
              ],
            ),

            // Logout
            Padding(
              padding: const EdgeInsets.all(16),
              child: CupertinoButton(
                color: CupertinoColors.systemRed.withOpacity(0.1),
                onPressed: () => _showLogout(context),
                child: const Text('Выйти', style: TextStyle(color: CupertinoColors.systemRed)),
              ),
            ),

            // Footer
            Padding(
              padding: const EdgeInsets.only(bottom: 32),
              child: Center(child: Text('MKR by Makarov', style: TextStyle(color: CupertinoColors.systemGrey, fontSize: 13))),
            ),
          ],
        ),
      ),
    );
  }

  Widget _icon(IconData icon, Color color) {
    return Container(
      width: 30, height: 30,
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(7)),
      child: Icon(icon, color: CupertinoColors.white, size: 18),
    );
  }

  void _showEditProfile(BuildContext context) {
    final user = _effectiveUser;
    final ctrl = TextEditingController(text: user?.displayName ?? '');
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Редактировать профиль'),
        content: Padding(
          padding: const EdgeInsets.only(top: 16),
          child: CupertinoTextField(controller: ctrl, placeholder: 'Имя', padding: const EdgeInsets.all(12)),
        ),
        actions: [
          CupertinoDialogAction(isDestructiveAction: true, onPressed: () => Navigator.pop(ctx), child: const Text('Отмена')),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () async {
              Navigator.pop(ctx);
              if (widget.userRepository != null && ctrl.text.trim().isNotEmpty) {
                final result = await widget.userRepository!.updateProfile(displayName: ctrl.text.trim());
                result.fold(
                  onSuccess: (_) {
                    widget.onProfileUpdated?.call();
                    _loadUser();
                  },
                  onFailure: (error) {
                    if (mounted) {
                      showCupertinoDialog(
                        context: context,
                        builder: (ctx) => CupertinoAlertDialog(
                          title: const Text('Ошибка'),
                          content: Text(error.message),
                          actions: [CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: const Text('OK'))],
                        ),
                      );
                    }
                  },
                );
              }
            },
            child: const Text('Сохранить'),
          ),
        ],
      ),
    );
  }

  void _showAppearance(BuildContext context) {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: const Text('Оформление'),
        actions: [
          CupertinoActionSheetAction(onPressed: () => Navigator.pop(ctx), child: const Text('Светлая')),
          CupertinoActionSheetAction(onPressed: () => Navigator.pop(ctx), child: const Text('Тёмная')),
          CupertinoActionSheetAction(onPressed: () => Navigator.pop(ctx), child: const Text('Системная')),
        ],
        cancelButton: CupertinoActionSheetAction(isDestructiveAction: true, onPressed: () => Navigator.pop(ctx), child: const Text('Отмена')),
      ),
    );
  }

  void _showAutoDelete(BuildContext context) {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: const Text('Автоудаление сообщений'),
        actions: [
          CupertinoActionSheetAction(onPressed: () { Navigator.pop(ctx); setState(() => _autoDeleteIndex = 0); }, child: const Text('1 час')),
          CupertinoActionSheetAction(onPressed: () { Navigator.pop(ctx); setState(() => _autoDeleteIndex = 1); }, child: const Text('24 часа')),
          CupertinoActionSheetAction(onPressed: () { Navigator.pop(ctx); setState(() => _autoDeleteIndex = 2); }, child: const Text('7 дней')),
          CupertinoActionSheetAction(onPressed: () { Navigator.pop(ctx); setState(() => _autoDeleteIndex = 3); }, child: const Text('Никогда')),
        ],
        cancelButton: CupertinoActionSheetAction(isDestructiveAction: true, onPressed: () => Navigator.pop(ctx), child: const Text('Отмена')),
      ),
    );
  }

  void _showInfo(BuildContext context, String title) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(title),
        content: const Padding(padding: EdgeInsets.only(top: 16), child: Text('Информация будет добавлена позже.')),
        actions: [CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: const Text('OK'))],
      ),
    );
  }

  void _showLogout(BuildContext context) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Выход'),
        content: const Text('Вы уверены?'),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: const Text('Отмена')),
          CupertinoDialogAction(isDestructiveAction: true, onPressed: () { Navigator.pop(ctx); widget.onLogout?.call(); }, child: const Text('Выйти')),
        ],
      ),
    );
  }
}
