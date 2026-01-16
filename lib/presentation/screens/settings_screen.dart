import 'package:flutter/cupertino.dart';

import '../../core/constants/app_constants.dart';

/// Settings screen
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
class SettingsScreen extends StatelessWidget {
  final String callsign;
  final VoidCallback? onLogout;

  const SettingsScreen({
    super.key,
    required this.callsign,
    this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Settings'),
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
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemGrey6,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(
              color: CupertinoColors.systemBlue.withOpacity(0.2),
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                callsign.isNotEmpty ? callsign[0].toUpperCase() : '?',
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: CupertinoColors.systemBlue,
                ),
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '@$callsign',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Online',
                  style: TextStyle(
                    color: CupertinoColors.systemGreen,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
          CupertinoButton(
            padding: EdgeInsets.zero,
            onPressed: () {},
            child: const Icon(CupertinoIcons.pencil),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsSection(BuildContext context) {
    return CupertinoListSection.insetGrouped(
      header: const Text('SETTINGS'),
      children: [
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.bell, color: CupertinoColors.systemBlue),
          title: const Text('Notifications'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.lock, color: CupertinoColors.systemBlue),
          title: const Text('Privacy'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.paintbrush, color: CupertinoColors.systemBlue),
          title: const Text('Appearance'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.clock, color: CupertinoColors.systemBlue),
          title: const Text('Auto-Delete'),
          subtitle: const Text('Messages delete after 24h'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
      ],
    );
  }

  Widget _buildAboutSection() {
    return CupertinoListSection.insetGrouped(
      header: const Text('ABOUT'),
      children: [
        const CupertinoListTile(
          leading: Icon(CupertinoIcons.info, color: CupertinoColors.systemGrey),
          title: Text('Version'),
          additionalInfo: Text(AppConstants.appVersion),
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.doc_text, color: CupertinoColors.systemGrey),
          title: const Text('Terms of Service'),
          trailing: const CupertinoListTileChevron(),
          onTap: () {},
        ),
        CupertinoListTile(
          leading: const Icon(CupertinoIcons.hand_raised, color: CupertinoColors.systemGrey),
          title: const Text('Privacy Policy'),
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
        onPressed: () => _showLogoutConfirmation(context),
        child: const Text(
          'Logout',
          style: TextStyle(color: CupertinoColors.systemRed),
        ),
      ),
    );
  }

  void _showLogoutConfirmation(BuildContext context) {
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Logout'),
        content: const Text('Are you sure you want to logout?'),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () {
              Navigator.pop(context);
              onLogout?.call();
            },
            child: const Text('Logout'),
          ),
        ],
      ),
    );
  }
}
