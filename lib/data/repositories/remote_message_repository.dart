import 'dart:async';
import 'dart:developer' as developer;

import '../../core/error/api_error.dart';
import '../../core/result/result.dart';
import '../../domain/entities/message.dart';
import '../datasources/api_client.dart';
import '../datasources/websocket_service.dart';
import '../models/message_models.dart';

/// Remote Message Repository for MKR Backend integration
///
/// Requirements: 5.1-5.4 - Message retrieval, display, sending, and real-time updates
class RemoteMessageRepository {
  final ApiClient _apiClient;
  final WebSocketService _webSocketService;
  
  final _newMessagesController = StreamController<Message>.broadcast();
  StreamSubscription<WsMessage>? _wsSubscription;

  RemoteMessageRepository({
    required ApiClient apiClient,
    required WebSocketService webSocketService,
  })  : _apiClient = apiClient,
        _webSocketService = webSocketService {
    _subscribeToWebSocket();
  }

  /// Stream of new messages received via WebSocket
  /// Requirements: 5.4 - Add new messages to chat view immediately
  Stream<Message> get newMessages => _newMessagesController.stream;

  /// Subscribe to WebSocket messages for real-time updates
  /// Requirements: 5.4 - Handle new message via WebSocket
  void _subscribeToWebSocket() {
    _wsSubscription?.cancel();
    _wsSubscription = _webSocketService.messages.listen((wsMessage) {
      if (wsMessage.messageType == WsMessageType.newMessage) {
        try {
          final payload = NewMessagePayload.fromJson(wsMessage.payload);
          final message = payload.toEntity();
          
          developer.log(
            'Received new message via WebSocket: ${message.id}',
            name: 'RemoteMessageRepository',
          );
          
          _newMessagesController.add(message);
        } catch (e) {
          developer.log(
            'Failed to parse WebSocket message: $e',
            name: 'RemoteMessageRepository',
          );
        }
      }
    });
  }


  /// Get messages for a chat
  /// Requirements: 5.1 - GET /api/messages/{chatId}
  /// Requirements: 5.2 - Display messages with sender info, content, timestamp
  Future<Result<List<Message>>> getMessages(
    String chatId, {
    int? limit,
    String? before,
  }) async {
    developer.log(
      'Fetching messages for chat: $chatId (limit: $limit, before: $before)',
      name: 'RemoteMessageRepository',
    );

    final queryParams = <String, dynamic>{};
    if (limit != null) {
      queryParams['limit'] = limit;
    }
    if (before != null) {
      queryParams['before'] = before;
    }

    final result = await _apiClient.get(
      '/api/messages/$chatId',
      queryParameters: queryParams.isNotEmpty ? queryParams : null,
    );

    return result.fold(
      onSuccess: (response) {
        try {
          final messagesResponse = MessagesListResponse.fromJson(response.data);
          final messages = messagesResponse.toEntities();

          developer.log(
            'Fetched ${messages.length} messages for chat: $chatId',
            name: 'RemoteMessageRepository',
          );

          return Success(messages);
        } catch (e) {
          developer.log(
            'Failed to parse messages response: $e',
            name: 'RemoteMessageRepository',
          );
          return Failure(ApiError(message: 'Failed to parse messages: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to fetch messages: ${apiError.message}',
          name: 'RemoteMessageRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Send a message to a chat
  /// Requirements: 5.3 - POST /api/messages
  Future<Result<Message>> sendMessage({
    required String chatId,
    required String content,
    String type = 'TEXT',
  }) async {
    final request = SendMessageRequest.fromContent(
      chatId: chatId,
      content: content,
      type: type,
    );

    developer.log(
      'Sending message to chat: $chatId (type: $type, content: ${content.substring(0, content.length > 20 ? 20 : content.length)}...)',
      name: 'RemoteMessageRepository',
    );

    final result = await _apiClient.post(
      '/api/messages',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (response) {
        try {
          developer.log(
            'Send message response: ${response.data}',
            name: 'RemoteMessageRepository',
          );
          final messageResponse = MessageResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final message = messageResponse.toEntity();

          developer.log(
            'Message sent successfully: ${message.id}',
            name: 'RemoteMessageRepository',
          );

          return Success(message);
        } catch (e) {
          developer.log(
            'Failed to parse send message response: $e',
            name: 'RemoteMessageRepository',
          );
          return Failure(ApiError(message: 'Failed to parse message: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to send message: ${apiError.message}',
          name: 'RemoteMessageRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Send typing indicator for a chat
  /// Requirements: 6.3 - Send typing events via WebSocket
  void sendTypingIndicator(String chatId) {
    developer.log(
      'Sending typing indicator for chat: $chatId',
      name: 'RemoteMessageRepository',
    );
    _webSocketService.sendTyping(chatId);
  }

  /// Send media message (image or video)
  Future<Result<Message>> sendMediaMessage({
    required String chatId,
    required String filePath,
    required String mediaType, // 'IMAGE' or 'VIDEO'
  }) async {
    developer.log(
      'Sending media message to chat: $chatId (type: $mediaType)',
      name: 'RemoteMessageRepository',
    );

    final result = await _apiClient.uploadFile(
      '/api/messages/$chatId/media',
      filePath: filePath,
      fieldName: 'media',
      additionalFields: {'type': mediaType},
    );

    return result.fold(
      onSuccess: (response) {
        try {
          developer.log(
            'Send media message response: ${response.data}',
            name: 'RemoteMessageRepository',
          );
          final messageResponse = MessageResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final message = messageResponse.toEntity();

          developer.log(
            'Media message sent successfully: ${message.id}',
            name: 'RemoteMessageRepository',
          );

          return Success(message);
        } catch (e) {
          developer.log(
            'Failed to parse send media message response: $e',
            name: 'RemoteMessageRepository',
          );
          return Failure(ApiError(message: 'Failed to parse message: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to send media message: ${apiError.message}',
          name: 'RemoteMessageRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Mark messages as read
  Future<Result<void>> markAsRead(String chatId, List<String> messageIds) async {
    developer.log(
      'Marking ${messageIds.length} messages as read in chat: $chatId',
      name: 'RemoteMessageRepository',
    );

    final result = await _apiClient.post(
      '/api/messages/$chatId/read',
      data: {'messageIds': messageIds},
    );

    return result.fold(
      onSuccess: (_) {
        developer.log(
          'Messages marked as read successfully',
          name: 'RemoteMessageRepository',
        );
        return const Success(null);
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to mark messages as read: ${apiError.message}',
          name: 'RemoteMessageRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Delete a message
  Future<Result<void>> deleteMessage(String messageId) async {
    developer.log(
      'Deleting message: $messageId',
      name: 'RemoteMessageRepository',
    );

    final result = await _apiClient.delete('/api/messages/$messageId');

    return result.fold(
      onSuccess: (_) {
        developer.log(
          'Message deleted successfully: $messageId',
          name: 'RemoteMessageRepository',
        );
        return const Success(null);
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to delete message: ${apiError.message}',
          name: 'RemoteMessageRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Dispose resources
  void dispose() {
    _wsSubscription?.cancel();
    _newMessagesController.close();
  }
}
