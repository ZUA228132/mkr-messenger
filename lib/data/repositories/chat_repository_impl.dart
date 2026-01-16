import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/errors/failures.dart';
import '../../core/utils/result.dart';
import '../../domain/entities/message.dart';
import '../../domain/repositories/chat_repository.dart';
import '../crypto/signal_protocol.dart';
import '../datasources/message_queue_datasource.dart';
import '../datasources/websocket_datasource.dart';

/// Implementation of ChatRepository with WebSocket support
/// Requirements: 6.1 - Send/receive messages via WebSocket, message queuing
class ChatRepositoryImpl implements ChatRepository {
  final WebSocketDatasource _webSocketDatasource;
  final MessageQueueDatasource _messageQueueDatasource;

  final _messagesController = StreamController<List<Message>>.broadcast();
  final Map<String, List<Message>> _chatMessages = {};
  StreamSubscription? _wsSubscription;

  ChatRepositoryImpl({
    required WebSocketDatasource webSocketDatasource,
    required MessageQueueDatasource messageQueueDatasource,
    SignalProtocol? signalProtocol, // Reserved for future encryption integration
  })  : _webSocketDatasource = webSocketDatasource,
        _messageQueueDatasource = messageQueueDatasource {
    _initializeMessageListener();
  }

  void _initializeMessageListener() {
    _wsSubscription = _webSocketDatasource.messages.listen(_handleWsMessage);
  }

  void _handleWsMessage(WsMessage wsMessage) {
    switch (wsMessage.type) {
      case WsMessageType.message:
        _handleIncomingMessage(wsMessage.data);
        break;
      case WsMessageType.messageStatus:
        _handleMessageStatus(wsMessage.data);
        break;
      case WsMessageType.read:
        _handleReadReceipt(wsMessage.data);
        break;
      default:
        break;
    }
  }

  void _handleIncomingMessage(Map<String, dynamic> data) {
    try {
      final message = Message(
        id: data['id'] as String,
        chatId: data['chat_id'] as String,
        senderId: data['sender_id'] as String,
        content: data['content'] as String,
        type: MessageType.values.firstWhere(
          (e) => e.name == data['type'],
          orElse: () => MessageType.text,
        ),
        timestamp: DateTime.parse(data['timestamp'] as String),
        status: MessageStatus.delivered,
        autoDelete: data['auto_delete'] != null
            ? AutoDeletePolicy.values.firstWhere(
                (e) => e.name == data['auto_delete'],
                orElse: () => AutoDeletePolicy.sevenDays,
              )
            : null,
      );

      // Add to local cache
      _chatMessages[message.chatId] ??= [];
      _chatMessages[message.chatId]!.insert(0, message);

      // Cache in database
      _messageQueueDatasource.cacheMessage(message);

      // Notify listeners
      _messagesController.add(_chatMessages[message.chatId]!);
    } catch (e) {
      debugPrint('Error handling incoming message: $e');
    }
  }

  void _handleMessageStatus(Map<String, dynamic> data) {
    final messageId = data['message_id'] as String?;
    final status = data['status'] as String?;
    final chatId = data['chat_id'] as String?;

    if (messageId == null || status == null || chatId == null) return;

    final newStatus = MessageStatus.values.firstWhere(
      (e) => e.name == status,
      orElse: () => MessageStatus.sent,
    );

    // Update local cache
    final messages = _chatMessages[chatId];
    if (messages != null) {
      final index = messages.indexWhere((m) => m.id == messageId);
      if (index != -1) {
        _chatMessages[chatId]![index] = messages[index].copyWith(status: newStatus);
        _messagesController.add(_chatMessages[chatId]!);
      }
    }

    // Update database
    _messageQueueDatasource.updateMessageStatus(messageId, newStatus);
  }

  void _handleReadReceipt(Map<String, dynamic> data) {
    final chatId = data['chat_id'] as String?;
    final messageIds = (data['message_ids'] as List?)?.cast<String>();

    if (chatId == null || messageIds == null) return;

    // Update local cache
    final messages = _chatMessages[chatId];
    if (messages != null) {
      for (final messageId in messageIds) {
        final index = messages.indexWhere((m) => m.id == messageId);
        if (index != -1) {
          _chatMessages[chatId]![index] =
              messages[index].copyWith(status: MessageStatus.read);
        }
      }
      _messagesController.add(_chatMessages[chatId]!);
    }
  }


  @override
  Future<Result<void>> connect(String accessToken) async {
    try {
      await _webSocketDatasource.connect(accessToken);
      return const Success(null);
    } catch (e) {
      return Error(WebSocketDisconnectedFailure());
    }
  }

  @override
  Future<void> disconnect() async {
    await _webSocketDatasource.disconnect();
  }

  @override
  bool get isConnected => _webSocketDatasource.isConnected;

  @override
  Stream<bool> get connectionState => _webSocketDatasource.connectionState;

