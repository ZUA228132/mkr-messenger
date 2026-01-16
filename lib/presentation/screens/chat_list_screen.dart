import 'package:flutter/cupertino.dart';

import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';

/// Экран списка чатов — чистый Apple стиль
class ChatListScreen extends StatelessWidget {
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
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Чаты'),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: onNewChat,
          child: const Icon(CupertinoIcons.square_pencil),
        ),
      ),
      child: SafeArea(child: _buildContent()),
    );
  }

  Widget _buildContent() {
    if (isLoading && chats.isEmpty) {
      return const Center(child: CupertinoActivityIndicator());
    }

    if (errorMessage != null && chats.isEmpty) {
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
              Text(errorMessage!, style: const TextStyle(color: CupertinoColors.systemGrey), textAlign: TextAlign.center),
              const SizedBox(height: 24),
              CupertinoButton.filled(onPressed: onRefresh, child: const Text('Повторить')),
            ],
          ),
        ),
      );
    }

    if (chats.isEmpty) {
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
            CupertinoButton.filled(onPressed: onNewChat, child: const Text('Новый чат')),
          ],
        ),
      );
    }

    return CustomScrollView(
      slivers: [
        CupertinoSliverRefreshControl(onRefresh: onRefresh),
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) => _ChatTile(chat: chats[index], currentUserId: currentUserId, onTap: () => onChatTap?.call(chats[index])),
            childCount: chats.length,
          ),
        ),
      ],
    );
  }
}

class _ChatTile extends StatelessWidget {
  final Chat chat;
  final String currentUserId;
  final VoidCallback? onTap;

  const _ChatTile({required this.chat, required this.currentUserId, this.onTap});

  @override
  Widget build(BuildContext context) {
    final name = chat.name ?? (chat.type == ChatType.direct 
        ? '@${chat.participantIds.firstWhere((id) => id != currentUserId, orElse: () => 'user')}'
        : 'Чат');
    final preview = chat.lastMessage == null ? 'Нет сообщений' : _preview(chat.lastMessage!);
    final time = chat.lastMessage != null ? _time(chat.lastMessage!.timestamp) : '';

    return CupertinoListTile(
      onTap: onTap,
      leading: Container(
        width: 50, height: 50,
        decoration: BoxDecoration(color: CupertinoColors.activeBlue.withOpacity(0.2), shape: BoxShape.circle),
        child: Center(child: Text(name.isNotEmpty ? name[0].toUpperCase() : '?', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: CupertinoColors.activeBlue))),
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
