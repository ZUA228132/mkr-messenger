package com.pioneer.messenger.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.crypto.CryptoManager
import com.pioneer.messenger.data.local.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val keyDao: SessionKeyDao,
    private val userDao: UserDao,
    private val cryptoManager: CryptoManager,
    private val authManager: AuthManager
) : ViewModel() {
    
    private var currentChatId: String? = null
    private var sessionKey: ByteArray? = null
    
    // Запись
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var timerJob: Job? = null
    
    // Воспроизведение
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: String? = null
    
    private val _messages = MutableStateFlow<List<MessageUiModel>>(emptyList())
    val messages: StateFlow<List<MessageUiModel>> = _messages
    
    private val _chatInfo = MutableStateFlow<ChatInfoUiModel?>(null)
    val chatInfo: StateFlow<ChatInfoUiModel?> = _chatInfo
    
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _playingMessageId = MutableStateFlow<String?>(null)
    
    // Видеокружок для воспроизведения
    private val _videoNoteToPlay = MutableStateFlow<String?>(null)
    val videoNoteToPlay: StateFlow<String?> = _videoNoteToPlay
    
    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId
    
    // Typing статус
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping
    
    private val _typingUserName = MutableStateFlow<String?>(null)
    val typingUserName: StateFlow<String?> = _typingUserName
    
    // Last seen
    private val _lastSeen = MutableStateFlow<Long?>(null)
    val lastSeen: StateFlow<Long?> = _lastSeen
    
    // Online статус
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline
    
    // WebSocket клиент
    private var wsClient: com.pioneer.messenger.data.network.WebSocketClient? = null
    
    // Таймер для отправки typing
    private var typingJob: Job? = null
    private var typingResetJob: Job? = null
    
    // Polling job для обновления сообщений
    private var pollingJob: Job? = null
    
    fun sendTyping() {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            currentChatId?.let { chatId ->
                wsClient?.sendTyping(chatId)
            }
        }
    }
    
    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _currentUserId.value = user?.id ?: ""
                // Инициализируем WebSocket клиент
                if (user != null && wsClient == null) {
                    wsClient = com.pioneer.messenger.data.network.WebSocketClient(user.id)
                    wsClient?.connect()
                }
            }
        }
        
        // Слушаем новые сообщения через RealtimeEvents
        viewModelScope.launch {
            com.pioneer.messenger.data.network.RealtimeEvents.newMessage.collect { messageId ->
                android.util.Log.d("ChatViewModel", "RealtimeEvents: new message $messageId")
                // Принудительно загружаем новые сообщения с сервера для текущего чата
                currentChatId?.let { chatId ->
                    loadNewMessagesFromServer(chatId)
                }
            }
        }
        
        // Слушаем typing через RealtimeEvents
        viewModelScope.launch {
            com.pioneer.messenger.data.network.RealtimeEvents.typing.collect { event ->
                android.util.Log.d("ChatViewModel", "RealtimeEvents: typing from ${event.userName} in ${event.chatId}")
                if (event.chatId == currentChatId) {
                    _isTyping.value = true
                    _typingUserName.value = event.userName
                    // Сбрасываем через 3 секунды
                    typingResetJob?.cancel()
                    typingResetJob = viewModelScope.launch {
                        delay(3000)
                        _isTyping.value = false
                        _typingUserName.value = null
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        wsClient?.disconnect()
        pollingJob?.cancel()
        cancelRecording()
        stopPlayback()
    }
    
    fun loadChat(chatId: String) {
        currentChatId = chatId
        
        viewModelScope.launch {
            // Сначала пробуем загрузить из локальной БД
            var chat = chatDao.getChatById(chatId)
            
            // Если нет локально - загружаем с сервера
            if (chat == null) {
                try {
                    val result = com.pioneer.messenger.data.network.ApiClient.getChats()
                    result.fold(
                        onSuccess = { serverChats ->
                            // Сохраняем все чаты в локальную БД
                            serverChats.forEach { serverChat ->
                                val existing = chatDao.getChatById(serverChat.id)
                                if (existing == null) {
                                    chatDao.insertChat(ChatEntity(
                                        id = serverChat.id,
                                        type = serverChat.type,
                                        name = serverChat.name,
                                        description = null,
                                        avatarUrl = null,
                                        participants = serverChat.participants.joinToString(","),
                                        admins = "",
                                        createdAt = serverChat.createdAt,
                                        encryptionKeyId = java.util.UUID.randomUUID().toString(),
                                        isPinned = false,
                                        isMuted = false,
                                        unreadCount = 0,
                                        autoDeleteDays = null
                                    ))
                                }
                            }
                            
                            // Теперь ищем нужный чат
                            val serverChat = serverChats.find { it.id == chatId }
                            if (serverChat != null) {
                                chat = chatDao.getChatById(chatId)
                                
                                // Находим ID другого участника (не текущего пользователя)
                                val currentUserId = authManager.currentUser.first()?.id
                                val otherUserId = serverChat.participants.firstOrNull { it != currentUserId }
                                
                                // Получаем имя из participantNames или используем name чата
                                val displayName = if (serverChat.type == "direct" && otherUserId != null) {
                                    serverChat.participantNames[otherUserId] ?: serverChat.name
                                } else {
                                    serverChat.name
                                }
                                
                                // Загружаем данные пользователя для аватара и верификации
                                var avatarUrl: String? = null
                                var isVerified = false
                                var isOnline = false
                                var emojiStatus: String? = null
                                var createdAt: Long = 0
                                
                                if (otherUserId != null) {
                                    try {
                                        val userResult = com.pioneer.messenger.data.network.ApiClient.getUser(otherUserId)
                                        userResult.onSuccess { user ->
                                            avatarUrl = user.avatarUrl
                                            isVerified = user.isVerified
                                            isOnline = user.isOnline
                                            emojiStatus = user.emojiStatus
                                            createdAt = user.createdAt
                                            android.util.Log.d("ChatViewModel", "Loaded user from server $otherUserId: verified=$isVerified, createdAt=$createdAt")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatViewModel", "Failed to load user: ${e.message}")
                                    }
                                }
                                
                                // Устанавливаем chatInfo сразу
                                _chatInfo.value = ChatInfoUiModel(
                                    id = serverChat.id,
                                    name = displayName.ifEmpty { "Пользователь" },
                                    isOnline = isOnline,
                                    isSecret = serverChat.type == "SECRET",
                                    isChannel = serverChat.type == "CHANNEL",
                                    autoDeleteDays = null,
                                    subscriberCount = if (serverChat.type == "CHANNEL") 1234 else 0,
                                    otherUserId = otherUserId,
                                    avatarUrl = avatarUrl,
                                    isVerified = isVerified,
                                    emojiStatus = emojiStatus,
                                    createdAt = createdAt
                                )
                            }
                        },
                        onFailure = { e ->
                            android.util.Log.e("ChatViewModel", "Failed to load chats: ${e.message}")
                        }
                    )
                } catch (e: Exception) { 
                    e.printStackTrace()
                }
            }
            
            // Если чат найден локально - устанавливаем chatInfo
            if (chat != null && _chatInfo.value == null) {
                val currentUserId = authManager.currentUser.first()?.id
                val participants = chat!!.participants.split(",")
                val otherUserId = participants.firstOrNull { it != currentUserId }
                
                // Загружаем данные пользователя для аватара и верификации
                var avatarUrl: String? = null
                var isVerified = false
                var isOnline = false
                var emojiStatus: String? = null
                var createdAt: Long = 0
                
                if (otherUserId != null) {
                    try {
                        val userResult = com.pioneer.messenger.data.network.ApiClient.getUser(otherUserId)
                        userResult.onSuccess { user ->
                            avatarUrl = user.avatarUrl
                            isVerified = user.isVerified
                            isOnline = user.isOnline
                            emojiStatus = user.emojiStatus
                            createdAt = user.createdAt
                            android.util.Log.d("ChatViewModel", "Loaded user $otherUserId: verified=$isVerified, createdAt=$createdAt")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Failed to load user: ${e.message}")
                    }
                }
                
                _chatInfo.value = ChatInfoUiModel(
                    id = chat!!.id,
                    name = chat!!.name.ifEmpty { "Пользователь" },
                    isOnline = isOnline,
                    isSecret = chat!!.type == "SECRET",
                    isChannel = chat!!.type == "CHANNEL",
                    autoDeleteDays = chat!!.autoDeleteDays,
                    subscriberCount = if (chat!!.type == "CHANNEL") 1234 else 0,
                    otherUserId = otherUserId,
                    avatarUrl = avatarUrl,
                    isVerified = isVerified,
                    emojiStatus = emojiStatus,
                    createdAt = createdAt
                )
            }
            
            // Если чат всё ещё не найден - показываем заглушку
            if (_chatInfo.value == null) {
                _chatInfo.value = ChatInfoUiModel(
                    id = chatId,
                    name = "Чат",
                    isOnline = false,
                    isSecret = false,
                    isChannel = false,
                    autoDeleteDays = null,
                    subscriberCount = 0
                )
            }
            
            // Загружаем сообщения с сервера
            loadMessagesFromServer(chatId)
            
            // Запускаем периодическое обновление сообщений (fallback для WebSocket)
            startMessagePolling(chatId)
            
            // Загружаем пользователей для отображения имён
            loadUsersFromServer()
            
            keyDao.getKeyForChat(chatId)?.let { keyEntity ->
                sessionKey = cryptoManager.decryptSessionKey(
                    com.pioneer.messenger.data.crypto.EncryptedData(keyEntity.encryptedKey, keyEntity.iv)
                )
            }
            
            val currentUserId = authManager.currentUser.first()?.id ?: return@launch
            
            // Кэш имён пользователей
            val userNamesCache = mutableMapOf<String, String>()
            
            // Комбинируем сообщения с состоянием воспроизведения
            combine(
                messageDao.getMessagesByChat(chatId),
                _playingMessageId
            ) { entities, playingId ->
                entities.map { entity ->
                    // Для медиа-сообщений показываем URL или путь к файлу
                    val decryptedContent = when {
                        entity.type == "VOICE" || entity.type == "VIDEO_NOTE" -> ""
                        entity.type == "IMAGE" -> entity.localFilePath ?: entity.encryptedContent
                        entity.type == "VIDEO" -> entity.localFilePath ?: entity.encryptedContent
                        entity.type == "FILE" -> entity.fileName ?: "Файл"
                        entity.encryptedContent.isEmpty() -> "[Сообщение]"
                        else -> entity.encryptedContent
                    }
                    
                    // Получаем имя отправителя
                    val senderName = if (entity.senderId == currentUserId) {
                        "Вы"
                    } else {
                        userNamesCache.getOrPut(entity.senderId) {
                            // Пробуем получить из локальной БД
                            val localUser = userDao.getUserById(entity.senderId)
                            localUser?.displayName ?: entity.senderId.take(8)
                        }
                    }
                    
                    MessageUiModel(
                        id = entity.id,
                        content = decryptedContent,
                        senderName = senderName,
                        timestamp = entity.timestamp,
                        isOwn = entity.senderId == currentUserId,
                        status = entity.status,
                        type = entity.type,
                        duration = entity.duration?.let { formatDuration(it) },
                        fileName = entity.fileName,
                        fileSize = entity.fileSize?.let { formatFileSize(it) },
                        isPlaying = entity.id == playingId,
                        localFilePath = entity.localFilePath,
                        fileUrl = entity.fileUrl
                    )
                }
            }.collect { _messages.value = it }
        }
    }
    
    private fun loadMessagesFromServer(chatId: String) {
        viewModelScope.launch {
            try {
                val result = com.pioneer.messenger.data.network.ApiClient.getMessages(chatId)
                result.fold(
                    onSuccess = { serverMessages ->
                        // Получаем существующие ID сообщений
                        val existingIds = messageDao.getMessagesByChat(chatId).first().map { it.id }.toSet()
                        
                        // Сохраняем только новые сообщения в локальную БД
                        serverMessages.forEach { msg ->
                            if (msg.id !in existingIds) {
                                // Для IMAGE/VIDEO/FILE сообщений URL хранится в encryptedContent
                                val fileUrl = if (msg.type in listOf("IMAGE", "VIDEO", "FILE", "VOICE", "VIDEO_NOTE")) {
                                    msg.encryptedContent
                                } else null
                                
                                messageDao.insertMessage(MessageEntity(
                                    id = msg.id,
                                    chatId = msg.chatId,
                                    senderId = msg.senderId,
                                    encryptedContent = msg.encryptedContent,
                                    nonce = msg.nonce,
                                    timestamp = msg.timestamp,
                                    type = msg.type,
                                    status = msg.status,
                                    replyToId = null,
                                    isEdited = false,
                                    editedAt = null,
                                    fileUrl = fileUrl
                                ))
                            }
                        }
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChatViewModel", "Failed to load messages: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading messages: ${e.message}")
            }
        }
    }
    
    // Загружает только новые сообщения (для реалтайма)
    private fun loadNewMessagesFromServer(chatId: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "Loading new messages for chat $chatId")
                val result = com.pioneer.messenger.data.network.ApiClient.getMessages(chatId)
                result.fold(
                    onSuccess = { serverMessages ->
                        var newCount = 0
                        // Вставляем только новые сообщения в Room
                        // Room Flow автоматически обновит UI
                        serverMessages.forEach { msg ->
                            val existing = messageDao.getMessageById(msg.id)
                            if (existing == null) {
                                // Для IMAGE/VIDEO/FILE сообщений URL хранится в encryptedContent
                                val fileUrl = if (msg.type in listOf("IMAGE", "VIDEO", "FILE", "VOICE", "VIDEO_NOTE")) {
                                    msg.encryptedContent
                                } else null
                                
                                messageDao.insertMessage(MessageEntity(
                                    id = msg.id,
                                    chatId = msg.chatId,
                                    senderId = msg.senderId,
                                    encryptedContent = msg.encryptedContent,
                                    nonce = msg.nonce,
                                    timestamp = msg.timestamp,
                                    type = msg.type,
                                    status = msg.status,
                                    replyToId = null,
                                    isEdited = false,
                                    editedAt = null,
                                    fileUrl = fileUrl
                                ))
                                newCount++
                            }
                        }
                        if (newCount > 0) {
                            android.util.Log.d("ChatViewModel", "Inserted $newCount new messages into Room")
                        }
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChatViewModel", "Failed to load new messages: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading new messages: ${e.message}")
            }
        }
    }
    
    // Периодическое обновление сообщений (fallback для WebSocket)
    private fun startMessagePolling(chatId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Каждые 5 секунд
                loadNewMessagesFromServer(chatId)
            }
        }
    }
    
    private fun loadUsersFromServer() {
        viewModelScope.launch {
            try {
                val result = com.pioneer.messenger.data.network.ApiClient.getUsers()
                result.fold(
                    onSuccess = { users ->
                        users.forEach { user ->
                            userDao.insertUser(UserEntity(
                                id = user.id,
                                username = user.username,
                                displayName = user.displayName,
                                publicKey = user.publicKey,
                                avatarUrl = user.avatarUrl,
                                accessLevel = user.accessLevel,
                                isOnline = user.isOnline,
                                lastSeen = System.currentTimeMillis()
                            ))
                        }
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChatViewModel", "Failed to load users: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading users: ${e.message}")
            }
        }
    }
    
    fun sendMessage(content: String) {
        val chatId = currentChatId ?: return
        if (content.isBlank()) return
        
        viewModelScope.launch {
            val currentUser = authManager.currentUser.first() ?: return@launch
            
            // Используем временную метку как часть ID для отслеживания
            val timestamp = System.currentTimeMillis()
            val messageId = "local_${timestamp}_${(0..9999).random()}"
            
            // Сохраняем локально со статусом SENDING
            messageDao.insertMessage(MessageEntity(
                id = messageId,
                chatId = chatId,
                senderId = currentUser.id,
                encryptedContent = content,
                nonce = timestamp.toString(), // Сохраняем timestamp в nonce для отслеживания
                timestamp = timestamp,
                type = "TEXT",
                status = "SENDING",
                replyToId = null,
                isEdited = false,
                editedAt = null
            ))
            
            // Отправляем на сервер
            try {
                val result = com.pioneer.messenger.data.network.ApiClient.sendMessage(
                    chatId = chatId,
                    encryptedContent = content,
                    nonce = timestamp.toString(), // Передаём timestamp для идентификации
                    type = "TEXT"
                )
                
                result.fold(
                    onSuccess = { response ->
                        // Удаляем локальное сообщение - серверное придёт через WebSocket
                        messageDao.deleteMessageById(messageId)
                    },
                    onFailure = { error ->
                        messageDao.updateMessageStatus(messageId, "FAILED")
                    }
                )
            } catch (e: Exception) {
                messageDao.updateMessageStatus(messageId, "FAILED")
            }
        }
    }

    
    // ========== ЗАПИСЬ ГОЛОСОВЫХ ==========
    
    fun startVoiceRecording() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            recordingFile = file
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            _recordingState.value = RecordingState(isRecording = true, isVideo = false, duration = 0)
            startRecordingTimer()
            
        } catch (e: Exception) {
            e.printStackTrace()
            _recordingState.value = RecordingState()
        }
    }
    
    private fun startRecordingTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_recordingState.value.isRecording) {
                delay(1000)
                _recordingState.value = _recordingState.value.copy(duration = _recordingState.value.duration + 1)
            }
        }
    }
    
    fun stopAndSendRecording() {
        val duration = _recordingState.value.duration
        val isVideo = _recordingState.value.isVideo
        
        timerJob?.cancel()
        _recordingState.value = RecordingState()
        
        if (isVideo) {
            // TODO: Видеокружок
            return
        }
        
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            
            recordingFile?.let { file ->
                if (file.exists() && file.length() > 0 && duration > 0) {
                    sendVoiceMessage(file, duration)
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recordingFile?.delete()
        }
        recordingFile = null
    }
    
    fun cancelRecording() {
        timerJob?.cancel()
        _recordingState.value = RecordingState()
        
        try {
            mediaRecorder?.apply { stop(); release() }
        } catch (e: Exception) { }
        mediaRecorder = null
        recordingFile?.delete()
        recordingFile = null
    }
    
    private fun sendVoiceMessage(file: File, durationSec: Int) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            try {
                val currentUser = authManager.currentUser.first() ?: return@launch
                
                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                
                // Сначала сохраняем локально со статусом SENDING
                val voiceDir = File(context.filesDir, "voice_messages")
                if (!voiceDir.exists()) voiceDir.mkdirs()
                
                val savedFile = File(voiceDir, "${messageId}.m4a")
                file.copyTo(savedFile, overwrite = true)
                file.delete()
                
                messageDao.insertMessage(MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUser.id,
                    encryptedContent = "",
                    nonce = "",
                    timestamp = timestamp,
                    type = "VOICE",
                    status = "SENDING",
                    replyToId = null,
                    isEdited = false,
                    editedAt = null,
                    duration = durationSec,
                    fileSize = savedFile.length(),
                    localFilePath = savedFile.absolutePath
                ))
                
                // Загружаем на сервер
                val fileBytes = savedFile.readBytes()
                val uploadResult = com.pioneer.messenger.data.network.ApiClient.uploadVoice(fileBytes, "${messageId}.m4a")
                
                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        // Отправляем сообщение с URL файла
                        val sendResult = com.pioneer.messenger.data.network.ApiClient.sendMessage(
                            chatId = chatId,
                            encryptedContent = uploadResponse.url,
                            nonce = durationSec.toString(),
                            type = "VOICE"
                        )
                        
                        sendResult.fold(
                            onSuccess = { serverMessage ->
                                // Удаляем локальное сообщение и заменяем серверным
                                messageDao.deleteMessageById(messageId)
                                messageDao.insertMessage(MessageEntity(
                                    id = serverMessage.id,
                                    chatId = chatId,
                                    senderId = currentUser.id,
                                    encryptedContent = serverMessage.encryptedContent,
                                    nonce = serverMessage.nonce,
                                    timestamp = serverMessage.timestamp,
                                    type = "VOICE",
                                    status = "SENT",
                                    replyToId = null,
                                    isEdited = false,
                                    editedAt = null,
                                    duration = durationSec,
                                    fileSize = savedFile.length(),
                                    localFilePath = savedFile.absolutePath,
                                    fileUrl = uploadResponse.url
                                ))
                            },
                            onFailure = { messageDao.updateMessageStatus(messageId, "FAILED") }
                        )
                    },
                    onFailure = { 
                        android.util.Log.e("ChatViewModel", "Failed to upload voice: ${it.message}")
                        messageDao.updateMessageStatus(messageId, "FAILED") 
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                file.delete()
            }
        }
    }
    
    // ========== ВОСПРОИЗВЕДЕНИЕ ГОЛОСОВЫХ ==========
    
    fun playVoiceMessage(messageId: String) {
        // Если уже играет это сообщение - пауза/продолжение
        if (currentPlayingId == messageId) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _playingMessageId.value = null
            } else {
                mediaPlayer?.start()
                _playingMessageId.value = messageId
            }
            return
        }
        
        // Остановить текущее воспроизведение
        stopPlayback()
        
        viewModelScope.launch {
            val message = messageDao.getMessageById(messageId) ?: return@launch
            
            if (message.type != "VOICE") return@launch
            
            try {
                // Используем локальный файл
                val filePath = message.localFilePath
                if (filePath.isNullOrEmpty() || !File(filePath).exists()) {
                    return@launch
                }
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    
                    setOnCompletionListener {
                        _playingMessageId.value = null
                        currentPlayingId = null
                    }
                }
                
                currentPlayingId = messageId
                _playingMessageId.value = messageId
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun stopPlayback() {
        try {
            mediaPlayer?.apply { stop(); release() }
        } catch (e: Exception) { }
        mediaPlayer = null
        currentPlayingId = null
        _playingMessageId.value = null
    }
    
    fun playVideoNote(messageId: String) {
        viewModelScope.launch {
            val message = messageDao.getMessageById(messageId) ?: return@launch
            
            if (message.type != "VIDEO_NOTE") return@launch
            
            // Проверяем локальный файл
            val localPath = message.localFilePath
            if (!localPath.isNullOrEmpty() && File(localPath).exists()) {
                _videoNoteToPlay.value = localPath
                return@launch
            }
            
            // Если нет локально - пробуем URL с сервера
            val fileUrl = message.fileUrl ?: message.encryptedContent
            if (fileUrl.isNullOrEmpty()) return@launch
            
            // Скачиваем файл
            try {
                val fullUrl = com.pioneer.messenger.data.network.ApiClient.getFileUrl(fileUrl)
                
                // Создаём директорию для кэша
                val cacheDir = File(context.cacheDir, "video_notes")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                
                val cachedFile = File(cacheDir, "${messageId}.mp4")
                
                // Скачиваем
                val url = java.net.URL(fullUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                
                connection.inputStream.use { input ->
                    cachedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    // Обновляем localFilePath в БД
                    messageDao.updateLocalFilePath(messageId, cachedFile.absolutePath)
                    _videoNoteToPlay.value = cachedFile.absolutePath
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to download video note: ${e.message}")
            }
        }
    }
    
    fun dismissVideoNote() {
        _videoNoteToPlay.value = null
    }
    
    // ========== ВИДЕОКРУЖКИ ==========
    
    fun startVideoNoteRecording() {
        _recordingState.value = RecordingState(isRecording = true, isVideo = true, duration = 0)
        startRecordingTimer()
    }
    
    fun sendVideoNote(file: File) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            try {
                val currentUser = authManager.currentUser.first() ?: return@launch
                
                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                
                // Сохраняем видео в постоянную директорию
                val videoDir = File(context.filesDir, "video_notes")
                if (!videoDir.exists()) videoDir.mkdirs()
                
                val savedFile = File(videoDir, "${messageId}.mp4")
                file.copyTo(savedFile, overwrite = true)
                file.delete()
                
                // Получаем длительность
                val durationSec = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(savedFile.absolutePath)
                    val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    (duration?.toLongOrNull() ?: 0L) / 1000
                } catch (e: Exception) {
                    5L
                }.toInt().coerceIn(1, 60)
                
                // Сохраняем локально со статусом SENDING
                messageDao.insertMessage(MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUser.id,
                    encryptedContent = "",
                    nonce = "",
                    timestamp = timestamp,
                    type = "VIDEO_NOTE",
                    status = "SENDING",
                    replyToId = null,
                    isEdited = false,
                    editedAt = null,
                    duration = durationSec,
                    fileSize = savedFile.length(),
                    localFilePath = savedFile.absolutePath
                ))
                
                // Загружаем на сервер
                val fileBytes = savedFile.readBytes()
                val uploadResult = com.pioneer.messenger.data.network.ApiClient.uploadVideoNote(fileBytes, "${messageId}.mp4")
                
                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        // Отправляем сообщение с URL файла
                        val sendResult = com.pioneer.messenger.data.network.ApiClient.sendMessage(
                            chatId = chatId,
                            encryptedContent = uploadResponse.url,
                            nonce = durationSec.toString(),
                            type = "VIDEO_NOTE"
                        )
                        
                        sendResult.fold(
                            onSuccess = { serverMessage ->
                                // Удаляем локальное сообщение и заменяем серверным
                                messageDao.deleteMessageById(messageId)
                                messageDao.insertMessage(MessageEntity(
                                    id = serverMessage.id,
                                    chatId = chatId,
                                    senderId = currentUser.id,
                                    encryptedContent = serverMessage.encryptedContent,
                                    nonce = serverMessage.nonce,
                                    timestamp = serverMessage.timestamp,
                                    type = "VIDEO_NOTE",
                                    status = "SENT",
                                    replyToId = null,
                                    isEdited = false,
                                    editedAt = null,
                                    duration = durationSec,
                                    fileSize = savedFile.length(),
                                    localFilePath = savedFile.absolutePath,
                                    fileUrl = uploadResponse.url
                                ))
                            },
                            onFailure = { messageDao.updateMessageStatus(messageId, "FAILED") }
                        )
                    },
                    onFailure = { 
                        android.util.Log.e("ChatViewModel", "Failed to upload video note: ${it.message}")
                        messageDao.updateMessageStatus(messageId, "FAILED") 
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    
    // ========== ВЛОЖЕНИЯ ==========
    
    private val _pickPhotoEvent = MutableStateFlow(false)
    val pickPhotoEvent: StateFlow<Boolean> = _pickPhotoEvent
    
    private val _pickVideoEvent = MutableStateFlow(false)
    val pickVideoEvent: StateFlow<Boolean> = _pickVideoEvent
    
    private val _pickFileEvent = MutableStateFlow(false)
    val pickFileEvent: StateFlow<Boolean> = _pickFileEvent
    
    private val _shareLocationEvent = MutableStateFlow(false)
    val shareLocationEvent: StateFlow<Boolean> = _shareLocationEvent
    
    fun pickPhoto() { 
        _pickPhotoEvent.value = true
    }
    
    fun resetPickPhoto() {
        _pickPhotoEvent.value = false
    }
    
    fun pickVideo() { 
        _pickVideoEvent.value = true
    }
    
    fun resetPickVideo() {
        _pickVideoEvent.value = false
    }
    
    fun pickFile() { 
        _pickFileEvent.value = true
    }
    
    fun resetPickFile() {
        _pickFileEvent.value = false
    }
    
    fun shareLocation() { 
        _shareLocationEvent.value = true
    }
    
    fun resetShareLocation() {
        _shareLocationEvent.value = false
    }
    
    fun shareContact() { /* TODO */ }
    
    fun sendPhoto(uri: android.net.Uri) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            try {
                val currentUser = authManager.currentUser.first() ?: return@launch
                
                // Копируем файл в постоянную директорию
                val mediaDir = File(context.filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                
                val messageId = "local_img_${System.currentTimeMillis()}_${(0..9999).random()}"
                val savedFile = File(mediaDir, "${messageId}.jpg")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    savedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Сохраняем локально со статусом SENDING
                messageDao.insertMessage(MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUser.id,
                    encryptedContent = "",
                    nonce = messageId, // Используем для отслеживания дубликатов
                    timestamp = System.currentTimeMillis(),
                    type = "IMAGE",
                    status = "SENDING",
                    replyToId = null,
                    isEdited = false,
                    editedAt = null,
                    fileSize = savedFile.length(),
                    localFilePath = savedFile.absolutePath,
                    fileUrl = null
                ))
                
                // Загружаем на сервер
                val fileBytes = savedFile.readBytes()
                android.util.Log.d("ChatViewModel", "Uploading image: ${fileBytes.size} bytes")
                
                val uploadResult = com.pioneer.messenger.data.network.ApiClient.uploadImage(fileBytes, "${messageId}.jpg")
                
                uploadResult.fold(
                    onSuccess = { uploadResponse ->
                        android.util.Log.d("ChatViewModel", "Image uploaded: ${uploadResponse.url}")
                        
                        // Отправляем сообщение на сервер
                        val sendResult = com.pioneer.messenger.data.network.ApiClient.sendMessage(
                            chatId = chatId,
                            encryptedContent = uploadResponse.url,
                            nonce = messageId, // Передаём локальный ID для отслеживания
                            type = "IMAGE"
                        )
                        
                        sendResult.fold(
                            onSuccess = { serverResponse ->
                                android.util.Log.d("ChatViewModel", "Image message sent, server id: ${serverResponse.id}")
                                // Удаляем локальное сообщение - серверное придёт через polling/WebSocket
                                messageDao.deleteMessageById(messageId)
                            },
                            onFailure = { e ->
                                android.util.Log.e("ChatViewModel", "Failed to send image message: ${e.message}")
                                messageDao.updateMessageStatus(messageId, "FAILED")
                            }
                        )
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChatViewModel", "Failed to upload image: ${e.message}")
                        messageDao.updateMessageStatus(messageId, "FAILED") 
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in sendPhoto: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun sendVideo(uri: android.net.Uri) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            try {
                val currentUser = authManager.currentUser.first() ?: return@launch
                
                val mediaDir = File(context.filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                
                val messageId = "local_vid_${System.currentTimeMillis()}_${(0..9999).random()}"
                val savedFile = File(mediaDir, "${messageId}.mp4")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    savedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Получаем длительность
                val durationSec = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    (duration?.toLongOrNull() ?: 0L) / 1000
                } catch (e: Exception) {
                    0L
                }.toInt()
                
                messageDao.insertMessage(MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUser.id,
                    encryptedContent = "",
                    nonce = messageId,
                    timestamp = System.currentTimeMillis(),
                    type = "VIDEO",
                    status = "SENDING",
                    replyToId = null,
                    isEdited = false,
                    editedAt = null,
                    duration = durationSec,
                    fileSize = savedFile.length(),
                    localFilePath = savedFile.absolutePath
                ))
                
                // TODO: Загрузка видео на сервер
                // Пока просто помечаем как отправленное локально
                messageDao.updateMessageStatus(messageId, "SENT")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun sendFile(uri: android.net.Uri) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            try {
                val currentUser = authManager.currentUser.first() ?: return@launch
                
                val mediaDir = File(context.filesDir, "files")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                
                // Получаем имя файла и MIME тип
                var fileName = "file"
                var mimeType = "application/octet-stream"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "file"
                }
                
                // Получаем MIME тип
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                
                val messageId = "local_file_${System.currentTimeMillis()}_${(0..9999).random()}"
                val savedFile = File(mediaDir, "${messageId}_$fileName")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    savedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                messageDao.insertMessage(MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUser.id,
                    encryptedContent = "",
                    nonce = messageId,
                    timestamp = System.currentTimeMillis(),
                    type = "FILE",
                    status = "SENDING",
                    replyToId = null,
                    isEdited = false,
                    editedAt = null,
                    fileName = fileName,
                    fileSize = savedFile.length(),
                    localFilePath = savedFile.absolutePath
                ))
                
                // Загрузка файла на сервер
                val fileBytes = savedFile.readBytes()
                val uploadResult = com.pioneer.messenger.data.network.ApiClient.uploadFile(
                    fileBytes, 
                    fileName,
                    mimeType
                )
                
                uploadResult.onSuccess { response ->
                    // Обновляем сообщение с URL файла
                    messageDao.updateMessage(MessageEntity(
                        id = messageId,
                        chatId = chatId,
                        senderId = currentUser.id,
                        encryptedContent = response.url,
                        nonce = messageId,
                        timestamp = System.currentTimeMillis(),
                        type = "FILE",
                        status = "SENT",
                        replyToId = null,
                        isEdited = false,
                        editedAt = null,
                        fileName = fileName,
                        fileSize = savedFile.length(),
                        localFilePath = savedFile.absolutePath
                    ))
                    
                    // Отправляем через WebSocket
                    wsClient?.sendMessage(
                        chatId = chatId,
                        content = response.url,
                        type = "FILE",
                        fileName = fileName,
                        fileSize = savedFile.length()
                    )
                }.onFailure { error ->
                    messageDao.updateMessageStatus(messageId, "FAILED")
                    error.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun sendLocation(latitude: Double, longitude: Double) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            try {
                val currentUser = authManager.currentUser.first() ?: return@launch
                val key = getOrCreateSessionKey(chatId)
                
                val locationText = "📍 Геопозиция: $latitude, $longitude"
                val encrypted = cryptoManager.encrypt(locationText.toByteArray(), key)
                
                val messageId = UUID.randomUUID().toString()
                
                messageDao.insertMessage(MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUser.id,
                    encryptedContent = encrypted.ciphertext,
                    nonce = encrypted.iv,
                    timestamp = System.currentTimeMillis(),
                    type = "LOCATION",
                    status = "SENT",
                    replyToId = null,
                    isEdited = false,
                    editedAt = null
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // ========== ЗВОНКИ ==========
    
    fun startCall() {
        viewModelScope.launch {
            // TODO: WebRTC звонок
        }
    }
    
    // ========== УДАЛЕНИЕ СООБЩЕНИЙ ==========
    
    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            try {
                val message = messageDao.getMessageById(messageId) ?: return@launch
                
                if (forEveryone) {
                    // TODO: Отправить запрос на сервер для удаления у всех
                    // ApiClient.deleteMessage(messageId)
                }
                
                // Удаляем локально
                messageDao.deleteMessage(message)
                
                // Удаляем локальный файл если есть
                message.localFilePath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to delete message: ${e.message}")
            }
        }
    }
    
    // ========== РЕАКЦИИ ==========
    
    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                val result = com.pioneer.messenger.data.network.ApiClient.addReaction(messageId, emoji)
                result.fold(
                    onSuccess = { response ->
                        android.util.Log.d("ChatViewModel", "Reaction ${response}: $emoji on $messageId")
                        // Перезагружаем сообщения чтобы обновить реакции
                        currentChatId?.let { loadMessagesFromServer(it) }
                    },
                    onFailure = { e ->
                        android.util.Log.e("ChatViewModel", "Failed to add reaction: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error adding reaction: ${e.message}")
            }
        }
    }
    
    // ========== УТИЛИТЫ ==========
    
    private suspend fun getOrCreateSessionKey(chatId: String): ByteArray {
        return sessionKey ?: run {
            val newKey = cryptoManager.generateSessionKey()
            val encryptedKey = cryptoManager.encryptSessionKey(newKey)
            
            keyDao.insertKey(SessionKeyEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                encryptedKey = encryptedKey.ciphertext,
                iv = encryptedKey.iv,
                createdAt = System.currentTimeMillis(),
                expiresAt = null
            ))
            
            sessionKey = newKey
            newKey
        }
    }
    
    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
