// Message API Models for MKR Backend integration
// Requirements: 5.1, 5.3 - Message retrieval and sending models

import '../../domain/entities/message.dart';

/// Response model for a single message from API
/// Requirements: 5.1 - GET /api/messages/{chatId} response
/// Requirements: 5.2 - Display messages with sender info, content, timestamp
class MessageResponse {
  final String id;
  final String chatId;
  final String senderId;
  final String content;
  final String type;
  final int timestamp;
  final String status;

  const MessageResponse({
    required this.id,
    required this.chatId,
    required this.senderId,
    required this.content,
    required this.type,
    required this.timestamp,
    required this.status,
  });

  factory MessageResponse.fromJson(Map<String, dynamic> json) {
    return MessageResponse(
      id: json['id'] as String,
      chatId: json['chatId'] as String? ?? '',
      senderId: json['senderId'] as String,
      content: json['content'] as String? ?? '',
      type: json['type'] as String? ?? 'TEXT',
      timestamp: json['timestamp'] as int? ?? 0,
      status: json['status'] as String? ?? 'sent',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'chatId': chatId,
      'senderId': senderId,
      'content': content,
      'type': type,
      'timestamp': timestamp,
      'status': status,
    };
  }

  /// Convert to domain entity
  Message toEntity() {
    return Message(
      id: id,
      chatId: chatId,
      senderId: senderId,
      content: content,
      type: _parseMessageType(type),
      timestamp: DateTime.fromMillisecondsSinceEpoch(timestamp),
      status: _parseMessageStatus(status),
    );
  }

  MessageType _parseMessageType(String type) {
    switch (type.toUpperCase()) {
      case 'TEXT':
        return MessageType.text;
      case 'IMAGE':
        return MessageType.image;
      case 'VIDEO':
        return MessageType.video;
      case 'AUDIO':
        return MessageType.audio;
      case 'FILE':
        return MessageType.file;
      case 'VOICE_NOTE':
      case 'VOICENOTE':
      case 'VOICE':
        return MessageType.voiceNote;
      case 'VIDEO_NOTE':
      case 'VIDEONOTE':
      case 'CIRCLE':
        return MessageType.videoNote;
      default:
        return MessageType.text;
    }
  }

  MessageStatus _parseMessageStatus(String status) {
    switch (status.toLowerCase()) {
      case 'sending':
        return MessageStatus.sending;
      case 'sent':
        return MessageStatus.sent;
      case 'delivered':
        return MessageStatus.delivered;
      case 'read':
        return MessageStatus.read;
      case 'failed':
        return MessageStatus.failed;
      default:
        return MessageStatus.sent;
    }
  }

  @override
  String toString() =>
      'MessageResponse(id: $id, senderId: $senderId, content: ${content.length > 20 ? '${content.substring(0, 20)}...' : content})';
}

/// Request body for sending a new message
/// Requirements: 5.3 - POST /api/messages
class SendMessageRequest {
  final String chatId;
  final String content;
  final String type;

  const SendMessageRequest({
    required this.chatId,
    required this.content,
    this.type = 'TEXT',
  });

  Map<String, dynamic> toJson() {
    return {
      'chatId': chatId,
      'content': content,
      'type': type,
    };
  }

  @override
  String toString() =>
      'SendMessageRequest(chatId: $chatId, type: $type, content: ${content.length > 20 ? '${content.substring(0, 20)}...' : content})';
}

/// Response wrapper for list of messages
/// Requirements: 5.1 - GET /api/messages/{chatId} response
class MessagesListResponse {
  final List<MessageResponse> messages;
  final bool hasMore;
  final String? nextCursor;

  const MessagesListResponse({
    required this.messages,
    this.hasMore = false,
    this.nextCursor,
  });

  factory MessagesListResponse.fromJson(dynamic json) {
    if (json is List) {
      return MessagesListResponse(
        messages: json
            .map((e) => MessageResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    } else if (json is Map<String, dynamic>) {
      final messagesList = json['messages'] as List<dynamic>? ?? [];
      return MessagesListResponse(
        messages: messagesList
            .map((e) => MessageResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
        hasMore: json['hasMore'] as bool? ?? false,
        nextCursor: json['nextCursor'] as String?,
      );
    }
    return const MessagesListResponse(messages: []);
  }

  List<Message> toEntities() {
    return messages.map((m) => m.toEntity()).toList();
  }

  @override
  String toString() =>
      'MessagesListResponse(count: ${messages.length}, hasMore: $hasMore)';
}

/// WebSocket new message payload
/// Requirements: 5.4 - Handle new message via WebSocket
class NewMessagePayload {
  final String id;
  final String chatId;
  final String senderId;
  final String senderName;
  final String content;
  final String type;
  final int timestamp;

  const NewMessagePayload({
    required this.id,
    required this.chatId,
    required this.senderId,
    required this.senderName,
    required this.content,
    required this.type,
    required this.timestamp,
  });

  factory NewMessagePayload.fromJson(Map<String, dynamic> json) {
    return NewMessagePayload(
      id: json['id'] as String? ?? '',
      chatId: json['chatId'] as String? ?? '',
      senderId: json['senderId'] as String? ?? '',
      senderName: json['senderName'] as String? ?? '',
      content: json['content'] as String? ?? '',
      type: json['type'] as String? ?? 'TEXT',
      timestamp: json['timestamp'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'chatId': chatId,
      'senderId': senderId,
      'senderName': senderName,
      'content': content,
      'type': type,
      'timestamp': timestamp,
    };
  }

  /// Convert to MessageResponse for consistency
  MessageResponse toMessageResponse() {
    return MessageResponse(
      id: id,
      chatId: chatId,
      senderId: senderId,
      content: content,
      type: type,
      timestamp: timestamp,
      status: 'sent',
    );
  }

  /// Convert directly to domain entity
  Message toEntity() {
    return toMessageResponse().toEntity();
  }

  @override
  String toString() =>
      'NewMessagePayload(id: $id, chatId: $chatId, senderId: $senderId)';
}
