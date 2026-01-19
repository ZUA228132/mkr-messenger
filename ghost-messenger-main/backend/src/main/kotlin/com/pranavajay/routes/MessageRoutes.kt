package com.pranavajay.routes

import com.pranavajay.database.*
import com.pranavajay.models.*
import com.pranavajay.websocket.chatServer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.messageRoutes() {
    authenticate("auth-jwt") {
        route("/api") {
            // Get all chats for current user
            get("/chats") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val chats = ChatRepository.getChatsByUserId(userId)
                val chatResponses = chats.mapNotNull { chat ->
                    val otherUserId = chat.participants.find { it != userId } ?: return@mapNotNull null
                    val otherUser = UserRepository.findById(otherUserId) ?: return@mapNotNull null
                    
                    ChatResponse(
                        id = chat.id,
                        participantId = otherUserId,
                        participantName = otherUser.username,
                        participantImage = otherUser.profileImage,
                        lastMessage = chat.lastMessage,
                        lastMessageTime = chat.lastMessageTime,
                        unreadCount = chat.unreadCount[userId] ?: 0,
                        isOnline = chatServer.isUserOnline(otherUserId)
                    )
                }
                
                call.respond(chatResponses)
            }
            
            // Create new chat
            post("/chats") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val request = call.receive<CreateChatRequest>()
                
                // Check if chat already exists
                val existingChat = ChatRepository.findChatByUsers(userId, request.participantId)
                if (existingChat != null) {
                    val otherUser = UserRepository.findById(request.participantId)
                    call.respond(ChatResponse(
                        id = existingChat.id,
                        participantId = request.participantId,
                        participantName = otherUser?.username ?: "Unknown",
                        participantImage = otherUser?.profileImage,
                        lastMessage = existingChat.lastMessage,
                        lastMessageTime = existingChat.lastMessageTime,
                        unreadCount = existingChat.unreadCount[userId] ?: 0,
                        isOnline = chatServer.isUserOnline(request.participantId)
                    ))
                    return@post
                }
                
                val chat = ChatEntity(
                    id = UUID.randomUUID().toString(),
                    participants = listOf(userId, request.participantId)
                )
                
                ChatRepository.createChat(chat)
                
                val otherUser = UserRepository.findById(request.participantId)
                call.respond(HttpStatusCode.Created, ChatResponse(
                    id = chat.id,
                    participantId = request.participantId,
                    participantName = otherUser?.username ?: "Unknown",
                    participantImage = otherUser?.profileImage,
                    lastMessage = null,
                    lastMessageTime = chat.lastMessageTime,
                    unreadCount = 0,
                    isOnline = chatServer.isUserOnline(request.participantId)
                ))
            }
            
            // Get messages for a chat
            get("/messages/{chatId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val chatId = call.parameters["chatId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Chat ID required"))
                    return@get
                }
                
                val messages = MessageRepository.getMessagesByChatId(chatId)
                val messageResponses = messages.map { msg ->
                    Message(
                        id = msg.id,
                        chatId = msg.chatId,
                        senderId = msg.senderId,
                        receiverId = msg.receiverId,
                        content = msg.content,
                        type = msg.type,
                        mediaUrl = msg.mediaUrl,
                        timestamp = msg.timestamp
                    )
                }
                
                // Reset unread count
                ChatRepository.resetUnread(chatId, userId)
                
                call.respond(messageResponses)
            }
            
            // Send message via REST (alternative to WebSocket)
            post("/messages") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val request = call.receive<SendMessageRequest>()
                
                val message = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = request.chatId,
                    senderId = userId,
                    receiverId = request.receiverId,
                    content = request.content,
                    type = request.type,
                    mediaUrl = request.mediaUrl,
                    replyToId = request.replyToId,
                    disappearAfter = request.disappearAfter,
                    expiresAt = if (request.disappearAfter != null && request.disappearAfter > 0) 
                        System.currentTimeMillis() + request.disappearAfter else null,
                    timestamp = System.currentTimeMillis()
                )
                
                MessageRepository.saveMessage(message)
                ChatRepository.updateLastMessage(request.chatId, request.content, message.timestamp)
                ChatRepository.incrementUnread(request.chatId, request.receiverId)
                
                val response = Message(
                    id = message.id,
                    chatId = message.chatId,
                    senderId = message.senderId,
                    receiverId = message.receiverId,
                    content = message.content,
                    type = message.type,
                    mediaUrl = message.mediaUrl,
                    timestamp = message.timestamp
                )
                
                call.respond(HttpStatusCode.Created, response)
            }
            
            // Forward message
            post("/messages/forward") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val request = call.receive<ForwardMessageRequest>()
                val originalMessage = MessageRepository.getMessageById(request.messageId)
                
                if (originalMessage == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Message not found"))
                    return@post
                }
                
                val forwardedMessages = request.toChatIds.map { chatId ->
                    val chat = ChatRepository.getChatById(chatId)
                    val receiverId = chat?.participants?.find { it != userId } ?: ""
                    
                    val message = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        senderId = userId,
                        receiverId = receiverId,
                        content = originalMessage.content,
                        type = originalMessage.type,
                        mediaUrl = originalMessage.mediaUrl,
                        isForwarded = true,
                        forwardedFromId = originalMessage.id,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    MessageRepository.saveMessage(message)
                    ChatRepository.updateLastMessage(chatId, originalMessage.content, message.timestamp)
                    ChatRepository.incrementUnread(chatId, receiverId)
                    message
                }
                
                call.respond(HttpStatusCode.Created, mapOf("forwarded" to forwardedMessages.size))
            }
            
            // Delete message
            delete("/messages/{messageId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@delete
                }
                
                val messageId = call.parameters["messageId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message ID required"))
                    return@delete
                }
                
                val deleteForEveryone = call.request.queryParameters["forEveryone"]?.toBoolean() ?: false
                
                val message = MessageRepository.getMessageById(messageId)
                if (message == null || message.senderId != userId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Cannot delete this message"))
                    return@delete
                }
                
                if (deleteForEveryone) {
                    MessageRepository.deleteMessage(messageId)
                } else {
                    MessageRepository.deleteForUser(messageId, userId)
                }
                
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
            }
            
            // Set disappearing messages for chat
            put("/chats/{chatId}/disappearing") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@put
                }
                
                val chatId = call.parameters["chatId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Chat ID required"))
                    return@put
                }
                
                val request = call.receive<DisappearingSettingsRequest>()
                ChatRepository.setDisappearingMessages(chatId, request.duration)
                
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "duration" to request.duration))
            }
            
            // Toggle ghost mode for chat
            put("/chats/{chatId}/ghost-mode") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@put
                }
                
                val chatId = call.parameters["chatId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Chat ID required"))
                    return@put
                }
                
                val request = call.receive<GhostModeRequest>()
                ChatRepository.setGhostMode(chatId, request.enabled)
                
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "ghostMode" to request.enabled))
            }
            
            // Pin/Unpin chat
            put("/chats/{chatId}/pin") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@put
                }
                
                val chatId = call.parameters["chatId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Chat ID required"))
                    return@put
                }
                
                val request = call.receive<PinChatRequest>()
                ChatRepository.setPinned(chatId, request.pinned)
                
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "pinned" to request.pinned))
            }
            
            // Archive/Unarchive chat
            put("/chats/{chatId}/archive") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@put
                }
                
                val chatId = call.parameters["chatId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Chat ID required"))
                    return@put
                }
                
                val request = call.receive<ArchiveChatRequest>()
                ChatRepository.setArchived(chatId, request.archived)
                
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "archived" to request.archived))
            }
        }
    }
}
