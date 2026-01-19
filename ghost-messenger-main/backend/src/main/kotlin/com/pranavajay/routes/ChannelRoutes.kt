package com.pranavajay.routes

import com.pranavajay.database.ChannelRepository
import com.pranavajay.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.channelRoutes() {
    authenticate("auth-jwt") {
        route("/api/channels") {
            // Get user's subscribed channels
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }

                val channels = ChannelRepository.getChannelsByUserId(userId)
                call.respond(channels)
            }

            // Create a channel
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val request = call.receive<CreateChannelRequest>()

                val channel = ChannelEntity(
                    id = UUID.randomUUID().toString(),
                    name = request.name,
                    description = request.description,
                    creatorId = userId,
                    admins = listOf(userId),
                    subscribers = listOf(userId),
                    subscriberCount = 1,
                    isPublic = request.isPublic,
                    inviteLink = "https://ghostmessenger.app/c/${UUID.randomUUID().toString().take(8)}"
                )

                ChannelRepository.createChannel(channel)
                call.respond(HttpStatusCode.Created, channel)
            }

            // Get channel by ID
            get("/{channelId}") {
                val channelId = call.parameters["channelId"] ?: return@get

                val channel = ChannelRepository.getChannelById(channelId)
                if (channel == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Channel not found"))
                    return@get
                }

                call.respond(channel)
            }

            // Subscribe to channel
            post("/{channelId}/subscribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val channelId = call.parameters["channelId"] ?: return@post

                ChannelRepository.subscribe(channelId, userId)
                call.respond(mapOf("success" to true))
            }

            // Unsubscribe from channel
            post("/{channelId}/unsubscribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }

                val channelId = call.parameters["channelId"] ?: return@post

                ChannelRepository.unsubscribe(channelId, userId)
                call.respond(mapOf("success" to true))
            }

            // Search public channels
            get("/search") {
                val query = call.request.queryParameters["q"] ?: ""

                if (query.length < 2) {
                    call.respond(emptyList<ChannelEntity>())
                    return@get
                }

                val channels = ChannelRepository.searchPublicChannels(query)
                call.respond(channels)
            }
        }
    }
}
