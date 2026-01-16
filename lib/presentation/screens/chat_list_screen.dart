import 'package:flutter/cupertino.dart';

import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';
import '../../domain/entities/user.dart';

/// Экран списка чатов — чистый Apple стиль
class ChatListScreen extends StatefulWidget {
  final List<Chat> chats;
  final String currentUserId;
  final bool isLoading;
  final String? errorMessage;
  final void Function(Chat chat)? onChatTap;
  final void Function()? onNewChat;
  final Future<void> Function()? onRefresh;
  final RemoteUserRepository? userRepository;

  const ChatListScreen({
    super.key,
    required this.chats,
    required this.currentUserId,
    this.isLoading = false,
    this.errorMessage,
    this.onChatTap,
    this.onNewChat,
    this.onRefresh,
    this.userRepository,
  });

  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  final Map<String, User> _userCache = {};

  @override
  void initState() {
    super.initState();
    _loadUsers();
  }

  @override
  void didUpdateWidget(ChatListScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.chats != widget.chats) {
      _loadUsers();
    }
  }

  Future<void> _loadUsers() async {
    if (widget.userRepository == null) return;
    
    // Collect all participant IDs that we don't have cached
    final userIds = <String>{};
    for (final chat in widget.chats) {
      if (chat.type == ChatType.direct) {
        for (final id in chat.participantIds) {
          if (id != widget.currentUserId && !_userCache.containsKey(id)) {
            userIds.add(id);
          }
        }
      }
    }
    
    // Load users
    for (final userId in userIds) {
      final result = await widget.userRepository!.getUser(userId);
      if (!mounted) return;
      result.fold(
        onSuccess: (user) => setState(() => _userCache[userId] = user),
        onFailure: (_) {},
      );
    }
  }

  User? _getRecipientUser(Chat chat) {
    if (chat.type != ChatType.direct) return null;
    final recipientId = chat.participantIds.firstWhere(
      (id) => id != widget.currentUserId,
      orElse: () => '',
    );
    return _userCache[recipientId];
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Чаты'),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onNewChat,
          child: const Icon(CupertinoIcons.square_pencil),
        ),
      ),
      child: SafeArea(child: _buildContent()),
    );
  }

  Widget _buildContent() {
    if (widget.isLoading && widget.chats.isEmpty) {
      return const Center(child: CupertinoActivityIndicator());
    }

    if (widget.errorMessage != null && widget.chats.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(CupertinoIcons.exclamationmark_triangle, size: 48, color: CupertinoColors.systemRed),
              const SizedBox(height: 16),
              const Text('Не удалось загрузить', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              Text(widget.errorMessage!, style: const TextStyle(color: CupertinoColors.systemGrey), textAlign: TextAlign.center),
              const SizedBox(height: 24),
              CupertinoButton.filled(onPressed: widget.onRefresh, child: const Text('Повторить')),
            ],
          ),
        ),
      );
    }

    if (widget.chats.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(CupertinoIcons.chat_bubble_2, size: 48, color: CupertinoColors.systemGrey),
            const SizedBox(height: 16),
            const Text('Нет чатов', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            const Text('Начните новый разговор', style: TextStyle(color: CupertinoColors.systemGrey)),
            const SizedBox(height: 24),
            CupertinoButton.filled(onPressed: widget.onNewChat, child: const Text('Новый чат')),
          ],
        ),
      );
    }

    return CustomScrollView(
      slivers: [
        CupertinoSliverRefreshControl(onRefresh: widget.onRefresh),
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              final chat = widget.chats[index];
              final recipientUser = _getRecipientUser(chat);
              return _ChatTile(
                chat: chat,
                currentUserId: widget.currentUserId,
                recipientUser: recipientUser,
                onTap: () => widget.onChatTap?.call(chat),
              );
            },
            childCount: widget.chats.length,
          ),
        ),
      ],
    );
  }
}

class _ChatTile extends StatelessWidget {
  final Chat chat;
  final String currentUserId;
  final User? recipientUser;
  final VoidCallback? onTap;

  const _ChatTile({
    required this.chat,
    required this.currentUserId,
    this.recipientUser,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    // Use recipient user info for direct chats, or chat name for groups
    String name;
    if (chat.type == ChatType.direct && recipientUser != null) {
      name = recipientUser!.displayName ?? recipientUser!.callsign ?? 'Пользователь';
    } else if (chat.name != null && chat.name!.isNotEmpty) {
      name = chat.name!;
    } else if (chat.type == ChatType.direct) {
      // Fallback - show loading or generic name
      name = 'Загрузка...';
    } else {
      name = 'Чат';
    }
    
    final preview = chat.lastMessage == null ? 'Нет сообщений' : _preview(chat.lastMessage!);
    final time = chat.lastMessage != null ? _time(chat.lastMessage!.timestamp) : '';
    final isOnline = recipientUser?.isOnline ?? false;

    return CupertinoListTile(
      onTap: onTap,
      leading: Stack(
        children: [
          Container(
            width: 50, height: 50,
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
                name.isNotEmpty ? name[0].toUpperCase() : '?',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: CupertinoColors.white),
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
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Text(preview, maxLines: 1, overflow: TextOverflow.ellipsis),
      additionalInfo: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(time, style: const TextStyle(fontSize: 12, color: CupertinoColors.systemGrey)),
          if (chat.unreadCount > 0) ...[
            const SizedBox(height: 4),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(color: CupertinoColors.activeBlue, borderRadius: BorderRadius.circular(10)),
              child: Text(chat.unreadCount > 99 ? '99+' : '${chat.unreadCount}', style: const TextStyle(color: CupertinoColors.white, fontSize: 12, fontWeight: FontWeight.w600)),
            ),
          ],
        ],
      ),
    );
  }

  String _preview(Message m) {
    switch (m.type) {
      case MessageType.text: return m.content;
      case MessageType.image: return '📷 Фото';
      case MessageType.video: return '🎥 Видео';
      case MessageType.audio: return '🎵 Аудио';
      case MessageType.file: return '📎 Файл';
      case MessageType.voiceNote: return '🎤 Голосовое';
      case MessageType.videoNote: return '⭕ Кружок';
    }
  }

  String _time(DateTime t) {
    final now = DateTime.now();
    final diff = now.difference(t);
    if (diff.inDays == 0) return '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
    if (diff.inDays == 1) return 'Вчера';
    if (diff.inDays < 7) return ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'][t.weekday - 1];
    return '${t.day}.${t.month}';
  }
}
