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
data class CreateStoryRequest(
    val mediaUrl: String,
    val mediaType: String, // image, video
    val thumbnailUrl: String? = null,
    val duration: Int = 5,
    val text: String? = null,
    val textColor: String? = null,
    val textPosition: String? = null, // JSON
    val backgroundColor: String? = null,
    val musicUrl: String? = null,
    val musicTitle: String? = null
)

@Serializable
data class StoryResponse(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val mediaUrl: String,
    val mediaType: String,
    val thumbnailUrl: String?,
    val duration: Int,
    val text: String?,
    val textColor: String?,
    val textPosition: String?,
    val backgroundColor: String?,
    val musicUrl: String?,
    val musicTitle: String?,
    val viewCount: Int,
    val isViewed: Boolean,
    val expiresAt: Long,
    val createdAt: Long
)

@Serializable
data class UserStoriesResponse(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val hasUnwatched: Boolean,
    val stories: List<StoryResponse>
)

@Serializable
data class StoryReactionRequest(
    val emoji: String
)

fun Route.storiesRoutes() {
    route("/api/stories") {
        
        // Получить все активные истории (сгруппированные по пользователям)
        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asString() ?: ""
                
                val now = System.currentTimeMillis()
                
                val userStories = transaction {
                    // Получаем все активные истории
                    val activeStories = Stories.innerJoin(Users, { Stories.userId }, { Users.id })
                        .select { Stories.expiresAt greater now }
                        .orderBy(Stories.createdAt, SortOrder.DESC)
                        .toList()
                    
                    // Группируем по пользователям
                    activeStories.groupBy { it[Users.id] }.map { (userId, stories) ->
                        val firstStory = stories.first()
                        
                        val storyResponses = stories.map { row ->
                            val storyId = row[Stories.id]
                            val isViewed = StoryViews.select {
                                (StoryViews.storyId eq storyId) and (StoryViews.userId eq currentUserId)
                            }.count() > 0
                            
                            StoryResponse(
                                id = storyId,
                                userId = row[Users.id],
                                username = row[Users.username],
                                displayName = row[Users.displayName],
                                avatarUrl = row[Users.avatarUrl],
                                mediaUrl = row[Stories.mediaUrl],
                                mediaType = row[Stories.mediaType],
                                thumbnailUrl = row[Stories.thumbnailUrl],
                                duration = row[Stories.duration],
                                text = row[Stories.text],
                                textColor = row[Stories.textColor],
                                textPosition = row[Stories.textPosition],
                                backgroundColor = row[Stories.backgroundColor],
                                musicUrl = row[Stories.musicUrl],
                                musicTitle = row[Stories.musicTitle],
                                viewCount = row[Stories.viewCount],
                                isViewed = isViewed,
                                expiresAt = row[Stories.expiresAt],
                                createdAt = row[Stories.createdAt]
                            )
                        }
                        
                        val hasUnwatched = storyResponses.any { !it.isViewed }
                        
                        UserStoriesResponse(
                            userId = userId,
                            username = firstStory[Users.username],
                            displayName = firstStory[Users.displayName],
                            avatarUrl = firstStory[Users.avatarUrl],
                            hasUnwatched = hasUnwatched,
                            stories = storyResponses
                        )
                    }.sortedByDescending { it.hasUnwatched } // Непросмотренные сверху
                }
                
                call.respond(userStories)
            }
        }
        
        // Получить мои истории
        authenticate("auth-jwt") {
            get("/my") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val now = System.currentTimeMillis()
                
                val stories = transaction {
                    Stories.innerJoin(Users, { Stories.userId }, { Users.id })
                        .select { (Stories.userId eq userId) and (Stories.expiresAt greater now) }
                        .orderBy(Stories.createdAt, SortOrder.DESC)
                        .map { row ->
                            StoryResponse(
                                id = row[Stories.id],
                                userId = row[Users.id],
                                username = row[Users.username],
                                displayName = row[Users.displayName],
                                avatarUrl = row[Users.avatarUrl],
                                mediaUrl = row[Stories.mediaUrl],
                                mediaType = row[Stories.mediaType],
                                thumbnailUrl = row[Stories.thumbnailUrl],
                                duration = row[Stories.duration],
                                text = row[Stories.text],
                                textColor = row[Stories.textColor],
                                textPosition = row[Stories.textPosition],
                                backgroundColor = row[Stories.backgroundColor],
                                musicUrl = row[Stories.musicUrl],
                                musicTitle = row[Stories.musicTitle],
                                viewCount = row[Stories.viewCount],
                                isViewed = true,
                                expiresAt = row[Stories.expiresAt],
                                createdAt = row[Stories.createdAt]
                            )
                        }
                }
                
                call.respond(stories)
            }
        }
        
        // Создать историю
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateStoryRequest>()
                
                val storyId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val expiresAt = now + 24 * 60 * 60 * 1000 // 24 часа
                
                transaction {
                    Stories.insert {
                        it[id] = storyId
                        it[Stories.userId] = userId
                        it[mediaUrl] = request.mediaUrl
                        it[mediaType] = request.mediaType
                        it[thumbnailUrl] = request.thumbnailUrl
                        it[duration] = request.duration
                        it[text] = request.text
                        it[textColor] = request.textColor
                        it[textPosition] = request.textPosition
                        it[backgroundColor] = request.backgroundColor
                        it[musicUrl] = request.musicUrl
                        it[musicTitle] = request.musicTitle
                        it[Stories.expiresAt] = expiresAt
                        it[createdAt] = now
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to storyId, "expiresAt" to expiresAt))
            }
        }
        
        // Отметить историю как просмотренную
        authenticate("auth-jwt") {
            post("/{storyId}/view") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val storyId = call.parameters["storyId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    val existing = StoryViews.select {
                        (StoryViews.storyId eq storyId) and (StoryViews.userId eq userId)
                    }.singleOrNull()
                    
                    if (existing == null) {
                        StoryViews.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[StoryViews.storyId] = storyId
                            it[StoryViews.userId] = userId
                            it[viewedAt] = System.currentTimeMillis()
                        }
                        
                        Stories.update({ Stories.id eq storyId }) {
                            with(SqlExpressionBuilder) {
                                it[viewCount] = viewCount + 1
                            }
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Получить просмотры истории (для автора)
        authenticate("auth-jwt") {
            get("/{storyId}/views") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val storyId = call.parameters["storyId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                // Проверяем что это наша история
                val isOwner = transaction {
                    Stories.select { (Stories.id eq storyId) and (Stories.userId eq userId) }.count() > 0
                }
                
                if (!isOwner) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Это не ваша история"))
                    return@get
                }
                
                val views = transaction {
                    StoryViews.innerJoin(Users, { StoryViews.userId }, { Users.id })
                        .select { StoryViews.storyId eq storyId }
                        .orderBy(StoryViews.viewedAt, SortOrder.DESC)
                        .map { row ->
                            mapOf(
                                "userId" to row[Users.id],
                                "username" to row[Users.username],
                                "displayName" to row[Users.displayName],
                                "avatarUrl" to row[Users.avatarUrl],
                                "viewedAt" to row[StoryViews.viewedAt]
                            )
                        }
                }
                
                call.respond(views)
            }
        }
        
        // Добавить реакцию на историю
        authenticate("auth-jwt") {
            post("/{storyId}/react") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val storyId = call.parameters["storyId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<StoryReactionRequest>()
                
                transaction {
                    StoryReactions.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[StoryReactions.storyId] = storyId
                        it[StoryReactions.userId] = userId
                        it[emoji] = request.emoji
                        it[createdAt] = System.currentTimeMillis()
                    }
                    
                    Stories.update({ Stories.id eq storyId }) {
                        with(SqlExpressionBuilder) {
                            it[replyCount] = replyCount + 1
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Удалить историю
        authenticate("auth-jwt") {
            delete("/{storyId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val storyId = call.parameters["storyId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    Stories.deleteWhere { (Stories.id eq storyId) and (Stories.userId eq userId) }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
    }
}
