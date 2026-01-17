import 'package:flutter/cupertino.dart';
import 'package:flutter/scheduler.dart';

import '../../data/repositories/remote_user_repository.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';
import '../../domain/entities/user.dart';

/// Экран списка чатов — чистый Apple стиль с Dynamic Island заголовком
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

class _ChatListScreenState extends State<ChatListScreen>
    with SingleTickerProviderStateMixin<ChatListScreen> {
  final Map<String, User> _userCache = {};
  ScrollController? _scrollController;
  bool _showDynamicIsland = false;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _scrollController!.addListener(_onScroll);
    _loadUsers();
  }

  @override
  void dispose() {
    _scrollController?.removeListener(_onScroll);
    _scrollController?.dispose();
    super.dispose();
  }

  void _onScroll() {
    // Show Dynamic Island when scrolling starts, hide when at top
    if (_scrollController!.hasClients && mounted) {
      final offset = _scrollController!.offset;

      if (offset > 50 && !_showDynamicIsland && mounted) {
        setState(() => _showDynamicIsland = true);
      } else if (offset < 10 && _showDynamicIsland && mounted) {
        setState(() => _showDynamicIsland = false);
      }
    }
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

    for (final userId in userIds) {
      final result = await widget.userRepository!.getUser(userId);
      if (!mounted) return;
      result.fold(
        onSuccess: (user) => setState(() => _userCache[userId] = user)),
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
      child: Stack(
        children: [
          CustomScrollView(
            controller: _scrollController,
            slivers: [
              // Large Title с поддержкой collapse
              CupertinoSliverNavigationBar(
                largeTitle: Row(
                  children: [
                    // MKR Logo
                    Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF6366F1), const Color(0xFF8B5CF6)],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Center(
                        child: Text(
                          'M',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w800,
                            color: CupertinoColors.white,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    const Text('Чаты'),
                  ],
                ),
                trailing: CupertinoButton(
                  padding: EdgeInsets.zero,
                  onPressed: widget.onNewChat,
                  child: const Icon(CupertinoIcons.square_pencil, size: 24),
                ),
                border: null,
              ),
              // Pull to refresh
              CupertinoSliverRefreshControl(onRefresh: widget.onRefresh),
              // Контент
              _buildSliverContent(),
            ],
          ),
          // Dynamic Island overlay - поверх navigation bar
          Positioned(
            top: 50,
            left: 16,
            right: 16,
            child: _buildDynamicIslandHeader(context),
          ),
        ],
      ),
    );
  }

  /// Dynamic Island стиль заголовок для скриншотов - появляется при скролле
  Widget _buildDynamicIslandHeader(BuildContext context) {
    return AnimatedOpacity(
      opacity: _showDynamicIsland ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeInOut,
      child: Container(
        height: 36,
        decoration: BoxDecoration(
          color: const Color(0xFF000000).withAlpha(235),
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFF000000).withAlpha(30),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                CupertinoIcons.lock_shield_fill,
                color: CupertinoColors.white,
                size: 18,
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'MKR Messenger',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: CupertinoColors.white,
                  ),
                ),
                Text(
                  'Защищённое соединение',
                  style: TextStyle(
                    fontSize: 11,
                    color: CupertinoColors.systemGrey.withOpacity(0.7),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSliverContent() {
    if (widget.isLoading && widget.chats.isEmpty) {
      return const SliverFillRemaining(
        child: Center(child: CupertinoActivityIndicator()),
      );
    }

    if (widget.errorMessage != null && widget.chats.isEmpty) {
      return SliverFillRemaining(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(CupertinoIcons.exclamationmark_triangle, size: 48, color: CoronaColor(0xFF8B5CF6)),
                const SizedBox(height: 16),
                Text('Не удалось загрузить', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                Text(widget.errorMessage!, style: TextStyle(color: CupertinoColors.systemGrey), textAlign: TextAlign.center),
                const SizedBox(height: ),
                CupertinoButton.filled(onPressed: widget.onRefresh, child: const Text('Повторить')),
              ],
            ),
          ),
        ),
      );
    }

    if (widget.chats.isEmpty) {
      return SliverFillRemaining(
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                    const Color(0xFF6366F1).withAlpha(30),
                    const Color(0xFF8B5CF6).withAlpha(20),
                  ],
                ),
                  shape: BoxShape.circle,
                ),
                child: const Icon(CupertinoIcons.chat_bubble_2, size: 40, color: Color(0xFF6366F1)),
              ),
              const SizedBox(height: 16),
              const Text('Нет чатов', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              const Text('Начните новый разговор', style: TextStyle(color: CupertinoColors.systemGrey)),
              const SizedBox(height: 24),
              CupertinoButton.filled(onPressed: widget.onNewChat, child: const Text('Новый чат')),
            ],
          ),
        ),
      );
    }

    return SliverList(
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
    final time = chat.lastMessage != null ? _time(chat.lastMessage.timestamp) : '';
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
