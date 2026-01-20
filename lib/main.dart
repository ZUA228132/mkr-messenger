import 'dart:io' show Platform;

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart' show DefaultMaterialLocalizations;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/constants/app_constants.dart';
import 'core/theme/mkr_cupertino_theme.dart';
import 'data/datasources/api_client.dart';
import 'data/datasources/secure_local_storage.dart';
import 'data/datasources/websocket_service.dart';
import 'data/repositories/remote_auth_repository.dart';
import 'data/repositories/remote_call_repository.dart';
import 'data/repositories/remote_chat_repository.dart';
import 'data/repositories/remote_message_repository.dart';
import 'data/repositories/remote_user_repository.dart';
import 'data/services/livekit_service.dart';
import 'data/services/push_notification_service.dart';
import 'data/services/security_checker.dart';
import 'presentation/screens/auth_screen.dart';
import 'presentation/screens/main_tab_screen.dart';
import 'presentation/screens/security_check_screen.dart';
import 'presentation/screens/simple_calculator_screen.dart';
import 'presentation/screens/simple_chat_screen.dart';
import 'presentation/screens/simple_panic_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize push notifications (Android only for now)
  // TODO: Re-enable for iOS after fixing initialization issues
  if (Platform.isAndroid) {
    await PushNotificationService().initialize();
  }

  runApp(const ProviderScope(child: MKRApp()));
}

/// MKR Messenger App
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
class MKRApp extends StatefulWidget {
  const MKRApp({super.key});

  @override
  State<MKRApp> createState() => _MKRAppState();
}

class _MKRAppState extends State<MKRApp> {
  String? _currentUserId;
  
  // Backend integration services
  late final ApiClient _apiClient;
  late final SecureLocalStorage _storage;
  late final WebSocketService _webSocketService;
  late final RemoteAuthRepository _authRepository;
  late final RemoteChatRepository _chatRepository;
  late final RemoteMessageRepository _messageRepository;
  late final RemoteUserRepository _userRepository;
  late final RemoteCallRepository _callRepository;
  late final LiveKitService _liveKitService;

  @override
  void initState() {
    super.initState();
    _initializeServices();
    _setupIosMethodChannels();
  }

  void _initializeServices() {
    _apiClient = ApiClient();
    _storage = SecureLocalStorage();
    _webSocketService = WebSocketService();
    
    _authRepository = RemoteAuthRepository(
      apiClient: _apiClient,
      storage: _storage,
    );
    
    _chatRepository = RemoteChatRepository(apiClient: _apiClient);
    _messageRepository = RemoteMessageRepository(
      apiClient: _apiClient,
      webSocketService: _webSocketService,
    );
    _userRepository = RemoteUserRepository(apiClient: _apiClient);
    _callRepository = RemoteCallRepository(apiClient: _apiClient);
    _liveKitService = LiveKitService();
    
    // Set up unauthorized callback for token expiration
    // Requirements: 3.3 - Redirect to login when token expires
    _apiClient.setOnUnauthorized(() {
      _handleLogout();
    });
    
    // Initialize token from storage on app start
    _initializeAuthFromStorage();
  }

  /// Set up method channels for iOS push notifications and calls
  void _setupIosMethodChannels() {
    if (!Platform.isIOS) return;

    const methodChannel = MethodChannel('com.mkr.messenger/push_notification');

    methodChannel.setMethodCallHandler((call) async {
      print('iOS Method Call: ${call.method}');

      switch (call.method) {
        case 'onIncomingCall':
          _handleIosIncomingCall(call.arguments);
          break;

        case 'onFCMTokenReceived':
          await _handleIosFCMTokenReceived(call.arguments);
          break;

        case 'onAPNsTokenReceived':
          await _handleIosAPNsTokenReceived(call.arguments);
          break;

        case 'onCallAnswered':
          print('Call answered via CallKit');
          break;

        case 'onCallEnded':
          print('Call ended via CallKit');
          break;

        default:
          print('Unknown iOS method call: ${call.method}');
      }
    });

    print('iOS method channels set up successfully');
  }

  /// Handle incoming call from iOS CallKit
  void _handleIosIncomingCall(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) return;

    final callerId = arguments['callerId'] as String?;
    final callerName = arguments['callerName'] as String?;
    final isVideo = arguments['isVideo'] as bool? ?? false;
    final roomId = arguments['roomId'] as String?;

    print('Incoming call from iOS: $callerName ($callerId), video: $isVideo, room: $roomId');

