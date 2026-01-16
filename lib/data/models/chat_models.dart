// Chat API Models for MKR Backend integration
// Requirements: 4.1, 4.4 - Chat list and creation models

import '../../domain/entities/chat.dart';
import '../../domain/entities/message.dart';

/// Response model for a single chat from API
/// Requirements: 4.1 - GET /api/chats response
/// Requirements: 4.2 - Display chat with name, last message, unread count
class ChatResponse {
  final String id;
  final String type;
  final String name;
  final List<String> participants;
  final Map<String, String> participantNames;
  final int createdAt;
  final MessageResponse? lastMessage;
  final int unreadCount;

  const ChatResponse({
    required this.id,
    required this.type,
    required this.name,
    required this.participants,
    required this.participantNames,
    required this.createdAt,
    this.lastMessage,
    required this.unreadCount,
  });

  factory ChatResponse.fromJson(Map<String, dynamic> json) {
    return ChatResponse(
      id: json['id'] as String,
      type: json['type'] as String? ?? 'direct',
      name: json['name'] as String? ?? '',
      participants: (json['participants'] as List<dynamic>?)
              ?.map((e) => e as String)
              .toList() ??
          [],
      participantNames:
          (json['participantNames'] as Map<String, dynamic>?)?.map(
                (key, value) => MapEntry(key, value as String),
              ) ??
              {},
      createdAt: json['createdAt'] as int? ?? 0,
      lastMessage: json['lastMessage'] != null
          ? MessageResponse.fromJson(json['lastMessage'] as Map<String, dynamic>)
          : null,
      unreadCount: json['unreadCount'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'type': type,
      'name': name,
      'participants': participants,
      'participantNames': participantNames,
      'createdAt': createdAt,
      'lastMessage': lastMessage?.toJson(),
      'unreadCount': unreadCount,
    };
  }

  /// Convert to domain entity
  Chat toEntity() {
    return Chat(
      id: id,
      type: _parseType(type),
      participantIds: participants,
      participantNames: participantNames,
      lastMessage: lastMessage?.toEntity(),
      updatedAt: DateTime.fromMillisecondsSinceEpoch(createdAt),
      name: name.isNotEmpty ? name : null,
      unreadCount: unreadCount,
    );
  }

  ChatType _parseType(String type) {
    switch (type.toLowerCase()) {
      case 'direct':
        return ChatType.direct;
      case 'group':
        return ChatType.group;
      case 'channel':
        return ChatType.channel;
      default:
        return ChatType.direct;
    }
  }

  @override
  String toString() =>
      'ChatResponse(id: $id, type: $type, name: $name, unreadCount: $unreadCount)';
}


/// Message response model for last message in chat
/// Requirements: 4.2 - Display last message preview
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

/// Request body for creating a new chat
/// Requirements: 4.4 - POST /api/chats with participantIds
class CreateChatRequest {
  final String type;
  final String? name;
  final List<String> participantIds;

  const CreateChatRequest({
    required this.type,
    this.name,
    required this.participantIds,
  });

  Map<String, dynamic> toJson() {
    return {
      'type': type,
      if (name != null) 'name': name,
      'participantIds': participantIds,
    };
  }

  @override
  String toString() =>
      'CreateChatRequest(type: $type, name: $name, participantIds: $participantIds)';
}

/// Response wrapper for list of chats
/// Requirements: 4.1 - GET /api/chats response
class ChatsListResponse {
  final List<ChatResponse> chats;

  const ChatsListResponse({required this.chats});

  factory ChatsListResponse.fromJson(dynamic json) {
    if (json is List) {
      return ChatsListResponse(
        chats: json
            .map((e) => ChatResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    } else if (json is Map<String, dynamic>) {
      final chatsList = json['chats'] as List<dynamic>? ?? [];
      return ChatsListResponse(
        chats: chatsList
            .map((e) => ChatResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    }
    return const ChatsListResponse(chats: []);
  }

  List<Chat> toEntities() {
    return chats.map((c) => c.toEntity()).toList();
  }

  @override
  String toString() => 'ChatsListResponse(count: ${chats.length})';
}
