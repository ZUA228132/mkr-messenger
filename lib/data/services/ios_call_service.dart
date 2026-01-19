import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Service for handling iOS native calls via CallKit and VOIP
/// Communicates with iOS native code through method channels
class IosCallService {
  static final IosCallService _instance = IosCallService._internal();
  factory IosCallService() => _instance;
  IosCallService._internal();

  static const _methodChannelName = 'com.mkr.messenger/push_notification';
  final MethodChannel _methodChannel = const MethodChannel(_methodChannelName);

  bool _isInitialized = false;
  final Map<String, Function(dynamic)> _handlers = {};

  /// Initialize the service and set up method call handlers
  void initialize() {
    if (_isInitialized) return;

    _methodChannel.setMethodCallHandler(_handleMethodCall);
    _isInitialized = true;
    debugPrint('IosCallService initialized');
  }

  /// Handle incoming method calls from iOS native code
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    debugPrint('IosCallService: Received method call: ${call.method}');

    switch (call.method) {
      case 'onIncomingCall':
        return _handleIncomingCall(call.arguments);

      case 'onCallAnswered':
        return _handleCallAnswered(call.arguments);

      case 'onCallEnded':
        return _handleCallEnded(call.arguments);

      case 'onCallFailed':
        return _handleCallFailed(call.arguments);

      case 'onFCMTokenReceived':
        return _handleFCMTokenReceived(call.arguments);

      case 'onAPNsTokenReceived':
        return _handleAPNsTokenReceived(call.arguments);

      case 'onVoipTokenReceived':
        return _handleVoipTokenReceived(call.arguments);

      case 'onNotificationReceived':
        return _handleNotificationReceived(call.arguments);

      case 'onNotificationTapped':
        return _handleNotificationTapped(call.arguments);

      default:
        if (_handlers.containsKey(call.method)) {
          return _handlers[call.method]?.call(call.arguments);
        }
        debugPrint('IosCallService: Unknown method: ${call.method}');
        return null;
    }
  }

  /// Handle incoming call from iOS CallKit
  dynamic _handleIncomingCall(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onIncomingCall: $arguments');
      return null;
    }

    final callerId = arguments['callerId'] as String?;
    final callerName = arguments['callerName'] as String?;
    final isVideo = arguments['isVideo'] as bool? ?? false;
    final roomId = arguments['roomId'] as String?;

    debugPrint('IosCallService: Incoming call from $callerName ($callerId), video: $isVideo, room: $roomId');

    // Trigger callback if registered
    _handlers['onIncomingCall']?.call({
      'callerId': callerId,
      'callerName': callerName,
      'isVideo': isVideo,
      'roomId': roomId,
    });

    return null;
  }

  /// Handle call answered event from iOS CallKit
  dynamic _handleCallAnswered(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onCallAnswered: $arguments');
      return null;
    }

    final uuid = arguments['uuid'] as String?;

    debugPrint('IosCallService: Call answered: $uuid');

    _handlers['onCallAnswered']?.call({'uuid': uuid});
    return null;
  }

  /// Handle call ended event from iOS CallKit
  dynamic _handleCallEnded(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onCallEnded: $arguments');
      return null;
    }

    final uuid = arguments['uuid'] as String?;

    debugPrint('IosCallService: Call ended: $uuid');

    _handlers['onCallEnded']?.call({'uuid': uuid});
    return null;
  }

  /// Handle call failed event
  dynamic _handleCallFailed(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onCallFailed: $arguments');
      return null;
    }

    final error = arguments['error'] as String?;

    debugPrint('IosCallService: Call failed: $error');

    _handlers['onCallFailed']?.call({'error': error});
    return null;
  }

  /// Handle FCM token received from iOS
  dynamic _handleFCMTokenReceived(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onFCMTokenReceived: $arguments');
      return null;
    }

    final token = arguments['token'] as String?;

    debugPrint('IosCallService: FCM token received: ${token?.substring(0, 20)}...');

    _handlers['onFCMTokenReceived']?.call({'token': token});
    return null;
  }

  /// Handle APNs token received from iOS
  dynamic _handleAPNsTokenReceived(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onAPNsTokenReceived: $arguments');
      return null;
    }

    final token = arguments['token'] as String?;

    debugPrint('IosCallService: APNs token received: ${token?.substring(0, 20)}...');

    _handlers['onAPNsTokenReceived']?.call({'token': token});
    return null;
  }

  /// Handle VOIP token received from iOS
  dynamic _handleVoipTokenReceived(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onVoipTokenReceived: $arguments');
      return null;
    }

    final token = arguments['token'] as String?;

    debugPrint('IosCallService: VOIP token received: ${token?.substring(0, 20)}...');

    _handlers['onVoipTokenReceived']?.call({'token': token});
    return null;
  }

  /// Handle notification received while app is in foreground
  dynamic _handleNotificationReceived(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onNotificationReceived: $arguments');
      return null;
    }

    final type = arguments['type'] as String?;
    final data = arguments['data'] as Map<String, dynamic>?;

    debugPrint('IosCallService: Notification received, type: $type');

    _handlers['onNotificationReceived']?.call({'type': type, 'data': data});
    return null;
  }

  /// Handle notification tapped by user
  dynamic _handleNotificationTapped(dynamic arguments) {
    if (arguments is! Map<String, dynamic>) {
      debugPrint('Invalid arguments for onNotificationTapped: $arguments');
      return null;
    }

    final data = arguments['data'] as Map<String, dynamic>?;

    debugPrint('IosCallService: Notification tapped');

    _handlers['onNotificationTapped']?.call({'data': data});
    return null;
  }

  /// Register a custom handler for specific events
  void registerHandler(String event, Function(dynamic) handler) {
    _handlers[event] = handler;
    debugPrint('IosCallService: Registered handler for $event');
  }

  /// Unregister a handler
  void unregisterHandler(String event) {
    _handlers.remove(event);
    debugPrint('IosCallService: Unregistered handler for $event');
  }

  /// Report an incoming call to iOS CallKit (for iOS only)
  Future<bool> reportIncomingCall({
    required String callerId,
    required String callerName,
    bool isVideo = false,
    String? roomId,
  }) async {
    try {
      debugPrint('IosCallService: Reporting incoming call from $callerName');

      final result = await _methodChannel.invokeMethod('reportIncomingCall', {
        'callerId': callerId,
        'callerName': callerName,
        'isVideo': isVideo,
        'roomId': roomId,
      });

      if (result == true) {
        debugPrint('IosCallService: Incoming call reported successfully');
        return true;
      } else {
        debugPrint('IosCallService: Failed to report incoming call');
        return false;
      }
    } catch (e) {
      debugPrint('IosCallService: Error reporting incoming call: $e');
      return false;
    }
  }

  /// Start an outgoing call via iOS CallKit (for iOS only)
  Future<bool> startOutgoingCall({
    required String callerId,
    bool isVideo = false,
  }) async {
    try {
      debugPrint('IosCallService: Starting outgoing call to $callerId');

      final result = await _methodChannel.invokeMethod('startOutgoingCall', {
        'callerId': callerId,
        'isVideo': isVideo,
      });

      if (result == true) {
        debugPrint('IosCallService: Outgoing call started successfully');
        return true;
      } else {
        debugPrint('IosCallService: Failed to start outgoing call');
        return false;
      }
    } catch (e) {
      debugPrint('IosCallService: Error starting outgoing call: $e');
      return false;
    }
  }

  /// End the current call via iOS CallKit (for iOS only)
  Future<void> endCall() async {
    try {
      debugPrint('IosCallService: Ending call');

      await _methodChannel.invokeMethod('endCall');

      debugPrint('IosCallService: Call ended successfully');
    } catch (e) {
      debugPrint('IosCallService: Error ending call: $e');
    }
  }

  /// Get APNs token (iOS only)
  Future<String?> getApnsToken() async {
    try {
      final result = await _methodChannel.invokeMethod('getAPNsToken');
      return result as String?;
    } catch (e) {
      debugPrint('IosCallService: Error getting APNs token: $e');
      return null;
    }
  }

  /// Register for remote notifications (iOS only)
  Future<void> registerForRemoteNotifications() async {
    try {
      await _methodChannel.invokeMethod('registerForRemoteNotifications');
      debugPrint('IosCallService: Registered for remote notifications');
    } catch (e) {
      debugPrint('IosCallService: Error registering for notifications: $e');
    }
  }
}
