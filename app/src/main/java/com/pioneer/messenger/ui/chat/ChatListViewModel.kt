package com.pioneer.messenger.ui.chat

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.ChatDao
import com.pioneer.messenger.data.local.ChatEntity
import com.pioneer.messenger.data.local.MessageDao
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.data.webrtc.CallState
import com.pioneer.messenger.data.webrtc.WebRTCClient
import com.pioneer.messenger.service.MessageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val authManager: AuthManager,
    private val webRTCClient: WebRTCClient
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    // Состояние соединения
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // Событие входящего звонка
    private val _incomingCall = MutableStateFlow<IncomingCallEvent?>(null)
    val incomingCall: StateFlow<IncomingCallEvent?> = _incomingCall
    
    // Stories
    private val _stories = MutableStateFlow<List<StoryUserModel>>(emptyList())
    val stories: StateFlow<List<StoryUserModel>> = _stories
    
    private val _myStories = MutableStateFlow<List<String>>(emptyList())
    val myStories: StateFlow<List<String>> = _myStories
    
    val chats: Flow<List<ChatUiModel>> = chatDao.getAllChats().map { entities ->
        entities.map { chat ->
            val lastMessage = try {
                messageDao.getMessagesByChat(chat.id).first().firstOrNull()
            } catch (e: Exception) {
                null
            }
            chat.toUiModel(lastMessage)
        }
    }
    
    init {
        viewModelScope.launch {
            // Проверяем сеть
            checkNetworkAndConnect()
        }
        
        viewModelScope.launch {
            authManager.restoreSession()
            loadChatsFromServer()
            
            // Запускаем сервис для WebSocket сообщений
            startMessageService()
            
            // Подключаемся к WebSocket для звонков
            connectToCallSignaling()
        }
        
        // Слушаем входящие звонки
        viewModelScope.launch {
            webRTCClient.callState.collect { state ->
                if (state is CallState.Incoming) {
                    _incomingCall.value = IncomingCallEvent(
                        callId = state.callId,
                        callerId = state.callerId,
                        isVideo = state.isVideo
                    )
                }
            }
        }
        
        // Слушаем новые сообщения для обновления списка чатов
        viewModelScope.launch {
            com.pioneer.messenger.data.network.RealtimeEvents.newMessage.collect { messageId ->
                android.util.Log.d("ChatListViewModel", "New message received, refreshing chats")
                // Перезагружаем чаты с сервера для обновления lastMessage
                loadChatsFromServer()
            }
        }
    }
    
    fun loadStories() {
        viewModelScope.launch {
            try {
                val result = ApiClient.getStories()
                result.fold(
                    onSuccess = { userStories ->
                        val currentUserId = ApiClient.getCurrentUserId()
                        
                        // Мои истории
                        val myUserStories = userStories.filter { it.userId == currentUserId }
                        _myStories.value = myUserStories.flatMap { it.stories.map { s -> s.id } }
                        
                        // Истории других пользователей
                        val otherUserStories = userStories.filter { it.userId != currentUserId }
                        _stories.value = otherUserStories.map { userStory ->
                            StoryUserModel(
                                oderId = userStory.userId,
                                userName = userStory.displayName,
                                avatarUrl = null,
                                hasUnwatched = userStory.hasUnwatched
                            )
                        }
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChatListViewModel", "Failed to load stories: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatListViewModel", "Error loading stories: ${e.message}")
            }
        }
    }
    
    private fun startMessageService() {
        try {
            MessageService.start(context)
            android.util.Log.d("ChatListViewModel", "MessageService started")
        } catch (e: Exception) {
            android.util.Log.e("ChatListViewModel", "Failed to start MessageService: ${e.message}")
        }
    }
    
    private fun connectToCallSignaling() {
        viewModelScope.launch {
            val userId = authManager.currentUser.first()?.id
            if (userId != null) {
                android.util.Log.d("ChatListViewModel", "Connecting to call signaling for user: $userId")
                webRTCClient.connect(userId)
            }
        }
    }
    
    private fun checkNetworkAndConnect() {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            
            // Проверяем доступность сети
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (capabilities == null || !capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                _connectionState.value = ConnectionState.WAITING_FOR_NETWORK
                
                // Периодически проверяем сеть
                while (_connectionState.value == ConnectionState.WAITING_FOR_NETWORK) {
                    kotlinx.coroutines.delay(3000)
                    val newNetwork = connectivityManager.activeNetwork
                    val newCapabilities = connectivityManager.getNetworkCapabilities(newNetwork)
                    if (newCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                        _connectionState.value = ConnectionState.CONNECTING
                        break
                    }
                }
            }
            
            // Пробуем подключиться к серверу
            try {
                _connectionState.value = ConnectionState.UPDATING
                val result = ApiClient.getChats()
                result.fold(
                    onSuccess = {
                        _connectionState.value = ConnectionState.CONNECTED
                    },
                    onFailure = {
                        // Если ошибка - показываем "Соединение..."
                        _connectionState.value = ConnectionState.CONNECTING
                        kotlinx.coroutines.delay(2000)
                        checkNetworkAndConnect() // Повторяем
                    }
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.CONNECTING
                kotlinx.coroutines.delay(2000)
                checkNetworkAndConnect()
            }
        }
    }
    
    fun clearIncomingCall() {
        _incomingCall.value = null
    }
    
    fun loadChatsFromServer() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _connectionState.value = ConnectionState.UPDATING
            
            // Проверяем авторизацию
            if (!ApiClient.hasAuthToken()) {
                authManager.restoreSession()
            }
            
            try {
                val result = ApiClient.getChats()
                result.fold(
                    onSuccess = { serverChats ->
                        _connectionState.value = ConnectionState.CONNECTED
                        // Синхронизируем с локальной БД
                        serverChats.forEach { serverChat ->
                            val existingChat = chatDao.getChatById(serverChat.id)
                            
                            // Для приватных чатов используем имя собеседника
                            val chatName = if (serverChat.type == "PRIVATE" || serverChat.type == "direct") {
                                // Берём имя из participantNames (бэкенд уже определил правильное имя)
                                serverChat.name
                            } else {
                                serverChat.name
                            }
                            
                            if (existingChat == null) {
                                // Канал MKR всегда закреплён
                                val isMkrChannel = serverChat.id == "mkr-official-channel"
                                
                                chatDao.insertChat(ChatEntity(
                                    id = serverChat.id,
                                    type = serverChat.type,
                                    name = chatName,
                                    description = null,
                                    avatarUrl = null,
                                    participants = serverChat.participants.joinToString(","),
                                    admins = "",
                                    createdAt = serverChat.createdAt,
                                    encryptionKeyId = UUID.randomUUID().toString(),
                                    isPinned = isMkrChannel, // MKR всегда закреплён
                                    isMuted = false,
                                    unreadCount = 0,
                                    autoDeleteDays = null
                                ))
                            } else {
                                // Обновляем имя если изменилось
                                if (existingChat.name != chatName) {
                                    chatDao.updateChat(existingChat.copy(name = chatName))
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        _error.value = e.message
                        _connectionState.value = ConnectionState.CONNECTING
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки чатов"
                _connectionState.value = ConnectionState.CONNECTING
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun createNewChat() {
        createChat("PRIVATE", "Новый чат", null)
    }
    
    fun createChat(type: String, name: String, autoDeleteDays: Int? = null, participantIds: List<String> = emptyList()) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // Создаём на сервере
                val result = ApiClient.createChat(type, name, participantIds)
                result.fold(
                    onSuccess = { serverChat ->
                        // Сохраняем локально
                        chatDao.insertChat(ChatEntity(
                            id = serverChat.id,
                            type = serverChat.type,
                            name = serverChat.name,
                            description = null,
                            avatarUrl = null,
                            participants = serverChat.participants.joinToString(","),
                            admins = "",
                            createdAt = serverChat.createdAt,
                            encryptionKeyId = UUID.randomUUID().toString(),
                            isPinned = false,
                            isMuted = false,
                            unreadCount = 0,
                            autoDeleteDays = autoDeleteDays
                        ))
                    },
                    onFailure = { e ->
                        // Если сервер недоступен - создаём локально
                        val newChat = ChatEntity(
                            id = UUID.randomUUID().toString(),
                            type = type,
                            name = name.ifBlank { 
                                when (type) {
                                    "GROUP" -> "Новая группа"
                                    "CHANNEL" -> "Новый канал"
                                    "SECRET" -> "Секретный чат"
                                    else -> "Новый чат"
                                }
                            },
                            description = null,
                            avatarUrl = null,
                            participants = "",
                            admins = "",
                            createdAt = System.currentTimeMillis(),
                            encryptionKeyId = UUID.randomUUID().toString(),
                            isPinned = false,
                            isMuted = false,
                            unreadCount = 0,
                            autoDeleteDays = autoDeleteDays
                        )
                        chatDao.insertChat(newChat)
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка создания чата"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun ChatEntity.toUiModel(lastMessage: com.pioneer.messenger.data.local.MessageEntity?) = ChatUiModel(
        id = id,
        name = name,
        lastMessage = when {
            lastMessage == null -> "Нет сообщений"
            lastMessage.type == "VOICE" -> "🎤 Голосовое сообщение"
            lastMessage.type == "VIDEO_NOTE" -> "📹 Видеокружок"
            lastMessage.type == "IMAGE" -> "🖼 Фото"
            lastMessage.type == "VIDEO" -> "🎬 Видео"
            lastMessage.type == "FILE" -> "📎 ${lastMessage.fileName ?: "Файл"}"
            lastMessage.encryptedContent.isNotBlank() -> lastMessage.encryptedContent
            else -> "💬 Сообщение"
        },
        lastMessageTime = lastMessage?.timestamp ?: createdAt,
        lastMessageType = lastMessage?.type,
        lastMessageStatus = lastMessage?.status,
        unreadCount = unreadCount,
        isGroup = type.equals("GROUP", ignoreCase = true),
        isChannel = type.equals("CHANNEL", ignoreCase = true),
        isPinned = isPinned,
        isSecret = type.equals("SECRET", ignoreCase = true),
        autoDeleteDays = autoDeleteDays,
        isMuted = isMuted,
        isSavedMessages = name == "Избранное"
    )
    
    fun togglePinChat(chatId: String) {
        viewModelScope.launch {
            try {
                val chat = chatDao.getChatById(chatId)
                chat?.let {
                    chatDao.updateChat(it.copy(isPinned = !it.isPinned))
                }
            } catch (e: Exception) {
                _error.value = "Ошибка закрепления чата"
            }
        }
    }
    
    fun toggleMuteChat(chatId: String) {
        viewModelScope.launch {
            try {
                val chat = chatDao.getChatById(chatId)
                chat?.let {
                    chatDao.updateChat(it.copy(isMuted = !it.isMuted))
                }
            } catch (e: Exception) {
                _error.value = "Ошибка изменения уведомлений"
            }
        }
    }
    
    fun deleteChat(chatId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            try {
                if (forEveryone) {
                    // Удаляем на сервере
                    val result = ApiClient.deleteChat(chatId)
                    result.fold(
                        onSuccess = {
                            // Удаляем локально
                            chatDao.deleteChat(chatId)
                            messageDao.deleteMessagesByChat(chatId)
                        },
                        onFailure = { e ->
                            // Если сервер недоступен - удаляем только локально
                            chatDao.deleteChat(chatId)
                            messageDao.deleteMessagesByChat(chatId)
                        }
                    )
                } else {
                    // Удаляем только локально
                    chatDao.deleteChat(chatId)
                    messageDao.deleteMessagesByChat(chatId)
                }
            } catch (e: Exception) {
                _error.value = "Ошибка удаления чата"
            }
        }
    }
}

data class IncomingCallEvent(
    val callId: String,
    val callerId: String,
    val isVideo: Boolean
)
