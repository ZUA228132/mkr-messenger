import 'package:flutter/cupertino.dart';

import '../../core/constants/app_constants.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/user.dart';

/// Settings screen with profile editing
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
    final displayName = widget.user?.displayName ?? widget.user?.callsign ?? 'User';
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
                        'Verified',
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


  /// Requirements: 7.2 - POST /api/users/me
  void _showEditProfileDialog(BuildContext context) {
    final displayNameController = TextEditingController(
      text: widget.user?.displayName ?? '',
    );

    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Edit Profile'),
        content: Padding(
          padding: const EdgeInsets.only(top: 16),
          child: Column(
            children: [
              CupertinoTextField(
                controller: displayNameController,
                placeholder: 'Display Name',
                padding: const EdgeInsets.all(12),
              ),
            ],
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
            onPressed: () async {
              Navigator.pop(context);
              await _updateProfile(displayNameController.text.trim());
            },
            child: const Text('Save'),
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
        _showSuccess('Profile updated successfully');
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
        title: const Text('Success'),
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
        onPressed: _isUpdating ? null : () => _showLogoutConfirmation(context),
        child: _isUpdating
            ? const CupertinoActivityIndicator()
            : const Text(
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
              widget.onLogout?.call();
            },
            child: const Text('Logout'),
          ),
        ],
      ),
    );
  }
}
