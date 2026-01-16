import 'package:flutter/cupertino.dart';

/// MKR Messenger Cupertino Theme Configuration
/// Requirements: 8.1 - Use Cupertino widgets for native iOS look
class MKRCupertinoTheme {
  MKRCupertinoTheme._();

  // Brand Colors
  static const Color primaryColor = CupertinoColors.systemBlue;
  static const Color accentColor = CupertinoColors.systemIndigo;
  static const Color dangerColor = CupertinoColors.systemRed;
  static const Color successColor = CupertinoColors.systemGreen;
  static const Color warningColor = CupertinoColors.systemOrange;

  // Dark Theme (Default for secure messenger)
  static CupertinoThemeData get darkTheme => const CupertinoThemeData(
        brightness: Brightness.dark,
        primaryColor: primaryColor,
        primaryContrastingColor: CupertinoColors.white,
        scaffoldBackgroundColor: CupertinoColors.black,
        barBackgroundColor: Color(0xFF1C1C1E),
        textTheme: CupertinoTextThemeData(
          primaryColor: CupertinoColors.white,
          textStyle: TextStyle(
            fontFamily: '.SF Pro Text',
            fontSize: 17,
            color: CupertinoColors.white,
          ),
          navTitleTextStyle: TextStyle(
            fontFamily: '.SF Pro Text',
            fontSize: 17,
            fontWeight: FontWeight.w600,
            color: CupertinoColors.white,
          ),
          navLargeTitleTextStyle: TextStyle(
            fontFamily: '.SF Pro Display',
            fontSize: 34,
            fontWeight: FontWeight.bold,
            color: CupertinoColors.white,
          ),
          actionTextStyle: TextStyle(
            fontFamily: '.SF Pro Text',
            fontSize: 17,
            color: primaryColor,
          ),
        ),
      );

  // Light Theme (Optional)
  static CupertinoThemeData get lightTheme => const CupertinoThemeData(
        brightness: Brightness.light,
        primaryColor: primaryColor,
        primaryContrastingColor: CupertinoColors.white,
        scaffoldBackgroundColor: CupertinoColors.systemGroupedBackground,
        barBackgroundColor: CupertinoColors.systemBackground,
        textTheme: CupertinoTextThemeData(
          primaryColor: CupertinoColors.black,
          textStyle: TextStyle(
            fontFamily: '.SF Pro Text',
            fontSize: 17,
            color: CupertinoColors.black,
          ),
          navTitleTextStyle: TextStyle(
            fontFamily: '.SF Pro Text',
            fontSize: 17,
            fontWeight: FontWeight.w600,
            color: CupertinoColors.black,
          ),
          navLargeTitleTextStyle: TextStyle(
            fontFamily: '.SF Pro Display',
            fontSize: 34,
            fontWeight: FontWeight.bold,
            color: CupertinoColors.black,
          ),
          actionTextStyle: TextStyle(
            fontFamily: '.SF Pro Text',
            fontSize: 17,
            color: primaryColor,
          ),
        ),
      );

  // Stealth Mode Theme (Calculator disguise)
  static CupertinoThemeData get stealthTheme => const CupertinoThemeData(
        brightness: Brightness.dark,
        primaryColor: CupertinoColors.systemOrange,
        scaffoldBackgroundColor: CupertinoColors.black,
        barBackgroundColor: CupertinoColors.black,
      );
}

/// Common iOS-style spacing values
class MKRSpacing {
  MKRSpacing._();

  static const double xs = 4.0;
  static const double sm = 8.0;
  static const double md = 16.0;
  static const double lg = 24.0;
  static const double xl = 32.0;
  static const double xxl = 48.0;
}

/// Common iOS-style border radius values
class MKRRadius {
  MKRRadius._();

  static const double small = 8.0;
  static const double medium = 12.0;
  static const double large = 16.0;
  static const double xlarge = 20.0;
  static const double circular = 100.0;
}
