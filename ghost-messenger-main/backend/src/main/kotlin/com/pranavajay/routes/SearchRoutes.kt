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

fun Route.searchRoutes() {
    authenticate("auth-jwt") {
        route("/api/search") {
            // Global search
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val request = call.receive<SearchRequest>()

                if (request.query.length < 2) {
                    call.respond(SearchResult(emptyList(), emptyList(), emptyList()))
                    return@post
                }

                // Search messages
                val messages = MessageRepository.searchMessages(request.query, request.chatId)

                // Search users
                val users = UserRepository.searchUsers(request.query)
                    .filter { it.id != userId }
                    .map { entity ->
                        User(
                            id = entity.id,
                            username = entity.username,
                            email = entity.email,
                            phone = entity.phone,
                            profileImage = entity.profileImage,
                            bio = entity.bio,
                            isOnline = entity.isOnline,
                            lastSeen = entity.lastSeen
                        )
                    }

                // Search chats
                val chats = ChatRepository.getChatsByUserId(userId)
                val chatResponses = chats.mapNotNull { chat ->
                    val otherUserId = chat.participants.find { it != userId } ?: return@mapNotNull null
                    val otherUser = UserRepository.findById(otherUserId) ?: return@mapNotNull null

                    if (!otherUser.username.contains(request.query, ignoreCase = true)) {
                        return@mapNotNull null
                    }

                    ChatResponse(
                        id = chat.id,
                        participantId = otherUserId,
                        participantName = otherUser.username,
                        participantImage = otherUser.profileImage,
                        lastMessage = chat.lastMessage,
                        lastMessageTime = chat.lastMessageTime,
                        lastMessageType = chat.lastMessageType,
                        unreadCount = chat.unreadCount[userId] ?: 0,
                        isOnline = chatServer.isUserOnline(otherUserId),
                        disappearingMode = chat.disappearingMode,
                        disappearAfter = chat.disappearAfter,
                        isPinned = chat.isPinned[userId] ?: false,
                        isMuted = chat.isMuted[userId] ?: false
                    )
                }

                call.respond(SearchResult(messages, users, chatResponses))
            }

            // Search in specific chat
            get("/chat/{chatId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }

                val chatId = call.parameters["chatId"] ?: return@get
                val query = call.request.queryParameters["q"] ?: ""

                if (query.length < 2) {
                    call.respond(emptyList<MessageEntity>())
                    return@get
                }

                val messages = MessageRepository.searchMessages(query, chatId)
                call.respond(messages)
            }
        }
    }
}
