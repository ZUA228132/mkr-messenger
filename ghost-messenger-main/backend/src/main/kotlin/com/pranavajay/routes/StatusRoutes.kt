package com.pranavajay.routes

import com.pranavajay.database.*
import com.pranavajay.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.statusRoutes() {
    authenticate("auth-jwt") {
        route("/api/status") {
            // Get my statuses
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val statuses = StatusRepository.getStatusesByUserId(userId)
                call.respond(statuses)
            }
            
            // Get contacts' statuses
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                // Get contact IDs from chats
                val chats = ChatRepository.getChatsByUserId(userId)
                val contactIds = chats.flatMap { it.participants }.filter { it != userId }.distinct()
                
                val statuses = StatusRepository.getContactsStatuses(contactIds)
                call.respond(statuses)
            }
            
            // Create status
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val request = call.receive<CreateStatusRequest>()
                
                val status = StatusEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    content = request.content,
                    mediaUrl = request.mediaUrl,
                    mediaType = request.mediaType,
                    backgroundColor = request.backgroundColor
                )
                
                StatusRepository.createStatus(status)
                call.respond(HttpStatusCode.Created, status)
            }
            
            // View status (mark as viewed)
            post("/{statusId}/view") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val statusId = call.parameters["statusId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Status ID required"))
                    return@post
                }
                
                StatusRepository.addViewer(statusId, userId)
                call.respond(mapOf("success" to true))
            }
            
            // React to status
            post("/{statusId}/react") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val statusId = call.parameters["statusId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Status ID required"))
                    return@post
                }
                
                val body = call.receive<Map<String, String>>()
                val emoji = body["emoji"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Emoji required"))
                    return@post
                }
                
                StatusRepository.addReaction(statusId, userId, emoji)
                call.respond(mapOf("success" to true))
            }
            
            // Delete status
            delete("/{statusId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@delete
                }
                
                val statusId = call.parameters["statusId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Status ID required"))
                    return@delete
                }
                
                StatusRepository.deleteStatus(statusId, userId)
                call.respond(mapOf("success" to true))
            }
            
            // Get status viewers
            get("/{statusId}/viewers") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val statusId = call.parameters["statusId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Status ID required"))
                    return@get
                }
                
                val viewers = StatusRepository.getStatusViewers(statusId)
                call.respond(viewers)
            }
        }
    }
}