    // TODO: Navigate to call screen or show incoming call UI
    // For now, just log the incoming call
  }

  /// Handle FCM token received from iOS
  Future<void> _handleIosFCMTokenReceived(dynamic arguments) async {
    if (arguments is! Map<String, dynamic>) return;

    final token = arguments['token'] as String?;
    if (token != null && token.isNotEmpty) {
      print('FCM Token received from iOS: ${token.substring(0, 20)}...');
      await _sendFcmTokenToBackend();
    }
  }

  /// Handle APNs token received from iOS
  Future<void> _handleIosAPNsTokenReceived(dynamic arguments) async {
    if (arguments is! Map<String, dynamic>) return;

    final token = arguments['token'] as String?;
    if (token != null && token.isNotEmpty) {
      print('APNs Token received from iOS: ${token.substring(0, 20)}...');
      // APNs token is automatically mapped to FCM by Firebase
    }
  }
  
  Future<void> _initializeAuthFromStorage() async {
    final token = await _storage.getToken();
    final userId = await _storage.getUserId();
    if (token != null && token.isNotEmpty && userId != null) {
      _apiClient.setAuthToken(token);
      setState(() => _currentUserId = userId);
      await _webSocketService.connect(userId);
      // Send FCM token to backend after restoring session
      await _sendFcmTokenToBackend();
    }
  }

  Future<void> _handleAuthenticated(String userId) async {
    setState(() => _currentUserId = userId);

    // Connect WebSocket after authentication
    // Requirements: 6.1 - Establish WebSocket connection
    await _webSocketService.connect(userId);

    // Send FCM token to backend after login
    await _sendFcmTokenToBackend();
  }

  /// Send FCM/APNs token to backend for push notifications
  Future<void> _sendFcmTokenToBackend() async {
    try {
      final pushService = PushNotificationService();
      final fcmToken = pushService.fcmToken;

      if (fcmToken != null && fcmToken.isNotEmpty) {
        print('Sending ${Platform.isAndroid ? 'FCM' : 'APNs'} token to backend: ${fcmToken.substring(0, 20)}...');
        final result = await _userRepository.updateFcmToken(fcmToken);
        result.fold(
          onSuccess: (_) {
            print('${Platform.isAndroid ? 'FCM' : 'APNs'} token sent to backend successfully');
          },
          onFailure: (error) {
            print('Failed to send ${Platform.isAndroid ? 'FCM' : 'APNs'} token: ${error.message}');
          },
        );
      } else {
        print('No ${Platform.isAndroid ? 'FCM' : 'APNs'} token available yet');
      }
    } catch (e) {
      print('Error sending ${Platform.isAndroid ? 'FCM' : 'APNs'} token: $e');
    }
  }

  Future<void> _handleLogout() async {
    // Disconnect WebSocket
    await _webSocketService.disconnect();
    
    // Clear credentials
    // Requirements: 3.4 - Clear all stored credentials
    await _authRepository.logout();
    
    setState(() => _currentUserId = null);
  }

  @override
  void dispose() {
    _webSocketService.dispose();
    _messageRepository.dispose();
    _liveKitService.dispose();
    super.dispose();
  }

  late final GoRouter _router = GoRouter(
    initialLocation: '/auth',
    redirect: (context, state) {
      final isAuth = state.matchedLocation == '/auth';
      final isLoggedIn = _currentUserId != null;

      if (!isLoggedIn && !isAuth) return '/auth';
      if (isLoggedIn && isAuth) return '/home';
      return null;
    },
    routes: [
      GoRoute(
        path: '/auth',
        builder: (context, state) => AuthScreen(
          onAuthenticated: (userId) {
            _handleAuthenticated(userId);
            context.go('/home');
          },
        ),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => MainTabScreen(
          currentUserId: _currentUserId ?? 'user',
          chatRepository: _chatRepository,
          userRepository: _userRepository,
          authRepository: _authRepository,
          onLogout: () {
            _handleLogout();
            context.go('/auth');
          },
        ),
      ),
      GoRoute(
        path: '/chat/:chatId',
        builder: (context, state) {
          final chatId = state.pathParameters['chatId'] ?? '';
          return SimpleChatScreen(
            chatId: chatId,
            currentUserId: _currentUserId ?? 'user',
            messageRepository: _messageRepository,
            userRepository: _userRepository,
            chatRepository: _chatRepository,
            callRepository: _callRepository,
            liveKitService: _liveKitService,
            webSocketService: _webSocketService,
            onBack: () => context.pop(),
          );
        },
      ),
      GoRoute(
        path: '/security-check',
        builder: (context, state) => SecurityCheckScreen(
          securityChecker: SecurityChecker(),
        ),
      ),
      GoRoute(
        path: '/panic',
        builder: (context, state) => SimplePanicScreen(
          onWipeComplete: () {
            // Wipe all data and logout
            _handleLogout();
            context.go('/auth');
          },
        ),
      ),
      GoRoute(
        path: '/stealth',
        builder: (context, state) => SimpleCalculatorScreen(
          secretCode: '1337',
          onSecretCodeEntered: () {
            // Unlock from stealth mode
            context.go('/home');
          },
        ),
      ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    return CupertinoApp.router(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: MKRCupertinoTheme.darkTheme,
      localizationsDelegates: const [
        DefaultMaterialLocalizations.delegate,
        DefaultCupertinoLocalizations.delegate,
        DefaultWidgetsLocalizations.delegate,
      ],
      routerConfig: _router,
    );
  }
}
