package com.pioneer.messenger.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoryUiModel(
    val id: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val mediaUrl: String,
    val mediaType: String,
    val text: String?,
    val textColor: String?,
    val backgroundColor: String?,
    val duration: Int,
    val time: String,
    val viewed: Boolean,
    val viewCount: Int
)

@HiltViewModel
class StatusViewModel @Inject constructor() : ViewModel() {
    
    private val _statuses = MutableStateFlow<List<Status>>(emptyList())
    val statuses: StateFlow<List<Status>> = _statuses
    
    private val _myStories = MutableStateFlow<List<StoryUiModel>>(emptyList())
    val myStories: StateFlow<List<StoryUiModel>> = _myStories
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    init {
        loadStories()
    }
    
    fun loadStories() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = ApiClient.getStories()
                result.fold(
                    onSuccess = { userStories ->
                        val currentUserId = ApiClient.getCurrentUserId()
                        
                        // Разделяем на свои и чужие истории
                        val myUserStories = userStories.filter { it.userId == currentUserId }
                        val otherUserStories = userStories.filter { it.userId != currentUserId }
                        
                        _myStories.value = myUserStories.flatMap { userStory ->
                            userStory.stories.map { story ->
                                StoryUiModel(
                                    id = story.id,
                                    userId = story.userId,
                                    userName = story.displayName,
                                    avatarUrl = story.avatarUrl,
                                    mediaUrl = story.mediaUrl,
                                    mediaType = story.mediaType,
                                    text = story.text,
                                    textColor = story.textColor,
                                    backgroundColor = story.backgroundColor,
                                    duration = story.duration,
                                    time = formatTime(story.createdAt),
                                    viewed = story.isViewed,
                                    viewCount = story.viewCount
                                )
                            }
                        }
                        
                        // Преобразуем в статусы
                        _statuses.value = otherUserStories.map { userStory ->
                            val latestStory = userStory.stories.maxByOrNull { it.createdAt }
                            Status(
                                id = latestStory?.id ?: userStory.userId,
                                username = userStory.displayName,
                                time = formatTime(latestStory?.createdAt ?: 0),
                                viewed = !userStory.hasUnwatched,
                                imageUrl = latestStory?.mediaUrl,
                                userId = userStory.userId,
                                avatarUrl = userStory.avatarUrl
                            )
                        }
                        
                        android.util.Log.d("StatusViewModel", "Loaded ${_statuses.value.size} statuses, ${_myStories.value.size} my stories")
                    },
                    onFailure = { e ->
                        _error.value = e.message
                        android.util.Log.e("StatusViewModel", "Failed to load stories: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message
                android.util.Log.e("StatusViewModel", "Error loading stories: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun viewStory(storyId: String) {
        viewModelScope.launch {
            try {
                ApiClient.viewStory(storyId)
            } catch (e: Exception) {
                android.util.Log.e("StatusViewModel", "Error viewing story: ${e.message}")
            }
        }
    }
    
    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.deleteStory(storyId)
                result.onSuccess {
                    loadStories() // Перезагружаем
                }
            } catch (e: Exception) {
                android.util.Log.e("StatusViewModel", "Error deleting story: ${e.message}")
            }
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "только что"
            diff < 3600_000 -> "${diff / 60_000} мин. назад"
            diff < 86400_000 -> "${diff / 3600_000} ч. назад"
            else -> "Вчера"
        }
    }
}
