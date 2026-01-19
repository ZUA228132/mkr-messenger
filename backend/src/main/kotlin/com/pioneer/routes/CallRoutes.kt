package com.pioneer.routes

import com.pioneer.plugins.*
import com.pioneer.service.FcmService
import com.pioneer.service.LiveKitService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * LiveKit + WebRTC Signaling Server для аудио/видео звонков
 */

// === LiveKit API ===

@Serializable
data class CallTokenRequest(
    val calleeId: String,
    val isVideo: Boolean = false
)

@Serializable
data class CallTokenResponse(
    val token: String,
    val roomName: String,
    val callId: String
)

@Serializable
data class EndCallRequest(
    val roomName: String,
    val duration: Int = 0,
    val status: String = "ended" // ended, missed, declined
)

@Serializable
data class CallHistoryResponse(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val callerName: String,
    val calleeName: String,
    val isVideo: Boolean,
    val status: String,
    val startedAt: Long,
    val duration: Int
)

fun Route.liveKitRoutes() {
    route("/api/calls") {
        authenticate("auth-jwt") {
            // Получить токен для начала звонка
            post("/token") {
                val principal = call.principal<JWTPrincipal>()
                val callerId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CallTokenRequest>()
                val calleeId = request.calleeId
                
                println("CALL: $callerId -> $calleeId (video: ${request.isVideo})")
                
                // Получаем имя звонящего
                val callerName = transaction {
                    Users.select { Users.id eq callerId }.singleOrNull()?.get(Users.displayName) ?: "Пользователь"
                }
                
                // Генерируем имя комнаты и токен
                val roomName = LiveKitService.generateRoomName(callerId, calleeId)
                val token = LiveKitService.generateToken(
                    roomName = roomName,
                    participantName = callerName,
                    participantIdentity = callerId
                )
                
                val callerAvatar = transaction {
                    Users.select { Users.id eq callerId }.singleOrNull()?.get(Users.avatarUrl)
                }
                
                // Находим или создаём чат между участниками
                val chatId = findOrCreateDirectChat(callerId, calleeId)
                
                // Записываем в историю звонков
                val callHistoryId = UUID.randomUUID().toString()
                transaction {
                    CallHistory.insert {
                        it[id] = callHistoryId
                        it[CallHistory.callerId] = callerId
                        it[CallHistory.calleeId] = calleeId
                        it[CallHistory.chatId] = chatId
                        it[CallHistory.roomName] = roomName
                        it[isVideo] = request.isVideo
                        it[status] = "initiated"
                        it[startedAt] = System.currentTimeMillis()
                    }
                }
                
                // Получаем ВСЕ FCM токены получателя
                val calleeTokens = transaction {
                    DeviceTokens.select { DeviceTokens.userId eq calleeId }.map { row ->
                        row[DeviceTokens.fcmToken]
                    }
                }
                
                val legacyToken = transaction {
                    Users.select { Users.id eq calleeId }.singleOrNull()?.get(Users.fcmToken)
                }
                
                val allTokens = (calleeTokens + listOfNotNull(legacyToken)).distinct()
                
                // Отправляем через WebSocket
                val incomingCallPayload = Json.encodeToString(mapOf(
                    "callId" to roomName,
                    "callerId" to callerId,
                    "callerName" to callerName,
                    "callerAvatar" to (callerAvatar ?: ""),
                    "isVideo" to request.isVideo.toString()
                ))
                
                val sentViaWs = kotlinx.coroutines.runBlocking {
                    ConnectionManager.sendToUser(calleeId, WsMessage("incoming_call", incomingCallPayload))
                }
                
                if (allTokens.isEmpty() && !sentViaWs) {
                    // Обновляем статус на missed
                    transaction {
                        CallHistory.update({ CallHistory.id eq callHistoryId }) {
                            it[status] = "missed"
                            it[endedAt] = System.currentTimeMillis()
                        }
                    }
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "USER_OFFLINE_NO_FCM",
                        "message" to "Пользователь недоступен для звонков"
                    ))
                    return@post
                }
                
                kotlinx.coroutines.GlobalScope.launch {
                    allTokens.forEach { fcmToken ->
                        FcmService.sendIncomingCallNotification(
                            fcmToken = fcmToken,
                            callId = roomName,
                            callerId = callerId,
                            callerName = callerName,
                            callerAvatar = callerAvatar,
                            isVideo = request.isVideo,
                            sdp = ""
                        )
                    }
                }
                
                call.respond(CallTokenResponse(
                    token = token,
                    roomName = roomName,
                    callId = callHistoryId
                ))
            }
            
            // Получить токен для принятия звонка
            post("/join/{roomName}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val roomName = call.parameters["roomName"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val userName = transaction {
                    Users.select { Users.id eq userId }.singleOrNull()?.get(Users.displayName) ?: "Пользователь"
                }
                
                val token = LiveKitService.generateToken(
                    roomName = roomName,
                    participantName = userName,
                    participantIdentity = userId
                )
                
                // Обновляем статус звонка на answered
                transaction {
                    CallHistory.update({ CallHistory.roomName eq roomName }) {
                        it[status] = "answered"
                        it[answeredAt] = System.currentTimeMillis()
                    }
                }
                
                call.respond(mapOf("token" to token))
            }
            
            // Завершить звонок и записать в историю
            post("/end") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<EndCallRequest>()
                val now = System.currentTimeMillis()
                
                // Получаем информацию о звонке
                val callInfo = transaction {
                    CallHistory.select { CallHistory.roomName eq request.roomName }.singleOrNull()
                }
                
                if (callInfo != null) {
                    val callerId = callInfo[CallHistory.callerId]
                    val calleeId = callInfo[CallHistory.calleeId]
                    val chatId = callInfo[CallHistory.chatId]
                    val isVideo = callInfo[CallHistory.isVideo]
                    val startedAt = callInfo[CallHistory.startedAt]
                    val answeredAt = callInfo[CallHistory.answeredAt]
                    
                    // Вычисляем длительность
                    val duration = if (answeredAt != null) {
                        ((now - answeredAt) / 1000).toInt()
                    } else {
                        request.duration
                    }
                    
                    // Определяем статус
                    val finalStatus = when {
                        request.status == "declined" -> "declined"
                        answeredAt == null -> "missed"
                        else -> "ended"
                    }
                    
                    // Обновляем запись
                    transaction {
                        CallHistory.update({ CallHistory.roomName eq request.roomName }) {
                            it[status] = finalStatus
                            it[endedAt] = now
                            it[CallHistory.duration] = duration
                        }
                    }
                    
                    // Отправляем сообщение в чат о звонке
                    if (chatId != null) {
                        val messageContent = when (finalStatus) {
                            "missed" -> if (isVideo) "📹 Пропущенный видеозвонок" else "📞 Пропущенный звонок"
                            "declined" -> if (isVideo) "📹 Отклонённый видеозвонок" else "📞 Отклонённый звонок"
                            else -> {
                                val mins = duration / 60
                                val secs = duration % 60
                                val durationStr = if (mins > 0) "${mins} мин ${secs} сек" else "${secs} сек"
                                if (isVideo) "📹 Видеозвонок ($durationStr)" else "📞 Звонок ($durationStr)"
                            }
                        }
                        
                        val messageId = UUID.randomUUID().toString()
                        transaction {
                            Messages.insert {
                                it[id] = messageId
                                it[Messages.chatId] = chatId
                                it[senderId] = userId
                                it[encryptedContent] = messageContent
                                it[nonce] = ""
                                it[timestamp] = now
                                it[type] = "CALL"
                                it[Messages.status] = "SENT"
                            }
                        }
                        
                        // Уведомляем участников через WebSocket
                        val wsMessage = WsMessage("new_message", messageId)
                        kotlinx.coroutines.runBlocking {
                            ConnectionManager.sendToUser(callerId, wsMessage)
                            ConnectionManager.sendToUser(calleeId, wsMessage)
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
            
            // Получить историю звонков
            get("/history") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val history = transaction {
                    CallHistory.select { 
                        (CallHistory.callerId eq userId) or (CallHistory.calleeId eq userId)
                    }
                    .orderBy(CallHistory.startedAt, SortOrder.DESC)
                    .limit(50)
                    .map { row ->
                        val callerId = row[CallHistory.callerId]
                        val calleeId = row[CallHistory.calleeId]
                        
                        val callerName = Users.select { Users.id eq callerId }.singleOrNull()?.get(Users.displayName) ?: "Пользователь"
                        val calleeName = Users.select { Users.id eq calleeId }.singleOrNull()?.get(Users.displayName) ?: "Пользователь"
                        
                        CallHistoryResponse(
                            id = row[CallHistory.id],
                            callerId = callerId,
                            calleeId = calleeId,
                            callerName = callerName,
                            calleeName = calleeName,
                            isVideo = row[CallHistory.isVideo],
                            status = row[CallHistory.status],
                            startedAt = row[CallHistory.startedAt],
                            duration = row[CallHistory.duration]
                        )
                    }
                }
                
                call.respond(history)
            }
        }
    }
}

