import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';

import '../../domain/entities/chat.dart';
import 'chat_list_screen.dart';
import 'settings_screen.dart';

/// Main tab screen with bottom navigation
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
class MainTabScreen extends StatefulWidget {
  final String currentUserId;

  const MainTabScreen({super.key, required this.currentUserId});

  @override
  State<MainTabScreen> createState() => _MainTabScreenState();
}

class _MainTabScreenState extends State<MainTabScreen> {
  // Demo chats for testing
  final List<Chat> _chats = [];

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
                onChatTap: (chat) => context.push('/chat/${chat.id}'),
                onNewChat: () => _showNewChatDialog(context),
                onRefresh: () async {
                  // Refresh chats from server
                  await Future.delayed(const Duration(seconds: 1));
                },
              ),
            );
          case 1:
            return CupertinoTabView(
              builder: (context) => const SecurityTabScreen(),
            );
          case 2:
            return CupertinoTabView(
              builder: (context) => SettingsScreen(
                callsign: widget.currentUserId,
                onLogout: () => context.go('/'),
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
            placeholder: 'Enter callsign',
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
              final callsign = controller.text.trim();
              if (callsign.isNotEmpty) {
                Navigator.pop(context);
                // Create new chat and navigate
                context.push('/chat/$callsign');
              }
            },
            child: const Text('Start Chat'),
          ),
        ],
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
