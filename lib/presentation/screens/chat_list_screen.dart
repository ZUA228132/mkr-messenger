import 'dart:ui';

import 'package:flutter/cupertino.dart';

import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';

/// Экран списка чатов с liquid glass дизайном
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
  static const Color _white = Color(0xFFFFFFFF);

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: const Color(0xFF0A0A1A),
      navigationBar: CupertinoNavigationBar(
        backgroundColor: const Color(0xFF0A0A1A).withOpacity(0.9),
        middle: const Text('Чаты', style: TextStyle(color: CupertinoColors.white)),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onNewChat,
          child: const Icon(CupertinoIcons.square_pencil, color: Color(0xFF00D4FF)),
        ),
      ),
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF0A0A1A), Color(0xFF1A1A3E), Color(0xFF0F0F2A)],
          ),
        ),
        child: SafeArea(child: _buildContent()),
      ),
    );
  }

  Widget _buildContent() {
    if (widget.isLoading && widget.chats.isEmpty) {
      return const Center(child: CupertinoActivityIndicator(color: Color(0xFF00D4FF)));
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
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80, height: 80,
              decoration: BoxDecoration(
                color: const Color(0xFFFF4757).withOpacity(0.15),
                shape: BoxShape.circle,
              ),
              child: const Icon(CupertinoIcons.exclamationmark_triangle_fill, size: 40, color: Color(0xFFFF4757)),
            ),
            const SizedBox(height: 24),
            const Text('Не удалось загрузить чаты', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: _white)),
            const SizedBox(height: 8),
            Text(widget.errorMessage!, style: TextStyle(color: _white.withOpacity(0.5)), textAlign: TextAlign.center),
            const SizedBox(height: 24),
            _buildGradientButton('Повторить', widget.onRefresh),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80, height: 80,
              decoration: BoxDecoration(
                gradient: LinearGradient(colors: [const Color(0xFF00D4FF).withOpacity(0.2), const Color(0xFF7B68EE).withOpacity(0.2)]),
                shape: BoxShape.circle,
              ),
              child: Icon(CupertinoIcons.chat_bubble_2, size: 40, color: _white.withOpacity(0.5)),
            ),
            const SizedBox(height: 24),
            const Text('Пока нет чатов', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: _white)),
            const SizedBox(height: 8),
            Text('Начните новый чат для общения', style: TextStyle(color: _white.withOpacity(0.5))),
            const SizedBox(height: 24),
            _buildGradientButton('Новый чат', widget.onNewChat),
          ],
        ),
      ),
    );
  }

  Widget _buildGradientButton(String text, VoidCallback? onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 14),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFF00D4FF), Color(0xFF7B68EE)]),
          borderRadius: BorderRadius.circular(14),
          boxShadow: [BoxShadow(color: const Color(0xFF00D4FF).withOpacity(0.3), blurRadius: 12, offset: const Offset(0, 4))],
        ),
        child: Text(text, style: const TextStyle(color: _white, fontSize: 16, fontWeight: FontWeight.w600)),
      ),
    );
  }

  Widget _buildChatList() {
    return CustomScrollView(
      slivers: [
        CupertinoSliverRefreshControl(onRefresh: widget.onRefresh),
        SliverPadding(
          padding: const EdgeInsets.all(16),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) {
                final chat = widget.chats[index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _ChatListItem(
                    chat: chat,
                    currentUserId: widget.currentUserId,
                    onTap: () => widget.onChatTap?.call(chat),
                  ),
                );
              },
              childCount: widget.chats.length,
            ),
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

  const _ChatListItem({required this.chat, required this.currentUserId, this.onTap});

  static const Color _white = Color(0xFFFFFFFF);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [_white.withOpacity(0.1), _white.withOpacity(0.05)],
              ),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: _white.withOpacity(0.1)),
            ),
            child: Row(
              children: [
                _buildAvatar(),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(child: Text(_getChatName(), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _white), maxLines: 1, overflow: TextOverflow.ellipsis)),
                          if (chat.lastMessage != null) Text(_formatTime(chat.lastMessage!.timestamp), style: TextStyle(fontSize: 12, color: _white.withOpacity(0.4))),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          if (chat.lastMessage != null) ...[_buildStatusIcon(chat.lastMessage!.status), const SizedBox(width: 4)],
                          Expanded(child: Text(_getLastMessagePreview(), style: TextStyle(fontSize: 14, color: _white.withOpacity(0.5)), maxLines: 1, overflow: TextOverflow.ellipsis)),
                          if (chat.unreadCount > 0) ...[const SizedBox(width: 8), _buildUnreadBadge()],
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAvatar() {
    return Container(
      width: 50, height: 50,
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [Color(0xFF00D4FF), Color(0xFF7B68EE)]),
        shape: BoxShape.circle,
      ),
      child: Center(child: Text(_getChatName().isNotEmpty ? _getChatName()[0].toUpperCase() : '?', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: _white))),
    );
  }

  Widget _buildUnreadBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [Color(0xFF00D4FF), Color(0xFF7B68EE)]),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(chat.unreadCount > 99 ? '99+' : chat.unreadCount.toString(), style: const TextStyle(color: _white, fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }

  Widget _buildStatusIcon(MessageStatus status) {
    IconData icon;
    Color color;
    switch (status) {
      case MessageStatus.sending: icon = CupertinoIcons.clock; color = _white.withOpacity(0.4);
      case MessageStatus.sent: icon = CupertinoIcons.checkmark; color = _white.withOpacity(0.4);
      case MessageStatus.delivered: icon = CupertinoIcons.checkmark_alt_circle; color = _white.withOpacity(0.4);
      case MessageStatus.read: icon = CupertinoIcons.checkmark_alt_circle_fill; color = const Color(0xFF00D4FF);
      case MessageStatus.failed: icon = CupertinoIcons.exclamationmark_circle; color = const Color(0xFFFF4757);
    }
    return Icon(icon, size: 14, color: color);
  }

  String _getChatName() {
    if (chat.name != null && chat.name!.isNotEmpty) return chat.name!;
    if (chat.type == ChatType.direct) {
      final otherId = chat.participantIds.firstWhere((id) => id != currentUserId, orElse: () => 'Неизвестный');
      return '@$otherId';
    }
    return 'Чат';
  }

  String _getLastMessagePreview() {
    if (chat.lastMessage == null) return 'Нет сообщений';
    final message = chat.lastMessage!;
    switch (message.type) {
      case MessageType.text: return message.content;
      case MessageType.image: return '📷 Фото';
      case MessageType.video: return '🎥 Видео';
      case MessageType.audio: return '🎵 Аудио';
      case MessageType.file: return '📎 Файл';
    }
  }

  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final diff = now.difference(time);
    if (diff.inDays == 0) return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
    if (diff.inDays == 1) return 'Вчера';
    if (diff.inDays < 7) {
      const days = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
      return days[time.weekday - 1];
    }
    return '${time.day}.${time.month}.${time.year}';
  }
}