// Вспомогательная функция для поиска/создания direct чата
private fun findOrCreateDirectChat(userId1: String, userId2: String): String? {
    return transaction {
        val participants = listOf(userId1, userId2).sorted()
        
        // Ищем существующий direct чат
        val userChats = (Chats innerJoin ChatParticipants)
            .select { ChatParticipants.userId eq userId1 }
            .filter { it[Chats.type] == "direct" }
            .map { it[Chats.id] }
        
        val existingChatId = userChats.firstOrNull { chatId ->
            val chatParticipants = ChatParticipants
                .select { ChatParticipants.chatId eq chatId }
                .map { it[ChatParticipants.userId] }
                .sorted()
            chatParticipants == participants
        }
        
        existingChatId
    }
}

// === Legacy WebRTC Signaling (для совместимости) ===

// Активные звонки
data class CallSession(
    val callId: String,
    val callerId: String,
    val calleeId: String,
    val isVideo: Boolean,
    var callerSocket: WebSocketSession? = null,
    var calleeSocket: WebSocketSession? = null,
    var status: CallStatus = CallStatus.RINGING
)

enum class CallStatus {
    RINGING,
    ACCEPTED,
    REJECTED,
    ENDED
}

// Сообщения сигнализации
@Serializable
sealed class SignalingMessage {
    abstract val callId: String
}

