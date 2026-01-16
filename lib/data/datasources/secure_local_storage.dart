import 'dart:convert';
import 'dart:typed_data';

import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as path;

import '../../domain/entities/encrypted_message.dart';
import '../../domain/entities/message.dart';
import '../crypto/signal_protocol.dart';
import 'secure_storage_datasource.dart';

/// Secure local storage with encrypted SQLite database
/// Requirements: 9.1 - Display cached messages from local storage
/// Requirements: 9.4 - Encrypt the local database
class SecureLocalStorage {
  Database? _database;
  final SecureStorageDatasource _secureStorage;
  final SignalProtocol _crypto;
  
  static const String _dbName = 'mkr_secure.db';
  static const String _messagesTable = 'encrypted_messages';
  static const String _queueTable = 'offline_queue';
  static const String _dbKeyStorageKey = 'db_encryption_key';
  
  // Token storage keys
  // Requirements: 2.4, 2.6, 3.1 - Store JWT token and userId securely
  static const String _jwtTokenKey = 'jwt_token';
  static const String _userIdKey = 'user_id';
  
  Uint8List? _encryptionKey;

  SecureLocalStorage({
    SecureStorageDatasource? secureStorage,
    SignalProtocol? crypto,
  })  : _secureStorage = secureStorage ?? SecureStorageDatasource(),
        _crypto = crypto ?? SignalProtocol();

  /// Initialize the secure database
  Future<void> init() async {
    if (_database != null) return;

    // Get or generate encryption key
    _encryptionKey = await _getOrCreateEncryptionKey();

    final dbPath = await getDatabasesPath();
    final dbFile = path.join(dbPath, _dbName);

    _database = await openDatabase(
      dbFile,
      version: 1,
      onCreate: _createTables,
    );
  }

  Future<void> _createTables(Database db, int version) async {
    // Create encrypted messages table
    await db.execute('''
      CREATE TABLE $_messagesTable (
        id TEXT PRIMARY KEY,
        chat_id TEXT NOT NULL,
        encrypted_data TEXT NOT NULL,
        nonce TEXT NOT NULL,
        tag TEXT NOT NULL,
        timestamp INTEGER NOT NULL
      )
    ''');


    // Create offline queue table
    await db.execute('''
      CREATE TABLE $_queueTable (
        id TEXT PRIMARY KEY,
        chat_id TEXT NOT NULL,
        encrypted_data TEXT NOT NULL,
        nonce TEXT NOT NULL,
        tag TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        created_at INTEGER NOT NULL,
        sync_status TEXT NOT NULL DEFAULT 'pending'
      )
    ''');

    // Create indexes for faster queries
    await db.execute(
      'CREATE INDEX idx_enc_messages_chat_id ON $_messagesTable(chat_id)',
    );
    await db.execute(
      'CREATE INDEX idx_enc_messages_timestamp ON $_messagesTable(timestamp)',
    );
    await db.execute(
      'CREATE INDEX idx_queue_sync_status ON $_queueTable(sync_status)',
    );
  }

  /// Get or create the database encryption key
  Future<Uint8List> _getOrCreateEncryptionKey() async {
    final storedKey = await _secureStorage.read(_dbKeyStorageKey);
    
    if (storedKey != null) {
      return base64Decode(storedKey);
    }

    // Generate a new encryption key
    final keyPair = await _crypto.generateKeyPair();
    final newKey = _crypto.deriveSessionKey(keyPair.privateKey);
    
    // Store the key securely
    await _secureStorage.write(_dbKeyStorageKey, base64Encode(newKey));
    
    return newKey;
  }

