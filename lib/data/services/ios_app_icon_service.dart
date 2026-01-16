import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Service for managing iOS alternate app icons (Stealth Mode)
/// Requirements: 5.1 - Change app icon to selected disguise
/// Requirements: 8.1 - iOS-specific features
class IOSAppIconService {
  static const _channel = MethodChannel('com.mkr.messenger/app_icon');

  /// Available alternate icons
  static const String iconDefault = 'AppIcon';
  static const String iconCalculator = 'Calculator';
  static const String iconNotes = 'Notes';
  static const String iconWeather = 'Weather';

  /// Check if alternate icons are supported (iOS 10.3+)
  static Future<bool> get supportsAlternateIcons async {
    if (!Platform.isIOS) return false;
    
    try {
      final result = await _channel.invokeMethod<bool>('supportsAlternateIcons');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Get the current app icon name
  /// Returns null if using the primary icon
  static Future<String?> get currentIconName async {
    if (!Platform.isIOS) return null;
    
    try {
      return await _channel.invokeMethod<String?>('getAlternateIconName');
    } on PlatformException {
      return null;
    }
  }

  /// Set the app icon
  /// Pass null to reset to the primary icon
  static Future<bool> setAppIcon(String? iconName) async {
    if (!Platform.isIOS) return false;
    
    try {
      await _channel.invokeMethod('setAlternateIconName', {'iconName': iconName});
      return true;
    } on PlatformException catch (e) {
      debugPrint('Failed to set app icon: ${e.message}');
      return false;
    }
  }

  /// Reset to the default app icon
  static Future<bool> resetToDefaultIcon() async {
    return setAppIcon(null);
  }

  /// Set calculator disguise icon
  static Future<bool> setCalculatorIcon() async {
    return setAppIcon(iconCalculator);
  }

  /// Set notes disguise icon
  static Future<bool> setNotesIcon() async {
    return setAppIcon(iconNotes);
  }

  /// Set weather disguise icon
  static Future<bool> setWeatherIcon() async {
    return setAppIcon(iconWeather);
  }
}

/// Enum for available disguise types
enum DisguiseIconType {
  none,
  calculator,
  notes,
  weather,
}

extension DisguiseIconTypeExtension on DisguiseIconType {
  String? get iconName {
    switch (this) {
      case DisguiseIconType.none:
        return null;
      case DisguiseIconType.calculator:
        return IOSAppIconService.iconCalculator;
      case DisguiseIconType.notes:
        return IOSAppIconService.iconNotes;
      case DisguiseIconType.weather:
        return IOSAppIconService.iconWeather;
    }
  }

  String get displayName {
    switch (this) {
      case DisguiseIconType.none:
        return 'MKR Messenger';
      case DisguiseIconType.calculator:
        return 'Calculator';
      case DisguiseIconType.notes:
        return 'Notes';
      case DisguiseIconType.weather:
        return 'Weather';
    }
  }
}
