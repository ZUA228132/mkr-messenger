/// Message types
enum MessageType { text, image, video, audio, file }

/// Message delivery status
enum MessageStatus { sending, sent, delivered, read, failed }

/// Auto-delete policy options
enum AutoDeletePolicy { 
  oneDay, 
  threeDays, 
  sevenDays, 
  thirtyDays, 
  afterRead, 
  onExit 
}

/// Message entity representing a chat message
class Message {
  final String id;
  final String chatId;
  final String senderId;
  final String content;
  final MessageType type;
  final DateTime timestamp;
  final MessageStatus status;
  final AutoDeletePolicy? autoDelete;

  const Message({
    required this.id,
    required this.chatId,
    required this.senderId,
    required this.content,
    this.type = MessageType.text,
    required this.timestamp,
    this.status = MessageStatus.sending,
    this.autoDelete,
  });

  Message copyWith({
    String? id,
    String? chatId,
    String? senderId,
    String? content,
    MessageType? type,
    DateTime? timestamp,
    MessageStatus? status,
    AutoDeletePolicy? autoDelete,
  }) {
    return Message(
      id: id ?? this.id,
      chatId: chatId ?? this.chatId,
      senderId: senderId ?? this.senderId,
      content: content ?? this.content,
      type: type ?? this.type,
      timestamp: timestamp ?? this.timestamp,
      status: status ?? this.status,
      autoDelete: autoDelete ?? this.autoDelete,
    );
  }
}
