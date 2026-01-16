/// Application-wide constants
class AppConstants {
  AppConstants._();

  static const String appName = 'MKR Messenger';
  static const String appVersion = '1.0.0';
  
  // API Configuration
  static const String baseUrl = 'https://api.mkr-messenger.com';
  static const String wsUrl = 'wss://api.mkr-messenger.com/ws';
  
  // Validation
  static const int minCallsignLength = 3;
  static const int maxCallsignLength = 16;
  static const int minPinLength = 4;
  static const int maxPinLength = 6;
  
  // Security
  static const int maxLoginAttempts = 5;
  static const int baseLockoutSeconds = 30;
  static const int stealthMaxAttempts = 5;
  
  // Auto-delete intervals (in seconds)
  static const int autoDeleteOneDay = 86400;
  static const int autoDeleteThreeDays = 259200;
  static const int autoDeleteSevenDays = 604800;
  static const int autoDeleteThirtyDays = 2592000;
}
