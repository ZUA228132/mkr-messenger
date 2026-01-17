import 'dart:async';
import 'dart:developer' as developer;

import 'package:livekit_client/livekit_client.dart';

/// LiveKit call states
enum LiveKitCallState {
  idle,
  connecting,
  waitingForPeer,
  connected,
  error,
}

/// LiveKit service for voice/video calls
class LiveKitService {
  static const String _livekitUrl = 'wss://patriot-7b97xaj8.livekit.cloud';
  
  Room? _room;
  LocalParticipant? _localParticipant;
  
  final _callStateController = StreamController<LiveKitCallState>.broadcast();
  Stream<LiveKitCallState> get callState => _callStateController.stream;
  LiveKitCallState _currentState = LiveKitCallState.idle;
  LiveKitCallState get currentState => _currentState;
  
  final _remoteParticipantController = StreamController<RemoteParticipant?>.broadcast();
  Stream<RemoteParticipant?> get remoteParticipant => _remoteParticipantController.stream;
  
  bool _isMuted = false;
  bool get isMuted => _isMuted;
  
  bool _isVideoEnabled = false;
  bool get isVideoEnabled => _isVideoEnabled;
  
  bool _isIncomingCall = false;
  
  /// Join a call room
  Future<void> joinCall({
    required String token,
    required bool isVideo,
    bool isIncoming = false,
  }) async {
    try {
      _updateState(LiveKitCallState.connecting);
      _isIncomingCall = isIncoming;
      _isVideoEnabled = isVideo;
      
      developer.log(
        'Joining call: isVideo=$isVideo, isIncoming=$isIncoming',
        name: 'LiveKitService',
      );
      
      // Create room
      _room = Room();
      
      // Listen to room events using EventsListener
      _room!.createListener()
        ..on<RoomConnectedEvent>((event) => _onRoomConnected())
        ..on<RoomDisconnectedEvent>((event) => _onRoomDisconnected())
        ..on<ParticipantConnectedEvent>((event) => _onParticipantConnected(event.participant))
        ..on<ParticipantDisconnectedEvent>((event) => _onParticipantDisconnected(event.participant))
        ..on<TrackSubscribedEvent>((event) => _onTrackSubscribed(event.publication, event.participant));
      
      // Connect to room
      await _room!.connect(
        _livekitUrl,
        token,
        roomOptions: RoomOptions(
          adaptiveStream: true,
          dynacast: true,
          defaultAudioPublishOptions: const AudioPublishOptions(
            audioBitrate: AudioPreset.speech.maxBitrate,
          ),
          defaultVideoPublishOptions: VideoPublishOptions(
            videoEncoding: VideoParametersPresets.h720_169.encoding,
          ),
        ),
      );
      
      _localParticipant = _room!.localParticipant;
      
    } catch (e) {
      developer.log(
        'Failed to join call: $e',
        name: 'LiveKitService',
      );
      _updateState(LiveKitCallState.error);
    }
  }
  
  void _onRoomConnected() async {
    developer.log('Room connected', name: 'LiveKitService');
    
    // Enable microphone
    try {
      await _localParticipant?.setMicrophoneEnabled(true);
      developer.log('Microphone enabled', name: 'LiveKitService');
    } catch (e) {
      developer.log('Failed to enable mic: $e', name: 'LiveKitService');
    }
    
    // Enable camera if video call
    if (_isVideoEnabled) {
      try {
        await _localParticipant?.setCameraEnabled(true);
        developer.log('Camera enabled', name: 'LiveKitService');
      } catch (e) {
        developer.log('Failed to enable camera: $e', name: 'LiveKitService');
      }
    }
    
    // Check if there are already remote participants
    final hasRemote = _room?.remoteParticipants.isNotEmpty ?? false;
    
    if (_isIncomingCall || hasRemote) {
      _updateState(LiveKitCallState.connected);
    } else {
      _updateState(LiveKitCallState.waitingForPeer);
    }
  }
  
  void _onRoomDisconnected() {
    developer.log('Room disconnected', name: 'LiveKitService');
    _updateState(LiveKitCallState.idle);
    _cleanup();
  }
  
  void _onParticipantConnected(RemoteParticipant participant) {
    developer.log('Participant connected: ${participant.identity}', name: 'LiveKitService');
    _remoteParticipantController.add(participant);
    _updateState(LiveKitCallState.connected);
  }
  
  void _onParticipantDisconnected(RemoteParticipant participant) {
    developer.log('Participant disconnected', name: 'LiveKitService');
    _remoteParticipantController.add(null);
    _updateState(LiveKitCallState.idle);
    _cleanup();
  }
  
  void _onTrackSubscribed(RemoteTrackPublication publication, RemoteParticipant participant) {
    developer.log('Track subscribed: ${publication.kind}', name: 'LiveKitService');
  }
  
  /// End the call
  void endCall() {
    developer.log('Ending call', name: 'LiveKitService');
    _room?.disconnect();
    _cleanup();
    _updateState(LiveKitCallState.idle);
  }
  
  /// Toggle microphone mute
  Future<void> toggleMute() async {
    _isMuted = !_isMuted;
    await _localParticipant?.setMicrophoneEnabled(!_isMuted);
    developer.log('Mute toggled: $_isMuted', name: 'LiveKitService');
  }
  
  /// Toggle video
  Future<void> toggleVideo() async {
    _isVideoEnabled = !_isVideoEnabled;
    await _localParticipant?.setCameraEnabled(_isVideoEnabled);
    developer.log('Video toggled: $_isVideoEnabled', name: 'LiveKitService');
  }
  
  /// Switch camera (front/back)
  Future<void> switchCamera() async {
    final videoTrack = _localParticipant?.videoTrackPublications.firstOrNull?.track;
    if (videoTrack is LocalVideoTrack) {
      await videoTrack.setCameraPosition(
        videoTrack.currentOptions.params.position == CameraPosition.front
            ? CameraPosition.back
            : CameraPosition.front,
      );
      developer.log('Camera switched', name: 'LiveKitService');
    }
  }
  
  void _updateState(LiveKitCallState state) {
    _currentState = state;
    _callStateController.add(state);
  }
  
  void _cleanup() {
    _isMuted = false;
    _isVideoEnabled = false;
    _room = null;
    _localParticipant = null;
  }
  
  void dispose() {
    _room?.disconnect();
    _callStateController.close();
    _remoteParticipantController.close();
  }
}

typedef VoidCallback = void Function();
