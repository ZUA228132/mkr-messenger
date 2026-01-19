package com.pioneer.messenger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pioneer.messenger.R
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.ChatDao
import com.pioneer.messenger.data.local.MessageDao
import com.pioneer.messenger.data.local.MessageEntity
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MessageService : Service() {
    
    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var chatDao: ChatDao
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val json = Json { ignoreUnknownKeys = true }
    private var pollingJob: Job? = null
    
    companion object {
        private const val CHANNEL_ID = "pioneer_messages"
        private const val CALL_CHANNEL_ID = "pioneer_calls"
        private const val NOTIFICATION_ID = 2001
        // Используем тот же ID что и в IncomingCallActivity
        const val CALL_NOTIFICATION_ID = 2002
        private const val POLL_INTERVAL_MS = 10000L // 10 секунд fallback polling
        
        fun start(context: Context) {
            val intent = Intent(context, MessageService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, MessageService::class.java))
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MessageService", "Service onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        connectWebSocket()
        startPolling() // Fallback polling для надёжности
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service stopped")
        pollingJob?.cancel()
        scope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Сообщения Pioneer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая синхронизация сообщений"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Pioneer")
        .setContentText("Синхронизация сообщений...")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
    
    private fun connectWebSocket() {
        scope.launch {
            android.util.Log.d("MessageService", "connectWebSocket: starting...")
            val user = authManager.currentUser.first()
            if (user == null) {
                android.util.Log.e("MessageService", "connectWebSocket: user is null, cannot connect")
                return@launch
            }
            val userId = user.id
            android.util.Log.d("MessageService", "connectWebSocket: userId = $userId")
            
            // Trust all certs для самоподписанного сертификата
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
            
            val request = Request.Builder()
                .url("${ApiClient.WS_URL}/$userId")
                .build()
            
            android.util.Log.d("MessageService", "Connecting to WebSocket: ${ApiClient.WS_URL}/$userId")
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    android.util.Log.d("MessageService", "WebSocket connected for user: $userId")
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    android.util.Log.e("MessageService", "WebSocket error: ${t.message}")
                    // Переподключение через 5 секунд
                    scope.launch {
                        delay(5000)
                        connectWebSocket()
                    }
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    android.util.Log.d("MessageService", "WebSocket closed: $reason")
                }
            })
        }
    }
    
    private fun handleMessage(text: String) {
        scope.launch {
            try {
                android.util.Log.d("MessageService", "Received WS message: $text")
                val wsMessage = json.decodeFromString<WsMessage>(text)
                
                when (wsMessage.type) {
                    "new_message" -> {
                        android.util.Log.d("MessageService", "New message received: ${wsMessage.payload}")
                        
                        try {
                            val messageData = json.decodeFromString<NewMessageData>(wsMessage.payload)
                            
                            // Проверяем, не наше ли это сообщение
                            val currentUser = authManager.currentUser.first()
                            if (currentUser != null && messageData.senderId == currentUser.id) {
                                android.util.Log.d("MessageService", "Skipping own message ${messageData.id}")
                                // Удаляем локальное сообщение со статусом SENDING если есть
                                val localMessages = messageDao.getRecentMessagesBySender(
                                    chatId = messageData.chatId,
                                    senderId = currentUser.id,
                                    since = messageData.timestamp - 10000
                                )
                                localMessages.filter { it.status == "SENDING" }.forEach {
                                    messageDao.deleteMessageById(it.id)
                                }
                                return@launch
                            }
                            
                            // Проверяем, не существует ли уже сообщение с таким ID
                            val existingMessage = messageDao.getMessageById(messageData.id)
                            if (existingMessage != null) {
                                android.util.Log.d("MessageService", "Message ${messageData.id} already exists, skipping")
                                return@launch
                            }
                            
                            // Проверяем существует ли чат, если нет - создаём
                            val existingChat = chatDao.getChatById(messageData.chatId)
                            if (existingChat == null) {
                                android.util.Log.d("MessageService", "Chat ${messageData.chatId} not found, creating...")
                                // Создаём чат локально
                                chatDao.insertChat(
                                    com.pioneer.messenger.data.local.ChatEntity(
                                        id = messageData.chatId,
                                        type = "PRIVATE",
                                        name = messageData.senderName.ifEmpty { "Новый чат" },
                                        description = null,
                                        avatarUrl = null,
                                        participants = messageData.senderId,
                                        admins = "",
                                        createdAt = System.currentTimeMillis(),
                                        encryptionKeyId = java.util.UUID.randomUUID().toString(),
                                        isPinned = false,
                                        isMuted = false,
                                        unreadCount = 1,
                                        autoDeleteDays = null
                                    )
                                )
                                android.util.Log.d("MessageService", "Chat ${messageData.chatId} created for ${messageData.senderName}")
                            } else {
                                // Увеличиваем счётчик непрочитанных
                                chatDao.updateChat(existingChat.copy(unreadCount = existingChat.unreadCount + 1))
                            }
                            
                            // Вставляем новое сообщение в Room
                            messageDao.insertMessage(
                                com.pioneer.messenger.data.local.MessageEntity(
                                    id = messageData.id,
                                    chatId = messageData.chatId,
                                    senderId = messageData.senderId,
                                    encryptedContent = messageData.content,
                                    nonce = messageData.nonce,
                                    timestamp = messageData.timestamp,
                                    type = messageData.type,
                                    status = messageData.status,
                                    replyToId = null,
                                    isEdited = false,
                                    editedAt = null
                                )
                            )
                            
                            android.util.Log.d("MessageService", "Message inserted into Room: ${messageData.id}")
                            
                            // Уведомляем UI
                            com.pioneer.messenger.data.network.RealtimeEvents.tryEmitNewMessage(messageData.id)
                            
                            // Показываем уведомление
                            showMessageNotification(messageData.senderName.ifEmpty { "Новое сообщение" }, messageData.content.take(50))
                            
                        } catch (e: Exception) {
                            android.util.Log.e("MessageService", "Error parsing message: ${e.message}")
                        }
                    }
                    "typing" -> {
                        // Отправляем через RealtimeEvents
                        try {
                            val typingData = json.decodeFromString<TypingData>(wsMessage.payload)
                            android.util.Log.d("MessageService", "Typing from ${typingData.userName} in chat ${typingData.chatId}")
                            com.pioneer.messenger.data.network.RealtimeEvents.tryEmitTyping(
                                typingData.chatId,
                                typingData.userId,
                                typingData.userName
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("MessageService", "Error parsing typing: ${e.message}")
                        }
                    }
                    "user_online" -> {
                        android.util.Log.d("MessageService", "User online: ${wsMessage.payload}")
                        com.pioneer.messenger.data.network.RealtimeEvents.tryEmitUserStatus(wsMessage.payload, true)
                    }
                    "user_offline" -> {
                        android.util.Log.d("MessageService", "User offline: ${wsMessage.payload}")
                        com.pioneer.messenger.data.network.RealtimeEvents.tryEmitUserStatus(wsMessage.payload, false)
                    }
                    "reaction_update" -> {
                        android.util.Log.d("MessageService", "Reaction update: ${wsMessage.payload}")
                        com.pioneer.messenger.data.network.RealtimeEvents.tryEmitNewMessage(wsMessage.payload)
                    }
                    "incoming_call" -> {
                        // Входящий звонок через WebSocket
                        handleIncomingCall(wsMessage.payload)
                    }
                    "call_ended", "call_rejected" -> {
                        // Звонок завершён
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager.cancel(CALL_NOTIFICATION_ID)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageService", "Error handling message: ${e.message}")
            }
        }
    }
    
    private fun handleIncomingCall(payload: String) {
        try {
            android.util.Log.d("MessageService", "Incoming call payload: $payload")
            
            // Парсим данные звонка
            val callData = json.decodeFromString<IncomingCallData>(payload)
            
            android.util.Log.d("MessageService", "Incoming call from ${callData.callerName} (${callData.callerId})")
            
            // Запускаем IncomingCallActivity
            val intent = Intent(this, com.pioneer.messenger.ui.calls.IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("callId", callData.callId)
                putExtra("callerId", callData.callerId)
                putExtra("callerName", callData.callerName)
                putExtra("callerAvatar", callData.callerAvatar)
                putExtra("isVideo", callData.isVideoCall())
                putExtra("sdp", callData.sdp ?: "")
            }
            startActivity(intent)
            
            // Показываем уведомление для lock screen
            showIncomingCallNotification(callData)
            
        } catch (e: Exception) {
            android.util.Log.e("MessageService", "Error handling incoming call: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun showIncomingCallNotification(callData: IncomingCallData) {
        // Создаём канал для звонков
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Входящие звонки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих звонках"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(callChannel)
        }
        
        // Full screen intent
        val fullScreenIntent = Intent(this, com.pioneer.messenger.ui.calls.IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("callId", callData.callId)
            putExtra("callerId", callData.callerId)
            putExtra("callerName", callData.callerName)
            putExtra("callerAvatar", callData.callerAvatar)
            putExtra("isVideo", callData.isVideoCall())
            putExtra("sdp", callData.sdp ?: "")
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val callType = if (callData.isVideoCall()) "Видеозвонок" else "Аудиозвонок"
        
        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callType)
            .setContentText("Входящий звонок от ${callData.callerName}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(CALL_NOTIFICATION_ID, notification)
    }
    
    private suspend fun loadNewMessage(messageId: String) {
        showMessageNotification("Новое сообщение", "У вас новое сообщение")
    }
    
    private fun startPolling() {
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                try {
                    // Отправляем broadcast для обновления UI
                    val intent = Intent("com.pioneer.messenger.NEW_MESSAGE")
                    intent.putExtra("poll", true)
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MessageService", "Polling error: ${e.message}")
                }
            }
        }
    }
    
    private fun showMessageNotification(title: String, text: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    @Serializable
    data class WsMessage(
        val type: String,
        val payload: String
    )
    
    @Serializable
    data class TypingData(
        val chatId: String,
        val userId: String,
        val userName: String = ""
    )
    
    @Serializable
    data class NewMessageData(
        val id: String,
        val chatId: String,
        val senderId: String,
        val senderName: String = "",
        val content: String,
        val nonce: String = "",
        val timestamp: Long,
        val type: String = "TEXT",
        val status: String = "SENT"
    )
    
    @Serializable
    data class IncomingCallData(
        val callId: String,
        val callerId: String,
        val callerName: String = "Неизвестный",
        val callerAvatar: String? = null,
        val isVideo: String = "false", // Приходит как строка
        val sdp: String? = null
    ) {
        fun isVideoCall(): Boolean = isVideo == "true"
    }
}
