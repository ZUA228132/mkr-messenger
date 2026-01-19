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

fun Route.groupRoutes() {
    authenticate("auth-jwt") {
        route("/api/groups") {
            // Get all groups for user
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val groups = GroupRepository.getGroupsByUserId(userId)
                call.respond(groups)
            }
            
            // Create group
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val request = call.receive<CreateGroupRequest>()
                
                val group = GroupEntity(
                    id = UUID.randomUUID().toString(),
                    name = request.name,
                    description = request.description,
                    creatorId = userId,
                    admins = listOf(userId),
                    members = listOf(userId) + request.members
                )
                
                GroupRepository.createGroup(group)
                call.respond(HttpStatusCode.Created, group)
            }
            
            // Get group by ID
            get("/{groupId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val groupId = call.parameters["groupId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Group ID required"))
                    return@get
                }
                
                val group = GroupRepository.getGroupById(groupId)
                if (group == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Group not found"))
                    return@get
                }
                
                call.respond(group)
            }
            
            // Add member to group
            post("/{groupId}/members") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val groupId = call.parameters["groupId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Group ID required"))
                    return@post
                }
                
                val body = call.receive<Map<String, String>>()
                val memberId = body["memberId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Member ID required"))
                    return@post
                }
                
                val group = GroupRepository.getGroupById(groupId)
                if (group == null || !group.admins.contains(userId)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not authorized"))
                    return@post
                }
                
                GroupRepository.addMember(groupId, memberId)
                call.respond(mapOf("success" to true))
            }
            
            // Remove member from group
            delete("/{groupId}/members/{memberId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@delete
                }
                
                val groupId = call.parameters["groupId"] ?: return@delete
                val memberId = call.parameters["memberId"] ?: return@delete
                
                val group = GroupRepository.getGroupById(groupId)
                if (group == null || !group.admins.contains(userId)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not authorized"))
                    return@delete
                }
                
                GroupRepository.removeMember(groupId, memberId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
