import 'dart:async';

import '../../domain/entities/message.dart';
import '../datasources/secure_local_storage.dart';
import '../datasources/websocket_datasource.dart';

/// Sync status for queued messages
enum SyncStatus {
  pending,
  syncing,
  synced,
  failed,
}

/// Service for managing offline message queue
/// Requirements: 9.2 - Queue message for sending when online
/// Requirements: 9.3 - Sync queued messages and fetch new ones
class OfflineMessageQueue {
  final SecureLocalStorage _storage;
  final WebSocketDatasource? _webSocket;
  
  bool _isOnline = false;
  bool _isSyncing = false;
  Timer? _syncTimer;
  
  final StreamController<List<Message>> _queueController = 
      StreamController<List<Message>>.broadcast();
  
  /// Stream of queued messages
  Stream<List<Message>> get queueStream => _queueController.stream;

  OfflineMessageQueue({
    required SecureLocalStorage storage,
    WebSocketDatasource? webSocket,
  })  : _storage = storage,
        _webSocket = webSocket;

  /// Initialize the queue service
  Future<void> init() async {
    await _storage.init();
    _startPeriodicSync();
  }

  /// Set online status
  void setOnlineStatus(bool isOnline) {
    final wasOffline = !_isOnline;
    _isOnline = isOnline;
    
    // If we just came online, trigger sync
    if (isOnline && wasOffline) {
      syncQueuedMessages();
    }
  }

  /// Check if currently online
  bool get isOnline => _isOnline;

  /// Queue a message for offline sending
  /// Requirements: 9.2 - Queue message for sending when online
  Future<void> queueMessage(Message message) async {
    await _storage.queueOfflineMessage(message);
    _notifyQueueChanged();
  }

  /// Get all pending messages
  Future<List<Message>> getPendingMessages() async {
    return await _storage.getQueuedMessages();
  }

  /// Get count of pending messages
  Future<int> getPendingCount() async {
    return await _storage.getQueueCount(status: 'pending');
  }


  /// Sync all queued messages when online
  /// Requirements: 9.3 - Sync queued messages and fetch new ones
  Future<SyncResult> syncQueuedMessages() async {
    if (!_isOnline) {
      return SyncResult(
        success: false,
        syncedCount: 0,
        failedCount: 0,
        error: 'Device is offline',
      );
    }

    if (_isSyncing) {
      return SyncResult(
        success: false,
        syncedCount: 0,
        failedCount: 0,
        error: 'Sync already in progress',
      );
    }

    _isSyncing = true;
    int syncedCount = 0;
    int failedCount = 0;

    try {
      final pendingMessages = await _storage.getQueuedMessages();
      
      for (final message in pendingMessages) {
        try {
          // Attempt to send the message
          final sent = await _sendMessage(message);
          
          if (sent) {
            await _storage.markMessageSynced(message.id);
            // Also store in local cache after successful send
            await _storage.storeMessage(
              message.copyWith(status: MessageStatus.sent),
            );
            syncedCount++;
          } else {
            await _storage.markMessageFailed(message.id);
            failedCount++;
          }
        } catch (e) {
          await _storage.markMessageFailed(message.id);
          failedCount++;
        }
      }

      // Clean up synced messages
      await _storage.clearSyncedMessages();
      _notifyQueueChanged();

      return SyncResult(
        success: failedCount == 0,
        syncedCount: syncedCount,
        failedCount: failedCount,
      );
    } finally {
      _isSyncing = false;
    }
  }

  /// Send a single message via WebSocket
  Future<bool> _sendMessage(Message message) async {
    if (_webSocket == null || !_webSocket.isConnected) {
      return false;
    }

    try {
      // Send message via WebSocket
      _webSocket.send(WsMessage(
        type: WsMessageType.message,
        data: {
          'chatId': message.chatId,
          'content': message.content,
          'messageId': message.id,
          'type': message.type.name,
        },
        timestamp: message.timestamp,
      ));
      return true;
    } catch (e) {
      return false;
    }
  }

  /// Retry failed messages
  Future<void> retryFailedMessages() async {
    // Reset failed messages to pending
    final pendingMessages = await _storage.getQueuedMessages();
    
    // Re-queue failed messages
    for (final message in pendingMessages) {
      await _storage.queueOfflineMessage(message);
    }
    
    // Trigger sync
    await syncQueuedMessages();
  }

  /// Remove a message from the queue
  Future<void> removeMessage(String messageId) async {
    await _storage.removeFromQueue(messageId);
    _notifyQueueChanged();
  }

  /// Clear all queued messages
  Future<void> clearQueue() async {
    await _storage.clearQueue();
    _notifyQueueChanged();
  }

  /// Check if a message is in the queue
  Future<bool> isMessageQueued(String messageId) async {
    return await _storage.isMessageInQueue(messageId);
  }

  void _notifyQueueChanged() async {
    final messages = await _storage.getQueuedMessages();
    _queueController.add(messages);
  }

  void _startPeriodicSync() {
    _syncTimer?.cancel();
    _syncTimer = Timer.periodic(
      const Duration(seconds: 30),
      (_) {
        if (_isOnline && !_isSyncing) {
          syncQueuedMessages();
        }
      },
    );
  }

  /// Dispose resources
  void dispose() {
    _syncTimer?.cancel();
    _queueController.close();
  }
}

/// Result of a sync operation
class SyncResult {
  final bool success;
  final int syncedCount;
  final int failedCount;
  final String? error;

  SyncResult({
    required this.success,
    required this.syncedCount,
    required this.failedCount,
    this.error,
  });

  @override
  String toString() {
    return 'SyncResult(success: $success, synced: $syncedCount, failed: $failedCount, error: $error)';
  }
}
