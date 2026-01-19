package com.pranavajay.routes

import com.pranavajay.database.MessageRepository
import com.pranavajay.models.AddReactionRequest
import com.pranavajay.models.RemoveReactionRequest
import com.pranavajay.websocket.chatServer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reactionRoutes() {
    authenticate("auth-jwt") {
        route("/api/reactions") {
            // Add reaction to message
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val request = call.receive<AddReactionRequest>()

                MessageRepository.addReaction(request.messageId, userId, request.emoji)

                // Notify via WebSocket
                val message = MessageRepository.getMessageById(request.messageId)
                message?.let {
                    chatServer.notifyReaction(it.chatId, request.messageId, userId, request.emoji, true)
                }

                call.respond(mapOf("success" to true))
            }

            // Remove reaction from message
            delete {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@delete
                }

                val request = call.receive<RemoveReactionRequest>()

                MessageRepository.removeReaction(request.messageId, userId, request.emoji)

                // Notify via WebSocket
                val message = MessageRepository.getMessageById(request.messageId)
                message?.let {
                    chatServer.notifyReaction(it.chatId, request.messageId, userId, request.emoji, false)
                }

                call.respond(mapOf("success" to true))
            }
        }
    }
}
