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
import kotlin.random.Random

// ID официального канала MKR
const val MKR_OFFICIAL_CHANNEL_ID = "mkr-official-channel"

@Serializable
data class ChannelResponse(
    val id: String,
    val chatId: String,
    val name: String,
    val username: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val isPublic: Boolean = true,
    val subscriberCount: Int = 0,
    val allowComments: Boolean = true,
    val isSubscribed: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val isVerified: Boolean = false,
    val isOfficial: Boolean = false
)


@Serializable
data class ChannelPostResponse(
    val id: String,
    val messageId: String,
    val content: String,
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0,
    val viewCount: Int = 0,
    val isPinned: Boolean = false,
    val allowComments: Boolean = true,
    val commentCount: Int = 0,
    val reactions: List<ReactionData> = emptyList()
)

@Serializable
data class ReactionData(
    val emoji: String,
    val count: Int,
    val hasReacted: Boolean = false
)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val username: String? = null,
    val description: String? = null,
    val isPublic: Boolean = true,
    val allowComments: Boolean = true
)

@Serializable
data class CreatePostRequest(
    val content: String,
    val allowComments: Boolean = true
)

@Serializable
data class NotificationToggleRequest(val enabled: Boolean)

// Генерация случайных просмотров для официального канала MKR
fun generateFakeViews(): Int = Random.nextInt(400_000, 800_001)

// Генерация случайных реакций для официального канала MKR
fun generateFakeReactions(): List<ReactionData> {
    val emojis = listOf("👍", "❤️", "🔥", "👏", "😍", "🎉")
    return emojis.take(Random.nextInt(3, 6)).map { emoji ->
        ReactionData(
            emoji = emoji,
            count = Random.nextInt(10_000, 100_001),
            hasReacted = Random.nextBoolean()
        )
    }
}

