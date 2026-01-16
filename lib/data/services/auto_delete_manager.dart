import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/constants/app_constants.dart';
import '../../domain/entities/message.dart';
import '../datasources/message_queue_datasource.dart';

/// Manager for auto-deleting messages based on configured policy
/// Requirements: 6.4 - Delete messages after the specified interval
class AutoDeleteManager {
  final MessageQueueDatasource _messageDatasource;
  Timer? _cleanupTimer;
  final Duration _checkInterval;

  /// Callback when messages are deleted
  final void Function(List<String> deletedMessageIds)? onMessagesDeleted;

  AutoDeleteManager({
    required MessageQueueDatasource messageDatasource,
    Duration checkInterval = const Duration(minutes: 1),
    this.onMessagesDeleted,
  })  : _messageDatasource = messageDatasource,
        _checkInterval = checkInterval;

  /// Start the auto-delete timer
  void start() {
    _cleanupTimer?.cancel();
    _cleanupTimer = Timer.periodic(_checkInterval, (_) => _runCleanup());
    // Run immediately on start
    _runCleanup();
  }

  /// Stop the auto-delete timer
  void stop() {
    _cleanupTimer?.cancel();
    _cleanupTimer = null;
  }

  /// Run cleanup manually
  Future<void> runCleanup() => _runCleanup();

  Future<void> _runCleanup() async {
    try {
      // This would need to be implemented with a method to get all messages
      // For now, we'll process messages per chat when they're loaded
      debugPrint('AutoDeleteManager: Running cleanup check');
    } catch (e) {
      debugPrint('AutoDeleteManager: Error during cleanup: $e');
    }
  }

  /// Check and delete expired messages for a specific chat
  Future<List<String>> processMessagesForChat(
    String chatId,
    List<Message> messages,
  ) async {
    final now = DateTime.now();
    final deletedIds = <String>[];

    for (final message in messages) {
      if (message.autoDelete == null) continue;

      final expiryTime = _calculateExpiryTime(message);
      if (expiryTime != null && now.isAfter(expiryTime)) {
        await _messageDatasource.deleteMessage(message.id);
        deletedIds.add(message.id);
      }
    }

    if (deletedIds.isNotEmpty) {
      onMessagesDeleted?.call(deletedIds);
    }

    return deletedIds;
  }

  /// Calculate expiry time for a message based on its auto-delete policy
  DateTime? _calculateExpiryTime(Message message) {
    if (message.autoDelete == null) return null;

    final duration = getAutoDeleteDuration(message.autoDelete!);
    if (duration == null) return null;

    return message.timestamp.add(duration);
  }

  /// Get the duration for an auto-delete policy
  static Duration? getAutoDeleteDuration(AutoDeletePolicy policy) {
    switch (policy) {
      case AutoDeletePolicy.oneDay:
        return const Duration(seconds: AppConstants.autoDeleteOneDay);
      case AutoDeletePolicy.threeDays:
        return const Duration(seconds: AppConstants.autoDeleteThreeDays);
      case AutoDeletePolicy.sevenDays:
        return const Duration(seconds: AppConstants.autoDeleteSevenDays);
      case AutoDeletePolicy.thirtyDays:
        return const Duration(seconds: AppConstants.autoDeleteThirtyDays);
      case AutoDeletePolicy.afterRead:
        // Handled separately when message is read
        return null;
      case AutoDeletePolicy.onExit:
        // Handled when app exits
        return null;
    }
  }


  /// Check if a message should be deleted based on its policy
  bool shouldDelete(Message message, {bool isRead = false, bool isExiting = false}) {
    if (message.autoDelete == null) return false;

    final now = DateTime.now();

    switch (message.autoDelete!) {
      case AutoDeletePolicy.afterRead:
        return isRead;
      case AutoDeletePolicy.onExit:
        return isExiting;
      case AutoDeletePolicy.oneDay:
      case AutoDeletePolicy.threeDays:
      case AutoDeletePolicy.sevenDays:
      case AutoDeletePolicy.thirtyDays:
        final duration = getAutoDeleteDuration(message.autoDelete!);
        if (duration == null) return false;
        return now.isAfter(message.timestamp.add(duration));
    }
  }

  /// Get remaining time before message is deleted
  Duration? getRemainingTime(Message message) {
    if (message.autoDelete == null) return null;

    final duration = getAutoDeleteDuration(message.autoDelete!);
    if (duration == null) return null;

    final expiryTime = message.timestamp.add(duration);
    final remaining = expiryTime.difference(DateTime.now());

    return remaining.isNegative ? Duration.zero : remaining;
  }

  /// Delete all messages with "onExit" policy
  Future<List<String>> deleteOnExitMessages(List<Message> messages) async {
    final deletedIds = <String>[];

    for (final message in messages) {
      if (message.autoDelete == AutoDeletePolicy.onExit) {
        await _messageDatasource.deleteMessage(message.id);
        deletedIds.add(message.id);
      }
    }

    if (deletedIds.isNotEmpty) {
      onMessagesDeleted?.call(deletedIds);
    }

    return deletedIds;
  }

  /// Delete a message after it's been read (for "afterRead" policy)
  Future<bool> deleteAfterRead(Message message) async {
    if (message.autoDelete != AutoDeletePolicy.afterRead) {
      return false;
    }

    await _messageDatasource.deleteMessage(message.id);
    onMessagesDeleted?.call([message.id]);
    return true;
  }

  /// Apply auto-delete policy to a list of messages
  /// Returns the filtered list with expired messages removed
  Future<List<Message>> applyPolicy(List<Message> messages) async {
    final validMessages = <Message>[];
    final deletedIds = <String>[];

    for (final message in messages) {
      if (shouldDelete(message)) {
        await _messageDatasource.deleteMessage(message.id);
        deletedIds.add(message.id);
      } else {
        validMessages.add(message);
      }
    }

    if (deletedIds.isNotEmpty) {
      onMessagesDeleted?.call(deletedIds);
    }

    return validMessages;
  }

  /// Dispose resources
  void dispose() {
    stop();
  }
}