@Serializable
data class CallOffer(
    override val callId: String,
    val callerId: String,
    val calleeId: String,
    val isVideo: String, // Приходит как строка "true"/"false"
    val sdp: String
) : SignalingMessage() {
    fun isVideoCall(): Boolean = isVideo == "true"
}

@Serializable
data class CallAnswer(
    override val callId: String,
    val sdp: String
) : SignalingMessage()

@Serializable
data class IceCandidate(
    override val callId: String,
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?
) : SignalingMessage()

@Serializable
data class CallControl(
    override val callId: String,
    val action: String // "accept", "reject", "end", "mute", "unmute"
) : SignalingMessage()

@Serializable
data class CallEvent(
    val type: String,
    val callId: String,
    val data: String? = null
)

object CallManager {
    private val activeCalls = ConcurrentHashMap<String, CallSession>()
    private val userSockets = ConcurrentHashMap<String, WebSocketSession>()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    fun registerUser(userId: String, socket: WebSocketSession) {
        userSockets[userId] = socket
    }
    
    fun unregisterUser(userId: String) {
        userSockets.remove(userId)
        // Завершаем все звонки пользователя
        activeCalls.values
            .filter { it.callerId == userId || it.calleeId == userId }
            .forEach { 
                it.status = CallStatus.ENDED
                activeCalls.remove(it.callId)
            }
    }
    
    suspend fun initiateCall(
        callId: String,
        callerId: String,
        calleeId: String,
        isVideo: Boolean,
        sdp: String
    ): Boolean {
        val callerSocket = userSockets[callerId] ?: return false
        val calleeSocket = userSockets[calleeId]
        
        val session = CallSession(
            callId = callId,
            callerId = callerId,
            calleeId = calleeId,
            isVideo = isVideo,
            callerSocket = callerSocket
        )
        activeCalls[callId] = session
        
        // Получаем информацию о звонящем для уведомления
        val callerInfo = transaction {
            Users.select { Users.id eq callerId }.singleOrNull()?.let {
                Triple(it[Users.displayName], it[Users.avatarUrl], it[Users.fcmToken])
            }
        }
        
        // Отправляем входящий звонок получателю через WebSocket
        if (calleeSocket != null) {
            val event = CallEvent(
                type = "incoming_call",
                callId = callId,
                data = json.encodeToString(mapOf(
                    "callerId" to callerId,
                    "isVideo" to isVideo.toString(),
                    "sdp" to sdp
                ))
            )
            calleeSocket.send(Frame.Text(json.encodeToString(event)))
        } else {
            // Пользователь офлайн - отправляем FCM push уведомление
            val calleeFcmToken = transaction {
                Users.select { Users.id eq calleeId }.singleOrNull()?.get(Users.fcmToken)
            }
            
            if (calleeFcmToken != null && callerInfo != null) {
                kotlinx.coroutines.GlobalScope.launch {
                    FcmService.sendIncomingCallNotification(
                        fcmToken = calleeFcmToken,
                        callId = callId,
                        callerId = callerId,
                        callerName = callerInfo.first,
                        callerAvatar = callerInfo.second,
                        isVideo = isVideo,
                        sdp = sdp
                    )
                }
            } else {
                // Нет FCM токена и нет WebSocket - звонок невозможен
                activeCalls.remove(callId)
                return false
            }
        }
        
        return true
    }
    
