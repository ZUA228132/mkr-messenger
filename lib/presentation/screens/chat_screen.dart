import 'package:flutter/cupertino.dart';

import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';

/// Screen for displaying and sending messages in a chat
/// Requirements: 6.3 - Display messages in real-time with delivery status
/// Requirements: 6.5 - Mark messages as read and sync status
class ChatScreen extends StatefulWidget {
  final Chat chat;
  final String currentUserId;
  final List<Message> messages;
  final bool isConnected;
  final void Function(String content)? onSendMessage;
  final void Function(List<String> messageIds)? onMarkAsRead;
  final void Function()? onBack;

  const ChatScreen({
    super.key,
    required this.chat,
    required this.currentUserId,
    required this.messages,
    this.isConnected = true,
    this.onSendMessage,
    this.onMarkAsRead,
    this.onBack,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  final _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _markVisibleMessagesAsRead();
  }

  @override
  void didUpdateWidget(ChatScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.messages.length != oldWidget.messages.length) {
      _markVisibleMessagesAsRead();
    }
  }

  void _markVisibleMessagesAsRead() {
    final unreadIds = widget.messages
        .where((m) =>
            m.senderId != widget.currentUserId &&
            m.status != MessageStatus.read)
        .map((m) => m.id)
        .toList();

    if (unreadIds.isNotEmpty) {
      widget.onMarkAsRead?.call(unreadIds);
    }
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _sendMessage() {
    final content = _messageController.text.trim();
    if (content.isEmpty) return;

    widget.onSendMessage?.call(content);
    _messageController.clear();
    _focusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onBack,
          child: const Icon(CupertinoIcons.back),
        ),
        middle: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_getChatName()),
            if (!widget.isConnected)
              const Text(
                'Connecting...',
                style: TextStyle(
                  fontSize: 12,
                  color: CupertinoColors.systemGrey,
                ),
              ),
          ],
        ),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () {
            // TODO: Show chat info/settings
          },
          child: const Icon(CupertinoIcons.info),
        ),
      ),
      child: SafeArea(
        child: Column(
          children: [
            Expanded(child: _buildMessageList()),
            _buildInputBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageList() {
    if (widget.messages.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              CupertinoIcons.lock_shield,
              size: 48,
              color: CupertinoColors.systemGrey,
            ),
            SizedBox(height: 16),
            Text(
              'End-to-end encrypted',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
            SizedBox(height: 8),
            Text(
              'Messages are secured with Signal Protocol',
              style: TextStyle(
                color: CupertinoColors.systemGrey,
              ),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      controller: _scrollController,
      reverse: true,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      itemCount: widget.messages.length,
      itemBuilder: (context, index) {
        final message = widget.messages[index];
        final isMe = message.senderId == widget.currentUserId;
        final showDate = _shouldShowDate(index);

        return Column(
          children: [
            if (showDate) _buildDateSeparator(message.timestamp),
            _MessageBubble(
              message: message,
              isMe: isMe,
            ),
          ],
        );
      },
    );
  }

  bool _shouldShowDate(int index) {
    if (index == widget.messages.length - 1) return true;

    final current = widget.messages[index];
    final previous = widget.messages[index + 1];

    return !_isSameDay(current.timestamp, previous.timestamp);
  }

  bool _isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  Widget _buildDateSeparator(DateTime date) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Text(
        _formatDate(date),
        style: const TextStyle(
          fontSize: 12,
          color: CupertinoColors.systemGrey,
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    final now = DateTime.now();
    if (_isSameDay(date, now)) return 'Today';
    if (_isSameDay(date, now.subtract(const Duration(days: 1)))) {
      return 'Yesterday';
    }
    return '${date.day}/${date.month}/${date.year}';
  }


  Widget _buildInputBar() {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: const BoxDecoration(
        border: Border(
          top: BorderSide(
            color: CupertinoColors.separator,
            width: 0.5,
          ),
        ),
      ),
      child: Row(
        children: [
          CupertinoButton(
            padding: const EdgeInsets.all(8),
            onPressed: () {
              // TODO: Attachment picker
            },
            child: const Icon(CupertinoIcons.paperclip),
          ),
          Expanded(
            child: CupertinoTextField(
              controller: _messageController,
              focusNode: _focusNode,
              placeholder: 'Message',
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey6,
                borderRadius: BorderRadius.circular(20),
              ),
              maxLines: 4,
              minLines: 1,
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => _sendMessage(),
            ),
          ),
          CupertinoButton(
            padding: const EdgeInsets.all(8),
            onPressed: _sendMessage,
            child: const Icon(CupertinoIcons.arrow_up_circle_fill),
          ),
        ],
      ),
    );
  }

  String _getChatName() {
    if (widget.chat.name != null && widget.chat.name!.isNotEmpty) {
      return widget.chat.name!;
    }
    if (widget.chat.type == ChatType.direct) {
      final otherId = widget.chat.participantIds.firstWhere(
        (id) => id != widget.currentUserId,
        orElse: () => 'Unknown',
      );
      return '@$otherId';
    }
    return 'Chat';
  }
}

class _MessageBubble extends StatelessWidget {
  final Message message;
  final bool isMe;

  const _MessageBubble({
    required this.message,
    required this.isMe,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment:
            isMe ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          if (isMe) const Spacer(flex: 1),
          Flexible(
            flex: 3,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: isMe
                    ? CupertinoColors.systemBlue
                    : CupertinoColors.systemGrey5,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(18),
                  topRight: const Radius.circular(18),
                  bottomLeft: Radius.circular(isMe ? 18 : 4),
                  bottomRight: Radius.circular(isMe ? 4 : 18),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  _buildContent(),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _formatTime(message.timestamp),
                        style: TextStyle(
                          fontSize: 11,
                          color: isMe
                              ? CupertinoColors.white.withValues(alpha: 0.7)
                              : CupertinoColors.systemGrey,
                        ),
                      ),
                      if (isMe) ...[
                        const SizedBox(width: 4),
                        _buildStatusIcon(),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ),
          if (!isMe) const Spacer(flex: 1),
        ],
      ),
    );
  }

  Widget _buildContent() {
    switch (message.type) {
      case MessageType.text:
        return Text(
          message.content,
          style: TextStyle(
            fontSize: 16,
            color: isMe ? CupertinoColors.white : CupertinoColors.black,
          ),
        );
      case MessageType.image:
        return const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.photo, size: 20),
            SizedBox(width: 8),
            Text('Photo'),
          ],
        );
      case MessageType.video:
        return const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.videocam, size: 20),
            SizedBox(width: 8),
            Text('Video'),
          ],
        );
      case MessageType.audio:
        return const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.waveform, size: 20),
            SizedBox(width: 8),
            Text('Audio'),
          ],
        );
      case MessageType.file:
        return const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.doc, size: 20),
            SizedBox(width: 8),
            Text('File'),
          ],
        );
    }
  }

  Widget _buildStatusIcon() {
    IconData icon;
    Color color = CupertinoColors.white.withValues(alpha: 0.7);

    switch (message.status) {
      case MessageStatus.sending:
        icon = CupertinoIcons.clock;
        break;
      case MessageStatus.sent:
        icon = CupertinoIcons.checkmark;
        break;
      case MessageStatus.delivered:
        icon = CupertinoIcons.checkmark_alt_circle;
        break;
      case MessageStatus.read:
        icon = CupertinoIcons.checkmark_alt_circle_fill;
        color = CupertinoColors.white;
        break;
      case MessageStatus.failed:
        icon = CupertinoIcons.exclamationmark_circle;
        color = CupertinoColors.systemRed;
        break;
    }

    return Icon(icon, size: 14, color: color);
  }

  String _formatTime(DateTime time) {
    return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }
}