  /// Store an encrypted message in local database
  /// Requirements: 9.1 - Display cached messages from local storage
  /// Requirements: 9.4 - Encrypt the local database
  Future<void> storeMessage(Message message) async {
    await init();
    
    final encrypted = _encryptMessage(message);
    
    await _database!.insert(
      _messagesTable,
      {
        'id': message.id,
        'chat_id': message.chatId,
        'encrypted_data': base64Encode(encrypted.ciphertext),
        'nonce': base64Encode(encrypted.nonce),
        'tag': base64Encode(encrypted.tag),
        'timestamp': message.timestamp.millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Store multiple messages efficiently
  Future<void> storeMessages(List<Message> messages) async {
    await init();
    
    final batch = _database!.batch();
    for (final message in messages) {
      final encrypted = _encryptMessage(message);
      batch.insert(
        _messagesTable,
        {
          'id': message.id,
          'chat_id': message.chatId,
          'encrypted_data': base64Encode(encrypted.ciphertext),
          'nonce': base64Encode(encrypted.nonce),
          'tag': base64Encode(encrypted.tag),
          'timestamp': message.timestamp.millisecondsSinceEpoch,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  /// Get cached messages for a chat
  /// Requirements: 9.1 - Display cached messages from local storage
  Future<List<Message>> getCachedMessages(String chatId) async {
    await init();
    
    final results = await _database!.query(
      _messagesTable,
      where: 'chat_id = ?',
      whereArgs: [chatId],
      orderBy: 'timestamp DESC',
    );
    
    return results.map(_decryptMessageRow).toList();
  }

  /// Get all cached messages
  Future<List<Message>> getAllCachedMessages() async {
    await init();
    
    final results = await _database!.query(
      _messagesTable,
      orderBy: 'timestamp DESC',
    );
    
    return results.map(_decryptMessageRow).toList();
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
  Future<void> clearAllMessages() async {
    await init();
    await _database!.delete(_messagesTable);
  }


  // ============ Offline Queue Methods ============
  // Requirements: 9.2 - Queue message for sending when online
  // Requirements: 9.3 - Sync queued messages and fetch new ones

  /// Queue a message for offline sending
  Future<void> queueOfflineMessage(Message message) async {
    await init();
    
    final encrypted = _encryptMessage(message);
    
    await _database!.insert(
      _queueTable,
      {
        'id': message.id,
        'chat_id': message.chatId,
        'encrypted_data': base64Encode(encrypted.ciphertext),
        'nonce': base64Encode(encrypted.nonce),
        'tag': base64Encode(encrypted.tag),
        'timestamp': message.timestamp.millisecondsSinceEpoch,
        'created_at': DateTime.now().millisecondsSinceEpoch,
        'sync_status': 'pending',
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Get all pending messages in the queue
  Future<List<Message>> getQueuedMessages() async {
    await init();
    
    final results = await _database!.query(
      _queueTable,
      where: 'sync_status = ?',
      whereArgs: ['pending'],
      orderBy: 'created_at ASC',
    );
    
    return results.map(_decryptMessageRow).toList();
  }

  /// Mark a queued message as synced
  Future<void> markMessageSynced(String messageId) async {
    await init();
    await _database!.update(
      _queueTable,
      {'sync_status': 'synced'},
      where: 'id = ?',
      whereArgs: [messageId],
    );
  }

  /// Mark a queued message as failed
  Future<void> markMessageFailed(String messageId) async {
    await init();
    await _database!.update(
      _queueTable,
      {'sync_status': 'failed'},
      where: 'id = ?',
      whereArgs: [messageId],
    );
  }

  /// Remove a message from the queue
  Future<void> removeFromQueue(String messageId) async {
    await init();
    await _database!.delete(
      _queueTable,
      where: 'id = ?',
      whereArgs: [messageId],
    );
  }

  /// Clear all synced messages from queue
  Future<void> clearSyncedMessages() async {
    await init();
    await _database!.delete(
      _queueTable,
      where: 'sync_status = ?',
      whereArgs: ['synced'],
    );
  }

  /// Clear entire queue
  Future<void> clearQueue() async {
    await init();
    await _database!.delete(_queueTable);
  }

  /// Get queue count by status
  Future<int> getQueueCount({String? status}) async {
    await init();
    
    if (status != null) {
      final result = await _database!.rawQuery(
        'SELECT COUNT(*) as count FROM $_queueTable WHERE sync_status = ?',
        [status],
      );
      return Sqflite.firstIntValue(result) ?? 0;
    }
    
    final result = await _database!.rawQuery(
      'SELECT COUNT(*) as count FROM $_queueTable',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Check if a message exists in the queue
  Future<bool> isMessageInQueue(String messageId) async {
    await init();
    final result = await _database!.query(
      _queueTable,
      where: 'id = ?',
      whereArgs: [messageId],
      limit: 1,
    );
    return result.isNotEmpty;
  }


  // ============ JWT Token Storage Methods ============
  // Requirements: 2.4, 2.6, 3.1 - Store JWT token in secure storage

  /// Save JWT token to secure storage
  /// Requirements: 2.4, 2.6 - Store JWT token securely after login/verification
  Future<void> saveToken(String token) async {
    await _secureStorage.write(_jwtTokenKey, token);
  }

  /// Get JWT token from secure storage
  /// Requirements: 3.1 - Retrieve stored JWT token for authenticated requests
  Future<String?> getToken() async {
    return await _secureStorage.read(_jwtTokenKey);
  }

  /// Clear JWT token from secure storage
  /// Requirements: 3.4 - Clear token on logout
  Future<void> clearToken() async {
    await _secureStorage.delete(_jwtTokenKey);
  }

  /// Check if a valid token exists
  Future<bool> hasToken() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }


  // ============ User ID Storage Methods ============
  // Requirements: 2.6 - Store userId securely after login

  /// Save user ID to secure storage
  /// Requirements: 2.6 - Store userId securely after login
  Future<void> saveUserId(String userId) async {
    await _secureStorage.write(_userIdKey, userId);
  }

  /// Get user ID from secure storage
  Future<String?> getUserId() async {
    return await _secureStorage.read(_userIdKey);
  }

  /// Clear user ID from secure storage
  Future<void> clearUserId() async {
    await _secureStorage.delete(_userIdKey);
  }


  /// Clear all authentication credentials (token and userId)
  /// Requirements: 3.4 - Clear all stored credentials on logout
  Future<void> clearCredentials() async {
    await Future.wait([
      clearToken(),
      clearUserId(),
    ]);
  }

  /// Check if user is authenticated (has both token and userId)
  Future<bool> isAuthenticated() async {
    final token = await getToken();
    final userId = await getUserId();
    return token != null && token.isNotEmpty && userId != null && userId.isNotEmpty;
  }


  // ============ Encryption/Decryption Helpers ============

  /// Encrypt a message for storage
  EncryptedMessage _encryptMessage(Message message) {
    final jsonData = jsonEncode(_messageToJson(message));
    return _crypto.encrypt(jsonData, _encryptionKey!);
  }

  /// Decrypt a message row from the database
  Message _decryptMessageRow(Map<String, dynamic> row) {
    final encrypted = EncryptedMessage(
      ciphertext: base64Decode(row['encrypted_data'] as String),
      nonce: base64Decode(row['nonce'] as String),
      tag: base64Decode(row['tag'] as String),
    );
    
    final decryptedJson = _crypto.decrypt(encrypted, _encryptionKey!);
    final jsonData = jsonDecode(decryptedJson) as Map<String, dynamic>;
    return _messageFromJson(jsonData);
  }

  /// Convert Message to JSON map
  Map<String, dynamic> _messageToJson(Message message) {
    return {
      'id': message.id,
      'chatId': message.chatId,
      'senderId': message.senderId,
      'content': message.content,
      'type': message.type.name,
      'timestamp': message.timestamp.toIso8601String(),
      'status': message.status.name,
      'autoDelete': message.autoDelete?.name,
    };
  }

  /// Convert JSON map to Message
  Message _messageFromJson(Map<String, dynamic> json) {
    return Message(
      id: json['id'] as String,
      chatId: json['chatId'] as String,
      senderId: json['senderId'] as String,
      content: json['content'] as String,
      type: MessageType.values.firstWhere(
        (e) => e.name == json['type'],
        orElse: () => MessageType.text,
      ),
      timestamp: DateTime.parse(json['timestamp'] as String),
      status: MessageStatus.values.firstWhere(
        (e) => e.name == json['status'],
        orElse: () => MessageStatus.sending,
      ),
      autoDelete: json['autoDelete'] != null
          ? AutoDeletePolicy.values.firstWhere(
              (e) => e.name == json['autoDelete'],
              orElse: () => AutoDeletePolicy.sevenDays,
            )
          : null,
    );
  }

  // ============ Database Management ============

  /// Clear all data (messages and queue)
  Future<void> clearAll() async {
    await init();
    await _database!.delete(_messagesTable);
    await _database!.delete(_queueTable);
  }

  /// Delete the encryption key (for secure wipe)
  Future<void> deleteEncryptionKey() async {
    await _secureStorage.delete(_dbKeyStorageKey);
    _encryptionKey = null;
  }

  /// Close the database
  Future<void> close() async {
    await _database?.close();
    _database = null;
  }

  /// Check if database is initialized
  bool get isInitialized => _database != null;

  /// Get raw database content for a message (for testing encryption)
  /// This returns the encrypted data as stored, useful for verifying
  /// that plaintext is not stored in the database
  Future<Map<String, dynamic>?> getRawMessageData(String messageId) async {
    await init();
    final results = await _database!.query(
      _messagesTable,
      where: 'id = ?',
      whereArgs: [messageId],
    );
    return results.isNotEmpty ? results.first : null;
  }
}
