import 'dart:async';

import 'package:flutter/cupertino.dart';

import '../../data/datasources/websocket_service.dart';
import '../../data/repositories/remote_message_repository.dart';
import '../../domain/entities/message.dart';

/// Simple chat screen for direct messaging with backend integration
/// Requirements: 5.1-5.4 - Message retrieval, display, sending, real-time updates
/// Requirements: 6.3 - Send typing indicators
class SimpleChatScreen extends StatefulWidget {
  final String recipientId;
  final String currentUserId;
  final RemoteMessageRepository? messageRepository;
  final WebSocketService? webSocketService;
  final VoidCallback? onBack;

  const SimpleChatScreen({
    super.key,
    required this.recipientId,
    required this.currentUserId,
    this.messageRepository,
    this.webSocketService,
    this.onBack,
  });

  @override
  State<SimpleChatScreen> createState() => _SimpleChatScreenState();
}

class _SimpleChatScreenState extends State<SimpleChatScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  List<Message> _messages = [];
  bool _isLoading = false;
  bool _isLoadingMore = false;
  bool _hasMore = true;
  bool _isSending = false;
  String? _errorMessage;
  StreamSubscription<Message>? _messageSubscription;
  StreamSubscription<WsConnectionState>? _connectionSubscription;
  WsConnectionState _connectionState = WsConnectionState.disconnected;
  Timer? _typingTimer;

  @override
  void initState() {
    super.initState();
    _loadMessages();
    _subscribeToMessages();
    _subscribeToConnectionState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    _messageSubscription?.cancel();
    _connectionSubscription?.cancel();
    _typingTimer?.cancel();
    super.dispose();
  }

  /// Load more messages when scrolling to top
  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      _loadMoreMessages();
    }
  }

  /// Load older messages (pagination)
  Future<void> _loadMoreMessages() async {
    if (_isLoadingMore || !_hasMore || _messages.isEmpty) return;
    if (widget.messageRepository == null) return;

    setState(() => _isLoadingMore = true);

    final oldestMessage = _messages.last;
    final result = await widget.messageRepository!.getMessages(
      widget.recipientId,
      limit: 20,
      before: oldestMessage.id,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (messages) {
        setState(() {
          if (messages.isEmpty) {
            _hasMore = false;
          } else {
            _messages.addAll(messages);
          }
          _isLoadingMore = false;
        });
      },
      onFailure: (error) {
        setState(() => _isLoadingMore = false);
      },
    );
  }

  /// Requirements: 5.4 - Handle new messages via WebSocket
  void _subscribeToMessages() {
    _messageSubscription = widget.messageRepository?.newMessages.listen((message) {
      if (message.chatId == widget.recipientId && mounted) {
        setState(() {
          _messages.insert(0, message);
        });
      }
    });
  }

  void _subscribeToConnectionState() {
    _connectionSubscription = widget.webSocketService?.connectionState.listen((state) {
      if (mounted) {
        setState(() => _connectionState = state);
      }
    });
    // Get initial state
    _connectionState = widget.webSocketService?.currentState ?? WsConnectionState.disconnected;
  }

  /// Requirements: 5.1 - GET /api/messages/{chatId}
  Future<void> _loadMessages() async {
    if (widget.messageRepository == null) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final result = await widget.messageRepository!.getMessages(
      widget.recipientId,
      limit: 50,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (messages) {
        setState(() {
          _messages = messages;
          _isLoading = false;
          _hasMore = messages.length >= 50;
        });
      },
      onFailure: (error) {
        setState(() {
          _errorMessage = error.message;
          _isLoading = false;
        });
      },
    );
  }

  /// Requirements: 5.3 - POST /api/messages
  Future<void> _sendMessage() async {
    final content = _messageController.text.trim();
    if (content.isEmpty) return;

    // Clear input immediately for better UX
    _messageController.clear();

    if (widget.messageRepository == null) {
      // Fallback to local-only mode
      setState(() {
        _messages.insert(0, Message(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          chatId: widget.recipientId,
          senderId: widget.currentUserId,
          content: content,
          type: MessageType.text,
          timestamp: DateTime.now(),
          status: MessageStatus.sent,
        ));
      });
      return;
    }

    // Add optimistic message
    final tempId = 'temp_${DateTime.now().millisecondsSinceEpoch}';
    final optimisticMessage = Message(
      id: tempId,
      chatId: widget.recipientId,
      senderId: widget.currentUserId,
      content: content,
      type: MessageType.text,
      timestamp: DateTime.now(),
      status: MessageStatus.sending,
    );

    setState(() {
      _messages.insert(0, optimisticMessage);
      _isSending = true;
    });

    final result = await widget.messageRepository!.sendMessage(
      chatId: widget.recipientId,
      content: content,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (message) {
        setState(() {
          // Replace optimistic message with real one
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = message;
          }
          _isSending = false;
        });
      },
      onFailure: (error) {
        setState(() {
          // Mark message as failed
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = Message(
              id: tempId,
              chatId: widget.recipientId,
              senderId: widget.currentUserId,
              content: content,
              type: MessageType.text,
              timestamp: DateTime.now(),
              status: MessageStatus.failed,
            );
          }
          _isSending = false;
        });
        _showError('Не удалось отправить: ${error.message}');
      },
    );
  }

  /// Requirements: 6.3 - Send typing events via WebSocket
  void _onTyping() {
    _typingTimer?.cancel();
    widget.messageRepository?.sendTypingIndicator(widget.recipientId);
    
    // Debounce typing events
    _typingTimer = Timer(const Duration(seconds: 3), () {});
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

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onBack ?? () => Navigator.pop(context),
          child: const Icon(CupertinoIcons.back),
        ),
        middle: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('@${widget.recipientId}'),
            Text(
              _getConnectionStatus(),
              style: TextStyle(
                fontSize: 11,
                color: _connectionState == WsConnectionState.connected
                    ? CupertinoColors.systemGreen
                    : CupertinoColors.systemGrey,
              ),
            ),
          ],
        ),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () {},
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

  String _getConnectionStatus() {
    switch (_connectionState) {
      case WsConnectionState.connected:
        return 'End-to-end encrypted';
      case WsConnectionState.connecting:
        return 'Connecting...';
      case WsConnectionState.reconnecting:
        return 'Reconnecting...';
      case WsConnectionState.disconnected:
        return 'Offline';
    }
  }


  Widget _buildMessageList() {
    if (_isLoading) {
      return const Center(child: CupertinoActivityIndicator());
    }

    if (_errorMessage != null && _messages.isEmpty) {
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
              _errorMessage!,
              style: const TextStyle(color: CupertinoColors.systemGrey),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            CupertinoButton(
              onPressed: _loadMessages,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (_messages.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: CupertinoColors.systemBlue.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  widget.recipientId.isNotEmpty 
                      ? widget.recipientId[0].toUpperCase() 
                      : '?',
                  style: const TextStyle(
                    fontSize: 32,
                    fontWeight: FontWeight.bold,
                    color: CupertinoColors.systemBlue,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              '@${widget.recipientId}',
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            const Icon(
              CupertinoIcons.lock_shield,
              size: 20,
              color: CupertinoColors.systemGrey,
            ),
            const SizedBox(height: 4),
            const Text(
              'Messages are end-to-end encrypted',
              style: TextStyle(
                color: CupertinoColors.systemGrey,
                fontSize: 13,
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
      itemCount: _messages.length + (_isLoadingMore ? 1 : 0),
      itemBuilder: (context, index) {
        // Show loading indicator at the end (top when reversed)
        if (index == _messages.length) {
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CupertinoActivityIndicator()),
          );
        }
        final message = _messages[index];
        final isMe = message.senderId == widget.currentUserId;
        return _MessageBubble(
          message: message,
          isMe: isMe,
          onRetry: message.status == MessageStatus.failed ? () => _retrySendMessage(message) : null,
        );
      },
    );
  }

  /// Retry sending a failed message
  Future<void> _retrySendMessage(Message failedMessage) async {
    if (widget.messageRepository == null) return;

    // Update status to sending
    setState(() {
      final index = _messages.indexWhere((m) => m.id == failedMessage.id);
      if (index != -1) {
        _messages[index] = Message(
          id: failedMessage.id,
          chatId: failedMessage.chatId,
          senderId: failedMessage.senderId,
          content: failedMessage.content,
          type: failedMessage.type,
          timestamp: failedMessage.timestamp,
          status: MessageStatus.sending,
        );
      }
    });

    final result = await widget.messageRepository!.sendMessage(
      chatId: widget.recipientId,
      content: failedMessage.content,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (message) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == failedMessage.id);
          if (index != -1) {
            _messages[index] = message;
          }
        });
      },
      onFailure: (error) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == failedMessage.id);
          if (index != -1) {
            _messages[index] = Message(
              id: failedMessage.id,
              chatId: failedMessage.chatId,
              senderId: failedMessage.senderId,
              content: failedMessage.content,
              type: failedMessage.type,
              timestamp: failedMessage.timestamp,
              status: MessageStatus.failed,
            );
          }
        });
        _showError('Не удалось отправить: ${error.message}');
      },
    );
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
            onPressed: () {},
            child: const Icon(CupertinoIcons.paperclip),
          ),
          Expanded(
            child: CupertinoTextField(
              controller: _messageController,
              placeholder: 'Message',
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey6,
                borderRadius: BorderRadius.circular(20),
              ),
              maxLines: 4,
              minLines: 1,
              textInputAction: TextInputAction.send,
              onChanged: (_) => _onTyping(),
              onSubmitted: (_) => _sendMessage(),
            ),
          ),
          CupertinoButton(
            padding: const EdgeInsets.all(8),
            onPressed: _isSending ? null : _sendMessage,
            child: _isSending
                ? const CupertinoActivityIndicator()
                : const Icon(
                    CupertinoIcons.arrow_up_circle_fill,
                    size: 32,
                    color: CupertinoColors.systemBlue,
                  ),
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final Message message;
  final bool isMe;
  final VoidCallback? onRetry;

  const _MessageBubble({required this.message, required this.isMe, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: isMe ? MainAxisAlignment.end : MainAxisAlignment.start,
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
                              ? CupertinoColors.white.withOpacity(0.7)
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
    Color color = CupertinoColors.white.withOpacity(0.7);

    switch (message.status) {
      case MessageStatus.sending:
        return const CupertinoActivityIndicator(radius: 6);
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
        return GestureDetector(
          onTap: onRetry,
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(CupertinoIcons.exclamationmark_circle, size: 14, color: CupertinoColors.systemRed),
              SizedBox(width: 4),
              Text('Повторить', style: TextStyle(fontSize: 10, color: CupertinoColors.systemRed)),
            ],
          ),
        );
    }

    return Icon(icon, size: 14, color: color);
  }

  String _formatTime(DateTime time) {
    return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }
}
