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
data class CreateReelRequest(
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val duration: Int = 0
)

@Serializable
data class ReelResponse(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val description: String?,
    val duration: Int,
    val viewCount: Int,
    val likeCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val isLiked: Boolean,
    val createdAt: Long
)

@Serializable
data class ReelCommentRequest(
    val content: String,
    val replyToId: String? = null
)

@Serializable
data class ReelCommentResponse(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val content: String,
    val replyToId: String?,
    val likeCount: Int,
    val createdAt: Long
)

fun Route.reelsRoutes() {
    route("/api/reels") {
        
        // Получить ленту Reels
        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asString() ?: ""
                
                val reels = transaction {
                    Reels.innerJoin(Users, { Reels.userId }, { Users.id })
                        .select { Reels.isPublic eq true }
                        .orderBy(Reels.createdAt, SortOrder.DESC)
                        .limit(50)
                        .map { row ->
                            val reelId = row[Reels.id]
                            val isLiked = ReelLikes.select { 
                                (ReelLikes.reelId eq reelId) and (ReelLikes.userId eq currentUserId)
                            }.count() > 0
                            
                            ReelResponse(
                                id = reelId,
                                userId = row[Users.id],
                                username = row[Users.username],
                                displayName = row[Users.displayName],
                                avatarUrl = row[Users.avatarUrl],
                                videoUrl = row[Reels.videoUrl],
                                thumbnailUrl = row[Reels.thumbnailUrl],
                                description = row[Reels.description],
                                duration = row[Reels.duration],
                                viewCount = row[Reels.viewCount],
                                likeCount = row[Reels.likeCount],
                                commentCount = row[Reels.commentCount],
                                shareCount = row[Reels.shareCount],
                                isLiked = isLiked,
                                createdAt = row[Reels.createdAt]
                            )
                        }
                }
                
                call.respond(reels)
            }
        }
        
        // Создать Reel
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateReelRequest>()
                
                val reelId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    Reels.insert {
                        it[id] = reelId
                        it[Reels.userId] = userId
                        it[videoUrl] = request.videoUrl
                        it[thumbnailUrl] = request.thumbnailUrl
                        it[description] = request.description
                        it[duration] = request.duration
                        it[createdAt] = now
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to reelId))
            }
        }
        
        // Лайкнуть/убрать лайк
        authenticate("auth-jwt") {
            post("/{reelId}/like") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val reelId = call.parameters["reelId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val action = transaction {
                    val existing = ReelLikes.select {
                        (ReelLikes.reelId eq reelId) and (ReelLikes.userId eq userId)
                    }.singleOrNull()
                    
                    if (existing != null) {
                        // Убираем лайк
                        ReelLikes.deleteWhere {
                            (ReelLikes.reelId eq reelId) and (ReelLikes.userId eq userId)
                        }
                        Reels.update({ Reels.id eq reelId }) {
                            with(SqlExpressionBuilder) {
                                it[likeCount] = likeCount - 1
                            }
                        }
                        "unliked"
                    } else {
                        // Ставим лайк
                        ReelLikes.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[ReelLikes.reelId] = reelId
                            it[ReelLikes.userId] = userId
                            it[createdAt] = System.currentTimeMillis()
                        }
                        Reels.update({ Reels.id eq reelId }) {
                            with(SqlExpressionBuilder) {
                                it[likeCount] = likeCount + 1
                            }
                        }
                        "liked"
                    }
                }
                
                call.respond(mapOf("action" to action))
            }
        }
        
        // Увеличить просмотры
        authenticate("auth-jwt") {
            post("/{reelId}/view") {
                val reelId = call.parameters["reelId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    Reels.update({ Reels.id eq reelId }) {
                        with(SqlExpressionBuilder) {
                            it[viewCount] = viewCount + 1
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Получить комментарии
        authenticate("auth-jwt") {
            get("/{reelId}/comments") {
                val reelId = call.parameters["reelId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val comments = transaction {
                    ReelComments.innerJoin(Users, { ReelComments.userId }, { Users.id })
                        .select { ReelComments.reelId eq reelId }
                        .orderBy(ReelComments.createdAt, SortOrder.DESC)
                        .map { row ->
                            ReelCommentResponse(
                                id = row[ReelComments.id],
                                userId = row[Users.id],
                                username = row[Users.username],
                                displayName = row[Users.displayName],
                                avatarUrl = row[Users.avatarUrl],
                                content = row[ReelComments.content],
                                replyToId = row[ReelComments.replyToId],
                                likeCount = row[ReelComments.likeCount],
                                createdAt = row[ReelComments.createdAt]
                            )
                        }
                }
                
                call.respond(comments)
            }
        }
        
        // Добавить комментарий
        authenticate("auth-jwt") {
            post("/{reelId}/comments") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val reelId = call.parameters["reelId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<ReelCommentRequest>()
                
                val commentId = UUID.randomUUID().toString()
                
                transaction {
                    ReelComments.insert {
                        it[id] = commentId
                        it[ReelComments.reelId] = reelId
                        it[ReelComments.userId] = userId
                        it[content] = request.content
                        it[replyToId] = request.replyToId
                        it[createdAt] = System.currentTimeMillis()
                    }
                    
                    Reels.update({ Reels.id eq reelId }) {
                        with(SqlExpressionBuilder) {
                            it[commentCount] = commentCount + 1
                        }
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to commentId))
            }
        }
        
        // Удалить свой Reel
        authenticate("auth-jwt") {
            delete("/{reelId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val reelId = call.parameters["reelId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    // Удаляем только свои reels
                    Reels.deleteWhere { (Reels.id eq reelId) and (Reels.userId eq userId) }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
    }
}