  @override
  Future<Result<void>> sendMessage(String chatId, Message message) async {
    try {
      if (!_webSocketDatasource.isConnected) {
        // Queue for offline sending
        await _messageQueueDatasource.queueMessage(message);
        
        // Add to local cache with sending status
        _chatMessages[chatId] ??= [];
        _chatMessages[chatId]!.insert(0, message);
        _messagesController.add(_chatMessages[chatId]!);
        
        return const Success(null);
      }

      // Send via WebSocket
      _webSocketDatasource.send(WsMessage(
        type: WsMessageType.message,
        data: {
          'id': message.id,
          'chat_id': chatId,
          'content': message.content,
          'type': message.type.name,
          'auto_delete': message.autoDelete?.name,
        },
        timestamp: DateTime.now(),
      ));

      // Add to local cache
      _chatMessages[chatId] ??= [];
      _chatMessages[chatId]!.insert(0, message.copyWith(status: MessageStatus.sent));
      _messagesController.add(_chatMessages[chatId]!);

      // Cache in database
      await _messageQueueDatasource.cacheMessage(
        message.copyWith(status: MessageStatus.sent),
      );

      return const Success(null);
    } catch (e) {
      return Error(NetworkFailure(e.toString()));
    }
  }

  @override
  Stream<List<Message>> getMessages(String chatId) {
    // Load cached messages first
    _loadCachedMessages(chatId);
    
    return _messagesController.stream.map((messages) {
      return _chatMessages[chatId] ?? [];
    });
  }

  Future<void> _loadCachedMessages(String chatId) async {
    if (_chatMessages[chatId] != null && _chatMessages[chatId]!.isNotEmpty) {
      return;
    }

    final cached = await _messageQueueDatasource.getCachedMessages(chatId);
    _chatMessages[chatId] = cached;
    _messagesController.add(cached);
  }

  @override
  Future<Result<List<Message>>> getCachedMessages(String chatId) async {
    try {
      final messages = await _messageQueueDatasource.getCachedMessages(chatId);
      _chatMessages[chatId] = messages;
      return Success(messages);
    } catch (e) {
      return Error(DatabaseFailure());
    }
  }

  @override
  Future<Result<void>> queueOfflineMessage(Message message) async {
    try {
      await _messageQueueDatasource.queueMessage(message);
      return const Success(null);
    } catch (e) {
      return Error(DatabaseFailure());
    }
  }

  @override
  Future<Result<List<Message>>> getQueuedMessages() async {
    try {
      final messages = await _messageQueueDatasource.getQueuedMessages();
      return Success(messages);
    } catch (e) {
      return Error(DatabaseFailure());
    }
  }

  @override
  Future<Result<void>> syncQueuedMessages() async {
    if (!_webSocketDatasource.isConnected) {
      return Error(NetworkUnavailableFailure());
    }

    try {
      final queuedMessages = await _messageQueueDatasource.getQueuedMessages();

      for (final message in queuedMessages) {
        // Send via WebSocket
        _webSocketDatasource.send(WsMessage(
          type: WsMessageType.message,
          data: {
            'id': message.id,
            'chat_id': message.chatId,
            'content': message.content,
            'type': message.type.name,
            'auto_delete': message.autoDelete?.name,
          },
          timestamp: DateTime.now(),
        ));

        // Remove from queue
        await _messageQueueDatasource.removeFromQueue(message.id);

        // Update status in cache
        await _messageQueueDatasource.updateMessageStatus(
          message.id,
          MessageStatus.sent,
        );
      }

      return const Success(null);
    } catch (e) {
      return Error(NetworkFailure(e.toString()));
    }
  }

  @override
  Future<Result<void>> markAsRead(String chatId, List<String> messageIds) async {
    try {
      if (_webSocketDatasource.isConnected) {
        _webSocketDatasource.send(WsMessage(
          type: WsMessageType.read,
          data: {
            'chat_id': chatId,
            'message_ids': messageIds,
          },
          timestamp: DateTime.now(),
        ));
      }

      // Update local cache
      for (final messageId in messageIds) {
        await _messageQueueDatasource.updateMessageStatus(
          messageId,
          MessageStatus.read,
        );
      }

      return const Success(null);
    } catch (e) {
      return Error(NetworkFailure(e.toString()));
    }
  }

  @override
  Future<Result<void>> deleteMessage(String chatId, String messageId) async {
    try {
      // Remove from local cache
      _chatMessages[chatId]?.removeWhere((m) => m.id == messageId);
      _messagesController.add(_chatMessages[chatId] ?? []);

      // Remove from database
      await _messageQueueDatasource.deleteMessage(messageId);

      return const Success(null);
    } catch (e) {
      return Error(DatabaseFailure());
    }
  }

  /// Dispose resources
  void dispose() {
    _wsSubscription?.cancel();
    _messagesController.close();
  }
}
