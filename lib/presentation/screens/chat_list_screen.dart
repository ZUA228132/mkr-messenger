import 'package:flutter/cupertino.dart';

import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';

/// Screen displaying list of chats
/// Requirements: 4.1-4.4 - Chat list with backend integration
/// Requirements: 6.3 - Display messages in real-time with delivery status
class ChatListScreen extends StatefulWidget {
  final List<Chat> chats;
  final String currentUserId;
  final bool isLoading;
  final String? errorMessage;
  final void Function(Chat chat)? onChatTap;
  final void Function()? onNewChat;
  final Future<void> Function()? onRefresh;

  const ChatListScreen({
    super.key,
    required this.chats,
    required this.currentUserId,
    this.isLoading = false,
    this.errorMessage,
    this.onChatTap,
    this.onNewChat,
    this.onRefresh,
  });

  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Chats'),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onNewChat,
          child: const Icon(CupertinoIcons.square_pencil),
        ),
      ),
      child: SafeArea(
        child: _buildContent(),
      ),
    );
  }

  Widget _buildContent() {
    if (widget.isLoading && widget.chats.isEmpty) {
      return const Center(
        child: CupertinoActivityIndicator(),
      );
    }

    if (widget.errorMessage != null && widget.chats.isEmpty) {
      return _buildErrorState();
    }

    if (widget.chats.isEmpty) {
      return _buildEmptyState();
    }

    return _buildChatList();
  }

  Widget _buildErrorState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            CupertinoIcons.exclamationmark_triangle,
            size: 64,
            color: CupertinoColors.systemRed,
          ),
          const SizedBox(height: 16),
          const Text(
            'Failed to load chats',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            widget.errorMessage!,
            style: const TextStyle(
              color: CupertinoColors.systemGrey,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          CupertinoButton.filled(
            onPressed: widget.onRefresh,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            CupertinoIcons.chat_bubble_2,
            size: 64,
            color: CupertinoColors.systemGrey,
          ),
          const SizedBox(height: 16),
          const Text(
            'No conversations yet',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Start a new chat to begin messaging',
            style: TextStyle(
              color: CupertinoColors.systemGrey,
            ),
          ),
          const SizedBox(height: 24),
          CupertinoButton.filled(
            onPressed: widget.onNewChat,
            child: const Text('New Chat'),
          ),
        ],
      ),
    );
  }

  Widget _buildChatList() {
    return CustomScrollView(
      slivers: [
        CupertinoSliverRefreshControl(
          onRefresh: widget.onRefresh,
        ),
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              final chat = widget.chats[index];
              return _ChatListItem(
                chat: chat,
                currentUserId: widget.currentUserId,
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


class _ChatListItem extends StatelessWidget {
  final Chat chat;
  final String currentUserId;
  final VoidCallback? onTap;

  const _ChatListItem({
    required this.chat,
    required this.currentUserId,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: const BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: CupertinoColors.separator,
              width: 0.5,
            ),
          ),
        ),
        child: Row(
          children: [
            _buildAvatar(),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          _getChatName(),
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (chat.lastMessage != null)
                        Text(
                          _formatTime(chat.lastMessage!.timestamp),
                          style: const TextStyle(
                            fontSize: 12,
                            color: CupertinoColors.systemGrey,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      if (chat.lastMessage != null) ...[
                        _buildStatusIcon(chat.lastMessage!.status),
                        const SizedBox(width: 4),
                      ],
                      Expanded(
                        child: Text(
                          _getLastMessagePreview(),
                          style: const TextStyle(
                            fontSize: 14,
                            color: CupertinoColors.systemGrey,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (chat.unreadCount > 0) ...[
                        const SizedBox(width: 8),
                        _buildUnreadBadge(),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAvatar() {
    return Container(
      width: 50,
      height: 50,
      decoration: BoxDecoration(
        color: CupertinoColors.systemBlue.withOpacity(0.2),
        shape: BoxShape.circle,
      ),
      child: chat.avatarUrl != null
          ? ClipOval(
              child: Image.network(
                chat.avatarUrl!,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _buildAvatarPlaceholder(),
              ),
            )
          : _buildAvatarPlaceholder(),
    );
  }

  Widget _buildAvatarPlaceholder() {
    return Center(
      child: Text(
        _getChatName().isNotEmpty ? _getChatName()[0].toUpperCase() : '?',
        style: const TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          color: CupertinoColors.systemBlue,
        ),
      ),
    );
  }

  Widget _buildUnreadBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: CupertinoColors.systemBlue,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        chat.unreadCount > 99 ? '99+' : chat.unreadCount.toString(),
        style: const TextStyle(
          color: CupertinoColors.white,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  Widget _buildStatusIcon(MessageStatus status) {
    IconData icon;
    Color color;

    switch (status) {
      case MessageStatus.sending:
        icon = CupertinoIcons.clock;
        color = CupertinoColors.systemGrey;
        break;
      case MessageStatus.sent:
        icon = CupertinoIcons.checkmark;
        color = CupertinoColors.systemGrey;
        break;
      case MessageStatus.delivered:
        icon = CupertinoIcons.checkmark_alt_circle;
        color = CupertinoColors.systemGrey;
        break;
      case MessageStatus.read:
        icon = CupertinoIcons.checkmark_alt_circle_fill;
        color = CupertinoColors.systemBlue;
        break;
      case MessageStatus.failed:
        icon = CupertinoIcons.exclamationmark_circle;
        color = CupertinoColors.systemRed;
        break;
    }

    return Icon(icon, size: 14, color: color);
  }

  String _getChatName() {
    if (chat.name != null && chat.name!.isNotEmpty) {
      return chat.name!;
    }
    // For direct chats, show the other participant's ID
    if (chat.type == ChatType.direct) {
      final otherId = chat.participantIds.firstWhere(
        (id) => id != currentUserId,
        orElse: () => 'Unknown',
      );
      return '@$otherId';
    }
    return 'Chat';
  }

  String _getLastMessagePreview() {
    if (chat.lastMessage == null) {
      return 'No messages yet';
    }

    final message = chat.lastMessage!;
    switch (message.type) {
      case MessageType.text:
        return message.content;
      case MessageType.image:
        return '📷 Photo';
      case MessageType.video:
        return '🎥 Video';
      case MessageType.audio:
        return '🎵 Audio';
      case MessageType.file:
        return '📎 File';
    }
  }

  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final diff = now.difference(time);

    if (diff.inDays == 0) {
      return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
    } else if (diff.inDays == 1) {
      return 'Yesterday';
    } else if (diff.inDays < 7) {
      const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
      return days[time.weekday - 1];
    } else {
      return '${time.day}/${time.month}/${time.year}';
    }
  }
}
