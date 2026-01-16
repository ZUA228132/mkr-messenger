import '../../core/utils/result.dart';
import '../entities/message.dart';

/// Abstract repository for chat operations
/// Requirements: 6.1 - Send/receive messages via WebSocket
abstract class ChatRepository {
  /// Send encrypted message via WebSocket
  Future<Result<void>> sendMessage(String chatId, Message message);

  /// Get messages stream for a chat (real-time updates)
  Stream<List<Message>> getMessages(String chatId);

  /// Get cached messages for a chat
  Future<Result<List<Message>>> getCachedMessages(String chatId);

  /// Queue message for offline sending
  Future<Result<void>> queueOfflineMessage(Message message);

  /// Get all queued offline messages
  Future<Result<List<Message>>> getQueuedMessages();

  /// Sync queued messages when online
  Future<Result<void>> syncQueuedMessages();

  /// Mark messages as read
  Future<Result<void>> markAsRead(String chatId, List<String> messageIds);

  /// Delete a message
  Future<Result<void>> deleteMessage(String chatId, String messageId);

  /// Connect to WebSocket
  Future<Result<void>> connect(String accessToken);

  /// Disconnect from WebSocket
  Future<void> disconnect();

  /// Check if connected
  bool get isConnected;

  /// Connection state stream
  Stream<bool> get connectionState;
}
