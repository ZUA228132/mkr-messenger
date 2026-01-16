import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../core/config/api_config.dart';

/// WebSocket message types supported by MKR Backend
/// Requirements: 6.2 - Handle WebSocket message types
enum WsMessageType {
  newMessage('new_message'),
  typing('typing'),
  userOnline('user_online'),
  userOffline('user_offline'),
  ping('ping'),
  pong('pong'),
  unknown('unknown');

  final String value;
  const WsMessageType(this.value);

  static WsMessageType fromString(String value) {
    return WsMessageType.values.firstWhere(
      (e) => e.value == value,
      orElse: () => WsMessageType.unknown,
    );
  }
}

/// WebSocket message wrapper
/// Requirements: 6.2 - Handle incoming WebSocket messages
class WsMessage {
  final String type;
  final Map<String, dynamic> payload;

  const WsMessage({
    required this.type,
    required this.payload,
  });

  /// Parse a WebSocket message from JSON string
  /// Requirements: 5.4, 6.2 - Parse incoming WebSocket messages
  factory WsMessage.fromJson(Map<String, dynamic> json) {
    return WsMessage(
      type: json['type'] as String? ?? 'unknown',
      payload: json['payload'] as Map<String, dynamic>? ?? {},
    );
  }

  /// Convert message to JSON for sending
  Map<String, dynamic> toJson() => {
        'type': type,
        'payload': payload,
      };

  /// Get the message type enum
  WsMessageType get messageType => WsMessageType.fromString(type);

  @override
  String toString() => 'WsMessage(type: $type, payload: $payload)';
}


/// Typing event payload
/// Requirements: 6.3 - Send typing events via WebSocket
class TypingPayload {
  final String chatId;
  final String userId;
  final String? userName;

  const TypingPayload({
    required this.chatId,
    required this.userId,
    this.userName,
  });

