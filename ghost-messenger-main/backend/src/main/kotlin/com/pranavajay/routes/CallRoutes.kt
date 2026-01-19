package com.pranavajay.routes

import com.pranavajay.database.CallRepository
import com.pranavajay.database.UserRepository
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

fun Route.callRoutes() {
    authenticate("auth-jwt") {
        route("/api/calls") {
            // Initiate a call
            post("/initiate") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val request = call.receive<InitiateCallRequest>()

                val callEntity = CallEntity(
                    id = UUID.randomUUID().toString(),
                    callerId = userId,
                    receiverId = request.receiverId,
                    groupId = request.groupId,
                    type = request.type,
                    status = "INITIATED",
                    participants = listOf(userId)
                )

                CallRepository.createCall(callEntity)

                // Notify receiver via WebSocket
                chatServer.notifyIncomingCall(request.receiverId, callEntity)

                // Return WebRTC configuration
                val response = CallResponse(
                    callId = callEntity.id,
                    roomId = "room_${callEntity.id}",
                    iceServers = listOf(
                        IceServer(
                            urls = listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")
                        )
                    )
                )

                call.respond(HttpStatusCode.Created, response)
            }

            // Accept a call
            post("/{callId}/accept") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val callId = call.parameters["callId"] ?: return@post

                CallRepository.updateCallStatus(callId, "ONGOING")

                val callEntity = CallRepository.getCallById(callId)
                callEntity?.let {
                    chatServer.notifyCallAccepted(it.callerId, callId)
                }

                call.respond(mapOf(
                    "success" to true,
                    "roomId" to "room_$callId"
                ))
            }

            // Decline a call
            post("/{callId}/decline") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val callId = call.parameters["callId"] ?: return@post

                CallRepository.updateCallStatus(callId, "DECLINED")

                val callEntity = CallRepository.getCallById(callId)
                callEntity?.let {
                    chatServer.notifyCallDeclined(it.callerId, callId)
                }

                call.respond(mapOf("success" to true))
            }

            // End a call
            post("/{callId}/end") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val callId = call.parameters["callId"] ?: return@post

                CallRepository.updateCallStatus(callId, "ENDED")

                val callEntity = CallRepository.getCallById(callId)
                callEntity?.let {
                    val otherUserId = if (it.callerId == userId) it.receiverId else it.callerId
                    chatServer.notifyCallEnded(otherUserId, callId)
                }

                call.respond(mapOf("success" to true))
            }

            // Get call history
            get("/history") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }

                val calls = CallRepository.getCallHistory(userId)
                call.respond(calls)
            }
        }
    }
}
