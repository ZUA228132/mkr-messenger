package com.pranavajay.websocket

import com.pranavajay.database.ChatRepository
import com.pranavajay.database.MessageRepository
import com.pranavajay.database.UserRepository
import com.pranavajay.models.MessageEntity
import com.pranavajay.utils.JwtConfig
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SocketMessage(
    val type: String,
    val data: String
)

@Serializable
data class ChatMessage(
    val chatId: String,
    val receiverId: String,
    val content: String,
    val messageType: String = "TEXT",
    val mediaUrl: String? = null,
    val replyToId: String? = null
)

@Serializable
data class TypingEvent(
    val chatId: String,
    val userId: String,
    val isTyping: Boolean
)

@Serializable
data class ReadReceipt(
    val chatId: String,
    val messageId: String
)

@Serializable
data class CallEvent(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val receiverId: String,
    val callType: String,
    val action: String
)

@Serializable
data class ReactionEvent(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val emoji: String,
    val action: String
)

class ChatServer {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun onConnect(session: WebSocketSession, token: String) {
        val userId = JwtConfig.getUserIdFromToken(token)
        if (userId != null) {
            connections[userId] = session
            UserRepository.setOnlineStatus(userId, true)
            broadcastOnlineStatus(userId, true)
        }
    }
    
    suspend fun onDisconnect(userId: String) {
        connections.remove(userId)
        UserRepository.setOnlineStatus(userId, false)
        broadcastOnlineStatus(userId, false)
    }
    
    suspend fun handleMessage(userId: String, text: String) {
        try {
            val socketMessage = json.decodeFromString<SocketMessage>(text)
            
            when (socketMessage.type) {
                "message" -> handleChatMessage(userId, socketMessage.data)
                "typing" -> handleTyping(userId, socketMessage.data)
                "read" -> handleReadReceipt(userId, socketMessage.data)
                "call" -> handleCallEvent(userId, socketMessage.data)
                "reaction" -> handleReactionEvent(userId, socketMessage.data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun handleChatMessage(senderId: String, data: String) {
        val chatMessage = json.decodeFromString<ChatMessage>(data)
        
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatMessage.chatId,
            senderId = senderId,
            receiverId = chatMessage.receiverId,
            content = chatMessage.content,
            type = chatMessage.messageType,
            mediaUrl = chatMessage.mediaUrl,
            replyToId = chatMessage.replyToId,
            timestamp = System.currentTimeMillis()
        )
        
        MessageRepository.saveMessage(message)
        ChatRepository.updateLastMessage(chatMessage.chatId, chatMessage.content, message.timestamp)
        ChatRepository.incrementUnread(chatMessage.chatId, chatMessage.receiverId)
        
        // Send to receiver if online
        sendToUser(chatMessage.receiverId, SocketMessage("message", json.encodeToString(message)))
        
        // Send confirmation to sender
        val deliveredMessage = message.copy(isDelivered = connections.containsKey(chatMessage.receiverId))
        sendToUser(senderId, SocketMessage("message_sent", json.encodeToString(deliveredMessage)))
    }
    
    private suspend fun handleTyping(userId: String, data: String) {
        val typing = json.decodeFromString<TypingEvent>(data)
        val chat = ChatRepository.getChatsByUserId(userId).find { it.id == typing.chatId }
        
        chat?.participants?.filter { it != userId }?.forEach { participantId ->
            sendToUser(participantId, SocketMessage("typing", json.encodeToString(
                TypingEvent(typing.chatId, userId, typing.isTyping)
            )))
        }
    }
    
    private suspend fun handleReadReceipt(userId: String, data: String) {
        val receipt = json.decodeFromString<ReadReceipt>(data)
        MessageRepository.markAsRead(receipt.messageId)
        ChatRepository.resetUnread(receipt.chatId, userId)
        
        // Notify sender
        val chat = ChatRepository.getChatsByUserId(userId).find { it.id == receipt.chatId }
        chat?.participants?.filter { it != userId }?.forEach { participantId ->
            sendToUser(participantId, SocketMessage("read", data))
        }
    }
    
    private suspend fun sendToUser(userId: String, message: SocketMessage) {
        connections[userId]?.send(Frame.Text(json.encodeToString(message)))
    }
    
    private suspend fun broadcastOnlineStatus(userId: String, isOnline: Boolean) {
        val chats = ChatRepository.getChatsByUserId(userId)
        val contactIds = chats.flatMap { it.participants }.filter { it != userId }.distinct()
        
        val statusMessage = SocketMessage("online_status", json.encodeToString(
            mapOf("userId" to userId, "isOnline" to isOnline)
        ))
        
        contactIds.forEach { contactId ->
            sendToUser(contactId, statusMessage)
        }
    }
    
    fun isUserOnline(userId: String): Boolean = connections.containsKey(userId)
    
    // Call notification methods
    private suspend fun handleCallEvent(userId: String, data: String) {
        val callEvent = json.decodeFromString<CallEvent>(data)
        when (callEvent.action) {
            "initiate" -> notifyIncomingCall(callEvent)
            "accept" -> notifyCallAccepted(callEvent)
            "decline" -> notifyCallDeclined(callEvent)
            "end" -> notifyCallEnded(callEvent)
        }
    }
    
    suspend fun notifyIncomingCall(callEvent: CallEvent) {
        sendToUser(callEvent.receiverId, SocketMessage("incoming_call", json.encodeToString(callEvent)))
    }
    
    suspend fun notifyCallAccepted(callEvent: CallEvent) {
        sendToUser(callEvent.callerId, SocketMessage("call_accepted", json.encodeToString(callEvent)))
    }
    
    suspend fun notifyCallDeclined(callEvent: CallEvent) {
        sendToUser(callEvent.callerId, SocketMessage("call_declined", json.encodeToString(callEvent)))
    }
    
    suspend fun notifyCallEnded(callEvent: CallEvent) {
        sendToUser(callEvent.callerId, SocketMessage("call_ended", json.encodeToString(callEvent)))
        sendToUser(callEvent.receiverId, SocketMessage("call_ended", json.encodeToString(callEvent)))
    }
    
    // Reaction notification methods
    private suspend fun handleReactionEvent(userId: String, data: String) {
        val reactionEvent = json.decodeFromString<ReactionEvent>(data)
        notifyReaction(reactionEvent)
    }
    
    suspend fun notifyReaction(reactionEvent: ReactionEvent) {
        val chat = ChatRepository.getChatsByUserId(reactionEvent.userId).find { it.id == reactionEvent.chatId }
        chat?.participants?.filter { it != reactionEvent.userId }?.forEach { participantId ->
            sendToUser(participantId, SocketMessage("reaction", json.encodeToString(reactionEvent)))
        }
    }
}

val chatServer = ChatServer()
