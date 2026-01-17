import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';

import '../../data/repositories/remote_auth_repository.dart';
import '../../data/repositories/remote_chat_repository.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/user.dart';
import 'chat_list_screen.dart';
import 'settings_screen.dart';

/// Screen for creating a new chat with user search
class NewChatScreen extends StatefulWidget {
  final RemoteUserRepository userRepository;
  final Function(String userId) onChatCreated;

  const NewChatScreen({
    super.key,
    required this.userRepository,
    required this.onChatCreated,
  });

  @override
  State<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends State<NewChatScreen> {
  final _searchController = TextEditingController();
  List<User> _searchResults = [];
  bool _isSearching = false;
  String? _errorMessage;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _searchUsers(String query) async {
    if (query.trim().isEmpty) {
      setState(() {
        _searchResults = [];
        _errorMessage = null;
      });
      return;
    }

    setState(() {
      _isSearching = true;
      _errorMessage = null;
    });

    final result = await widget.userRepository.searchUsers(query.trim());

    if (!mounted) return;

    result.fold(
      onSuccess: (users) => setState(() {
        _searchResults = users;
        _isSearching = false;
      }),
      onFailure: (error) => setState(() {
        _errorMessage = error.message;
        _isSearching = false;
        _searchResults = [];
      }),
    );
  }

  void _selectUser(User user) {
    widget.onChatCreated(user.id);
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Новый чат'),
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () => Navigator.pop(context),
          child: const Icon(CupertinoIcons.xmark),
        ),
      ),
      child: SafeArea(
        child: Column(
          children: [
            // Search bar
            Padding(
              padding: const EdgeInsets.all(16),
              child: CupertinoSearchTextField(
                controller: _searchController,
                placeholder: 'Поиск по username или имени',
                onChanged: _searchUsers,
                onSubmitted: _searchUsers,
              ),
            ),
            // Results
            Expanded(
              child: _buildResults(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResults() {
    if (_isSearching) {
      return const Center(child: CupertinoActivityIndicator());
    }

    if (_errorMessage != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              CupertinoIcons.exclamationmark_triangle,
              size: 48,
              color: CupertinoColors.systemRed,
            ),
            const SizedBox(height: 16),
            Text(
              _errorMessage!,
              style: const TextStyle(color: CupertinoColors.systemGrey),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }

    if (_searchController.text.trim().isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              CupertinoIcons.search,
              size: 64,
              color: CupertinoColors.systemGrey3.resolveFrom(context),
            ),
            const SizedBox(height: 16),
            Text(
              'Введите username или имя',
              style: TextStyle(
                color: CupertinoColors.secondaryLabel.resolveFrom(context),
                fontSize: 16,
              ),
            ),
          ],
        ),
      );
    }

    if (_searchResults.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              CupertinoIcons.person_badge_minus,
              size: 64,
              color: CupertinoColors.systemGrey3.resolveFrom(context),
            ),
            const SizedBox(height: 16),
            const Text(
              'Пользователи не найдены',
              style: TextStyle(fontSize: 16),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      itemCount: _searchResults.length,
      itemBuilder: (context, index) {
        final user = _searchResults[index];
        final displayName = user.displayName ?? user.callsign ?? 'Пользователь';
        final firstLetter = displayName.isNotEmpty ? displayName[0].toUpperCase() : '?';
        final isOnline = user.isOnline;

        return CupertinoListTile(
          onTap: () => _selectUser(user),
          leading: Stack(
            children: [
              Container(
                width: 50,
                height: 50,
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
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                      color: CupertinoColors.white,
                    ),
                  ),
                ),
              ),
              if (isOnline)
                Positioned(
                  right: 0,
                  bottom: 0,
                  child: Container(
                    width: 14,
                    height: 14,
                    decoration: BoxDecoration(
                      color: CupertinoColors.systemGreen,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: CupertinoColors.systemBackground.resolveFrom(context),
                        width: 2,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          title: Text(displayName, style: const TextStyle(fontWeight: FontWeight.w600)),
          subtitle: user.callsign != null
              ? Text('@${user.callsign}', style: const TextStyle(fontSize: 14))
              : null,
          trailing: const Icon(CupertinoIcons.chevron_right, size: 18),
        );
      },
    );
  }
}

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
    Navigator.of(context).push(
      CupertinoPageRoute(
        builder: (_) => NewChatScreen(
          userRepository: widget.userRepository,
          onChatCreated: (userId) => _createChat(userId),
        ),
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