  factory TypingPayload.fromJson(Map<String, dynamic> json) {
    return TypingPayload(
      chatId: json['chatId'] as String? ?? '',
      userId: json['userId'] as String? ?? '',
      userName: json['userName'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'chatId': chatId,
        'userId': userId,
        if (userName != null) 'userName': userName,
      };
}

/// WebSocket connection state
enum WsConnectionState {
  disconnected,
  connecting,
  connected,
  reconnecting,
}

/// WebSocket Service for real-time communication with MKR Backend
/// 
/// Requirements: 6.1 - Establish WebSocket connection to /ws/{userId}
/// Requirements: 6.2 - Handle WebSocket message types
/// Requirements: 6.3 - Send typing events via WebSocket
/// Requirements: 6.4 - Automatic reconnection with exponential backoff
class WebSocketService {
  WebSocketChannel? _channel;
  final _messageController = StreamController<WsMessage>.broadcast();
  final _connectionStateController = StreamController<WsConnectionState>.broadcast();
  
  Timer? _reconnectTimer;
  Timer? _pingTimer;
  
  String? _userId;
  bool _shouldReconnect = true;
  int _reconnectAttempts = 0;
  WsConnectionState _connectionState = WsConnectionState.disconnected;
  
  // Reconnection settings from ApiConfig
  // Requirements: 6.4 - Exponential backoff: 1s, 2s, 4s, 8s up to max
  static const int _maxReconnectAttempts = 10;
  static const Duration _pingInterval = Duration(seconds: 30);

  /// Stream of incoming WebSocket messages
  /// Requirements: 6.2 - Handle incoming messages
  Stream<WsMessage> get messages => _messageController.stream;

  /// Stream of connection state changes
  Stream<WsConnectionState> get connectionState => _connectionStateController.stream;

  /// Current connection state
  WsConnectionState get currentState => _connectionState;

  /// Check if connected
  bool get isConnected => _connectionState == WsConnectionState.connected;

  /// Current reconnect attempt count (for testing)
  int get reconnectAttempts => _reconnectAttempts;

  /// Connect to WebSocket server
  /// Requirements: 6.1 - Establish WebSocket connection to /ws/{userId}
  Future<void> connect(String userId) async {
    _userId = userId;
    _shouldReconnect = true;
    _reconnectAttempts = 0;
    await _establishConnection();
  }


  /// Establish WebSocket connection
  Future<void> _establishConnection() async {
    if (_userId == null) {
      debugPrint('WebSocketService: Cannot connect without userId');
      return;
    }

    _updateConnectionState(WsConnectionState.connecting);

    try {
      final wsUrl = ApiConfig.wsUrl(_userId!);
      debugPrint('WebSocketService: Connecting to $wsUrl');
      
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      await _channel!.ready;
      
      _updateConnectionState(WsConnectionState.connected);
      _reconnectAttempts = 0;
      
      _startPingTimer();
      _listenToMessages();
      
      debugPrint('WebSocketService: Connected successfully');
    } catch (e) {
      debugPrint('WebSocketService: Connection error: $e');
      _updateConnectionState(WsConnectionState.disconnected);
      _scheduleReconnect();
    }
  }

  /// Listen to incoming WebSocket messages
  /// Requirements: 6.2 - Handle WebSocket message types
  void _listenToMessages() {
    _channel?.stream.listen(
      (data) {
        try {
          final jsonData = jsonDecode(data as String) as Map<String, dynamic>;
          final message = WsMessage.fromJson(jsonData);
          
          // Handle pong internally
          if (message.messageType == WsMessageType.pong) {
            debugPrint('WebSocketService: Pong received');
            return;
          }
          
          debugPrint('WebSocketService: Received message: ${message.type}');
          if (!_messageController.isClosed) {
            _messageController.add(message);
          }
        } catch (e) {
          debugPrint('WebSocketService: Error parsing message: $e');
        }
      },
      onError: (error) {
        debugPrint('WebSocketService: Stream error: $error');
        _handleDisconnect();
      },
      onDone: () {
        debugPrint('WebSocketService: Stream closed');
        _handleDisconnect();
      },
    );
  }

  /// Handle disconnection
  void _handleDisconnect() {
    _stopPingTimer();
    _updateConnectionState(WsConnectionState.disconnected);
    _scheduleReconnect();
  }

  /// Calculate reconnection delay with exponential backoff
  /// Requirements: 6.4 - Exponential backoff: 1s, 2s, 4s, 8s up to max
  Duration _calculateReconnectDelay() {
    final baseDelay = ApiConfig.initialReconnectDelay.inMilliseconds;
    final maxDelay = ApiConfig.maxReconnectDelay.inMilliseconds;
    final multiplier = ApiConfig.reconnectBackoffMultiplier;
    
    // Calculate delay: baseDelay * (multiplier ^ attempts)
    final delay = baseDelay * pow(multiplier, _reconnectAttempts);
    final clampedDelay = min(delay.toInt(), maxDelay);
    
    return Duration(milliseconds: clampedDelay);
  }


  /// Schedule reconnection with exponential backoff
  /// Requirements: 6.4 - Automatic reconnection with exponential backoff
  void _scheduleReconnect() {
    if (!_shouldReconnect) {
      debugPrint('WebSocketService: Reconnection disabled');
      return;
    }
    
    if (_reconnectAttempts >= _maxReconnectAttempts) {
      debugPrint('WebSocketService: Max reconnect attempts reached');
      return;
    }

    _reconnectTimer?.cancel();
    
    final delay = _calculateReconnectDelay();
    _reconnectAttempts++;
    
    debugPrint('WebSocketService: Scheduling reconnect in ${delay.inSeconds}s (attempt $_reconnectAttempts)');
    _updateConnectionState(WsConnectionState.reconnecting);
    
    _reconnectTimer = Timer(delay, () {
      debugPrint('WebSocketService: Attempting reconnection...');
      _establishConnection();
    });
  }

  /// Start ping timer to keep connection alive
  void _startPingTimer() {
    _stopPingTimer();
    _pingTimer = Timer.periodic(_pingInterval, (_) {
      _sendPing();
    });
  }

  /// Stop ping timer
  void _stopPingTimer() {
    _pingTimer?.cancel();
    _pingTimer = null;
  }

  /// Send ping message
  void _sendPing() {
    if (isConnected) {
      send(const WsMessage(type: 'ping', payload: {}));
    }
  }

  /// Update connection state and notify listeners
  void _updateConnectionState(WsConnectionState state) {
    _connectionState = state;
    if (!_connectionStateController.isClosed) {
      _connectionStateController.add(state);
    }
  }

  /// Send a WebSocket message
  void send(WsMessage message) {
    if (_channel != null && isConnected) {
      final jsonString = jsonEncode(message.toJson());
      _channel!.sink.add(jsonString);
      debugPrint('WebSocketService: Sent message: ${message.type}');
    } else {
      debugPrint('WebSocketService: Cannot send message - not connected');
    }
  }

  /// Send typing indicator
  /// Requirements: 6.3 - Send typing events via WebSocket
  /// Format: {"type": "typing", "payload": {"chatId": "...", "userId": "..."}}
  void sendTyping(String chatId) {
    if (_userId == null) {
      debugPrint('WebSocketService: Cannot send typing - no userId');
      return;
    }
    
    final payload = TypingPayload(
      chatId: chatId,
      userId: _userId!,
    );
    
    final message = WsMessage(
      type: 'typing',
      payload: payload.toJson(),
    );
    
    send(message);
  }

  /// Disconnect from WebSocket server
  Future<void> disconnect() async {
    debugPrint('WebSocketService: Disconnecting...');
    _shouldReconnect = false;
    _stopPingTimer();
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    
    await _channel?.sink.close();
    _channel = null;
    
    _updateConnectionState(WsConnectionState.disconnected);
    debugPrint('WebSocketService: Disconnected');
  }

  /// Dispose resources
  void dispose() {
    disconnect();
    _messageController.close();
    _connectionStateController.close();
  }

  /// Reset reconnection attempts (useful for manual reconnect)
  void resetReconnectAttempts() {
    _reconnectAttempts = 0;
  }

  /// Force reconnection
  Future<void> reconnect() async {
    await disconnect();
    if (_userId != null) {
      _shouldReconnect = true;
      _reconnectAttempts = 0;
      await _establishConnection();
    }
  }
}
