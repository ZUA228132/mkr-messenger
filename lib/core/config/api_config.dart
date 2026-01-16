import 'package:flutter/foundation.dart';

/// API configuration for MKR Messenger backend integration.
/// 
/// Provides base URLs for development and production environments,
/// timeout settings, and WebSocket configuration.
/// 
/// Requirements: 1.1, 1.2 - API endpoint configuration with environment switching
class ApiConfig {
  ApiConfig._();

  // Base URLs
  static const String devBaseUrl = 'http://localhost:8080';
  static const String prodBaseUrl = 'https://kluboksrm.ru';

  /// Returns the appropriate base URL based on build mode.
  /// Uses devBaseUrl in debug mode, prodBaseUrl in release mode.
  static String get baseUrl => kDebugMode ? devBaseUrl : prodBaseUrl;

  /// WebSocket URL derived from base URL.
  /// Replaces http/https with ws/wss protocol.
  static String get wsBaseUrl {
    final url = baseUrl;
    if (url.startsWith('https://')) {
      return url.replaceFirst('https://', 'wss://');
    }
    return url.replaceFirst('http://', 'ws://');
  }

  /// Full WebSocket endpoint for user connection.
  static String wsUrl(String userId) => '$wsBaseUrl/ws/$userId';

  // Timeout settings
  static const Duration connectTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 30);
  static const Duration sendTimeout = Duration(seconds: 30);

  // API paths
  static const String authBasePath = '/api/auth';
  static const String usersBasePath = '/api/users';
  static const String chatsBasePath = '/api/chats';
  static const String messagesBasePath = '/api/messages';

  // Auth endpoints
  static const String registerEmailPath = '$authBasePath/register/email';
  static const String verifyEmailPath = '$authBasePath/verify/email';
  static const String loginEmailPath = '$authBasePath/login/email';
  static const String logoutPath = '$authBasePath/logout';

  // User endpoints
  static String userPath(String userId) => '$usersBasePath/$userId';
  static const String currentUserPath = '$usersBasePath/me';
  static const String currentUserAvatarPath = '$usersBasePath/me/avatar';
  static const String searchUsersPath = '$usersBasePath/search';
  static const String fcmTokenPath = '$usersBasePath/fcm-token';

  // Chat endpoints
  static String chatPath(String chatId) => '$chatsBasePath/$chatId';

  // Message endpoints
  static String chatMessagesPath(String chatId) => '$messagesBasePath/$chatId';

  // Retry settings
  static const int maxRetryAttempts = 3;
  static const Duration retryDelay = Duration(seconds: 1);

  // WebSocket reconnection settings
  static const Duration initialReconnectDelay = Duration(seconds: 1);
  static const Duration maxReconnectDelay = Duration(seconds: 30);
  static const double reconnectBackoffMultiplier = 2.0;
}