fun Route.channelRoutes() {
    route("/api/channels") {
        authenticate("auth-jwt") {
            // Получить все каналы
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val channels = transaction {
                    Channels.selectAll().map { row ->
                        val channelId = row[Channels.id]
                        val chatId = row[Channels.chatId]
                        
                        // Проверяем подписку
                        val isSubscribed = ChannelSubscriptions.select {
                            (ChannelSubscriptions.channelId eq channelId) and
                            (ChannelSubscriptions.userId eq userId)
                        }.count() > 0
                        
                        // Получаем имя канала из Chats
                        val chatRow = Chats.select { Chats.id eq chatId }.singleOrNull()
                        val isVerified = chatRow?.get(Chats.isVerified) ?: false
                        val isOfficial = chatId == MKR_OFFICIAL_CHANNEL_ID
                        
                        ChannelResponse(
                            id = channelId,
                            chatId = chatId,
                            name = chatRow?.get(Chats.name) ?: row[Channels.username] ?: "Канал",
                            username = row[Channels.username],
                            description = row[Channels.description],
                            avatarUrl = row[Channels.avatarUrl],
                            isPublic = row[Channels.isPublic],
                            subscriberCount = row[Channels.subscriberCount],
                            allowComments = row[Channels.allowComments],
                            isSubscribed = isSubscribed,
                            notificationsEnabled = true,
                            isVerified = isVerified,
                            isOfficial = isOfficial
                        )
                    }
                }
                call.respond(channels)
            }

            
            // Получить канал по ID
            get("/{channelId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val channelId = call.parameters["channelId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val channel = transaction {
                    Channels.select { Channels.id eq channelId }.singleOrNull()?.let { row ->
                        val chatId = row[Channels.chatId]
                        val isSubscribed = ChannelSubscriptions.select {
                            (ChannelSubscriptions.channelId eq channelId) and
                            (ChannelSubscriptions.userId eq userId)
                        }.count() > 0
                        
                        val chatRow = Chats.select { Chats.id eq chatId }.singleOrNull()
                        val isVerified = chatRow?.get(Chats.isVerified) ?: false
                        val isOfficial = chatId == MKR_OFFICIAL_CHANNEL_ID
                        
                        ChannelResponse(
                            id = channelId,
                            chatId = chatId,
                            name = chatRow?.get(Chats.name) ?: row[Channels.username] ?: "Канал",
                            username = row[Channels.username],
                            description = row[Channels.description],
                            avatarUrl = row[Channels.avatarUrl],
                            isPublic = row[Channels.isPublic],
                            subscriberCount = row[Channels.subscriberCount],
                            allowComments = row[Channels.allowComments],
                            isSubscribed = isSubscribed,
                            notificationsEnabled = true,
                            isVerified = isVerified,
                            isOfficial = isOfficial
                        )
                    }
                }
                
                if (channel != null) {
                    call.respond(channel)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
            
            // Создать канал
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateChannelRequest>()
                val channelId = UUID.randomUUID().toString()
                val chatId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    // Создаём чат для канала
                    Chats.insert {
                        it[id] = chatId
                        it[type] = "channel"
                        it[name] = request.name
                        it[description] = request.description
                        it[isVerified] = false
                        it[createdAt] = now
                        it[encryptionKeyId] = UUID.randomUUID().toString()
                    }
                    
                    // Создаём канал
                    Channels.insert {
                        it[id] = channelId
                        it[Channels.chatId] = chatId
                        it[username] = request.username
                        it[description] = request.description
                        it[isPublic] = request.isPublic
                        it[subscriberCount] = 1
                        it[allowComments] = request.allowComments
                        it[createdAt] = now
                    }
                    
                    // Добавляем создателя как владельца
                    ChatParticipants.insert {
                        it[ChatParticipants.chatId] = chatId
                        it[ChatParticipants.userId] = userId
                        it[role] = "owner"
                        it[joinedAt] = now
                    }
                    
                    // Автоподписка создателя
                    ChannelSubscriptions.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[ChannelSubscriptions.channelId] = channelId
                        it[ChannelSubscriptions.userId] = userId
                        it[notificationsEnabled] = true
                        it[subscribedAt] = now
                    }
                }
                
                call.respond(HttpStatusCode.Created, ChannelResponse(
                    id = channelId,
                    chatId = chatId,
                    name = request.name,
                    username = request.username,
                    description = request.description,
                    isPublic = request.isPublic,
                    subscriberCount = 1,
                    allowComments = request.allowComments,
                    isSubscribed = true,
                    notificationsEnabled = true
                ))
            }

            
            // Подписаться на канал
            post("/{channelId}/subscribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val channelId = call.parameters["channelId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    val existing = ChannelSubscriptions.select {
                        (ChannelSubscriptions.channelId eq channelId) and
                        (ChannelSubscriptions.userId eq userId)
                    }.singleOrNull()
                    
                    if (existing == null) {
                        ChannelSubscriptions.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[ChannelSubscriptions.channelId] = channelId
                            it[ChannelSubscriptions.userId] = userId
                            it[notificationsEnabled] = true
                            it[subscribedAt] = System.currentTimeMillis()
                        }
                        
                        Channels.update({ Channels.id eq channelId }) {
                            with(SqlExpressionBuilder) {
                                it[subscriberCount] = subscriberCount + 1
                            }
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
            
            // Отписаться от канала
            delete("/{channelId}/subscribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val channelId = call.parameters["channelId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    val deleted = ChannelSubscriptions.deleteWhere {
                        (ChannelSubscriptions.channelId eq channelId) and
                        (ChannelSubscriptions.userId eq userId)
                    }
                    
                    if (deleted > 0) {
                        Channels.update({ Channels.id eq channelId }) {
                            with(SqlExpressionBuilder) {
                                it[subscriberCount] = subscriberCount - 1
                            }
                        }
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
            
            // Переключить уведомления
            post("/{channelId}/notifications") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val channelId = call.parameters["channelId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<NotificationToggleRequest>()
                
                transaction {
                    ChannelSubscriptions.update({
                        (ChannelSubscriptions.channelId eq channelId) and
                        (ChannelSubscriptions.userId eq userId)
                    }) {
                        it[notificationsEnabled] = request.enabled
                    }
                }
                
                call.respond(mapOf("success" to true))
            }

            
            // Получить посты канала
            get("/{channelId}/posts") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val channelId = call.parameters["channelId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val posts = transaction {
                    // Получаем chatId канала
                    val channel = Channels.select { Channels.id eq channelId }.singleOrNull()
                        ?: return@transaction emptyList()
                    
                    val chatId = channel[Channels.chatId]
                    val isOfficialMkr = chatId == MKR_OFFICIAL_CHANNEL_ID
                    
                    // Получаем сообщения канала
                    Messages.select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp, SortOrder.DESC)
                        .limit(50)
                        .map { msg ->
                            val senderId = msg[Messages.senderId]
                            val senderName = Users.select { Users.id eq senderId }
                                .singleOrNull()?.get(Users.displayName) ?: "MKR"
                            
                            // Получаем реакции
                            val reactions = if (isOfficialMkr) {
                                // Для официального канала MKR - накрученные реакции
                                generateFakeReactions()
                            } else {
                                // Реальные реакции из БД
                                MessageReactions.select { MessageReactions.messageId eq msg[Messages.id] }
                                    .groupBy { it[MessageReactions.emoji] }
                                    .map { (emoji, rows) ->
                                        val hasReacted = rows.any { it[MessageReactions.userId] == userId }
                                        ReactionData(emoji, rows.size, hasReacted)
                                    }
                            }
                            
                            // Просмотры - накрученные для MKR
                            val viewCount = if (isOfficialMkr) {
                                generateFakeViews()
                            } else {
                                ChannelPosts.select { ChannelPosts.messageId eq msg[Messages.id] }
                                    .singleOrNull()?.get(ChannelPosts.viewCount) ?: 0
                            }
                            
                            ChannelPostResponse(
                                id = msg[Messages.id],
                                messageId = msg[Messages.id],
                                content = msg[Messages.encryptedContent],
                                senderId = senderId,
                                senderName = senderName,
                                timestamp = msg[Messages.timestamp],
                                viewCount = viewCount,
                                isPinned = false,
                                allowComments = channel[Channels.allowComments],
                                commentCount = 0,
                                reactions = reactions
                            )
                        }
                }
                
                call.respond(posts)
            }
            
            // Создать пост в канале
            post("/{channelId}/posts") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val accessLevel = principal.payload.getClaim("accessLevel")?.asInt() ?: 0
                
                val channelId = call.parameters["channelId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<CreatePostRequest>()
                
                // Проверяем права на публикацию
                val canPost = transaction {
                    val channel = Channels.select { Channels.id eq channelId }.singleOrNull()
                        ?: return@transaction false
                    
                    val chatId = channel[Channels.chatId]
                    
                    // Для официального канала MKR - только админы
                    if (chatId == MKR_OFFICIAL_CHANNEL_ID) {
                        return@transaction accessLevel >= 10
                    }
                    
                    // Для других каналов - владелец или админ
                    val participant = ChatParticipants.select {
                        (ChatParticipants.chatId eq chatId) and
                        (ChatParticipants.userId eq userId)
                    }.singleOrNull()
                    
                    participant?.get(ChatParticipants.role) in listOf("owner", "admin")
                }
                
                if (!canPost) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Нет прав на публикацию"))
                }
                
                val messageId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    val channel = Channels.select { Channels.id eq channelId }.single()
                    val chatId = channel[Channels.chatId]
                    
                    Messages.insert {
                        it[id] = messageId
                        it[Messages.chatId] = chatId
                        it[senderId] = userId
                        it[encryptedContent] = request.content
                        it[nonce] = ""
                        it[timestamp] = now
                        it[type] = "TEXT"
                        it[status] = "SENT"
                    }
                    
                    ChannelPosts.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[ChannelPosts.channelId] = channelId
                        it[ChannelPosts.messageId] = messageId
                        it[viewCount] = 0
                        it[isPinned] = false
                        it[allowComments] = request.allowComments
                        it[commentCount] = 0
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to messageId))
            }
        }
    }
}
