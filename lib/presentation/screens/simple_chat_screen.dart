import 'dart:async';
import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:image_picker/image_picker.dart';

import '../../data/datasources/websocket_service.dart';
import '../../data/repositories/remote_call_repository.dart';
import '../../data/repositories/remote_chat_repository.dart';
import '../../data/repositories/remote_message_repository.dart';
import '../../data/repositories/remote_user_repository.dart';
import '../../data/services/livekit_service.dart';
import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';
import '../../domain/entities/user.dart';
import 'call_screen.dart';

/// Simple chat screen for direct messaging with backend integration
/// Requirements: 5.1-5.4 - Message retrieval, display, sending, real-time updates
/// Requirements: 6.3 - Send typing indicators
class SimpleChatScreen extends StatefulWidget {
  final String chatId;
  final String currentUserId;
  final RemoteMessageRepository? messageRepository;
  final RemoteUserRepository? userRepository;
  final RemoteChatRepository? chatRepository;
  final RemoteCallRepository? callRepository;
  final LiveKitService? liveKitService;
  final WebSocketService? webSocketService;
  final VoidCallback? onBack;

  const SimpleChatScreen({
    super.key,
    required this.chatId,
    required this.currentUserId,
    this.messageRepository,
    this.userRepository,
    this.chatRepository,
    this.callRepository,
    this.liveKitService,
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
  Chat? _chat;
  User? _recipientUser;
  String? _recipientUserId;
  bool _isLoading = false;
  bool _isLoadingMore = false;
  bool _hasMore = true;
  bool _isSending = false;
  bool _isRecording = false;
  bool _isRecordingVideo = false;
  int _recordingDuration = 0;
  String? _errorMessage;
  StreamSubscription<Message>? _messageSubscription;
  StreamSubscription<WsConnectionState>? _connectionSubscription;
  WsConnectionState _connectionState = WsConnectionState.disconnected;
  Timer? _typingTimer;
  Timer? _recordingTimer;

  @override
  void initState() {
    super.initState();
    _loadChatInfo();
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
    _recordingTimer?.cancel();
    super.dispose();
  }

  /// Load chat info and then recipient user
  Future<void> _loadChatInfo() async {
    if (widget.chatRepository == null) {
      // Fallback: try to load user directly by chatId (old behavior)
      _loadRecipientUser(widget.chatId);
      return;
    }
    
    final result = await widget.chatRepository!.getChat(widget.chatId);
    if (!mounted) return;
    
    result.fold(
      onSuccess: (chat) {
        setState(() => _chat = chat);
        // Find recipient (participant who is not current user)
        final recipientId = chat.participantIds.firstWhere(
          (id) => id != widget.currentUserId,
          orElse: () => chat.participantIds.isNotEmpty ? chat.participantIds.first : '',
        );
        if (recipientId.isNotEmpty) {
          setState(() => _recipientUserId = recipientId);
          // Try to get name from participantNames first
          final nameFromChat = chat.getParticipantName(recipientId);
          if (nameFromChat != null && nameFromChat.isNotEmpty) {
            // We have the name from chat, but still load user for online status
            _loadRecipientUser(recipientId);
          } else {
            _loadRecipientUser(recipientId);
          }
        }
      },
      onFailure: (_) {
        // Fallback: try to load user directly
        _loadRecipientUser(widget.chatId);
      },
    );
  }

  /// Load recipient user info
  Future<void> _loadRecipientUser(String userId) async {
    if (widget.userRepository == null) return;
    
    final result = await widget.userRepository!.getUser(userId);
    if (!mounted) return;
    
    result.fold(
      onSuccess: (user) => setState(() => _recipientUser = user),
      onFailure: (_) {}, // Silently fail, will show chatId as fallback
    );
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
      widget.chatId,
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
      if (message.chatId == widget.chatId && mounted) {
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
      widget.chatId,
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
          chatId: widget.chatId,
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
      chatId: widget.chatId,
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
      chatId: widget.chatId,
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
              chatId: widget.chatId,
              senderId: widget.currentUserId,
              content: content,
              type: MessageType.text,
              timestamp: DateTime.now(),
              status: MessageStatus.failed,
            );
          }
          _isSending = false;
        });
        // Show more user-friendly error message
        String errorMsg = 'Не удалось отправить сообщение';
        if (error.statusCode == 401) {
          errorMsg = 'Сессия истекла. Войдите заново';
        } else if (error.statusCode == 404) {
          errorMsg = 'Чат не найден';
        } else if (error.statusCode == 500) {
          errorMsg = 'Ошибка сервера. Попробуйте позже';
        } else if (error.message.contains('network') || error.message.contains('connection')) {
          errorMsg = 'Нет подключения к интернету';
        }
        _showError(errorMsg);
      },
    );
  }

  /// Requirements: 6.3 - Send typing events via WebSocket
  void _onTyping() {
    _typingTimer?.cancel();
    widget.messageRepository?.sendTypingIndicator(widget.chatId);
    
    // Debounce typing events
    _typingTimer = Timer(const Duration(seconds: 3), () {});
  }

  /// Start voice recording
  void _startVoiceRecording() {
    setState(() {
      _isRecording = true;
      _isRecordingVideo = false;
      _recordingDuration = 0;
    });
    
    _recordingTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
        setState(() => _recordingDuration++);
      }
    });
  }

  /// Start video note recording
  void _startVideoRecording() {
    setState(() {
      _isRecording = true;
      _isRecordingVideo = true;
      _recordingDuration = 0;
    });
    
    _recordingTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
        setState(() => _recordingDuration++);
        // Max 60 seconds for video notes
        if (_recordingDuration >= 60) {
          _stopRecording();
        }
      }
    });
  }

  /// Stop recording and send
  void _stopRecording() {
    _recordingTimer?.cancel();
    
    if (_recordingDuration < 1) {
      _cancelRecording();
      return;
    }

    final type = _isRecordingVideo ? MessageType.videoNote : MessageType.voiceNote;
    final duration = _recordingDuration;
    
    setState(() {
      _isRecording = false;
      _isRecordingVideo = false;
      _recordingDuration = 0;
    });

    // Add optimistic message
    final tempId = 'temp_${DateTime.now().millisecondsSinceEpoch}';
    final optimisticMessage = Message(
      id: tempId,
      chatId: widget.chatId,
      senderId: widget.currentUserId,
      content: _isRecordingVideo ? 'Видео-кружок' : 'Голосовое сообщение',
      type: type,
      timestamp: DateTime.now(),
      status: MessageStatus.sending,
      duration: duration,
    );

    setState(() {
      _messages.insert(0, optimisticMessage);
    });

    // TODO: Upload media file and send message
    // For now, simulate success after delay
    Future.delayed(const Duration(seconds: 1), () {
      if (mounted) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = optimisticMessage.copyWith(status: MessageStatus.sent);
          }
        });
      }
    });
  }

  /// Cancel recording
  void _cancelRecording() {
    _recordingTimer?.cancel();
    setState(() {
      _isRecording = false;
      _isRecordingVideo = false;
      _recordingDuration = 0;
    });
  }

  String _formatDuration(int seconds) {
    final mins = seconds ~/ 60;
    final secs = seconds % 60;
    return '${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
  }

  /// Show attachment options
  void _showAttachmentOptions() {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        actions: [
          CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(ctx);
              _pickImage();
            },
            child: const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(CupertinoIcons.photo, color: CupertinoColors.systemBlue),
                SizedBox(width: 8),
                Text('Фото'),
              ],
            ),
          ),
          CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(ctx);
              _pickVideo();
            },
            child: const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(CupertinoIcons.videocam, color: CupertinoColors.systemGreen),
                SizedBox(width: 8),
                Text('Видео'),
              ],
            ),
          ),
          CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(ctx);
              _showError('Отправка файлов будет добавлена в следующем обновлении');
            },
            child: const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(CupertinoIcons.doc, color: CupertinoColors.systemOrange),
                SizedBox(width: 8),
                Text('Файл'),
              ],
            ),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(
          isDestructiveAction: true,
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Отмена'),
        ),
      ),
    );
  }

  Future<void> _pickImage() async {
    try {
      final picker = ImagePicker();
      final XFile? image = await picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
      );

      if (image != null && mounted) {
        _sendImageMessage(File(image.path));
      }
    } catch (e) {
      if (mounted) {
        _showError('Не удалось выбрать фото: $e');
      }
    }
  }

  Future<void> _pickVideo() async {
    try {
      final picker = ImagePicker();
      final XFile? video = await picker.pickVideo(
        source: ImageSource.gallery,
      );

      if (video != null && mounted) {
        _sendVideoMessage(File(video.path));
      }
    } catch (e) {
      if (mounted) {
        _showError('Не удалось выбрать видео: $e');
      }
    }
  }

  Future<void> _sendImageMessage(File imageFile) async {
    if (widget.messageRepository == null) {
      _showError('Отправка изображений недоступна');
      return;
    }

    // Add optimistic message
    final tempId = 'temp_${DateTime.now().millisecondsSinceEpoch}';
    final optimisticMessage = Message(
      id: tempId,
      chatId: widget.chatId,
      senderId: widget.currentUserId,
      content: imageFile.path,
      type: MessageType.image,
      timestamp: DateTime.now(),
      status: MessageStatus.sending,
    );

    setState(() {
      _messages.insert(0, optimisticMessage);
      _isSending = true;
    });

    // Upload image to server
    final result = await widget.messageRepository!.sendMediaMessage(
      chatId: widget.chatId,
      filePath: imageFile.path,
      mediaType: 'IMAGE',
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (message) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = message;
          }
          _isSending = false;
        });
      },
      onFailure: (error) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = optimisticMessage.copyWith(
              status: MessageStatus.failed,
            );
          }
          _isSending = false;
        });
        _showError('Не удалось отправить фото: ${error.message}');
      },
    );
  }

  Future<void> _sendVideoMessage(File videoFile) async {
    if (widget.messageRepository == null) {
      _showError('Отправка видео недоступна');
      return;
    }

    // Add optimistic message
    final tempId = 'temp_${DateTime.now().millisecondsSinceEpoch}';
    final optimisticMessage = Message(
      id: tempId,
      chatId: widget.chatId,
      senderId: widget.currentUserId,
      content: videoFile.path,
      type: MessageType.video,
      timestamp: DateTime.now(),
      status: MessageStatus.sending,
    );

    setState(() {
      _messages.insert(0, optimisticMessage);
      _isSending = true;
    });

    // Upload video to server
    final result = await widget.messageRepository!.sendMediaMessage(
      chatId: widget.chatId,
      filePath: videoFile.path,
      mediaType: 'VIDEO',
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (message) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = message;
          }
          _isSending = false;
        });
      },
      onFailure: (error) {
        setState(() {
          final index = _messages.indexWhere((m) => m.id == tempId);
          if (index != -1) {
            _messages[index] = optimisticMessage.copyWith(
              status: MessageStatus.failed,
            );
          }
          _isSending = false;
        });
        _showError('Не удалось отправить видео: ${error.message}');
      },
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

  @override
  Widget build(BuildContext context) {
    // Get recipient name from multiple sources with priority:
    // 1. User displayName (most accurate, from user profile)
    // 2. User callsign
    // 3. Chat participantNames (from chat API)
    // 4. Chat name
    // 5. Loading placeholder
    String displayName;
    if (_recipientUser?.displayName != null && _recipientUser!.displayName!.isNotEmpty) {
      displayName = _recipientUser!.displayName!;
    } else if (_recipientUser?.callsign != null && _recipientUser!.callsign!.isNotEmpty) {
      displayName = _recipientUser!.callsign!;
    } else if (_recipientUserId != null && _chat?.getParticipantName(_recipientUserId!) != null) {
      displayName = _chat!.getParticipantName(_recipientUserId!)!;
    } else if (_chat?.name != null && _chat!.name!.isNotEmpty) {
      displayName = _chat!.name!;
    } else if (_isLoading) {
      displayName = 'Загрузка...';
    } else {
      displayName = 'Чат';
    }
    final firstLetter = displayName.isNotEmpty ? displayName[0].toUpperCase() : '?';
    
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: widget.onBack ?? () => Navigator.pop(context),
          child: const Icon(CupertinoIcons.back),
        ),
        middle: GestureDetector(
          onTap: () => _showUserProfile(context),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Mini avatar
              Container(
                width: 32,
                height: 32,
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
                    firstLetter,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: CupertinoColors.white,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    displayName,
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                  ),
                  Text(
                    _getStatusText(),
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.normal,
                      color: _getStatusColor(),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            CupertinoButton(
              padding: EdgeInsets.zero,
              onPressed: () => _startVoiceCall(context),
              child: const Icon(CupertinoIcons.phone_fill),
            ),
          ],
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

  void _showComingSoon(BuildContext context, String feature) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Скоро'),
        content: Text('$feature будут добавлены в следующем обновлении'),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  /// Start a voice call
  void _startVoiceCall(BuildContext context) {
    final displayName = _recipientUser?.displayName ?? 
                        _recipientUser?.callsign ?? 
                        _chat?.name ?? 
                        'Пользователь';
    
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: const Text('Голосовой звонок'),
        content: Text('Позвонить $displayName?'),
        actions: [
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Отмена'),
          ),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () {
              Navigator.pop(ctx);
              _initiateCall(context, displayName);
            },
            child: const Text('Позвонить'),
          ),
        ],
      ),
    );
  }

  void _initiateCall(BuildContext context, String displayName) async {
    if (widget.callRepository == null || widget.liveKitService == null) {
      _showError('Звонки недоступны');
      return;
    }
    
    final recipientId = _recipientUserId ?? widget.chatId;
    
    // Show loading dialog
    showCupertinoDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => CupertinoAlertDialog(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CupertinoActivityIndicator(),
            const SizedBox(width: 12),
            Flexible(child: Text('Вызов $displayName...')),
          ],
        ),
        actions: [
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () {
              Navigator.pop(ctx);
              widget.liveKitService?.endCall();
            },
            child: const Text('Отмена'),
          ),
        ],
      ),
    );
    
    // Get call token from backend
    final result = await widget.callRepository!.getCallToken(
      calleeId: recipientId,
      isVideo: false,
    );
    
    if (!context.mounted) return;
    Navigator.pop(context); // Close loading dialog
    
    result.fold(
      onSuccess: (callResponse) {
        // Open call screen
        Navigator.of(context).push(
          CupertinoPageRoute(
            fullscreenDialog: true,
            builder: (_) => CallScreen(
              recipientId: recipientId,
              recipientName: displayName,
              isVideo: false,
              isIncoming: false,
              token: callResponse.token,
              liveKitService: widget.liveKitService!,
              onCallEnded: () {
                // Optionally report call end to backend
              },
            ),
          ),
        );
      },
      onFailure: (error) {
        String errorMsg = 'Не удалось начать звонок';
        if (error.statusCode == 401) {
          errorMsg = 'Сессия истекла. Войдите заново';
        } else if (error.statusCode == 404) {
          errorMsg = 'Пользователь не найден';
        } else if (error.message.contains('network') || error.message.contains('connection')) {
          errorMsg = 'Нет подключения к интернету';
        }
        _showError(errorMsg);
      },
    );
  }

  String _getStatusText() {
    if (_recipientUser != null) {
      if (_recipientUser!.isOnline) {
        return 'В сети';
      } else if (_recipientUser!.lastSeen != null) {
        return _formatLastSeen(_recipientUser!.lastSeen!);
      }
    }
    return _getConnectionStatus();
  }

  Color _getStatusColor() {
    if (_recipientUser?.isOnline == true) {
      return CupertinoColors.systemGreen;
    }
    if (_connectionState == WsConnectionState.connected) {
      return CupertinoColors.systemGrey;
    }
    return CupertinoColors.systemOrange;
  }

  String _formatLastSeen(DateTime lastSeen) {
    final now = DateTime.now();
    final diff = now.difference(lastSeen);
    
    if (diff.inMinutes < 1) return 'был(а) только что';
    if (diff.inMinutes < 60) return 'был(а) ${diff.inMinutes} мин. назад';
    if (diff.inHours < 24) return 'был(а) ${diff.inHours} ч. назад';
    if (diff.inDays == 1) return 'был(а) вчера';
    return 'был(а) ${lastSeen.day}.${lastSeen.month}';
  }

  void _showUserProfile(BuildContext context) {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        height: MediaQuery.of(context).size.height * 0.85,
        decoration: BoxDecoration(
          color: CupertinoColors.systemGroupedBackground.resolveFrom(context),
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          children: [
            // Handle
            Container(
              margin: const EdgeInsets.only(top: 8),
              width: 36,
              height: 5,
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey3,
                borderRadius: BorderRadius.circular(3),
              ),
            ),
            Expanded(
              child: _UserProfileContent(
                user: _recipientUser,
                recipientId: widget.chatId,
                onClose: () => Navigator.pop(ctx),
                onCall: () {
                  Navigator.pop(ctx);
                  _showComingSoon(context, 'Звонки');
                },
                onVideoCall: () {
                  Navigator.pop(ctx);
                  _showComingSoon(context, 'Видеозвонки');
                },
              ),
            ),
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
      final emptyDisplayName = _recipientUser?.displayName ?? 
                               _recipientUser?.callsign ?? 
                               _chat?.name ?? 
                               'Новый чат';
      final emptyFirstLetter = emptyDisplayName.isNotEmpty ? emptyDisplayName[0].toUpperCase() : '?';
      
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: CupertinoColors.systemBlue.withAlpha(25),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  emptyFirstLetter,
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
              emptyDisplayName,
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
            if (_recipientUser?.callsign != null) ...[
              const SizedBox(height: 4),
              Text(
                '@${_recipientUser!.callsign}',
                style: TextStyle(
                  fontSize: 14,
                  color: CupertinoColors.secondaryLabel.resolveFrom(context),
                ),
              ),
            ],
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
      chatId: widget.chatId,
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
    // Recording mode UI
    if (_isRecording) {
      return Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: CupertinoColors.systemBlue.withAlpha(25),
          border: const Border(
            top: BorderSide(color: CupertinoColors.separator, width: 0.5),
          ),
        ),
        child: Row(
          children: [
            // Cancel button
            CupertinoButton(
              padding: const EdgeInsets.all(8),
              onPressed: _cancelRecording,
              child: const Icon(CupertinoIcons.xmark_circle_fill, 
                color: CupertinoColors.systemGrey, size: 28),
            ),
            // Recording indicator
            Expanded(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 12,
                    height: 12,
                    decoration: const BoxDecoration(
                      color: CupertinoColors.systemRed,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _formatDuration(_recordingDuration),
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                      color: CupertinoColors.systemBlue,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'Голосовое',
                    style: TextStyle(
                      fontSize: 14,
                      color: CupertinoColors.systemGrey,
                    ),
                  ),
                ],
              ),
            ),
            // Send button
            CupertinoButton(
              padding: const EdgeInsets.all(8),
              onPressed: _stopRecording,
              child: const Icon(
                CupertinoIcons.arrow_up_circle_fill,
                size: 32,
                color: CupertinoColors.systemBlue,
              ),
            ),
          ],
        ),
      );
    }

    // Normal input mode
    final hasText = _messageController.text.trim().isNotEmpty;
    
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: const BoxDecoration(
        border: Border(
          top: BorderSide(color: CupertinoColors.separator, width: 0.5),
        ),
      ),
      child: Row(
        children: [
          // Attachment button
          CupertinoButton(
            padding: const EdgeInsets.all(8),
            onPressed: _showAttachmentOptions,
            child: const Icon(CupertinoIcons.paperclip),
          ),
          // Text input
          Expanded(
            child: CupertinoTextField(
              controller: _messageController,
              placeholder: 'Сообщение',
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey6,
                borderRadius: BorderRadius.circular(20),
              ),
              maxLines: 4,
              minLines: 1,
              textInputAction: TextInputAction.send,
              onChanged: (_) {
                _onTyping();
                setState(() {}); // Update UI for send button
              },
              onSubmitted: (_) => _sendMessage(),
            ),
          ),
          // Voice/Video/Send buttons
          if (hasText) ...[
            // Send text button
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
          ] else ...[
            // Voice note button only (removed video circles)
            GestureDetector(
              onLongPressStart: (_) => _startVoiceRecording(),
              onLongPressEnd: (_) => _stopRecording(),
              child: CupertinoButton(
                padding: const EdgeInsets.all(8),
                onPressed: () {
                  // Short tap shows hint
                  showCupertinoDialog(
                    context: context,
                    builder: (ctx) => CupertinoAlertDialog(
                      title: const Text('Голосовое сообщение'),
                      content: const Text('Удерживайте кнопку для записи'),
                      actions: [
                        CupertinoDialogAction(
                          onPressed: () => Navigator.pop(ctx),
                          child: const Text('OK'),
                        ),
                      ],
                    ),
                  );
                },
                child: const Icon(
                  CupertinoIcons.mic_fill,
                  size: 24,
                  color: CupertinoColors.systemBlue,
                ),
              ),
            ),
          ],
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
                    : CupertinoColors.systemGrey5.resolveFrom(context),
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
                  _buildContent(context),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _formatTime(message.timestamp),
                        style: TextStyle(
                          fontSize: 11,
                          color: isMe
                              ? CupertinoColors.white.withAlpha(179)
                              : CupertinoColors.secondaryLabel.resolveFrom(context),
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

  Widget _buildContent(BuildContext context) {
    final textColor = isMe ? CupertinoColors.white : CupertinoColors.label.resolveFrom(context);

    switch (message.type) {
      case MessageType.text:
        return Text(
          message.content,
          style: TextStyle(
            fontSize: 16,
            color: textColor,
          ),
        );
      case MessageType.image:
        return _buildImageContent(context);
      case MessageType.video:
        return _buildVideoContent(context);
      case MessageType.audio:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.waveform, size: 20, color: textColor),
            const SizedBox(width: 8),
            Text('Аудио', style: TextStyle(color: textColor)),
          ],
        );
      case MessageType.file:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.doc, size: 20, color: textColor),
            const SizedBox(width: 8),
            Text('Файл', style: TextStyle(color: textColor)),
          ],
        );
      case MessageType.voiceNote:
        return _buildVoiceNote(context);
      case MessageType.videoNote:
        return _buildVideoNote(context);
    }
  }

  Widget _buildImageContent(BuildContext context) {
    // Check if content is a URL (starts with http) or local file path
    final isNetworkImage = message.content.startsWith('http');

    if (isNetworkImage) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Image.network(
          message.content,
          width: 200,
          fit: BoxFit.cover,
          errorBuilder: (ctx, error, stackTrace) {
            return Container(
              width: 200,
              height: 150,
              decoration: BoxDecoration(
                color: isMe
                    ? CupertinoColors.white.withAlpha(51)
                    : CupertinoColors.systemGrey5,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(CupertinoIcons.photo, size: 32),
                  const SizedBox(height: 8),
                  Text(
                    'Не удалось загрузить',
                    style: TextStyle(
                      fontSize: 12,
                      color: isMe ? CupertinoColors.white : CupertinoColors.systemGrey,
                    ),
                  ),
                ],
              ),
            );
          },
          loadingBuilder: (ctx, child, loadingProgress) {
            if (loadingProgress == null) return child;
            return Container(
              width: 200,
              height: 150,
              decoration: BoxDecoration(
                color: isMe
                    ? CupertinoColors.white.withAlpha(51)
                    : CupertinoColors.systemGrey5,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const CupertinoActivityIndicator(),
            );
          },
        ),
      );
    } else {
      // Local file (during sending)
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (message.status == MessageStatus.sending) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Image.file(
                File(message.content),
                width: 200,
                fit: BoxFit.cover,
                errorBuilder: (ctx, error, stackTrace) {
                  return Container(
                    width: 200,
                    height: 150,
                    decoration: BoxDecoration(
                      color: isMe
                          ? CupertinoColors.white.withAlpha(51)
                          : CupertinoColors.systemGrey5,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(CupertinoIcons.photo, size: 32),
                  );
                },
              ),
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(
                  width: 12,
                  height: 12,
                  child: CupertinoActivityIndicator(),
                ),
                const SizedBox(width: 6),
                Text(
                  'Отправка...',
                  style: TextStyle(
                    fontSize: 11,
                    color: isMe ? CupertinoColors.white.withAlpha(179) : CupertinoColors.systemGrey,
                  ),
                ),
              ],
            ),
          ] else if (message.status == MessageStatus.sent) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Image.file(
                File(message.content),
                width: 200,
                fit: BoxFit.cover,
              ),
            ),
          ] else ...[
            // Failed status
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(CupertinoIcons.exclamationmark_triangle, size: 16, color: CupertinoColors.systemRed),
                const SizedBox(width: 6),
                Text(
                  'Не отправлено',
                  style: TextStyle(
                    fontSize: 12,
                    color: CupertinoColors.systemRed,
                  ),
                ),
              ],
            ),
          ],
        ],
      );
    }
  }

  Widget _buildVideoContent(BuildContext context) {
    final isNetworkVideo = message.content.startsWith('http');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 200,
          height: 150,
          decoration: BoxDecoration(
            color: isMe
                ? CupertinoColors.white.withAlpha(51)
                : CupertinoColors.systemGrey5,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Stack(
            alignment: Alignment.center,
            children: [
              if (isNetworkVideo && message.status == MessageStatus.sent)
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.network(
                    message.content,
                    width: 200,
                    height: 150,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                  ),
                )
              else if (!isNetworkVideo)
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.file(
                    File(message.content),
                    width: 200,
                    height: 150,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                  ),
                ),
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: CupertinoColors.black.withAlpha(128),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  CupertinoIcons.play_fill,
                  color: CupertinoColors.white,
                  size: 24,
                ),
              ),
            ],
          ),
        ),
        if (message.status == MessageStatus.sending) ...[
          const SizedBox(height: 4),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(
                width: 12,
                height: 12,
                child: CupertinoActivityIndicator(),
              ),
              const SizedBox(width: 6),
              Text(
                'Отправка...',
                style: TextStyle(
                  fontSize: 11,
                  color: isMe ? CupertinoColors.white.withAlpha(179) : CupertinoColors.systemGrey,
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildVoiceNote(BuildContext context) {
    final duration = message.duration ?? 0;
    final mins = duration ~/ 60;
    final secs = duration % 60;
    final durationText = '${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
    final secondaryColor = isMe 
        ? CupertinoColors.white.withAlpha(179)
        : CupertinoColors.secondaryLabel.resolveFrom(context);
    
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: isMe 
                ? CupertinoColors.white.withAlpha(51)
                : CupertinoColors.systemRed.withAlpha(51),
            shape: BoxShape.circle,
          ),
          child: Icon(
            CupertinoIcons.play_fill,
            size: 18,
            color: isMe ? CupertinoColors.white : CupertinoColors.systemRed,
          ),
        ),
        const SizedBox(width: 8),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Waveform placeholder
            Row(
              children: List.generate(12, (i) => Container(
                width: 3,
                height: 8 + (i % 3) * 4.0,
                margin: const EdgeInsets.symmetric(horizontal: 1),
                decoration: BoxDecoration(
                  color: secondaryColor,
                  borderRadius: BorderRadius.circular(2),
                ),
              )),
            ),
            const SizedBox(height: 4),
            Text(
              durationText,
              style: TextStyle(
                fontSize: 12,
                color: secondaryColor,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildVideoNote(BuildContext context) {
    final duration = message.duration ?? 0;
    final mins = duration ~/ 60;
    final secs = duration % 60;
    final durationText = '${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
    
    return Column(
      children: [
        // Circle video placeholder
        Container(
          width: 200,
          height: 200,
          decoration: BoxDecoration(
            color: CupertinoColors.systemPurple.withAlpha(51),
            shape: BoxShape.circle,
            border: Border.all(
              color: CupertinoColors.systemPurple,
              width: 3,
            ),
          ),
          child: Stack(
            alignment: Alignment.center,
            children: [
              const Icon(
                CupertinoIcons.play_circle_fill,
                size: 48,
                color: CupertinoColors.systemPurple,
              ),
              Positioned(
                bottom: 16,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: CupertinoColors.black.withAlpha(128),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    durationText,
                    style: const TextStyle(
                      fontSize: 12,
                      color: CupertinoColors.white,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildStatusIcon() {
    IconData icon;
    Color color = CupertinoColors.white.withAlpha(179);

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


/// Inline user profile content for modal
class _UserProfileContent extends StatelessWidget {
  final User? user;
  final String recipientId;
  final VoidCallback onClose;
  final VoidCallback? onCall;
  final VoidCallback? onVideoCall;

  const _UserProfileContent({
    required this.user,
    required this.recipientId,
    required this.onClose,
    this.onCall,
    this.onVideoCall,
  });

  @override
  Widget build(BuildContext context) {
    final displayName = user?.displayName ?? user?.callsign ?? recipientId;
    final firstLetter = displayName.isNotEmpty ? displayName[0].toUpperCase() : '?';
    final isOnline = user?.isOnline ?? false;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          // Close button
          Align(
            alignment: Alignment.topRight,
            child: CupertinoButton(
              padding: EdgeInsets.zero,
              onPressed: onClose,
              child: const Text('Готово'),
            ),
          ),
          const SizedBox(height: 8),
          // Avatar
          Container(
            width: 100,
            height: 100,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [CupertinoColors.systemBlue, CupertinoColors.systemIndigo],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              shape: BoxShape.circle,
              image: user?.avatarUrl != null
                  ? DecorationImage(
                      image: NetworkImage(user!.avatarUrl!),
                      fit: BoxFit.cover,
                    )
                  : null,
            ),
            child: user?.avatarUrl == null
                ? Center(
                    child: Text(
                      firstLetter,
                      style: const TextStyle(
                        fontSize: 42,
                        fontWeight: FontWeight.w600,
                        color: CupertinoColors.white,
                      ),
                    ),
                  )
                : null,
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
          if (user?.callsign != null && user?.displayName != null) ...[
            const SizedBox(height: 4),
            Text(
              '@${user!.callsign}',
              style: TextStyle(
                fontSize: 16,
                color: CupertinoColors.secondaryLabel.resolveFrom(context),
              ),
            ),
          ],
          const SizedBox(height: 8),
          // Online status
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: isOnline ? CupertinoColors.systemGreen : CupertinoColors.systemGrey,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                isOnline ? 'В сети' : _formatLastSeen(user?.lastSeen),
                style: TextStyle(
                  fontSize: 14,
                  color: CupertinoColors.secondaryLabel.resolveFrom(context),
                ),
              ),
            ],
          ),
          // Bio
          if (user?.bio != null && user!.bio!.isNotEmpty) ...[
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: CupertinoColors.systemGrey6.resolveFrom(context),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                user!.bio!,
                style: const TextStyle(fontSize: 15),
                textAlign: TextAlign.center,
              ),
            ),
          ],
          const SizedBox(height: 24),
          // Action buttons
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildActionButton(
                context,
                icon: CupertinoIcons.phone_fill,
                label: 'Звонок',
                onTap: onCall,
              ),
              _buildActionButton(
                context,
                icon: CupertinoIcons.video_camera_solid,
                label: 'Видео',
                onTap: onVideoCall,
              ),
              _buildActionButton(
                context,
                icon: CupertinoIcons.bell_slash_fill,
                label: 'Без звука',
                onTap: () => _showComingSoon(context),
              ),
            ],
          ),
          const SizedBox(height: 32),
          // Encryption info
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: CupertinoColors.systemGreen.withAlpha(25),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                const Icon(
                  CupertinoIcons.lock_shield_fill,
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
          // Verified badge
          if (user?.isVerified == true) ...[
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: CupertinoColors.systemBlue.withAlpha(25),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  const Icon(
                    CupertinoIcons.checkmark_seal_fill,
                    color: CupertinoColors.systemBlue,
                    size: 24,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Верифицированный пользователь',
                      style: TextStyle(
                        fontSize: 14,
                        color: CupertinoColors.label.resolveFrom(context),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _formatLastSeen(DateTime? lastSeen) {
    if (lastSeen == null) return 'Не в сети';
    
    final now = DateTime.now();
    final diff = now.difference(lastSeen);
    
    if (diff.inMinutes < 1) return 'был(а) только что';
    if (diff.inMinutes < 60) return 'был(а) ${diff.inMinutes} мин. назад';
    if (diff.inHours < 24) return 'был(а) ${diff.inHours} ч. назад';
    if (diff.inDays == 1) return 'был(а) вчера';
    return 'был(а) ${lastSeen.day}.${lastSeen.month}';
  }

  void _showComingSoon(BuildContext context) {
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
  }

  Widget _buildActionButton(BuildContext context, {
    required IconData icon,
    required String label,
    VoidCallback? onTap,
  }) {
    return GestureDetector(
      onTap: onTap ?? () => _showComingSoon(context),
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
}
