import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/constants/app_constants.dart';
import 'core/theme/mkr_cupertino_theme.dart';
import 'data/services/push_notification_service.dart';
import 'presentation/screens/home_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize push notifications
  // Requirements: 8.4 - Use APNs for push notifications
  await PushNotificationService().initialize();
  
  runApp(const ProviderScope(child: MKRApp()));
}

/// MKR Messenger App
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
class MKRApp extends StatelessWidget {
  const MKRApp({super.key});

  @override
  Widget build(BuildContext context) {
    // Use CupertinoApp for iOS-native experience
    // On Android, Cupertino widgets still work but with iOS styling
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

final _router = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const HomeScreen(),
    ),
    GoRoute(
      path: '/chats',
      builder: (context, state) => const _PlaceholderScreen(title: 'Chats'),
    ),
    GoRoute(
      path: '/chat/:chatId',
      builder: (context, state) {
        final chatId = state.pathParameters['chatId'] ?? '';
        return _PlaceholderScreen(title: 'Chat: $chatId');
      },
    ),
    GoRoute(
      path: '/security',
      builder: (context, state) => const _PlaceholderScreen(title: 'Security'),
    ),
    GoRoute(
      path: '/panic',
      builder: (context, state) => const _PlaceholderScreen(title: 'Panic'),
    ),
    GoRoute(
      path: '/settings',
      builder: (context, state) => const _PlaceholderScreen(title: 'Settings'),
    ),
  ],
);

/// Placeholder screen for routes that need full implementation
class _PlaceholderScreen extends StatelessWidget {
  final String title;

  const _PlaceholderScreen({required this.title});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: Text(title),
      ),
      child: Center(
        child: Text(title),
      ),
    );
  }
}

/// Helper to check if running on iOS
bool get isIOS => Platform.isIOS;
