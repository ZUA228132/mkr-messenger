import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as path;

import '../../domain/entities/message.dart';

/// Datasource for managing offline message queue
/// Requirements: 6.1 - Message queuing for offline support
class MessageQueueDatasource {
  Database? _database;
  static const String _tableName = 'message_queue';
  static const String _messagesTable = 'messages';

  /// Initialize the database
  Future<void> init() async {
    if (_database != null) return;

    final dbPath = await getDatabasesPath();
    final dbFile = path.join(dbPath, 'mkr_messages.db');

    _database = await openDatabase(
      dbFile,
      version: 1,
      onCreate: (db, version) async {
        // Create message queue table for offline messages
        await db.execute('''
          CREATE TABLE $_tableName (
            id TEXT PRIMARY KEY,
            chat_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            content TEXT NOT NULL,
            type TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            status TEXT NOT NULL,
            auto_delete TEXT,
            created_at INTEGER NOT NULL
          )
        ''');

        // Create messages table for cached messages
        await db.execute('''
          CREATE TABLE $_messagesTable (
            id TEXT PRIMARY KEY,
            chat_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            content TEXT NOT NULL,
            type TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            status TEXT NOT NULL,
            auto_delete TEXT,
            UNIQUE(id, chat_id)
          )
        ''');

        // Create index for faster queries
        await db.execute(
          'CREATE INDEX idx_messages_chat_id ON $_messagesTable(chat_id)',
        );
        await db.execute(
          'CREATE INDEX idx_queue_chat_id ON $_tableName(chat_id)',
        );
      },
    );
  }

  /// Queue a message for offline sending
  Future<void> queueMessage(Message message) async {
    await init();
    await _database!.insert(
      _tableName,
      _messageToMap(message, includeCreatedAt: true),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Get all queued messages
  Future<List<Message>> getQueuedMessages() async {
    await init();
    final results = await _database!.query(
      _tableName,
      orderBy: 'created_at ASC',
    );
    return results.map(_mapToMessage).toList();
  }

  /// Remove a message from the queue
  Future<void> removeFromQueue(String messageId) async {
    await init();
    await _database!.delete(
      _tableName,
      where: 'id = ?',
      whereArgs: [messageId],
    );
  }

  /// Clear all queued messages
  Future<void> clearQueue() async {
    await init();
    await _database!.delete(_tableName);
  }


  /// Cache a message locally
  Future<void> cacheMessage(Message message) async {
    await init();
    await _database!.insert(
      _messagesTable,
      _messageToMap(message),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Cache multiple messages
  Future<void> cacheMessages(List<Message> messages) async {
    await init();
    final batch = _database!.batch();
    for (final message in messages) {
      batch.insert(
        _messagesTable,
        _messageToMap(message),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  /// Get cached messages for a chat
  Future<List<Message>> getCachedMessages(String chatId) async {
    await init();
    final results = await _database!.query(
      _messagesTable,
      where: 'chat_id = ?',
      whereArgs: [chatId],
      orderBy: 'timestamp DESC',
    );
    return results.map(_mapToMessage).toList();
  }

  /// Update message status
  Future<void> updateMessageStatus(String messageId, MessageStatus status) async {
    await init();
    await _database!.update(
      _messagesTable,
      {'status': status.name},
      where: 'id = ?',
      whereArgs: [messageId],
    );
  }

  /// Delete a message from cache
  Future<void> deleteMessage(String messageId) async {
    await init();
    await _database!.delete(
      _messagesTable,
      where: 'id = ?',
      whereArgs: [messageId],
    );
  }

  /// Delete all messages for a chat
  Future<void> deleteMessagesForChat(String chatId) async {
    await init();
    await _database!.delete(
      _messagesTable,
      where: 'chat_id = ?',
      whereArgs: [chatId],
    );
  }

  /// Clear all cached messages
  Future<void> clearCache() async {
    await init();
    await _database!.delete(_messagesTable);
  }

  /// Close the database
  Future<void> close() async {
    await _database?.close();
    _database = null;
  }

  Map<String, dynamic> _messageToMap(Message message, {bool includeCreatedAt = false}) {
    final map = {
      'id': message.id,
      'chat_id': message.chatId,
      'sender_id': message.senderId,
      'content': message.content,
      'type': message.type.name,
      'timestamp': message.timestamp.millisecondsSinceEpoch,
      'status': message.status.name,
      'auto_delete': message.autoDelete?.name,
    };
    if (includeCreatedAt) {
      map['created_at'] = DateTime.now().millisecondsSinceEpoch;
    }
    return map;
  }

  Message _mapToMessage(Map<String, dynamic> map) {
    return Message(
      id: map['id'] as String,
      chatId: map['chat_id'] as String,
      senderId: map['sender_id'] as String,
      content: map['content'] as String,
      type: MessageType.values.firstWhere(
        (e) => e.name == map['type'],
        orElse: () => MessageType.text,
      ),
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
      status: MessageStatus.values.firstWhere(
        (e) => e.name == map['status'],
        orElse: () => MessageStatus.sending,
      ),
      autoDelete: map['auto_delete'] != null
          ? AutoDeletePolicy.values.firstWhere(
              (e) => e.name == map['auto_delete'],
              orElse: () => AutoDeletePolicy.sevenDays,
            )
          : null,
    );
  }
}
