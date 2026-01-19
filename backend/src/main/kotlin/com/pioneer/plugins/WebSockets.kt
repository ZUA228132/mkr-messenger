package com.pioneer.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

// Хранилище активных соединений
object ConnectionManager {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    
    fun addConnection(userId: String, session: WebSocketSession) {
        connections[userId] = session
        println("WebSocket: User $userId connected. Total connections: ${connections.size}")
    }
    
    fun removeConnection(userId: String) {
        connections.remove(userId)
        println("WebSocket: User $userId disconnected. Total connections: ${connections.size}")
    }
    
    suspend fun sendToUser(userId: String, message: WsMessage): Boolean {
        val session = connections[userId]
        return if (session != null) {
            try {
                session.send(Json.encodeToString(message))
                println("WebSocket: Sent message to $userId: ${message.type}")
                true
            } catch (e: Exception) {
                println("WebSocket: Failed to send to $userId: ${e.message}")
                connections.remove(userId)
                false
            }
        } else {
            println("WebSocket: User $userId not connected")
            false
        }
    }
    
    suspend fun broadcast(message: WsMessage, excludeUserId: String? = null) {
        connections.forEach { (userId, session) ->
            if (userId != excludeUserId) {
                try {
                    session.send(Json.encodeToString(message))
                } catch (e: Exception) {
                    connections.remove(userId)
                }
            }
        }
    }
    
    fun isOnline(userId: String): Boolean = connections.containsKey(userId)
    
    fun getOnlineUserIds(): Set<String> = connections.keys.toSet()
}

@Serializable
data class WsMessage(
    val type: String,
    val payload: String
)

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        webSocket("/ws/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket close(
                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No user ID")
            )
            
            ConnectionManager.addConnection(userId, this)
            
            try {
                // Уведомляем о подключении
                ConnectionManager.broadcast(
                    WsMessage("user_online", userId),
                    excludeUserId = userId
                )
                
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            handleMessage(userId, text)
                        }
                        else -> {}
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                // Connection closed
            } finally {
                ConnectionManager.removeConnection(userId)
                ConnectionManager.broadcast(
                    WsMessage("user_offline", userId)
                )
            }
        }
    }
}

private suspend fun handleMessage(senderId: String, messageJson: String) {
    try {
        val message = Json.decodeFromString<WsMessage>(messageJson)
        println("WebSocket: Received ${message.type} from $senderId")
        
        when (message.type) {
            "typing" -> {
                // Уведомление о наборе текста - пересылаем всем участникам чата
                try {
                    val data = Json.decodeFromString<TypingPayload>(message.payload)
                    // Получаем участников чата из БД
                    val participants = transaction {
                        ChatParticipants
                            .select { ChatParticipants.chatId eq data.chatId }
                            .map { it[ChatParticipants.userId] }
                    }
                    
                    // Получаем имя отправителя
                    val senderName = transaction {
                        Users.select { Users.id eq senderId }.singleOrNull()?.get(Users.displayName) ?: "Пользователь"
                    }
                    
                    // Формируем payload с именем
                    val typingPayload = """{"chatId":"${data.chatId}","userId":"$senderId","userName":"$senderName"}"""
                    val typingMessage = WsMessage("typing", typingPayload)
                    
                    // Отправляем всем кроме отправителя
                    for (participantId in participants) {
                        if (participantId != senderId) {
                            ConnectionManager.sendToUser(participantId, typingMessage)
                        }
                    }
                } catch (e: Exception) {
                    println("WebSocket: Error handling typing: ${e.message}")
                }
            }
            
            "call_offer" -> {
                // Предложение звонка
                try {
                    val data = Json.decodeFromString<CallPayload>(message.payload)
                    ConnectionManager.sendToUser(data.targetUserId, WsMessage("call_offer", message.payload))
                } catch (e: Exception) {
                    println("WebSocket: Error handling call_offer: ${e.message}")
                }
            }
            
            "call_answer" -> {
                // Ответ на звонок
                try {
                    val data = Json.decodeFromString<CallPayload>(message.payload)
                    ConnectionManager.sendToUser(data.targetUserId, WsMessage("call_answer", message.payload))
                } catch (e: Exception) {
                    println("WebSocket: Error handling call_answer: ${e.message}")
                }
            }
            
            "call_ice" -> {
                // ICE кандидат для WebRTC
                try {
                    val data = Json.decodeFromString<CallPayload>(message.payload)
                    ConnectionManager.sendToUser(data.targetUserId, WsMessage("call_ice", message.payload))
                } catch (e: Exception) {
                    println("WebSocket: Error handling call_ice: ${e.message}")
                }
            }
            
            "call_end" -> {
                // Завершение звонка
                try {
                    val data = Json.decodeFromString<CallPayload>(message.payload)
                    ConnectionManager.sendToUser(data.targetUserId, WsMessage("call_end", message.payload))
                } catch (e: Exception) {
                    println("WebSocket: Error handling call_end: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        println("WebSocket: Error parsing message: ${e.message}")
    }
}

@Serializable
data class TypingPayload(
    val chatId: String,
    val userId: String
)

@Serializable
data class CallPayload(
    val targetUserId: String,
    val sdp: String? = null,
    val candidate: String? = null
)
