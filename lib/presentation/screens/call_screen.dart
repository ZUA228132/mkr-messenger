import 'dart:async';

import 'package:flutter/cupertino.dart';

import '../../data/services/livekit_service.dart';
import '../../domain/entities/user.dart';

/// Voice/Video call screen
class CallScreen extends StatefulWidget {
  final String recipientId;
  final String recipientName;
  final String? recipientAvatarUrl;
  final bool isVideo;
  final bool isIncoming;
  final String? token;
  final LiveKitService liveKitService;
  final VoidCallback? onCallEnded;

  const CallScreen({
    super.key,
    required this.recipientId,
    required this.recipientName,
    this.recipientAvatarUrl,
    this.isVideo = false,
    this.isIncoming = false,
    this.token,
    required this.liveKitService,
    this.onCallEnded,
  });

  @override
  State<CallScreen> createState() => _CallScreenState();
}

class _CallScreenState extends State<CallScreen> {
  LiveKitCallState _callState = LiveKitCallState.idle;
  StreamSubscription<LiveKitCallState>? _stateSubscription;
  
  int _callDuration = 0;
  Timer? _durationTimer;
  
  bool _isMuted = false;
  bool _isSpeakerOn = false;
  
  @override
  void initState() {
    super.initState();
    _subscribeToCallState();
    _startCall();
  }
  
  void _subscribeToCallState() {
    _stateSubscription = widget.liveKitService.callState.listen((state) {
      if (!mounted) return;
      setState(() => _callState = state);
      
      if (state == LiveKitCallState.connected) {
        _startDurationTimer();
      } else if (state == LiveKitCallState.idle) {
        _stopDurationTimer();
        // Auto close after call ends
        Future.delayed(const Duration(seconds: 1), () {
          if (mounted) {
            widget.onCallEnded?.call();
            Navigator.of(context).pop();
          }
        });
      }
    });
  }
  
  Future<void> _startCall() async {
    if (widget.token != null) {
      await widget.liveKitService.joinCall(
        token: widget.token!,
        isVideo: widget.isVideo,
        isIncoming: widget.isIncoming,
      );
    }
  }
  
  void _startDurationTimer() {
    _durationTimer?.cancel();
    _callDuration = 0;
    _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
        setState(() => _callDuration++);
      }
    });
  }
  
  void _stopDurationTimer() {
    _durationTimer?.cancel();
    _durationTimer = null;
  }
  
  void _endCall() {
    widget.liveKitService.endCall();
  }
  
  void _toggleMute() {
    widget.liveKitService.toggleMute();
    setState(() => _isMuted = !_isMuted);
  }
  
  void _toggleSpeaker() {
    setState(() => _isSpeakerOn = !_isSpeakerOn);
    // TODO: Implement speaker toggle
  }
  
  String _formatDuration(int seconds) {
    final mins = seconds ~/ 60;
    final secs = seconds % 60;
    return '${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
  }
  
  String _getStatusText() {
    switch (_callState) {
      case LiveKitCallState.idle:
        return 'Завершён';
      case LiveKitCallState.connecting:
        return 'Подключение...';
      case LiveKitCallState.waitingForPeer:
        return 'Вызов...';
      case LiveKitCallState.connected:
        return _formatDuration(_callDuration);
      case LiveKitCallState.error:
        return 'Ошибка';
    }
  }
  
  @override
  void dispose() {
    _stateSubscription?.cancel();
    _stopDurationTimer();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final firstLetter = widget.recipientName.isNotEmpty 
        ? widget.recipientName[0].toUpperCase() 
        : '?';
    
    return CupertinoPageScaffold(
      backgroundColor: const Color(0xFF1C1C1E),
      child: SafeArea(
        child: Column(
          children: [
            // Top bar with back button
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  CupertinoButton(
                    padding: EdgeInsets.zero,
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Icon(
                      CupertinoIcons.chevron_down,
                      color: CupertinoColors.white,
                      size: 28,
                    ),
                  ),
                  const Spacer(),
                  // Encryption indicator
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: CupertinoColors.systemGreen.withAlpha(40),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          CupertinoIcons.lock_shield_fill,
                          color: CupertinoColors.systemGreen,
                          size: 14,
                        ),
                        SizedBox(width: 4),
                        Text(
                          'E2E',
                          style: TextStyle(
                            color: CupertinoColors.systemGreen,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            
            const Spacer(),
            
            // Avatar
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF6366F1).withAlpha(80),
                    blurRadius: 30,
                    offset: const Offset(0, 10),
                  ),
                ],
              ),
              child: Center(
                child: Text(
                  firstLetter,
                  style: const TextStyle(
                    fontSize: 48,
                    fontWeight: FontWeight.w600,
                    color: CupertinoColors.white,
                  ),
                ),
              ),
            ),
            
            const SizedBox(height: 24),
            
            // Name
            Text(
              widget.recipientName,
              style: const TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w600,
                color: CupertinoColors.white,
              ),
            ),
            
            const SizedBox(height: 8),
            
            // Status
            Text(
              _getStatusText(),
              style: TextStyle(
                fontSize: 16,
                color: _callState == LiveKitCallState.connected
                    ? CupertinoColors.systemGreen
                    : CupertinoColors.systemGrey,
              ),
            ),
            
            const Spacer(),
            
            // Call controls
            Padding(
              padding: const EdgeInsets.only(bottom: 60),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  // Mute button
                  _buildControlButton(
                    icon: _isMuted 
                        ? CupertinoIcons.mic_off 
                        : CupertinoIcons.mic,
                    label: _isMuted ? 'Вкл. звук' : 'Без звука',
                    isActive: _isMuted,
                    onPressed: _toggleMute,
                  ),
                  
                  // End call button
                  GestureDetector(
                    onTap: _endCall,
                    child: Container(
                      width: 72,
                      height: 72,
                      decoration: const BoxDecoration(
                        color: CupertinoColors.systemRed,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        CupertinoIcons.phone_down_fill,
                        color: CupertinoColors.white,
                        size: 32,
                      ),
                    ),
                  ),
                  
                  // Speaker button
                  _buildControlButton(
                    icon: _isSpeakerOn 
                        ? CupertinoIcons.speaker_3_fill 
                        : CupertinoIcons.speaker_1,
                    label: _isSpeakerOn ? 'Динамик' : 'Телефон',
                    isActive: _isSpeakerOn,
                    onPressed: _toggleSpeaker,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildControlButton({
    required IconData icon,
    required String label,
    required bool isActive,
    required VoidCallback onPressed,
  }) {
    return GestureDetector(
      onTap: onPressed,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: isActive 
                  ? CupertinoColors.white 
                  : CupertinoColors.white.withAlpha(30),
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              color: isActive 
                  ? CupertinoColors.black 
                  : CupertinoColors.white,
              size: 24,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: CupertinoColors.white,
            ),
          ),
        ],
      ),
    );
  }
}
