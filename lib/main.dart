import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/constants/app_constants.dart';
import 'core/theme/mkr_cupertino_theme.dart';
import 'data/services/push_notification_service.dart';
import 'data/services/security_checker.dart';
import 'presentation/screens/auth_screen.dart';
import 'presentation/screens/fake_calculator_screen.dart';
import 'presentation/screens/main_tab_screen.dart';
import 'presentation/screens/panic_button_screen.dart';
import 'presentation/screens/security_check_screen.dart';
import 'presentation/screens/simple_chat_screen.dart';

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
  String? _currentUser;

  late final GoRouter _router = GoRouter(
    initialLocation: '/auth',
    redirect: (context, state) {
      final isAuth = state.matchedLocation == '/auth';
      final isLoggedIn = _currentUser != null;

      if (!isLoggedIn && !isAuth) return '/auth';
      if (isLoggedIn && isAuth) return '/home';
      return null;
    },
    routes: [
      GoRoute(
        path: '/auth',
        builder: (context, state) => AuthScreen(
          onAuthenticated: (callsign) {
            setState(() => _currentUser = callsign);
            context.go('/home');
          },
        ),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => MainTabScreen(
          currentUserId: _currentUser ?? 'user',
        ),
      ),
      GoRoute(
        path: '/chat/:chatId',
        builder: (context, state) {
          final chatId = state.pathParameters['chatId'] ?? '';
          return SimpleChatScreen(
            recipientId: chatId,
            currentUserId: _currentUser ?? 'user',
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
        builder: (context, state) => PanicButtonScreen(
          onPanicTriggered: () async {
            // Wipe all data
            setState(() => _currentUser = null);
            if (context.mounted) {
              context.go('/auth');
            }
          },
        ),
      ),
      GoRoute(
        path: '/stealth',
        builder: (context, state) => FakeCalculatorScreen(
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
