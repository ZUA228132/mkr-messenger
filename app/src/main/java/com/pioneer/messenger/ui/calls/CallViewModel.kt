package com.pioneer.messenger.ui.calls

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.calls.LiveKitCallState
import com.pioneer.messenger.data.calls.LiveKitClient
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CallUiState {
    object Idle : CallUiState()
    data class Calling(val calleeId: String) : CallUiState()
    data class Incoming(val callerId: String, val callerName: String) : CallUiState()
    object Connected : CallUiState()
    data class Ended(val reason: String = "") : CallUiState()
}

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liveKitClient: LiveKitClient,
    private val authManager: AuthManager
) : ViewModel() {
    
    private var timerJob: Job? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val _callState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callState: StateFlow<CallUiState> = _callState
    
    private val _callDuration = MutableStateFlow(0)
    val callDuration: StateFlow<Int> = _callDuration
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled
    
    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera
    
    // Информация о собеседнике
    private val _callerName = MutableStateFlow("Пользователь")
    val callerName: StateFlow<String> = _callerName
    
    private val _callerAvatarUrl = MutableStateFlow<String?>(null)
    val callerAvatarUrl: StateFlow<String?> = _callerAvatarUrl
    
    // LiveKit video tracks
    val localVideoTrack = liveKitClient.localVideoTrack
    val remoteVideoTrack = liveKitClient.remoteVideoTrack
    
    private var isVideoCall = false
    private var currentRoomName: String? = null
    
    init {
        // Слушаем состояние LiveKit
        viewModelScope.launch {
            liveKitClient.callState.collect { state ->
                when (state) {
                    is LiveKitCallState.Idle -> {
                        if (_callState.value is CallUiState.Connected) {
                            _callState.value = CallUiState.Ended("Звонок завершён")
                        }
                        stopTimer()
                    }
                    is LiveKitCallState.Connecting -> {
                        // Уже показываем Calling
                    }
                    is LiveKitCallState.WaitingForPeer -> {
                        // Продолжаем показывать "Вызов..." пока собеседник не присоединится
                        // Состояние Calling уже установлено
                    }
                    is LiveKitCallState.Connected -> {
                        _callState.value = CallUiState.Connected
                        startTimer()
                    }
                    is LiveKitCallState.Error -> {
                        _callState.value = CallUiState.Ended(state.message)
                        stopTimer()
                    }
                }
            }
        }
        
        // Синхронизируем состояние mute/video
        viewModelScope.launch {
            liveKitClient.isMuted.collect { _isMuted.value = it }
        }
        viewModelScope.launch {
            liveKitClient.isVideoEnabled.collect { _isVideoEnabled.value = it }
        }
    }
    
    private fun loadUserInfo(userId: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.getUser(userId)
                result.fold(
                    onSuccess = { user ->
                        _callerName.value = user.displayName
                        _callerAvatarUrl.value = ApiClient.getAvatarUrl(user.avatarUrl)
                    },
                    onFailure = {
                        _callerName.value = "Пользователь"
                    }
                )
            } catch (e: Exception) {
                _callerName.value = "Пользователь"
            }
        }
    }
    
    /**
     * Начать исходящий звонок
     */
    fun startCall(userId: String, isVideo: Boolean) {
        isVideoCall = isVideo
        _isVideoEnabled.value = isVideo
        
        // Загружаем информацию о собеседнике
        loadUserInfo(userId)
        
        viewModelScope.launch {
            _callState.value = CallUiState.Calling(userId)
            
            // Получаем токен от сервера
            val result = ApiClient.getCallToken(userId, isVideo)
            result.fold(
                onSuccess = { response ->
                    currentRoomName = response.roomName
                    // Подключаемся к LiveKit (исходящий звонок - ждём peer)
                    liveKitClient.joinCall(response.token, isVideo, isIncoming = false)
                },
                onFailure = { error ->
                    _callState.value = CallUiState.Ended(error.message ?: "Ошибка")
                }
            )
        }
    }
    
    /**
     * Принять входящий звонок
     */
    fun acceptCall(roomName: String, isVideo: Boolean = false) {
        isVideoCall = isVideo
        _isVideoEnabled.value = isVideo
        currentRoomName = roomName
        
        viewModelScope.launch {
            // Получаем токен для присоединения
            val result = ApiClient.joinCall(roomName)
            result.fold(
                onSuccess = { token ->
                    // Входящий звонок - сразу Connected
                    liveKitClient.joinCall(token, isVideo, isIncoming = true)
                },
                onFailure = { error ->
                    _callState.value = CallUiState.Ended(error.message ?: "Ошибка")
                }
            )
        }
    }
    
    /**
     * Завершить звонок
     */
    fun endCall() {
        val duration = _callDuration.value
        val roomName = currentRoomName
        
        stopTimer()
        liveKitClient.endCall()
        _callState.value = CallUiState.Ended()
        
        // Отправляем информацию о завершении на сервер
        if (roomName != null) {
            viewModelScope.launch {
                try {
                    ApiClient.endCall(roomName, duration, "ended")
                } catch (e: Exception) {
                    android.util.Log.e("CallViewModel", "Failed to report call end: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Отклонить входящий звонок
     */
    fun declineCall() {
        val roomName = currentRoomName
        
        stopTimer()
        liveKitClient.endCall()
        _callState.value = CallUiState.Ended("Звонок отклонён")
        
        if (roomName != null) {
            viewModelScope.launch {
                try {
                    ApiClient.endCall(roomName, 0, "declined")
                } catch (e: Exception) {
                    android.util.Log.e("CallViewModel", "Failed to report call decline: ${e.message}")
                }
            }
        }
    }
    
    fun toggleMute(): Boolean {
        liveKitClient.toggleMute()
        return _isMuted.value
    }
    
    fun toggleSpeaker(): Boolean {
        _isSpeakerOn.value = !_isSpeakerOn.value
        audioManager.isSpeakerphoneOn = _isSpeakerOn.value
        return _isSpeakerOn.value
    }
    
    fun toggleVideo() {
        liveKitClient.toggleVideo()
    }
    
    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        liveKitClient.switchCamera()
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        _callDuration.value = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callDuration.value++
            }
        }
    }
    
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    
    override fun onCleared() {
        super.onCleared()
        stopTimer()
        liveKitClient.endCall()
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = false
    }
}
