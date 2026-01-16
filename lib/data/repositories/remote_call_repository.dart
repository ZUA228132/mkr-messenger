import 'dart:developer' as developer;

import '../../core/error/api_error.dart';
import '../../core/result/result.dart';
import '../datasources/api_client.dart';

/// Call token response from backend
class CallTokenResponse {
  final String token;
  final String roomName;
  final String callId;

  CallTokenResponse({
    required this.token,
    required this.roomName,
    required this.callId,
  });

  factory CallTokenResponse.fromJson(Map<String, dynamic> json) {
    return CallTokenResponse(
      token: json['token'] as String,
      roomName: json['roomName'] as String,
      callId: json['callId'] as String,
    );
  }
}

/// Remote Call Repository for LiveKit voice/video calls
class RemoteCallRepository {
  final ApiClient _apiClient;

  RemoteCallRepository({required ApiClient apiClient}) : _apiClient = apiClient;

  /// Get call token to initiate a call
  /// POST /api/calls/token
  Future<Result<CallTokenResponse>> getCallToken({
    required String calleeId,
    bool isVideo = false,
  }) async {
    developer.log(
      'Getting call token for callee: $calleeId, isVideo: $isVideo',
      name: 'RemoteCallRepository',
    );

    final result = await _apiClient.post(
      '/api/calls/token',
      data: {
        'calleeId': calleeId,
        'isVideo': isVideo,
      },
    );

    return result.fold(
      onSuccess: (response) {
        try {
          final callResponse = CallTokenResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          developer.log(
            'Got call token, roomName: ${callResponse.roomName}',
            name: 'RemoteCallRepository',
          );
          return Success(callResponse);
        } catch (e) {
          developer.log(
            'Failed to parse call token response: $e',
            name: 'RemoteCallRepository',
          );
          return Failure(ApiError(message: 'Failed to parse call token: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to get call token: ${apiError.message}',
          name: 'RemoteCallRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Join an existing call room
  /// POST /api/calls/join/{roomName}
  Future<Result<String>> joinCall(String roomName) async {
    developer.log(
      'Joining call room: $roomName',
      name: 'RemoteCallRepository',
    );

    final result = await _apiClient.post('/api/calls/join/$roomName');

    return result.fold(
      onSuccess: (response) {
        try {
          final data = response.data as Map<String, dynamic>;
          final token = data['token'] as String;
          developer.log(
            'Got join token for room: $roomName',
            name: 'RemoteCallRepository',
          );
          return Success(token);
        } catch (e) {
          developer.log(
            'Failed to parse join call response: $e',
            name: 'RemoteCallRepository',
          );
          return Failure(ApiError(message: 'Failed to parse join response: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to join call: ${apiError.message}',
          name: 'RemoteCallRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// End a call
  /// POST /api/calls/{callId}/end
  Future<Result<void>> endCall(String callId, int duration, String reason) async {
    developer.log(
      'Ending call: $callId, duration: $duration, reason: $reason',
      name: 'RemoteCallRepository',
    );

    final result = await _apiClient.post(
      '/api/calls/$callId/end',
      data: {
        'duration': duration,
        'reason': reason,
      },
    );

    return result.fold(
      onSuccess: (_) {
        developer.log('Call ended successfully', name: 'RemoteCallRepository');
        return const Success(null);
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to end call: ${apiError.message}',
          name: 'RemoteCallRepository',
        );
        return Failure(apiError);
      },
    );
  }
}
