import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart' show DefaultMaterialLocalizations;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/constants/app_constants.dart';
import 'core/theme/mkr_cupertino_theme.dart';
import 'data/datasources/api_client.dart';
import 'data/datasources/secure_local_storage.dart';
import 'data/datasources/websocket_service.dart';
import 'data/repositories/remote_auth_repository.dart';
import 'data/repositories/remote_chat_repository.dart';
import 'data/repositories/remote_message_repository.dart';
import 'data/repositories/remote_user_repository.dart';
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
  
  // Initialize push notifications
  await PushNotificationService().initialize();
  
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

  @override
  void initState() {
    super.initState();
    _initializeServices();
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
    
    // Set up unauthorized callback for token expiration
    // Requirements: 3.3 - Redirect to login when token expires
    _apiClient.setOnUnauthorized(() {
      _handleLogout();
    });
  }

  Future<void> _handleAuthenticated(String userId) async {
    setState(() => _currentUserId = userId);
    
    // Connect WebSocket after authentication
    // Requirements: 6.1 - Establish WebSocket connection
    await _webSocketService.connect(userId);
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
            recipientId: chatId,
            currentUserId: _currentUserId ?? 'user',
            messageRepository: _messageRepository,
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
