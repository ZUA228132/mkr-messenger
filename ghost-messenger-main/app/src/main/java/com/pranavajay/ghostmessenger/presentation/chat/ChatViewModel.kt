package com.pranavajay.ghostmessenger.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavajay.ghostmessenger.data.models.Chat
import com.pranavajay.ghostmessenger.data.models.Message
import com.pranavajay.ghostmessenger.data.repository.ChatRepository
import com.pranavajay.ghostmessenger.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListState(
    val isLoading: Boolean = false,
    val chats: List<Chat> = emptyList(),
    val error: String? = null
)

data class ChatDetailState(
    val isLoading: Boolean = false,
    val messages: List<Message> = emptyList(),
    val chat: Chat? = null,
    val isTyping: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _chatListState = MutableStateFlow(ChatListState())
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()
    
    private val _chatDetailState = MutableStateFlow(ChatDetailState())
    val chatDetailState: StateFlow<ChatDetailState> = _chatDetailState.asStateFlow()
    
    private var currentChatId: String? = null
    
    init {
        connectSocket()
        observeIncomingMessages()
        observeTypingStatus()
    }
    
    private fun connectSocket() {
        viewModelScope.launch {
            chatRepository.connectSocket()
        }
    }
    
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            chatRepository.incomingMessages.collect { message ->
                if (message.chatId == currentChatId) {
                    val currentMessages = _chatDetailState.value.messages.toMutableList()
                    currentMessages.add(message)
                    _chatDetailState.value = _chatDetailState.value.copy(messages = currentMessages)
                }
                // Refresh chat list to update last message
                loadChats()
            }
        }
    }
    
    private fun observeTypingStatus() {
        viewModelScope.launch {
            chatRepository.typingStatus.collect { (userId, isTyping) ->
                // Update typing indicator if in current chat
                _chatDetailState.value = _chatDetailState.value.copy(isTyping = isTyping)
            }
        }
    }
    
    fun loadChats() {
        viewModelScope.launch {
            _chatListState.value = _chatListState.value.copy(isLoading = true)
            
            when (val result = chatRepository.getChats()) {
                is Result.Success -> {
                    _chatListState.value = _chatListState.value.copy(
                        isLoading = false,
                        chats = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _chatListState.value = _chatListState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun loadMessages(chatId: String) {
        currentChatId = chatId
        viewModelScope.launch {
            _chatDetailState.value = _chatDetailState.value.copy(isLoading = true)
            
            when (val result = chatRepository.getMessages(chatId)) {
                is Result.Success -> {
                    _chatDetailState.value = _chatDetailState.value.copy(
                        isLoading = false,
                        messages = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _chatDetailState.value = _chatDetailState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun loadChatDetails(chatId: String) {
        viewModelScope.launch {
            val chats = _chatListState.value.chats
            val chat = chats.find { it.id == chatId }
            _chatDetailState.value = _chatDetailState.value.copy(chat = chat)
        }
    }
    
    fun sendMessage(chatId: String, receiverId: String, content: String, replyToId: String? = null) {
        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(chatId, receiverId, content, replyToId)) {
                is Result.Success -> {
                    val currentMessages = _chatDetailState.value.messages.toMutableList()
                    currentMessages.add(result.data)
                    _chatDetailState.value = _chatDetailState.value.copy(messages = currentMessages)
                }
                is Result.Error -> {
                    _chatDetailState.value = _chatDetailState.value.copy(error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }
    
    fun sendTyping(chatId: String, isTyping: Boolean) {
        chatRepository.sendTyping(chatId, isTyping)
    }
    
    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            chatRepository.addReaction(messageId, emoji)
        }
    }
    
    fun removeReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            chatRepository.removeReaction(messageId, emoji)
        }
    }
    
    fun initiateCall(receiverId: String, callType: String) {
        viewModelScope.launch {
            chatRepository.initiateCall(receiverId, callType)
        }
    }
    
    fun setGhostMode(chatId: String, duration: Long?) {
        viewModelScope.launch {
            chatRepository.setGhostMode(chatId, duration)
            // Update local state
            val currentChat = _chatDetailState.value.chat
            currentChat?.let {
                _chatDetailState.value = _chatDetailState.value.copy(
                    chat = it.copy(
                        disappearingMode = duration != null,
                        disappearAfter = duration
                    )
                )
            }
        }
    }
    
    fun pinChat(chatId: String, pinned: Boolean) {
        viewModelScope.launch {
            chatRepository.pinChat(chatId, pinned)
            loadChats()
        }
    }
    
    fun archiveChat(chatId: String, archived: Boolean) {
        viewModelScope.launch {
            chatRepository.archiveChat(chatId, archived)
            loadChats()
        }
    }
    
    fun forwardMessage(messageId: String, toChatIds: List<String>) {
        viewModelScope.launch {
            chatRepository.forwardMessage(messageId, toChatIds)
        }
    }
    
    fun deleteMessage(messageId: String, forEveryone: Boolean = false) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId, forEveryone)
            // Remove from local state
            val currentMessages = _chatDetailState.value.messages.filter { it.id != messageId }
            _chatDetailState.value = _chatDetailState.value.copy(messages = currentMessages)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        chatRepository.disconnectSocket()
    }
}
