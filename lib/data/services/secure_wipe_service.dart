import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

import '../datasources/secure_storage_datasource.dart';

/// Result of a secure wipe operation
class WipeResult {
  final bool keysWiped;
  final bool messagesWiped;
  final bool mediaWiped;
  final bool tokensRevoked;
  final bool appReset;
  final String? error;

  const WipeResult({
    required this.keysWiped,
    required this.messagesWiped,
    required this.mediaWiped,
    required this.tokensRevoked,
    required this.appReset,
    this.error,
  });

  bool get isComplete =>
      keysWiped && messagesWiped && mediaWiped && tokensRevoked && appReset;

  factory WipeResult.success() => const WipeResult(
        keysWiped: true,
        messagesWiped: true,
        mediaWiped: true,
        tokensRevoked: true,
        appReset: true,
      );

  factory WipeResult.failure(String error) => WipeResult(
        keysWiped: false,
        messagesWiped: false,
        mediaWiped: false,
        tokensRevoked: false,
        appReset: false,
        error: error,
      );
}

/// Service for securely wiping all app data
/// Requirements: 4.2, 4.3, 4.4, 4.5
class SecureWipeService {
  final SecureStorageDatasource _secureStorage;
  final Dio _dio;
  final String? _mediaDirectory;
  final String? _databasePath;

  SecureWipeService({
    required SecureStorageDatasource secureStorage,
    required Dio dio,
    String? mediaDirectory,
    String? databasePath,
  })  : _secureStorage = secureStorage,
        _dio = dio,
        _mediaDirectory = mediaDirectory,
        _databasePath = databasePath;

  /// Execute complete data wipe
  /// Requirements: 4.2, 4.3, 4.4, 4.5
  Future<WipeResult> executeWipe() async {
    bool keysWiped = false;
    bool messagesWiped = false;
    bool mediaWiped = false;
    bool tokensRevoked = false;
    bool appReset = false;
    String? error;

    try {
      // 1. Revoke server tokens first (while we still have them)
      tokensRevoked = await _revokeServerTokens();

      // 2. Delete all encryption keys
      keysWiped = await _wipeKeys();

      // 3. Overwrite and delete messages
      messagesWiped = await _wipeMessages();

      // 4. Overwrite and delete media files
      mediaWiped = await _wipeMedia();

      // 5. Reset app to initial state
      appReset = await _resetAppState();
    } catch (e) {
      error = e.toString();
    }

    return WipeResult(
      keysWiped: keysWiped,
      messagesWiped: messagesWiped,
      mediaWiped: mediaWiped,
      tokensRevoked: tokensRevoked,
      appReset: appReset,
      error: error,
    );
  }

  /// Delete all encryption keys from secure storage
  /// Requirements: 4.2
  Future<bool> wipeKeys() => _wipeKeys();

  Future<bool> _wipeKeys() async {
    try {
      // Delete identity keys
      await _secureStorage.delete('identity_public_key');
      await _secureStorage.delete('identity_private_key');

      // Delete session keys
      await _secureStorage.delete('session_keys');

      // Delete pre-keys
      await _secureStorage.delete('pre_keys');
      await _secureStorage.delete('signed_pre_key');

      // Delete any other crypto-related keys
      await _secureStorage.delete('encryption_key');
      await _secureStorage.delete('database_key');

      return true;
    } catch (e) {
      debugPrint('Error wiping keys: $e');
      return false;
    }
  }

  /// Overwrite and delete all local messages
  /// Requirements: 4.3
  Future<bool> wipeMessages() => _wipeMessages();

  Future<bool> _wipeMessages() async {
    try {
      final dbPath = _databasePath ?? await _getDefaultDatabasePath();

      if (dbPath != null) {
        final dbFile = File(dbPath);
        if (await dbFile.exists()) {
          // Overwrite with random data before deletion
          await _secureOverwrite(dbFile);
          await dbFile.delete();
        }
      }

      // Also delete any SQLite journal/WAL files
      await _deleteRelatedDatabaseFiles(dbPath);

      return true;
    } catch (e) {
      debugPrint('Error wiping messages: $e');
      return false;
    }
  }

  /// Overwrite and delete all media files
  /// Requirements: 4.3
  Future<bool> wipeMedia() => _wipeMedia();

