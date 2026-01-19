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
class ChannelViewModel @Inject constructor() : ViewModel() {
    
    private val _channels = MutableStateFlow<List<ChannelUiModel>>(emptyList())
    val channels: StateFlow<List<ChannelUiModel>> = _channels
    
    private val _currentChannel = MutableStateFlow<ChannelUiModel?>(null)
    val currentChannel: StateFlow<ChannelUiModel?> = _currentChannel
    
    private val _posts = MutableStateFlow<List<ChannelPostUiModel>>(emptyList())
    val posts: StateFlow<List<ChannelPostUiModel>> = _posts
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    fun loadChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = ApiClient.getChannels()
                result.fold(
                    onSuccess = { channelList ->
                        _channels.value = channelList.map { it.toUiModel() }
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки каналов"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadChannel(channelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = ApiClient.getChannel(channelId)
                result.fold(
                    onSuccess = { channel ->
                        _currentChannel.value = channel.toUiModel()
                        loadPosts(channelId)
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки канала"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadPosts(channelId: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.getChannelPosts(channelId)
                result.fold(
                    onSuccess = { postList ->
                        _posts.value = postList.map { it.toUiModel() }
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChannelViewModel", "Failed to load posts: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChannelViewModel", "Error loading posts: ${e.message}")
            }
        }
    }
    
    fun subscribe(channelId: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.subscribeToChannel(channelId)
                result.fold(
                    onSuccess = {
                        // Обновляем состояние
                        _currentChannel.value = _currentChannel.value?.copy(
                            isSubscribed = true,
                            subscriberCount = (_currentChannel.value?.subscriberCount ?: 0) + 1
                        )
                        loadChannels()
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка подписки"
            }
        }
    }
    
    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.unsubscribeFromChannel(channelId)
                result.fold(
                    onSuccess = {
                        _currentChannel.value = _currentChannel.value?.copy(
                            isSubscribed = false,
                            subscriberCount = ((_currentChannel.value?.subscriberCount ?: 1) - 1).coerceAtLeast(0)
                        )
                        loadChannels()
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка отписки"
            }
        }
    }
    
    fun toggleNotifications(channelId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val result = ApiClient.toggleChannelNotifications(channelId, enabled)
                result.fold(
                    onSuccess = {
                        _currentChannel.value = _currentChannel.value?.copy(
                            notificationsEnabled = enabled
                        )
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка изменения уведомлений"
            }
        }
    }
    
    fun createPost(channelId: String, content: String, allowComments: Boolean = true) {
        viewModelScope.launch {
            try {
                val result = ApiClient.createChannelPost(channelId, content, allowComments)
                result.fold(
                    onSuccess = {
                        loadPosts(channelId)
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка публикации"
            }
        }
    }
    
    fun addReactionToPost(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                ApiClient.addReaction(messageId, emoji)
                _currentChannel.value?.let { loadPosts(it.id) }
            } catch (e: Exception) {
                android.util.Log.e("ChannelViewModel", "Error adding reaction: ${e.message}")
            }
        }
    }
    
    private fun ApiClient.ChannelApiResponse.toUiModel(): ChannelUiModel {
        val isMkrOfficial = chatId == "mkr-official-channel"
        
        return ChannelUiModel(
            id = id,
            chatId = chatId,
            name = name,
            username = username,
            description = description,
            avatarUrl = avatarUrl,
            isPublic = isPublic,
            subscriberCount = subscriberCount,
            allowComments = allowComments,
            isSubscribed = isSubscribed,
            notificationsEnabled = notificationsEnabled,
            isOfficial = isMkrOfficial,
            canPost = !isMkrOfficial // Для MKR - нельзя публиковать обычным пользователям
        )
    }
    
    private fun ApiClient.ChannelPostApiResponse.toUiModel() = ChannelPostUiModel(
        id = id,
        messageId = messageId,
        content = content,
        senderName = senderName,
        timestamp = timestamp,
        viewCount = viewCount,
        isPinned = isPinned,
        allowComments = allowComments,
        commentCount = commentCount,
        reactions = reactions.map { ReactionUiModel(it.emoji, it.count, it.hasReacted) }
    )
}

data class ChannelPostUiModel(
    val id: String,
    val messageId: String,
    val content: String,
    val senderName: String,
    val timestamp: Long,
    val viewCount: Int,
    val isPinned: Boolean,
    val allowComments: Boolean,
    val commentCount: Int,
    val reactions: List<ReactionUiModel> = emptyList()
)

data class ReactionUiModel(
    val emoji: String,
    val count: Int,
    val hasReacted: Boolean
)
