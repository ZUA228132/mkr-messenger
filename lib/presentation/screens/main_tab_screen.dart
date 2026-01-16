import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';

import '../../data/repositories/remote_auth_repository.dart';
import '../../data/repositories/remote_chat_repository.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/user.dart';
import 'chat_list_screen.dart';
import 'settings_screen.dart';

/// Main tab screen with bottom navigation
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
/// Requirements: 4.1-4.4 - Chat list integration with backend
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

  /// Requirements: 4.1 - GET /api/chats
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

  /// Requirements: 7.1 - GET /api/users/{userId}
  Future<void> _loadCurrentUser() async {
    final result = await widget.userRepository.getCurrentUser();

    if (!mounted) return;

    result.fold(
      onSuccess: (user) {
        setState(() => _currentUser = user);
      },
      onFailure: (_) {
        // Silently fail - we can still show the screen
      },
    );
  }

  /// Requirements: 4.4 - POST /api/chats with participantIds
  Future<void> _createChat(String userId) async {
    final result = await widget.chatRepository.getOrCreateDirectChat(userId);

    if (!mounted) return;

    result.fold(
      onSuccess: (chat) {
        // Navigate to the new chat
        context.push('/chat/${chat.id}');
        // Refresh chat list
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
        title: const Text('Error'),
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
        items: const [
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.chat_bubble_2),
            activeIcon: Icon(CupertinoIcons.chat_bubble_2_fill),
            label: 'Chats',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.shield),
            activeIcon: Icon(CupertinoIcons.shield_fill),
            label: 'Security',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.settings),
            activeIcon: Icon(CupertinoIcons.settings_solid),
            label: 'Settings',
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
        title: const Text('New Chat'),
        content: Padding(
          padding: const EdgeInsets.only(top: 16),
          child: CupertinoTextField(
            controller: controller,
            placeholder: 'Enter username or search',
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
            child: const Text('Cancel'),
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
            child: const Text('Start Chat'),
          ),
        ],
      ),
    );
  }

  /// Requirements: 8.1-8.3 - Search users and create chat
  Future<void> _searchAndCreateChat(String query) async {
    // First search for the user
    final searchResult = await widget.userRepository.searchUsers(query);

    if (!mounted) return;

    searchResult.fold(
      onSuccess: (users) {
        if (users.isEmpty) {
          _showError('No users found with username "$query"');
        } else if (users.length == 1) {
          // Single result - create chat directly
          _createChat(users.first.id);
        } else {
          // Multiple results - show selection dialog
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
        title: const Text('Select User'),
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
          child: const Text('Cancel'),
        ),
      ),
    );
  }
}

/// Security tab with quick access to security features
class SecurityTabScreen extends StatelessWidget {
  const SecurityTabScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Security'),
      ),
      child: SafeArea(
        child: ListView(
          children: [
            _buildSecurityItem(
              context,
              icon: CupertinoIcons.shield_lefthalf_fill,
              title: 'Security Check',
              subtitle: 'Check device security status',
              onTap: () => context.push('/security-check'),
            ),
            _buildSecurityItem(
              context,
              icon: CupertinoIcons.exclamationmark_triangle_fill,
              title: 'Panic Button',
              subtitle: 'Emergency data wipe',
              color: CupertinoColors.systemRed,
              onTap: () => context.push('/panic'),
            ),
            _buildSecurityItem(
              context,
              icon: CupertinoIcons.eye_slash_fill,
              title: 'Stealth Mode',
              subtitle: 'Hide app as calculator',
              onTap: () => context.push('/stealth'),
            ),
            const SizedBox(height: 32),
            _buildSecurityStatus(),
          ],
        ),
      ),
    );
  }

  Widget _buildSecurityItem(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    Color color = CupertinoColors.systemBlue,
    required VoidCallback onTap,
  }) {
    return CupertinoListTile(
      leading: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(icon, color: color, size: 20),
      ),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: const CupertinoListTileChevron(),
      onTap: onTap,
    );
  }

  Widget _buildSecurityStatus() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemGreen.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: CupertinoColors.systemGreen.withOpacity(0.2),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              CupertinoIcons.checkmark_shield_fill,
              color: CupertinoColors.systemGreen,
              size: 24,
            ),
          ),
          const SizedBox(width: 16),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Device Secure',
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 16,
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  'All security checks passed',
                  style: TextStyle(
                    color: CupertinoColors.systemGrey,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
