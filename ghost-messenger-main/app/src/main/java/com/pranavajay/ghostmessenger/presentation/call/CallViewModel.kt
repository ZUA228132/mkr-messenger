package com.pranavajay.ghostmessenger.presentation.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavajay.ghostmessenger.data.models.CallEntity
import com.pranavajay.ghostmessenger.data.repository.ChatRepository
import com.pranavajay.ghostmessenger.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallState(
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoOff: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val callHistory: List<CallEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    fun acceptCall(callId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.acceptCall(callId)) {
                is Result.Success -> {
                    _callState.value = _callState.value.copy(isConnected = true)
                }
                is Result.Error -> {
                    _callState.value = _callState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun declineCall(callId: String) {
        viewModelScope.launch {
            chatRepository.declineCall(callId)
        }
    }
    
    fun endCall(callId: String) {
        viewModelScope.launch {
            chatRepository.endCall(callId)
            _callState.value = _callState.value.copy(isConnected = false)
        }
    }
    
    fun toggleMute() {
        _callState.value = _callState.value.copy(isMuted = !_callState.value.isMuted)
        // TODO: Actually mute audio
    }
    
    fun toggleVideo() {
        _callState.value = _callState.value.copy(isVideoOff = !_callState.value.isVideoOff)
        // TODO: Actually toggle video
    }
    
    fun toggleSpeaker() {
        _callState.value = _callState.value.copy(isSpeakerOn = !_callState.value.isSpeakerOn)
        // TODO: Actually toggle speaker
    }
    
    fun loadCallHistory() {
        viewModelScope.launch {
            when (val result = chatRepository.getCallHistory()) {
                is Result.Success -> {
                    _callState.value = _callState.value.copy(callHistory = result.data)
                }
                is Result.Error -> {
                    _callState.value = _callState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
}
