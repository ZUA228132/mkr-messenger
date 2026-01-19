package com.pranavajay.ghostmessenger.presentation.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavajay.ghostmessenger.data.models.Channel
import com.pranavajay.ghostmessenger.data.models.CreateChannelRequest
import com.pranavajay.ghostmessenger.data.repository.ChannelRepository
import com.pranavajay.ghostmessenger.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelState(
    val isLoading: Boolean = false,
    val myChannels: List<Channel> = emptyList(),
    val subscribedChannels: List<Channel> = emptyList(),
    val discoverChannels: List<Channel> = emptyList(),
    val searchResults: List<Channel> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
) : ViewModel() {
    
    private val _channelState = MutableStateFlow(ChannelState())
    val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()
    
    fun loadChannels() {
        viewModelScope.launch {
            _channelState.value = _channelState.value.copy(isLoading = true)
            
            when (val result = channelRepository.getChannels()) {
                is Result.Success -> {
                    // Separate my channels and subscribed channels
                    val userId = channelRepository.getCurrentUserId()
                    val myChannels = result.data.filter { it.creatorId == userId }
                    val subscribedChannels = result.data.filter { it.creatorId != userId }
                    
                    _channelState.value = _channelState.value.copy(
                        isLoading = false,
                        myChannels = myChannels,
                        subscribedChannels = subscribedChannels,
                        error = null
                    )
                }
                is Result.Error -> {
                    _channelState.value = _channelState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {}
            }
            
            // Load discover channels
            loadDiscoverChannels()
        }
    }
    
    private fun loadDiscoverChannels() {
        viewModelScope.launch {
            when (val result = channelRepository.searchChannels("")) {
                is Result.Success -> {
                    _channelState.value = _channelState.value.copy(
                        discoverChannels = result.data.take(10)
                    )
                }
                is Result.Error -> {}
                is Result.Loading -> {}
            }
        }
    }
    
    fun searchChannels(query: String) {
        viewModelScope.launch {
            when (val result = channelRepository.searchChannels(query)) {
                is Result.Success -> {
                    _channelState.value = _channelState.value.copy(
                        searchResults = result.data
                    )
                }
                is Result.Error -> {
                    _channelState.value = _channelState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun createChannel(name: String, description: String?, isPublic: Boolean) {
        viewModelScope.launch {
            when (val result = channelRepository.createChannel(name, description, isPublic)) {
                is Result.Success -> {
                    loadChannels()
                }
                is Result.Error -> {
                    _channelState.value = _channelState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun subscribeChannel(channelId: String) {
        viewModelScope.launch {
            when (val result = channelRepository.subscribeChannel(channelId)) {
                is Result.Success -> {
                    loadChannels()
                }
                is Result.Error -> {
                    _channelState.value = _channelState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun unsubscribeChannel(channelId: String) {
        viewModelScope.launch {
            when (val result = channelRepository.unsubscribeChannel(channelId)) {
                is Result.Success -> {
                    loadChannels()
                }
                is Result.Error -> {
                    _channelState.value = _channelState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
}
