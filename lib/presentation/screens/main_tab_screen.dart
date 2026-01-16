import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';

import '../../data/repositories/remote_auth_repository.dart';
import '../../data/repositories/remote_chat_repository.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/user.dart';
import 'chat_list_screen.dart';
import 'settings_screen.dart';

/// Главный экран с табами — Apple стиль
class MainTabScreen extends StatefulWidget {
  final String currentUserId;
  final RemoteChatRepository chatRepository;
  final RemoteUserRepository userRepository;
  final RemoteAuthRepository authRepository;
  final VoidCallback? onLogout;

  const MainTabScreen({
    super.key,
    required this.currentUserId,
    required this.chatRepository,
    required this.userRepository,
    required this.authRepository,
    this.onLogout,
  });

  @override
  State<MainTabScreen> createState() => _MainTabScreenState();
}

class _MainTabScreenState extends State<MainTabScreen> {
  List<Chat> _chats = [];
  User? _currentUser;
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    await Future.wait([_loadChats(), _loadCurrentUser()]);
  }

  Future<void> _loadChats() async {
    setState(() { _isLoading = true; _errorMessage = null; });
    final result = await widget.chatRepository.getChats();
    if (!mounted) return;
    result.fold(
      onSuccess: (chats) => setState(() { _chats = chats; _isLoading = false; }),
      onFailure: (e) => setState(() { _errorMessage = e.message; _isLoading = false; }),
    );
  }

  Future<void> _loadCurrentUser() async {
    final result = await widget.userRepository.getCurrentUser();
    if (!mounted) return;
    result.fold(onSuccess: (user) => setState(() => _currentUser = user), onFailure: (_) {});
  }

  void _showNewChat(BuildContext context) {
    final ctrl = TextEditingController();
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Новый чат'),
        content: Padding(
          padding: const EdgeInsets.only(top: 16),
          child: CupertinoTextField(controller: ctrl, placeholder: 'Позывной пользователя', prefix: const Padding(padding: EdgeInsets.only(left: 8), child: Text('@'))),
        ),
        actions: [
          CupertinoDialogAction(isDestructiveAction: true, onPressed: () => Navigator.pop(ctx), child: const Text('Отмена')),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () async {
              final username = ctrl.text.trim();
              if (username.isEmpty) return;
              Navigator.pop(ctx);
              final searchResult = await widget.userRepository.searchUsers(username);
              if (!mounted) return;
              searchResult.fold(
                onSuccess: (users) {
                  if (users.isEmpty) {
                    _showError('Пользователь не найден');
                  } else {
                    _createChat(users.first.id);
                  }
                },
                onFailure: (e) => _showError(e.message),
              );
            },
            child: const Text('Найти'),
          ),
        ],
      ),
    );
  }

  Future<void> _createChat(String userId) async {
    final result = await widget.chatRepository.getOrCreateDirectChat(userId);
    if (!mounted) return;
    result.fold(
      onSuccess: (chat) { context.push('/chat/${chat.id}'); _loadChats(); },
      onFailure: (e) => _showError(e.message),
    );
  }

  void _showError(String message) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Ошибка'),
        content: Text(message),
        actions: [CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: const Text('OK'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoTabScaffold(
      tabBar: CupertinoTabBar(
        items: const [
          BottomNavigationBarItem(icon: Icon(CupertinoIcons.chat_bubble_2), activeIcon: Icon(CupertinoIcons.chat_bubble_2_fill), label: 'Чаты'),
          BottomNavigationBarItem(icon: Icon(CupertinoIcons.shield), activeIcon: Icon(CupertinoIcons.shield_fill), label: 'Безопасность'),
          BottomNavigationBarItem(icon: Icon(CupertinoIcons.settings), activeIcon: Icon(CupertinoIcons.settings_solid), label: 'Настройки'),
        ],
      ),
      tabBuilder: (context, index) {
        switch (index) {
          case 0:
            return CupertinoTabView(
              builder: (_) => ChatListScreen(
                chats: _chats,
                currentUserId: widget.currentUserId,
                isLoading: _isLoading,
                errorMessage: _errorMessage,
                onChatTap: (chat) => context.push('/chat/${chat.id}'),
                onNewChat: () => _showNewChat(context),
                onRefresh: _loadChats,
                userRepository: widget.userRepository,
              ),
            );
          case 1:
            return CupertinoTabView(builder: (_) => const _SecurityTab());
          case 2:
            return CupertinoTabView(
              builder: (_) => SettingsScreen(
                user: _currentUser,
                userRepository: widget.userRepository,
                onLogout: widget.onLogout,
                onProfileUpdated: _loadCurrentUser,
              ),
            );
          default:
            return const SizedBox.shrink();
        }
      },
    );
  }
}

class _SecurityTab extends StatelessWidget {
  const _SecurityTab();

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(middle: Text('Безопасность')),
      child: SafeArea(
        child: ListView(
          children: [
            CupertinoListSection.insetGrouped(
              children: [
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.shield_lefthalf_fill, CupertinoColors.activeBlue),
                  title: const Text('Проверка безопасности'),
                  subtitle: const Text('Статус защиты устройства'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => context.push('/security-check'),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.exclamationmark_triangle_fill, CupertinoColors.systemRed),
                  title: const Text('Тревожная кнопка'),
                  subtitle: const Text('Экстренное удаление данных'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => context.push('/panic'),
                ),
                CupertinoListTile(
                  leading: _icon(CupertinoIcons.eye_slash_fill, CupertinoColors.systemPurple),
                  title: const Text('Режим маскировки'),
                  subtitle: const Text('Скрыть под калькулятор'),
                  trailing: const CupertinoListTileChevron(),
                  onTap: () => context.push('/stealth'),
                ),
              ],
            ),
            const SizedBox(height: 24),
            _buildStatus(),
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

  Widget _buildStatus() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemGreen.withAlpha(25),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 44, height: 44,
            decoration: const BoxDecoration(color: CupertinoColors.systemGreen, shape: BoxShape.circle),
            child: const Icon(CupertinoIcons.checkmark_shield_fill, color: CupertinoColors.white, size: 24),
          ),
          const SizedBox(width: 16),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Устройство защищено', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                SizedBox(height: 2),
                Text('Все проверки пройдены', style: TextStyle(color: CupertinoColors.systemGrey, fontSize: 14)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