    suspend fun acceptCall(callId: String, sdp: String): Boolean {
        val session = activeCalls[callId] ?: return false
        session.status = CallStatus.ACCEPTED
        
        // Отправляем ответ звонящему
        session.callerSocket?.let {
            val event = CallEvent(
                type = "call_accepted",
                callId = callId,
                data = sdp
            )
            it.send(Frame.Text(json.encodeToString(event)))
        }
        
        return true
    }
    
    suspend fun rejectCall(callId: String): Boolean {
        val session = activeCalls[callId] ?: return false
        session.status = CallStatus.REJECTED
        
        // Уведомляем звонящего
        session.callerSocket?.let {
            val event = CallEvent(
                type = "call_rejected",
                callId = callId
            )
            it.send(Frame.Text(json.encodeToString(event)))
        }
        
        activeCalls.remove(callId)
        return true
    }
    
    suspend fun endCall(callId: String): Boolean {
        val session = activeCalls[callId] ?: return false
        session.status = CallStatus.ENDED
        
        val event = CallEvent(
            type = "call_ended",
            callId = callId
        )
        val eventJson = json.encodeToString(event)
        
        // Уведомляем обоих участников
        session.callerSocket?.send(Frame.Text(eventJson))
        session.calleeSocket?.send(Frame.Text(eventJson))
        
        activeCalls.remove(callId)
        return true
    }
    
    suspend fun sendIceCandidate(callId: String, fromUserId: String, candidate: IceCandidate) {
        val session = activeCalls[callId] ?: return
        
        val event = CallEvent(
            type = "ice_candidate",
            callId = callId,
            data = json.encodeToString(candidate)
        )
        val eventJson = json.encodeToString(event)
        
        // Отправляем другому участнику
        if (fromUserId == session.callerId) {
            session.calleeSocket?.send(Frame.Text(eventJson))
        } else {
            session.callerSocket?.send(Frame.Text(eventJson))
        }
    }
    
    fun setCalleeSocket(callId: String, socket: WebSocketSession) {
        activeCalls[callId]?.calleeSocket = socket
    }
    
    fun getCall(callId: String) = activeCalls[callId]
}

fun Route.callRoutes() {
    val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // WebSocket для сигнализации звонков
    webSocket("/call/{userId}") {
        val userId = call.parameters["userId"] ?: return@webSocket close(
            CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing userId")
        )
        
        CallManager.registerUser(userId, this)
        
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        handleSignalingMessage(userId, text, json)
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            // Соединение закрыто
        } finally {
            CallManager.unregisterUser(userId)
        }
    }
}

private suspend fun WebSocketSession.handleSignalingMessage(
    userId: String,
    message: String,
    json: Json
) {
    try {
        // Парсим тип сообщения
        val baseMessage = json.decodeFromString<Map<String, String>>(message)
        val type = baseMessage["type"] ?: return
        
        when (type) {
            "offer" -> {
                val offer = json.decodeFromString<CallOffer>(message)
                CallManager.initiateCall(
                    callId = offer.callId,
                    callerId = offer.callerId,
                    calleeId = offer.calleeId,
                    isVideo = offer.isVideoCall(),
                    sdp = offer.sdp
                )
            }
            
            "answer" -> {
                val answer = json.decodeFromString<CallAnswer>(message)
                CallManager.setCalleeSocket(answer.callId, this)
                CallManager.acceptCall(answer.callId, answer.sdp)
            }
            
            "ice_candidate" -> {
                val candidate = json.decodeFromString<IceCandidate>(message)
                CallManager.sendIceCandidate(candidate.callId, userId, candidate)
            }
            
            "reject" -> {
                val callId = baseMessage["callId"] ?: return
                CallManager.rejectCall(callId)
            }
            
            "end" -> {
                val callId = baseMessage["callId"] ?: return
                CallManager.endCall(callId)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
