package com.pioneer.messenger.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateChannelViewModel @Inject constructor() : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _createdChannelId = MutableStateFlow<String?>(null)
    val createdChannelId: StateFlow<String?> = _createdChannelId
    
    fun createChannel(
        name: String,
        username: String?,
        description: String?,
        isPublic: Boolean,
        allowComments: Boolean
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val result = ApiClient.createChannel(
                    name = name,
                    username = username,
                    description = description,
                    isPublic = isPublic,
                    allowComments = allowComments
                )
                
                result.fold(
                    onSuccess = { channel ->
                        _createdChannelId.value = channel.id
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Ошибка создания канала"
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка создания канала"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
