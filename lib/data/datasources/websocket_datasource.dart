import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../core/constants/app_constants.dart';

/// WebSocket message types
enum WsMessageType {
  message,
  messageStatus,
  typing,
  read,
  error,
  ping,
  pong,
}

/// WebSocket message wrapper
class WsMessage {
  final WsMessageType type;
  final Map<String, dynamic> data;
  final DateTime timestamp;

  const WsMessage({
    required this.type,
    required this.data,
    required this.timestamp,
  });

  factory WsMessage.fromJson(Map<String, dynamic> json) {
    return WsMessage(
      type: WsMessageType.values.firstWhere(
        (e) => e.name == json['type'],
        orElse: () => WsMessageType.error,
      ),
      data: json['data'] as Map<String, dynamic>? ?? {},
      timestamp: json['timestamp'] != null
          ? DateTime.parse(json['timestamp'] as String)
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() => {
        'type': type.name,
        'data': data,
        'timestamp': timestamp.toIso8601String(),
      };
}


/// WebSocket datasource for real-time messaging
/// Requirements: 6.1 - Send/receive messages via WebSocket
class WebSocketDatasource {
  WebSocketChannel? _channel;
  final _messageController = StreamController<WsMessage>.broadcast();
  final _connectionController = StreamController<bool>.broadcast();
  Timer? _pingTimer;
  Timer? _reconnectTimer;
  String? _accessToken;
  bool _isConnected = false;
  bool _shouldReconnect = true;
  int _reconnectAttempts = 0;
  static const int _maxReconnectAttempts = 5;
  static const Duration _pingInterval = Duration(seconds: 30);
  static const Duration _reconnectDelay = Duration(seconds: 5);

  /// Stream of incoming WebSocket messages
  Stream<WsMessage> get messages => _messageController.stream;

  /// Stream of connection state changes
  Stream<bool> get connectionState => _connectionController.stream;

  /// Current connection status
  bool get isConnected => _isConnected;

  /// Connect to WebSocket server
  Future<void> connect(String accessToken) async {
    _accessToken = accessToken;
    _shouldReconnect = true;
    await _establishConnection();
  }

  Future<void> _establishConnection() async {
    if (_accessToken == null) return;

    try {
      final uri = Uri.parse('${AppConstants.wsUrl}?token=$_accessToken');
      _channel = WebSocketChannel.connect(uri);

      await _channel!.ready;
      _isConnected = true;
      _reconnectAttempts = 0;
      _connectionController.add(true);

      _startPingTimer();
      _listenToMessages();

      debugPrint('WebSocket connected');
    } catch (e) {
      debugPrint('WebSocket connection error: $e');
      _isConnected = false;
      _connectionController.add(false);
      _scheduleReconnect();
    }
  }

  void _listenToMessages() {
    _channel?.stream.listen(
      (data) {
        try {
          final json = jsonDecode(data as String) as Map<String, dynamic>;
          final message = WsMessage.fromJson(json);

          if (message.type == WsMessageType.pong) {
            // Pong received, connection is alive
            return;
          }

          _messageController.add(message);
        } catch (e) {
          debugPrint('Error parsing WebSocket message: $e');
        }
      },
      onError: (error) {
        debugPrint('WebSocket error: $error');
        _handleDisconnect();
      },
      onDone: () {
        debugPrint('WebSocket closed');
        _handleDisconnect();
      },
    );
  }

  void _handleDisconnect() {
    _isConnected = false;
    _connectionController.add(false);
    _stopPingTimer();
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (!_shouldReconnect) return;
    if (_reconnectAttempts >= _maxReconnectAttempts) {
      debugPrint('Max reconnect attempts reached');
      return;
    }

    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(_reconnectDelay * (_reconnectAttempts + 1), () {
      _reconnectAttempts++;
      debugPrint('Reconnecting... attempt $_reconnectAttempts');
      _establishConnection();
    });
  }

  void _startPingTimer() {
    _pingTimer?.cancel();
    _pingTimer = Timer.periodic(_pingInterval, (_) {
      _sendPing();
    });
  }

  void _stopPingTimer() {
    _pingTimer?.cancel();
    _pingTimer = null;
  }

  void _sendPing() {
    if (_isConnected) {
      send(WsMessage(
        type: WsMessageType.ping,
        data: {},
        timestamp: DateTime.now(),
      ));
    }
  }

  /// Send a message through WebSocket
  void send(WsMessage message) {
    if (_channel != null && _isConnected) {
      _channel!.sink.add(jsonEncode(message.toJson()));
    }
  }

  /// Send raw JSON data
  void sendRaw(Map<String, dynamic> data) {
    if (_channel != null && _isConnected) {
      _channel!.sink.add(jsonEncode(data));
    }
  }

  /// Disconnect from WebSocket server
  Future<void> disconnect() async {
    _shouldReconnect = false;
    _stopPingTimer();
    _reconnectTimer?.cancel();
    await _channel?.sink.close();
    _channel = null;
    _isConnected = false;
    _connectionController.add(false);
    debugPrint('WebSocket disconnected');
  }

  /// Dispose resources
  void dispose() {
    disconnect();
    _messageController.close();
    _connectionController.close();
  }
}
