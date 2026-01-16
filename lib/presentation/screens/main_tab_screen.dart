import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';

import '../../data/repositories/remote_auth_repository.dart';
import '../../data/repositories/remote_chat_repository.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/user.dart';
import 'chat_list_screen.dart';
import 'settings_screen.dart';

/// Главный экран с табами
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
    await Future.wait([
      _loadChats(),
      _loadCurrentUser(),
    ]);
  }

  Future<void> _loadChats() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await widget.chatRepository.getChats();

    if (!mounted) return;

    result.fold(
      onSuccess: (chats) {
        setState(() {
          _chats = chats;
          _isLoading = false;
        });
      },
      onFailure: (error) {
        setState(() {
          _errorMessage = error.message;
          _isLoading = false;
        });
      },
    );
  }

  Future<void> _loadCurrentUser() async {
    final result = await widget.userRepository.getCurrentUser();

    if (!mounted) return;

    result.fold(
      onSuccess: (user) {
        setState(() => _currentUser = user);
      },
      onFailure: (_) {},
    );
  }

  Future<void> _createChat(String userId) async {
    final result = await widget.chatRepository.getOrCreateDirectChat(userId);

    if (!mounted) return;

    result.fold(
      onSuccess: (chat) {
        context.push('/chat/${chat.id}');
        _loadChats();
      },
      onFailure: (error) {
        _showError(error.message);
      },
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

  @override
  Widget build(BuildContext context) {
    return CupertinoTabScaffold(
      tabBar: CupertinoTabBar(
        backgroundColor: const Color(0xFF0A0A1A).withOpacity(0.9),
        activeColor: const Color(0xFF00D4FF),
        inactiveColor: CupertinoColors.systemGrey,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.chat_bubble_2),
            activeIcon: Icon(CupertinoIcons.chat_bubble_2_fill),
            label: 'Чаты',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.shield),
            activeIcon: Icon(CupertinoIcons.shield_fill),
            label: 'Безопасность',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.settings),
            activeIcon: Icon(CupertinoIcons.settings_solid),
            label: 'Настройки',
          ),
        ],
      ),
      tabBuilder: (context, index) {
        switch (index) {
          case 0:
            return CupertinoTabView(
              builder: (context) => ChatListScreen(
                chats: _chats,
                currentUserId: widget.currentUserId,
                isLoading: _isLoading,
                errorMessage: _errorMessage,
                onChatTap: (chat) => context.push('/chat/${chat.id}'),
                onNewChat: () => _showNewChatDialog(context),
                onRefresh: _loadChats,
              ),
            );
          case 1:
            return CupertinoTabView(
              builder: (context) => const SecurityTabScreen(),
            );
          case 2:
            return CupertinoTabView(
              builder: (context) => SettingsScreen(
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

  void _showNewChatDialog(BuildContext context) {
    final controller = TextEditingController();
    
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Новый чат'),
        content: Padding(
          padding: const EdgeInsets.only(top: 16),
          child: CupertinoTextField(
            controller: controller,
            placeholder: 'Введите позывной',
            prefix: const Padding(
              padding: EdgeInsets.only(left: 8),
              child: Text('@'),
            ),
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
            onPressed: () {
              final username = controller.text.trim();
              if (username.isNotEmpty) {
                Navigator.pop(context);
                _searchAndCreateChat(username);
              }
            },
            child: const Text('Начать'),
          ),
        ],
      ),
    );
  }

  Future<void> _searchAndCreateChat(String query) async {
    final searchResult = await widget.userRepository.searchUsers(query);

    if (!mounted) return;

    searchResult.fold(
      onSuccess: (users) {
        if (users.isEmpty) {
          _showError('Пользователь "$query" не найден');
        } else if (users.length == 1) {
          _createChat(users.first.id);
        } else {
          _showUserSelectionDialog(users);
        }
      },
      onFailure: (error) {
        _showError(error.message);
      },
    );
  }

  void _showUserSelectionDialog(List<User> users) {
    showCupertinoModalPopup(
      context: context,
      builder: (context) => CupertinoActionSheet(
        title: const Text('Выберите пользователя'),
        actions: users.map((user) {
          return CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(context);
              _createChat(user.id);
            },
            child: Text('@${user.callsign}${user.displayName != null ? ' (${user.displayName})' : ''}'),
          );
        }).toList(),
        cancelButton: CupertinoActionSheetAction(
          isDestructiveAction: true,
          onPressed: () => Navigator.pop(context),
          child: const Text('Отмена'),
        ),
      ),
    );
  }
}

/// Вкладка безопасности с liquid glass дизайном
class SecurityTabScreen extends StatelessWidget {
  const SecurityTabScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: const Color(0xFF0A0A1A),
      navigationBar: CupertinoNavigationBar(
        backgroundColor: const Color(0xFF0A0A1A).withOpacity(0.9),
        middle: const Text(
          'Безопасность',
          style: TextStyle(color: CupertinoColors.white),
        ),
      ),
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Color(0xFF0A0A1A),
              Color(0xFF1A1A3E),
              Color(0xFF0F0F2A),
            ],
          ),
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _buildGlassSecurityItem(
                context,
                icon: CupertinoIcons.shield_lefthalf_fill,
                title: 'Проверка безопасности',
                subtitle: 'Статус защиты устройства',
                gradient: const [Color(0xFF00D4FF), Color(0xFF0099CC)],
                onTap: () => context.push('/security-check'),
              ),
              const SizedBox(height: 12),
              _buildGlassSecurityItem(
                context,
                icon: CupertinoIcons.exclamationmark_triangle_fill,
                title: 'Тревожная кнопка',
                subtitle: 'Экстренное удаление данных',
                gradient: const [Color(0xFFFF4757), Color(0xFFCC0033)],
                onTap: () => context.push('/panic'),
              ),
              const SizedBox(height: 12),
              _buildGlassSecurityItem(
                context,
                icon: CupertinoIcons.eye_slash_fill,
                title: 'Режим маскировки',
                subtitle: 'Скрыть приложение под калькулятор',
                gradient: const [Color(0xFF7B68EE), Color(0xFF5B4BC9)],
                onTap: () => context.push('/stealth'),
              ),
              const SizedBox(height: 32),
              _buildSecurityStatus(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildGlassSecurityItem(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required List<Color> gradient,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Colors.white.withOpacity(0.12),
                  Colors.white.withOpacity(0.05),
                ],
              ),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: Colors.white.withOpacity(0.15),
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(colors: gradient),
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: [
                      BoxShadow(
                        color: gradient[0].withOpacity(0.4),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Icon(icon, color: Colors.white, size: 24),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 17,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        subtitle,
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.5),
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  CupertinoIcons.chevron_right,
                  color: Colors.white.withOpacity(0.3),
                  size: 20,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSecurityStatus() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                const Color(0xFF00FF88).withOpacity(0.15),
                const Color(0xFF00CC66).withOpacity(0.08),
              ],
            ),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: const Color(0xFF00FF88).withOpacity(0.3),
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF00FF88), Color(0xFF00CC66)],
                  ),
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF00FF88).withOpacity(0.4),
                      blurRadius: 16,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: const Icon(
                  CupertinoIcons.checkmark_shield_fill,
                  color: Colors.white,
                  size: 28,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Устройство защищено',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w600,
                        fontSize: 17,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Все проверки пройдены',
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.5),
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// Colors class for compatibility
class Colors {
  static const Color white = Color(0xFFFFFFFF);
  static const Color black = Color(0xFF000000);
  static const Color transparent = Color(0x00000000);
}
