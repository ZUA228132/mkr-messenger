import 'dart:async';
import 'dart:io';
import 'package:path_provider/path_provider.dart';

/// Service for recording voice notes and video notes
class MediaRecorderService {
  bool _isRecording = false;
  DateTime? _recordingStartTime;
  String? _currentRecordingPath;
  Timer? _durationTimer;
  final _durationController = StreamController<int>.broadcast();

  bool get isRecording => _isRecording;
  Stream<int> get durationStream => _durationController.stream;
  
  int get currentDuration {
    if (_recordingStartTime == null) return 0;
    return DateTime.now().difference(_recordingStartTime!).inSeconds;
  }

  /// Start recording voice note
  Future<bool> startVoiceRecording() async {
    if (_isRecording) return false;
    
    try {
      final dir = await getTemporaryDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      _currentRecordingPath = '${dir.path}/voice_$timestamp.m4a';
      
      // TODO: Implement actual audio recording with record package
      // For now, simulate recording
      _isRecording = true;
      _recordingStartTime = DateTime.now();
      
      _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
        _durationController.add(currentDuration);
      });
      
      return true;
    } catch (e) {
      return false;
    }
  }

  /// Start recording video note (circle video)
  Future<bool> startVideoRecording() async {
    if (_isRecording) return false;
    
    try {
      final dir = await getTemporaryDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      _currentRecordingPath = '${dir.path}/video_$timestamp.mp4';
      
      // TODO: Implement actual video recording with camera package
      _isRecording = true;
      _recordingStartTime = DateTime.now();
      
      _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
        _durationController.add(currentDuration);
      });
      
      return true;
    } catch (e) {
      return false;
    }
  }

  /// Stop recording and return the file path
  Future<RecordingResult?> stopRecording() async {
    if (!_isRecording) return null;
    
    _durationTimer?.cancel();
    _durationTimer = null;
    
    final duration = currentDuration;
    final path = _currentRecordingPath;
    
    _isRecording = false;
    _recordingStartTime = null;
    _currentRecordingPath = null;
    
    if (path == null || duration < 1) return null;
    
    // TODO: Actually stop the recorder
    
    return RecordingResult(
      filePath: path,
      duration: duration,
    );
  }

  /// Cancel recording without saving
  Future<void> cancelRecording() async {
    _durationTimer?.cancel();
    _durationTimer = null;
    
    if (_currentRecordingPath != null) {
      try {
        final file = File(_currentRecordingPath!);
        if (await file.exists()) {
          await file.delete();
        }
      } catch (_) {}
    }
    
    _isRecording = false;
    _recordingStartTime = null;
    _currentRecordingPath = null;
  }

  void dispose() {
    _durationTimer?.cancel();
    _durationController.close();
  }
}

class RecordingResult {
  final String filePath;
  final int duration;

  RecordingResult({
    required this.filePath,
    required this.duration,
  });
}
