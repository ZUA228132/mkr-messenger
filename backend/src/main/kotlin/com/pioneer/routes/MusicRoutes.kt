package com.pioneer.routes

import com.pioneer.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

@Serializable
data class CreateMusicRoomRequest(
    val chatId: String,
    val trackUrl: String,
    val trackTitle: String,
    val trackArtist: String? = null,
    val trackCover: String? = null
)

@Serializable
data class UpdateTrackRequest(
    val trackUrl: String,
    val trackTitle: String,
    val trackArtist: String? = null,
    val trackCover: String? = null
)

@Serializable
data class SyncRequest(
    val position: Long,
    val isPlaying: Boolean
)

@Serializable
data class MusicRoomResponse(
    val id: String,
    val chatId: String,
    val hostId: String,
    val hostName: String,
    val currentTrackUrl: String?,
    val currentTrackTitle: String?,
    val currentTrackArtist: String?,
    val currentTrackCover: String?,
    val currentPosition: Long,
    val isPlaying: Boolean,
    val participants: List<ParticipantInfo>,
    val lastSyncAt: Long,
    val createdAt: Long
)

@Serializable
data class ParticipantInfo(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?
)

fun Route.musicRoutes() {
    route("/api/music") {
        
        // Создать музыкальную комнату в чате
        authenticate("auth-jwt") {
            post("/room") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateMusicRoomRequest>()
                
                val roomId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                val result = transaction {
                    // Проверяем что пользователь участник чата
                    val isParticipant = ChatParticipants.select {
                        (ChatParticipants.chatId eq request.chatId) and (ChatParticipants.userId eq userId)
                    }.count() > 0
                    
                    if (!isParticipant) {
                        return@transaction null to "Вы не участник этого чата"
                    }
                    
                    // Проверяем нет ли уже активной комнаты в этом чате
                    val existingRoom = MusicRooms.select { MusicRooms.chatId eq request.chatId }.singleOrNull()
                    if (existingRoom != null) {
                        return@transaction existingRoom[MusicRooms.id] to "exists"
                    }
                    
                    // Создаём комнату
                    MusicRooms.insert {
                        it[id] = roomId
                        it[chatId] = request.chatId
                        it[hostId] = userId
                        it[currentTrackUrl] = request.trackUrl
                        it[currentTrackTitle] = request.trackTitle
                        it[currentTrackArtist] = request.trackArtist
                        it[currentTrackCover] = request.trackCover
                        it[currentPosition] = 0
                        it[isPlaying] = true
                        it[lastSyncAt] = now
                        it[createdAt] = now
                    }
                    
                    // Добавляем хоста как участника
                    MusicRoomParticipants.insert {
                        it[MusicRoomParticipants.roomId] = roomId
                        it[MusicRoomParticipants.userId] = userId
                        it[joinedAt] = now
                    }
                    
                    // Добавляем в историю
                    MusicRoomHistory.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[MusicRoomHistory.roomId] = roomId
                        it[trackUrl] = request.trackUrl
                        it[trackTitle] = request.trackTitle
                        it[trackArtist] = request.trackArtist
                        it[addedBy] = userId
                        it[playedAt] = now
                    }
                    
                    roomId to "created"
                }
                
                val (id, status) = result
                if (id != null) {
                    call.respond(HttpStatusCode.Created, mapOf("roomId" to id, "status" to status))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to status))
                }
            }
        }
        
        // Получить комнату по chatId
        authenticate("auth-jwt") {
            get("/room/chat/{chatId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val chatId = call.parameters["chatId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val room = transaction {
                    val roomRow = MusicRooms.select { MusicRooms.chatId eq chatId }.singleOrNull()
                        ?: return@transaction null
                    
                    val hostUser = Users.select { Users.id eq roomRow[MusicRooms.hostId] }.single()
                    
                    val participants = MusicRoomParticipants
                        .innerJoin(Users, { MusicRoomParticipants.userId }, { Users.id })
                        .select { MusicRoomParticipants.roomId eq roomRow[MusicRooms.id] }
                        .map { row ->
                            ParticipantInfo(
                                userId = row[Users.id],
                                username = row[Users.username],
                                displayName = row[Users.displayName],
                                avatarUrl = row[Users.avatarUrl]
                            )
                        }
                    
                    MusicRoomResponse(
                        id = roomRow[MusicRooms.id],
                        chatId = roomRow[MusicRooms.chatId],
                        hostId = roomRow[MusicRooms.hostId],
                        hostName = hostUser[Users.displayName],
                        currentTrackUrl = roomRow[MusicRooms.currentTrackUrl],
                        currentTrackTitle = roomRow[MusicRooms.currentTrackTitle],
                        currentTrackArtist = roomRow[MusicRooms.currentTrackArtist],
                        currentTrackCover = roomRow[MusicRooms.currentTrackCover],
                        currentPosition = roomRow[MusicRooms.currentPosition],
                        isPlaying = roomRow[MusicRooms.isPlaying],
                        participants = participants,
                        lastSyncAt = roomRow[MusicRooms.lastSyncAt],
                        createdAt = roomRow[MusicRooms.createdAt]
                    )
                }
                
                if (room != null) {
                    call.respond(room)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Комната не найдена"))
                }
            }
        }
        
        // Присоединиться к комнате
        authenticate("auth-jwt") {
            post("/room/{roomId}/join") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val roomId = call.parameters["roomId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    val existing = MusicRoomParticipants.select {
                        (MusicRoomParticipants.roomId eq roomId) and (MusicRoomParticipants.userId eq userId)
                    }.singleOrNull()
                    
                    if (existing == null) {
                        MusicRoomParticipants.insert {
                            it[MusicRoomParticipants.roomId] = roomId
                            it[MusicRoomParticipants.userId] = userId
                            it[joinedAt] = System.currentTimeMillis()
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Покинуть комнату
        authenticate("auth-jwt") {
            post("/room/{roomId}/leave") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val roomId = call.parameters["roomId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    MusicRoomParticipants.deleteWhere {
                        (MusicRoomParticipants.roomId eq roomId) and (MusicRoomParticipants.userId eq userId)
                    }
                    
                    // Если никого не осталось - удаляем комнату
                    val remainingCount = MusicRoomParticipants.select { MusicRoomParticipants.roomId eq roomId }.count()
                    if (remainingCount == 0L) {
                        MusicRooms.deleteWhere { MusicRooms.id eq roomId }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Синхронизация позиции (от хоста)
        authenticate("auth-jwt") {
            post("/room/{roomId}/sync") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val roomId = call.parameters["roomId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<SyncRequest>()
                
                transaction {
                    // Только хост может синхронизировать
                    val room = MusicRooms.select { MusicRooms.id eq roomId }.singleOrNull()
                    if (room != null && room[MusicRooms.hostId] == userId) {
                        MusicRooms.update({ MusicRooms.id eq roomId }) {
                            it[currentPosition] = request.position
                            it[isPlaying] = request.isPlaying
                            it[lastSyncAt] = System.currentTimeMillis()
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Сменить трек (только хост)
        authenticate("auth-jwt") {
            post("/room/{roomId}/track") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val roomId = call.parameters["roomId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<UpdateTrackRequest>()
                val now = System.currentTimeMillis()
                
                val success = transaction {
                    val room = MusicRooms.select { MusicRooms.id eq roomId }.singleOrNull()
                    if (room != null && room[MusicRooms.hostId] == userId) {
                        MusicRooms.update({ MusicRooms.id eq roomId }) {
                            it[currentTrackUrl] = request.trackUrl
                            it[currentTrackTitle] = request.trackTitle
                            it[currentTrackArtist] = request.trackArtist
                            it[currentTrackCover] = request.trackCover
                            it[currentPosition] = 0
                            it[isPlaying] = true
                            it[lastSyncAt] = now
                        }
                        
                        // Добавляем в историю
                        MusicRoomHistory.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[MusicRoomHistory.roomId] = roomId
                            it[trackUrl] = request.trackUrl
                            it[trackTitle] = request.trackTitle
                            it[trackArtist] = request.trackArtist
                            it[addedBy] = userId
                            it[playedAt] = now
                        }
                        
                        true
                    } else {
                        false
                    }
                }
                
                if (success) {
                    call.respond(mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Только хост может менять трек"))
                }
            }
        }
        
        // Закрыть комнату (только хост)
        authenticate("auth-jwt") {
            delete("/room/{roomId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val roomId = call.parameters["roomId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                val success = transaction {
                    val room = MusicRooms.select { MusicRooms.id eq roomId }.singleOrNull()
                    if (room != null && room[MusicRooms.hostId] == userId) {
                        MusicRoomParticipants.deleteWhere { MusicRoomParticipants.roomId eq roomId }
                        MusicRooms.deleteWhere { MusicRooms.id eq roomId }
                        true
                    } else {
                        false
                    }
                }
                
                if (success) {
                    call.respond(mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Только хост может закрыть комнату"))
                }
            }
        }
    }
}
