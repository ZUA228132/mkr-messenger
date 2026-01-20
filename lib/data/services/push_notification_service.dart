import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

/// Service for handling push notifications
/// Requirements: 8.4 - Use APNs for push notifications
///
/// Supports:
/// - FCM (Firebase Cloud Messaging) for Android
/// - APNs (Apple Push Notification Service) for iOS
/// - VOIP push notifications for incoming calls on iOS
class PushNotificationService {
  static final PushNotificationService _instance =
      PushNotificationService._internal();
  factory PushNotificationService() => _instance;
  PushNotificationService._internal();

  final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;
  String? _fcmToken;
  String? _apnsToken;
  bool _isInitialized = false;

  /// Get the current FCM token (Android) or APNs token mapped via FCM (iOS)
  String? get fcmToken => _fcmToken;

  /// Get the current APNs token (iOS only)
  String? get apnsToken => _apnsToken;

  /// Check if service is initialized
  bool get isInitialized => _isInitialized;

  /// Initialize push notifications and Firebase
  Future<void> initialize() async {
    if (_isInitialized) {
      debugPrint('PushNotificationService already initialized');
      return;
    }

    try {
      // Initialize Firebase Core
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
        debugPrint('Firebase initialized successfully');
      } else {
        debugPrint('Firebase already initialized');
      }

      // Request notification permissions (iOS only, Android handles in manifest)
      if (Platform.isIOS) {
        await _requestPermissions();
      }

      // Get tokens
      await _getTokens();

      // Handle messages when app is in foreground
      FirebaseMessaging.onMessage.listen(_handleForegroundMessage);

      // Handle messages when app is in background but opened
      FirebaseMessaging.onMessageOpenedApp.listen(_handleMessage);

      // Handle background messages (Android only, iOS uses native handlers)
      if (Platform.isAndroid) {
        try {
          FirebaseMessaging.onBackgroundMessage(_firebaseBackgroundMessageHandler);
        } catch (e) {
          debugPrint('Failed to set background message handler: $e');
        }
      }

      // Listen for token refresh
      _firebaseMessaging.onTokenRefresh.listen(_onTokenRefresh);

      _isInitialized = true;
      debugPrint('PushNotificationService initialized successfully');
      if (_fcmToken != null && _fcmToken!.isNotEmpty) {
        final displayToken = _fcmToken!.length > 20 ? '${_fcmToken!.substring(0, 20)}...' : _fcmToken!;
        debugPrint('FCM Token: $displayToken');
      }
      if (Platform.isIOS && _apnsToken != null && _apnsToken!.isNotEmpty) {
        final displayToken = _apnsToken!.length > 20 ? '${_apnsToken!.substring(0, 20)}...' : _apnsToken!;
        debugPrint('APNs Token: $displayToken');
      }
    } catch (e, st) {
      debugPrint('Failed to initialize push notifications: $e');
      debugPrint('Stack trace: $st');
      // Don't rethrow - allow app to continue even if push notifications fail
      _isInitialized = true;
    }
  }

  /// Request notification permissions
  Future<void> _requestPermissions() async {
    try {
      if (Platform.isIOS) {
        // Request APNs token for regular notifications
        final settings = await _firebaseMessaging.requestPermission(
          alert: true,
          announcement: false,
          badge: true,
          carPlay: false,
          criticalAlert: false,
          provisional: false,
          sound: true,
        );

        debugPrint('APNs permission status: ${settings.authorizationStatus}');

        // Get APNs token (may be null initially)
        try {
          _apnsToken = await _firebaseMessaging.getAPNSToken();
          if (_apnsToken != null) {
            debugPrint('APNs Token received: ${_apnsToken?.substring(0, 20)}...');
          }
        } catch (e) {
          debugPrint('Could not get APNs token: $e');
        }
      }

      // Enable auto-init for FCM token
      await _firebaseMessaging.setAutoInitEnabled(true);
    } catch (e) {
      debugPrint('Error requesting permissions: $e');
    }
  }

  /// Get FCM and APNs tokens
  Future<void> _getTokens() async {
    try {
      // Get FCM token
      _fcmToken = await _firebaseMessaging.getToken();
      if (_fcmToken != null) {
        debugPrint('FCM Token: ${_fcmToken?.substring(0, 20)}...');
      }

      // For iOS, also get APNs token if not already retrieved
      if (Platform.isIOS && _apnsToken == null) {
        _apnsToken = await _firebaseMessaging.getAPNSToken();
        if (_apnsToken != null) {
          debugPrint('APNs Token: ${_apnsToken?.substring(0, 20)}...');
        }
      }
    } catch (e) {
      debugPrint('Error getting tokens: $e');
    }
  }

  /// Handle incoming message when app is in foreground
  void _handleForegroundMessage(RemoteMessage message) {
    debugPrint('Received foreground message: ${message.messageId}');
    _handleMessage(message);
  }

  /// Handle incoming message
  void _handleMessage(RemoteMessage message) {
    debugPrint('Handling message: ${message.messageId}');
    debugPrint('Message data: ${message.data}');

    // Handle different notification types
    final messageType = message.data['type'] ?? 'unknown';
    debugPrint('Message type: $messageType');

    switch (messageType) {
      case 'call':
        _handleCallNotification(message);
        break;
      case 'message':
        _handleChatMessage(message);
        break;
      default:
        _handleGenericNotification(message);
    }
  }

  /// Handle incoming call notification
  void _handleCallNotification(RemoteMessage message) {
    debugPrint('Incoming call notification');
    debugPrint('Call data: ${message.data}');

    final callerId = message.data['caller_id'];
    final callerName = message.data['caller_name'];
    final callType = message.data['call_type'] ?? 'audio';
    final roomId = message.data['room_id'];

    debugPrint('Incoming $callType call from $callerName ($callerId)');
    debugPrint('Room ID: $roomId');

    // TODO: Navigate to call screen or show incoming call UI
    // This should trigger the LiveKit service to connect to the call
  }

  /// Handle chat message notification
  void _handleChatMessage(RemoteMessage message) {
    debugPrint('Chat message notification');
    debugPrint('Message data: ${message.data}');

    final senderId = message.data['sender_id'];
    final senderName = message.data['sender_name'];
    final messageContent = message.data['message'];

    debugPrint('Message from $senderName ($senderId): $messageContent');

    // TODO: Update chat list or navigate to chat screen
  }

  /// Handle generic notification
  void _handleGenericNotification(RemoteMessage message) {
    debugPrint('Generic notification');
    if (message.notification != null) {
      debugPrint('Title: ${message.notification!.title}');
      debugPrint('Body: ${message.notification!.body}');
    }
  }

  /// Handle token refresh
  void _onTokenRefresh(String newToken) {
    debugPrint('Token refreshed: ${newToken.substring(0, 20)}...');
    _fcmToken = newToken;

    // TODO: Send new token to backend
    // This should update the user's FCM token on the server
  }

  /// Subscribe to a topic (for group notifications)
  Future<void> subscribeToTopic(String topic) async {
    try {
      await _firebaseMessaging.subscribeToTopic(topic);
      debugPrint('Subscribed to topic: $topic');
    } catch (e) {
      debugPrint('Failed to subscribe to topic $topic: $e');
    }
  }

  /// Unsubscribe from a topic
  Future<void> unsubscribeFromTopic(String topic) async {
    try {
      await _firebaseMessaging.unsubscribeFromTopic(topic);
      debugPrint('Unsubscribed from topic: $topic');
    } catch (e) {
      debugPrint('Failed to unsubscribe from topic $topic: $e');
    }
  }

  /// Delete token (for logout)
  Future<void> deleteToken() async {
    try {
      await _firebaseMessaging.deleteToken();
      _fcmToken = null;
      _apnsToken = null;
      _isInitialized = false;
      debugPrint('Tokens deleted successfully');
    } catch (e) {
      debugPrint('Failed to delete token: $e');
    }
  }

  /// Check if notifications are enabled
  Future<bool> areNotificationsEnabled() async {
    if (Platform.isIOS) {
      final settings = await _firebaseMessaging.getNotificationSettings();
      return settings.authorizationStatus == AuthorizationStatus.authorized ||
          settings.authorizationStatus == AuthorizationStatus.provisional;
    }
    // Android doesn't have a simple check for this
    return true;
  }

  /// Get APNs token for iOS (can be called from native code)
  Future<String?> getApnsToken() async {
    if (!Platform.isIOS) {
      debugPrint('APNs token is only available on iOS');
      return null;
    }
    _apnsToken = await _firebaseMessaging.getAPNSToken();
    return _apnsToken;
  }

  /// Force refresh tokens
  Future<void> refreshTokens() async {
    try {
      // Force token refresh by deleting and getting new one
      await _firebaseMessaging.deleteToken();
      await _getTokens();
      debugPrint('Tokens refreshed successfully');
    } catch (e) {
      debugPrint('Failed to refresh tokens: $e');
    }
  }
}

/// Top-level function for background message handling
/// This must be a top-level function or static method
@pragma('vm:entry-point')
Future<void> _firebaseBackgroundMessageHandler(RemoteMessage message) async {
  // Initialize Firebase if not already initialized (Android only)
  // On iOS, Firebase is initialized in AppDelegate
  // Note: Background message handler has limited execution time
  debugPrint('Background message received: ${message.messageId}');
  debugPrint('Message data: ${message.data}');

  // Handle background messages differently
  // For calls, we might want to show a local notification
  final messageType = message.data['type'] ?? 'unknown';

  if (messageType == 'call') {
    debugPrint('Background call notification received');
    // TODO: Show local notification for incoming call
    // This is important when the app is terminated or in background
  }
}