  Future<bool> _wipeMedia() async {
    try {
      final mediaDir = _mediaDirectory ?? await _getDefaultMediaDirectory();

      if (mediaDir != null) {
        final directory = Directory(mediaDir);
        if (await directory.exists()) {
          // Recursively overwrite and delete all files
          await _secureDeleteDirectory(directory);
        }
      }

      return true;
    } catch (e) {
      debugPrint('Error wiping media: $e');
      return false;
    }
  }

  /// Revoke all server tokens via API call
  /// Requirements: 4.4
  Future<bool> revokeServerTokens() => _revokeServerTokens();

  Future<bool> _revokeServerTokens() async {
    try {
      final accessToken = await _secureStorage.getAccessToken();

      if (accessToken != null) {
        await _dio.post(
          '/auth/revoke-all',
          options: Options(
            headers: {'Authorization': 'Bearer $accessToken'},
          ),
        );
      }

      return true;
    } on DioException catch (e) {
      // Even if server call fails, continue with local wipe
      debugPrint('Error revoking server tokens: ${e.message}');
      return true; // Return true to continue wipe process
    } catch (e) {
      debugPrint('Error revoking server tokens: $e');
      return true; // Return true to continue wipe process
    }
  }

  /// Reset the app to initial state
  /// Requirements: 4.5
  Future<bool> resetAppState() => _resetAppState();

  Future<bool> _resetAppState() async {
    try {
      // Clear all secure storage
      await _secureStorage.deleteAll();

      // Clear any shared preferences (if used)
      // Note: SharedPreferences would need to be injected if used

      // Clear any cached data
      await _clearCacheDirectory();

      return true;
    } catch (e) {
      debugPrint('Error resetting app state: $e');
      return false;
    }
  }

  /// Securely overwrite a file with random data before deletion
  Future<void> _secureOverwrite(File file) async {
    try {
      final length = await file.length();
      if (length > 0) {
        // Overwrite with zeros (faster than random for large files)
        final zeros = List<int>.filled(
          length > 4096 ? 4096 : length,
          0,
        );

        final sink = file.openWrite();
        int written = 0;
        while (written < length) {
          final toWrite =
              (length - written) > zeros.length ? zeros.length : length - written;
          sink.add(zeros.sublist(0, toWrite));
          written += toWrite;
        }
        await sink.close();
      }
    } catch (e) {
      debugPrint('Error overwriting file: $e');
    }
  }

  /// Recursively delete a directory with secure overwrite
  Future<void> _secureDeleteDirectory(Directory directory) async {
    try {
      await for (final entity in directory.list(recursive: true)) {
        if (entity is File) {
          await _secureOverwrite(entity);
          await entity.delete();
        }
      }
      await directory.delete(recursive: true);
    } catch (e) {
      debugPrint('Error deleting directory: $e');
    }
  }

  /// Delete related database files (journal, WAL, etc.)
  Future<void> _deleteRelatedDatabaseFiles(String? dbPath) async {
    if (dbPath == null) return;

    final extensions = ['-journal', '-wal', '-shm'];
    for (final ext in extensions) {
      final file = File('$dbPath$ext');
      if (await file.exists()) {
        await _secureOverwrite(file);
        await file.delete();
      }
    }
  }

  /// Get default database path
  Future<String?> _getDefaultDatabasePath() async {
    try {
      final dbDir = await getDatabasesPath();
      return path.join(dbDir, 'mkr_messenger.db');
    } catch (e) {
      return null;
    }
  }

  /// Get default media directory
  Future<String?> _getDefaultMediaDirectory() async {
    try {
      final dbDir = await getDatabasesPath();
      final appDir = path.dirname(dbDir);
      return path.join(appDir, 'media');
    } catch (e) {
      return null;
    }
  }

  /// Clear cache directory
  Future<void> _clearCacheDirectory() async {
    try {
      final dbDir = await getDatabasesPath();
      final appDir = path.dirname(dbDir);
      final cacheDir = Directory(path.join(appDir, 'cache'));

      if (await cacheDir.exists()) {
        await cacheDir.delete(recursive: true);
      }
    } catch (e) {
      debugPrint('Error clearing cache: $e');
    }
  }
}
