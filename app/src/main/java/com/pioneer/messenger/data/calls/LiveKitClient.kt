package com.pioneer.messenger.data.calls

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed class LiveKitCallState {
    object Idle : LiveKitCallState()
    object Connecting : LiveKitCallState()
    object WaitingForPeer : LiveKitCallState() // Ждём когда собеседник присоединится
    object Connected : LiveKitCallState()
    data class Error(val message: String) : LiveKitCallState()
}

@Singleton
class LiveKitClient @Inject constructor(
    private val context: Context
) {
    companion object {
        // LiveKit Cloud URL
        private const val LIVEKIT_URL = "wss://patriot-7b97xaj8.livekit.cloud"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var room: Room? = null
    
    private val _callState = MutableStateFlow<LiveKitCallState>(LiveKitCallState.Idle)
    val callState: StateFlow<LiveKitCallState> = _callState.asStateFlow()
    
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()
    
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()
    
    // Флаг: это входящий звонок (сразу Connected) или исходящий (ждём peer)
    private var isIncomingCall = false
    
    /**
     * Подключиться к комнате звонка
     * @param token JWT токен от бэкенда
     * @param isVideo включить видео
     * @param isIncoming true если это входящий звонок (сразу Connected)
     */
    suspend fun joinCall(token: String, isVideo: Boolean, isIncoming: Boolean = false) {
        try {
            _callState.value = LiveKitCallState.Connecting
            isIncomingCall = isIncoming
            _isVideoEnabled.value = isVideo
            
            android.util.Log.d("LiveKitClient", "Creating room, isVideo=$isVideo, isIncoming=$isIncoming")
            
            room = LiveKit.create(context)
            
            // Слушаем события комнаты
            scope.launch {
                room?.events?.collect { event ->
                    android.util.Log.d("LiveKitClient", "Room event: $event")
                    when (event) {
                        is RoomEvent.Connected -> {
                            android.util.Log.d("LiveKitClient", "Room connected!")
                            
                            // Включаем микрофон после подключения
                            try {
                                room?.localParticipant?.setMicrophoneEnabled(true)
                                android.util.Log.d("LiveKitClient", "Microphone enabled")
                            } catch (e: Exception) {
                                android.util.Log.e("LiveKitClient", "Failed to enable mic: ${e.message}")
                            }
                            
                            // Включаем камеру если видеозвонок
                            if (isVideo) {
                                try {
                                    room?.localParticipant?.setCameraEnabled(true)
                                    android.util.Log.d("LiveKitClient", "Camera enabled")
                                    
                                    // Получаем video track после небольшой задержки
                                    kotlinx.coroutines.delay(500)
                                    _localVideoTrack.value = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                                    android.util.Log.d("LiveKitClient", "Local video track: ${_localVideoTrack.value}")
                                } catch (e: Exception) {
                                    android.util.Log.e("LiveKitClient", "Failed to enable camera: ${e.message}")
                                }
                            }
                            
                            // Проверяем есть ли уже участники в комнате
                            val hasRemoteParticipants = room?.remoteParticipants?.isNotEmpty() == true
                            
                            if (isIncomingCall || hasRemoteParticipants) {
                                _callState.value = LiveKitCallState.Connected
                            } else {
                                _callState.value = LiveKitCallState.WaitingForPeer
                            }
                        }
                        is RoomEvent.ParticipantConnected -> {
                            android.util.Log.d("LiveKitClient", "Participant connected: ${event.participant.identity}")
                            _callState.value = LiveKitCallState.Connected
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            android.util.Log.d("LiveKitClient", "Participant disconnected - ending call")
                            _callState.value = LiveKitCallState.Idle
                            cleanup()
                        }
                        is RoomEvent.Disconnected -> {
                            android.util.Log.d("LiveKitClient", "Room disconnected")
                            _callState.value = LiveKitCallState.Idle
                            cleanup()
                        }
                        is RoomEvent.TrackSubscribed -> {
                            val track = event.track
                            android.util.Log.d("LiveKitClient", "Track subscribed: ${track.javaClass.simpleName}")
                            if (track is VideoTrack) {
                                _remoteVideoTrack.value = track
                            }
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            android.util.Log.d("LiveKitClient", "Track unsubscribed")
                            if (event.track is VideoTrack) {
                                _remoteVideoTrack.value = null
                            }
                        }
                        is RoomEvent.FailedToConnect -> {
                            android.util.Log.e("LiveKitClient", "Failed to connect to room")
                            _callState.value = LiveKitCallState.Error("Не удалось подключиться")
                        }
                        else -> {}
                    }
                }
            }
            
            // Подключаемся к комнате
            android.util.Log.d("LiveKitClient", "Connecting to $LIVEKIT_URL")
            room?.connect(LIVEKIT_URL, token)
            
        } catch (e: Exception) {
            android.util.Log.e("LiveKitClient", "joinCall error: ${e.message}")
            _callState.value = LiveKitCallState.Error(e.message ?: "Ошибка подключения")
        }
    }
    
    /**
     * Завершить звонок
     */
    fun endCall() {
        room?.disconnect()
        cleanup()
        _callState.value = LiveKitCallState.Idle
    }
    
    /**
     * Включить/выключить микрофон
     */
    fun toggleMute() {
        scope.launch {
            _isMuted.value = !_isMuted.value
            room?.localParticipant?.setMicrophoneEnabled(!_isMuted.value)
        }
    }
    
    /**
     * Включить/выключить видео
     */
    fun toggleVideo() {
        scope.launch {
            try {
                _isVideoEnabled.value = !_isVideoEnabled.value
                android.util.Log.d("LiveKitClient", "Toggle video: ${_isVideoEnabled.value}")
                
                room?.localParticipant?.setCameraEnabled(_isVideoEnabled.value)
                
                if (_isVideoEnabled.value) {
                    kotlinx.coroutines.delay(300)
                    _localVideoTrack.value = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                    android.util.Log.d("LiveKitClient", "Video enabled, track: ${_localVideoTrack.value}")
                } else {
                    _localVideoTrack.value = null
                    android.util.Log.d("LiveKitClient", "Video disabled")
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveKitClient", "toggleVideo error: ${e.message}")
            }
        }
    }
    
    /**
     * Переключить камеру (фронтальная/задняя)
     */
    fun switchCamera() {
        scope.launch {
            try {
                val videoTrack = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                videoTrack?.let {
                    val currentPosition = it.options.position
                    val newPosition = if (currentPosition == CameraPosition.FRONT) CameraPosition.BACK else CameraPosition.FRONT
                    android.util.Log.d("LiveKitClient", "Switching camera from $currentPosition to $newPosition")
                    it.restartTrack(it.options.copy(position = newPosition))
                } ?: run {
                    android.util.Log.w("LiveKitClient", "No video track to switch camera")
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveKitClient", "switchCamera error: ${e.message}")
            }
        }
    }
    
    private fun cleanup() {
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _isMuted.value = false
        _isVideoEnabled.value = true
        room = null
    }
}
