package com.pioneer.routes

import com.pioneer.plugins.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class LocationUpdateRequest(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class NearbyUserResponse(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val lastSeen: String
)

@Serializable
data class PrivacySettingsRequest(
    val whoCanCall: String,
    val whoCanSeeAvatar: String,
    val whoCanMessage: String,
    val whoCanFindMe: String,
    val ghostMode: Boolean
)

@Serializable
data class PrivacySettingsResponse(
    val whoCanCall: String,
    val whoCanSeeAvatar: String,
    val whoCanMessage: String,
    val whoCanFindMe: String,
    val ghostMode: Boolean
)

fun Route.locationRoutes() {
    
    // Обновить местоположение
    authenticate("auth-jwt") {
        post("/api/location/update") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
            
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@post
            }
            
            try {
                val request = call.receive<LocationUpdateRequest>()
                
                transaction {
                    Users.update({ Users.id eq userId }) {
                        it[latitude] = request.latitude
                        it[longitude] = request.longitude
                        it[locationUpdatedAt] = System.currentTimeMillis()
                        it[lastSeen] = System.currentTimeMillis()
                    }
                }
                
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
    
    // Получить пользователей на карте
    authenticate("auth-jwt") {
        get("/api/location/nearby") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
            
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            
            try {
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                
                val users = transaction {
                    Users.select {
                        (Users.id neq userId) and
                        (Users.ghostMode eq false) and
                        (Users.latitude.isNotNull()) and
                        (Users.longitude.isNotNull()) and
                        (Users.locationUpdatedAt.isNotNull()) and
                        (Users.locationUpdatedAt greaterEq oneHourAgo) and
                        (Users.isBanned eq false)
                    }.map { row ->
                        val lastSeenTime = row[Users.lastSeen] ?: row[Users.locationUpdatedAt] ?: System.currentTimeMillis()
                        val lastSeenStr = dateFormat.format(Date(lastSeenTime))
                        
                        NearbyUserResponse(
                            userId = row[Users.id],
                            username = row[Users.username],
                            displayName = row[Users.displayName],
                            avatarUrl = row[Users.avatarUrl],
                            latitude = row[Users.latitude]!!,
                            longitude = row[Users.longitude]!!,
                            lastSeen = lastSeenStr
                        )
                    }
                }
                
                call.respond(HttpStatusCode.OK, users)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
    
    // Получить настройки приватности
    authenticate("auth-jwt") {
        get("/api/privacy/settings") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
            
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            
            try {
                val settings = transaction {
                    Users.select { Users.id eq userId }.singleOrNull()?.let { row ->
                        PrivacySettingsResponse(
                            whoCanCall = row[Users.whoCanCall],
                            whoCanSeeAvatar = row[Users.whoCanSeeAvatar],
                            whoCanMessage = row[Users.whoCanMessage],
                            whoCanFindMe = row[Users.whoCanFindMe],
                            ghostMode = row[Users.ghostMode]
                        )
                    }
                }
                
                if (settings != null) {
                    call.respond(HttpStatusCode.OK, settings)
                } else {
                    // Возвращаем дефолтные настройки
                    call.respond(HttpStatusCode.OK, PrivacySettingsResponse(
                        whoCanCall = "everyone",
                        whoCanSeeAvatar = "everyone",
                        whoCanMessage = "everyone",
                        whoCanFindMe = "everyone",
                        ghostMode = false
                    ))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
    
    // Обновить настройки приватности
    authenticate("auth-jwt") {
        post("/api/privacy/settings") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
            
            println("Privacy settings update request from user: $userId")
            
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@post
            }
            
            try {
                val request = call.receive<PrivacySettingsRequest>()
                println("Privacy settings: whoCanCall=${request.whoCanCall}, ghostMode=${request.ghostMode}")
                
                // Валидация значений
                val validValues = listOf("everyone", "contacts", "nobody")
                if (request.whoCanCall !in validValues ||
                    request.whoCanSeeAvatar !in validValues ||
                    request.whoCanMessage !in validValues ||
                    request.whoCanFindMe !in validValues) {
                    println("Invalid privacy value!")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid privacy value"))
                    return@post
                }
                
                transaction {
                    Users.update({ Users.id eq userId }) {
                        it[whoCanCall] = request.whoCanCall
                        it[whoCanSeeAvatar] = request.whoCanSeeAvatar
                        it[whoCanMessage] = request.whoCanMessage
                        it[whoCanFindMe] = request.whoCanFindMe
                        it[ghostMode] = request.ghostMode
                    }
                }
                
                println("Privacy settings saved successfully for user $userId")
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            } catch (e: Exception) {
                println("Error saving privacy settings: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }
}
