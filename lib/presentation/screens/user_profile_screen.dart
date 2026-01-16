import 'package:flutter/cupertino.dart';

import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/user.dart';

/// Экран профиля пользователя
class UserProfileScreen extends StatefulWidget {
  final String userId;
  final RemoteUserRepository? userRepository;
  final VoidCallback? onBack;
  final VoidCallback? onStartChat;
  final VoidCallback? onCall;
  final VoidCallback? onVideoCall;

  const UserProfileScreen({
    super.key,
    required this.userId,
    this.userRepository,
    this.onBack,
    this.onStartChat,
    this.onCall,
    this.onVideoCall,
  });

  @override
  State<UserProfileScreen> createState() => _UserProfileScreenState();
}

class _UserProfileScreenState extends State<UserProfileScreen> {
  User? _user;
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    if (widget.userRepository == null) {
      setState(() {
        _isLoading = false;
        _user = User(
          id: widget.userId,
          callsign: widget.userId,
          displayName: widget.userId,
          isVerified: false,
          createdAt: DateTime.now(),
        );
      });
      return;
    }

    final result = await widget.userRepository!.getUser(widget.userId);
    if (!mounted) return;

    result.fold(
      onSuccess: (user) => setState(() {
        _user = user;
        _isLoading = false;
      }),
      onFailure: (error) => setState(() {
        _error = error.message;
        _isLoading = false;
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      navigationBar: CupertinoNavigationBar(
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onBack ?? () => Navigator.pop(context),
          child: const Text('Закрыть'),
        ),
        middle: const Text('Профиль'),
      ),
      child: SafeArea(
        child: _isLoading
            ? const Center(child: CupertinoActivityIndicator())
            : _error != null
                ? _buildError()
                : _buildContent(),
      ),
    );
  }

  Widget _buildError() {
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
            _error!,
            style: const TextStyle(color: CupertinoColors.systemGrey),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 16),
          CupertinoButton(
            onPressed: _loadUser,
            child: const Text('Повторить'),
          ),
        ],
      ),
    );
  }

  Widget _buildContent() {
    final user = _user!;
    final displayName = user.displayName ?? user.callsign;
    final firstLetter = displayName.isNotEmpty ? displayName[0].toUpperCase() : '?';

    return SingleChildScrollView(
      child: Column(
        children: [
          const SizedBox(height: 32),
          // Avatar
          Container(
            width: 100,
            height: 100,
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
                firstLetter,
                style: const TextStyle(
                  fontSize: 42,
                  fontWeight: FontWeight.w600,
                  color: CupertinoColors.white,
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Name
          Text(
            displayName,
            style: const TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '@${user.callsign}',
            style: TextStyle(
              fontSize: 17,
              color: CupertinoColors.secondaryLabel.resolveFrom(context),
            ),
          ),
          const SizedBox(height: 8),
          // Online status
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: const BoxDecoration(
                  color: CupertinoColors.systemGreen,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                'В сети',
                style: TextStyle(
                  fontSize: 14,
                  color: CupertinoColors.secondaryLabel.resolveFrom(context),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          // Action buttons
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _buildActionButton(
                  icon: CupertinoIcons.chat_bubble_fill,
                  label: 'Сообщение',
                  onTap: widget.onStartChat,
                ),
                _buildActionButton(
                  icon: CupertinoIcons.phone_fill,
                  label: 'Звонок',
                  onTap: widget.onCall,
                ),
                _buildActionButton(
                  icon: CupertinoIcons.video_camera_solid,
                  label: 'Видео',
                  onTap: widget.onVideoCall,
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          // Info section
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: CupertinoColors.systemBackground.resolveFrom(context),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              children: [
                _buildInfoRow(
                  icon: CupertinoIcons.at,
                  title: 'Позывной',
                  value: user.callsign,
                ),
                _buildDivider(),
                if (user.bio != null && user.bio!.isNotEmpty) ...[
                  _buildInfoRow(
                    icon: CupertinoIcons.info_circle,
                    title: 'О себе',
                    value: user.bio!,
                  ),
                  _buildDivider(),
                ],
                _buildInfoRow(
                  icon: CupertinoIcons.lock_shield,
                  title: 'Шифрование',
                  value: 'End-to-end',
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          // Encryption info
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: CupertinoColors.systemGreen.withAlpha(25),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                const Icon(
                  CupertinoIcons.checkmark_shield_fill,
                  color: CupertinoColors.systemGreen,
                  size: 24,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Сообщения защищены сквозным шифрованием',
                    style: TextStyle(
                      fontSize: 14,
                      color: CupertinoColors.label.resolveFrom(context),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required String label,
    VoidCallback? onTap,
  }) {
    return GestureDetector(
      onTap: onTap ?? () {
        showCupertinoDialog(
          context: context,
          builder: (ctx) => CupertinoAlertDialog(
            title: const Text('Скоро'),
            content: const Text('Эта функция будет добавлена позже'),
            actions: [
              CupertinoDialogAction(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('OK'),
              ),
            ],
          ),
        );
      },
      child: Column(
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: CupertinoColors.systemBlue.withAlpha(25),
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              color: CupertinoColors.systemBlue,
              size: 24,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: CupertinoColors.systemBlue,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow({
    required IconData icon,
    required String title,
    required String value,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          Icon(
            icon,
            color: CupertinoColors.systemGrey,
            size: 22,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 13,
                    color: CupertinoColors.secondaryLabel.resolveFrom(context),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(fontSize: 17),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDivider() {
    return Padding(
      padding: const EdgeInsets.only(left: 50),
      child: Container(
        height: 0.5,
        color: CupertinoColors.separator.resolveFrom(context),
      ),
    );
  }
}
