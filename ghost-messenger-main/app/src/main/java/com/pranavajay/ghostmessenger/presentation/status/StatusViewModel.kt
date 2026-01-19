package com.pranavajay.ghostmessenger.presentation.status

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavajay.ghostmessenger.data.models.Status
import com.pranavajay.ghostmessenger.data.models.StatusGroup
import com.pranavajay.ghostmessenger.data.repository.StatusRepository
import com.pranavajay.ghostmessenger.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatusState(
    val isLoading: Boolean = false,
    val myStatuses: List<Status> = emptyList(),
    val contactStatuses: List<StatusGroup> = emptyList(),
    val currentViewingStatus: Status? = null,
    val currentViewingIndex: Int = 0,
    val error: String? = null
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val statusRepository: StatusRepository
) : ViewModel() {
    
    private val _statusState = MutableStateFlow(StatusState())
    val statusState: StateFlow<StatusState> = _statusState.asStateFlow()
    
    init {
        loadStatuses()
    }
    
    fun loadStatuses() {
        viewModelScope.launch {
            _statusState.value = _statusState.value.copy(isLoading = true)
            
            // Load my statuses
            when (val myResult = statusRepository.getMyStatuses()) {
                is Result.Success -> {
                    _statusState.value = _statusState.value.copy(myStatuses = myResult.data)
                }
                is Result.Error -> {}
                is Result.Loading -> {}
            }
            
            // Load contacts' statuses
            when (val contactsResult = statusRepository.getContactsStatuses()) {
                is Result.Success -> {
                    _statusState.value = _statusState.value.copy(
                        isLoading = false,
                        contactStatuses = contactsResult.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _statusState.value = _statusState.value.copy(
                        isLoading = false,
                        error = contactsResult.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun createTextStatus(content: String, backgroundColor: String) {
        viewModelScope.launch {
            when (val result = statusRepository.createStatus(
                content = content,
                mediaUrl = null,
                mediaType = "TEXT",
                backgroundColor = backgroundColor
            )) {
                is Result.Success -> {
                    loadStatuses()
                }
                is Result.Error -> {
                    _statusState.value = _statusState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun createMediaStatus(mediaUri: Uri, mediaType: String) {
        viewModelScope.launch {
            // First upload media
            when (val uploadResult = statusRepository.uploadMedia(mediaUri)) {
                is Result.Success -> {
                    // Then create status with media URL
                    when (val result = statusRepository.createStatus(
                        content = null,
                        mediaUrl = uploadResult.data,
                        mediaType = mediaType,
                        backgroundColor = null
                    )) {
                        is Result.Success -> {
                            loadStatuses()
                        }
                        is Result.Error -> {
                            _statusState.value = _statusState.value.copy(error = result.message)
                        }
                        is Result.Loading -> {}
                    }
                }
                is Result.Error -> {
                    _statusState.value = _statusState.value.copy(error = uploadResult.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun viewStatus(status: Status) {
        viewModelScope.launch {
            _statusState.value = _statusState.value.copy(currentViewingStatus = status)
            statusRepository.markStatusViewed(status.id)
        }
    }
    
    fun nextStatus() {
        val currentIndex = _statusState.value.currentViewingIndex
        val allStatuses = _statusState.value.contactStatuses.flatMap { it.statuses }
        if (currentIndex < allStatuses.size - 1) {
            _statusState.value = _statusState.value.copy(
                currentViewingIndex = currentIndex + 1,
                currentViewingStatus = allStatuses[currentIndex + 1]
            )
            viewStatus(allStatuses[currentIndex + 1])
        } else {
            closeStatusViewer()
        }
    }
    
    fun previousStatus() {
        val currentIndex = _statusState.value.currentViewingIndex
        val allStatuses = _statusState.value.contactStatuses.flatMap { it.statuses }
        if (currentIndex > 0) {
            _statusState.value = _statusState.value.copy(
                currentViewingIndex = currentIndex - 1,
                currentViewingStatus = allStatuses[currentIndex - 1]
            )
        }
    }
    
    fun closeStatusViewer() {
        _statusState.value = _statusState.value.copy(
            currentViewingStatus = null,
            currentViewingIndex = 0
        )
    }
    
    fun reactToStatus(statusId: String, emoji: String) {
        viewModelScope.launch {
            statusRepository.reactToStatus(statusId, emoji)
        }
    }
}
