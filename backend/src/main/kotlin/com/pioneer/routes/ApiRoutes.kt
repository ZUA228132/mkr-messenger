package com.pioneer.routes

import com.pioneer.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

// === USER ROUTES ===

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val publicKey: String,
    val accessLevel: Int,
    val isOnline: Boolean,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val isVerified: Boolean = false,
    val isBanned: Boolean = false,
    val banReason: String? = null,
    val emojiStatus: String? = null,
    val createdAt: Long = 0
)

fun Route.userRoutes() {
    route("/api/users") {
        authenticate("auth-jwt") {
            get {
                val users = transaction {
                    Users.selectAll().map { row ->
                        UserResponse(
                            id = row[Users.id],
                            username = row[Users.username],
                            displayName = row[Users.displayName],
                            publicKey = row[Users.publicKey],
                            accessLevel = row[Users.accessLevel],
                            isOnline = ConnectionManager.isOnline(row[Users.id]),
                            avatarUrl = row[Users.avatarUrl],
                            bio = row[Users.bio],
                            isVerified = row[Users.isVerified],
                            isBanned = row[Users.isBanned],
                            banReason = row[Users.banReason],
                            emojiStatus = row[Users.emojiStatus],
                            createdAt = row[Users.createdAt]
                        )
                    }
                }
                call.respond(users)
            }
            
            get("/{id}") {
                val userId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val user = transaction {
                    Users.select { Users.id eq userId }.singleOrNull()?.let { row ->
                        UserResponse(
                            id = row[Users.id],
                            username = row[Users.username],
                            displayName = row[Users.displayName],
                            publicKey = row[Users.publicKey],
                            accessLevel = row[Users.accessLevel],
                            isOnline = ConnectionManager.isOnline(row[Users.id]),
                            avatarUrl = row[Users.avatarUrl],
                            bio = row[Users.bio],
                            isVerified = row[Users.isVerified],
                            isBanned = row[Users.isBanned],
                            banReason = row[Users.banReason],
                            emojiStatus = row[Users.emojiStatus],
                            createdAt = row[Users.createdAt]
                        )
                    }
                }
                
                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
            
            // Поиск пользователей
            get("/search") {
                val query = call.request.queryParameters["q"] ?: ""
                
                if (query.length < 2) {
                    call.respond(emptyList<UserResponse>())
                    return@get
                }
                
                val users = transaction {
                    Users.select { 
                        (Users.username.lowerCase() like "%${query.lowercase()}%") or
                        (Users.displayName.lowerCase() like "%${query.lowercase()}%")
                    }
                    .limit(20)
                    .map { row ->
                        UserResponse(
                            id = row[Users.id],
                            username = row[Users.username],
                            displayName = row[Users.displayName],
                            publicKey = row[Users.publicKey],
                            accessLevel = row[Users.accessLevel],
                            isOnline = ConnectionManager.isOnline(row[Users.id]),
                            avatarUrl = row[Users.avatarUrl],
                            bio = row[Users.bio],
                            isVerified = row[Users.isVerified],
                            emojiStatus = row[Users.emojiStatus],
                            createdAt = row[Users.createdAt]
                        )
                    }
                }
                
                call.respond(users)
            }
            
            // Обновление профиля текущего пользователя
            post("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<UpdateProfileRequest>()
                
                transaction {
                    Users.update({ Users.id eq userId }) {
                        it[displayName] = request.displayName
                        request.bio?.let { bioVal -> it[bio] = bioVal }
                    }
                }
                
                val user = transaction {
                    Users.select { Users.id eq userId }.singleOrNull()?.let { row ->
                        UserResponse(
                            id = row[Users.id],
                            username = row[Users.username],
                            displayName = row[Users.displayName],
                            publicKey = row[Users.publicKey],
                            accessLevel = row[Users.accessLevel],
                            isOnline = ConnectionManager.isOnline(row[Users.id]),
                            avatarUrl = row[Users.avatarUrl],
                            bio = row[Users.bio],
                            isVerified = row[Users.isVerified],
                            emojiStatus = row[Users.emojiStatus]
                        )
                    }
                }
                
                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
            
            // Обновление аватара
            post("/me/avatar") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<UpdateAvatarRequest>()
                
                transaction {
                    Users.update({ Users.id eq userId }) {
                        it[avatarUrl] = request.avatarUrl
                    }
                }
                
                call.respond(mapOf("success" to true, "avatarUrl" to request.avatarUrl))
            }
            
            // Обновление эмодзи-статуса
            post("/me/emoji-status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<UpdateEmojiStatusRequest>()
                
                transaction {
                    Users.update({ Users.id eq userId }) {
                        it[emojiStatus] = request.emojiStatus
                    }
                }
                
                call.respond(mapOf("success" to true, "emojiStatus" to request.emojiStatus))
            }
            
            // Верификация пользователя (только для админов)
            post("/{id}/verify") {
                val principal = call.principal<JWTPrincipal>()
                val adminAccessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (adminAccessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@post
                }
                
                val targetUserId = call.parameters["id"] 
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    Users.update({ Users.id eq targetUserId }) {
                        it[isVerified] = true
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
            
            // Снятие верификации (только для админов)
            delete("/{id}/verify") {
                val principal = call.principal<JWTPrincipal>()
                val adminAccessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (adminAccessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@delete
                }
                
                val targetUserId = call.parameters["id"] 
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    Users.update({ Users.id eq targetUserId }) {
                        it[isVerified] = false
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
            
            // Бан пользователя (только для админов)
            post("/{id}/ban") {
                val principal = call.principal<JWTPrincipal>()
                val adminId = principal?.payload?.getClaim("userId")?.asString()
                val adminAccessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (adminAccessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@post
                }
                
                val targetUserId = call.parameters["id"] 
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<BanUserRequest>()
                
                transaction {
                    Users.update({ Users.id eq targetUserId }) {
                        it[isBanned] = true
                        it[banReason] = request.reason
                        it[bannedAt] = System.currentTimeMillis()
                        it[bannedBy] = adminId
                    }
                }
                
                println("User $targetUserId banned by $adminId. Reason: ${request.reason}")
                call.respond(mapOf("success" to true))
            }
            
            // Разбан пользователя (только для админов)
            delete("/{id}/ban") {
                val principal = call.principal<JWTPrincipal>()
                val adminAccessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (adminAccessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@delete
                }
                
                val targetUserId = call.parameters["id"] 
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    Users.update({ Users.id eq targetUserId }) {
                        it[isBanned] = false
                        it[banReason] = null
                        it[bannedAt] = null
                        it[bannedBy] = null
                    }
                }
                
                println("User $targetUserId unbanned")
                call.respond(mapOf("success" to true))
            }
            
            // Подача апелляции на бан
            post("/appeal") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<AppealRequest>()
                
                // Здесь можно сохранить апелляцию в БД или отправить на email админу
                println("Appeal from user $userId: ${request.message}")
                
                call.respond(mapOf("success" to true, "message" to "Апелляция отправлена"))
            }
        }
    }
}

@Serializable
data class UpdateProfileRequest(
    val displayName: String,
    val bio: String? = null
)

@Serializable
data class BanUserRequest(
    val reason: String
)

@Serializable
data class AppealRequest(
    val message: String
)

@Serializable
data class UpdateAvatarRequest(
    val avatarUrl: String
)

@Serializable
data class UpdateEmojiStatusRequest(
    val emojiStatus: String?
)

@Serializable
data class FcmTokenRequest(
    val token: String,
    val deviceId: String? = null,
    val deviceName: String? = null
)

fun Route.fcmTokenRoute() {
    route("/api/users") {
        authenticate("auth-jwt") {
            // Обновление FCM токена устройства
            post("/fcm-token") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<FcmTokenRequest>()
                val deviceId = request.deviceId ?: "default-device"
                val now = System.currentTimeMillis()
                
                println("FCM Token update for user $userId, device $deviceId: ${request.token.take(20)}...")
                
                transaction {
                    // Проверяем существует ли запись для этого устройства
                    val existing = DeviceTokens.select { 
                        (DeviceTokens.userId eq userId) and (DeviceTokens.deviceId eq deviceId)
                    }.singleOrNull()
                    
                    if (existing != null) {
                        // Обновляем существующий токен
                        DeviceTokens.update({ 
                            (DeviceTokens.userId eq userId) and (DeviceTokens.deviceId eq deviceId)
                        }) {
                            it[fcmToken] = request.token
                            it[deviceName] = request.deviceName
                            it[updatedAt] = now
                        }
                        println("FCM Token updated for existing device")
                    } else {
                        // Создаём новую запись
                        DeviceTokens.insert {
                            it[id] = java.util.UUID.randomUUID().toString()
                            it[DeviceTokens.userId] = userId
                            it[fcmToken] = request.token
                            it[DeviceTokens.deviceId] = deviceId
                            it[deviceName] = request.deviceName
                            it[platform] = "android"
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        println("FCM Token created for new device")
                    }
                    
                    // Также обновляем в таблице Users для обратной совместимости
                    Users.update({ Users.id eq userId }) {
                        it[Users.fcmToken] = request.token
                    }
                }
                
                // Проверяем что токен сохранился
                val savedTokens = transaction {
                    DeviceTokens.select { DeviceTokens.userId eq userId }.count()
                }
                
                println("FCM Token saved. User $userId now has $savedTokens device(s)")
                
                call.respond(mapOf("success" to true, "deviceCount" to savedTokens))
            }
            
            // Получить все устройства пользователя
            get("/devices") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val devices = transaction {
                    DeviceTokens.select { DeviceTokens.userId eq userId }.map { row ->
                        mapOf(
                            "deviceId" to row[DeviceTokens.deviceId],
                            "deviceName" to row[DeviceTokens.deviceName],
                            "platform" to row[DeviceTokens.platform],
                            "updatedAt" to row[DeviceTokens.updatedAt]
                        )
                    }
                }
                
                call.respond(devices)
            }
            
            // Удалить устройство
            delete("/devices/{deviceId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val deviceId = call.parameters["deviceId"] 
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    DeviceTokens.deleteWhere { 
                        (DeviceTokens.userId eq userId) and (DeviceTokens.deviceId eq deviceId)
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
            
            // Проверка FCM токена пользователя (для отладки)
            get("/fcm-token/{userId}") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                // Только админы могут проверять токены
                if (accessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                val targetUserId = call.parameters["userId"] 
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val devices = transaction {
                    DeviceTokens.select { DeviceTokens.userId eq targetUserId }.map { row ->
                        mapOf(
                            "deviceId" to row[DeviceTokens.deviceId],
                            "tokenPreview" to row[DeviceTokens.fcmToken].take(20),
                            "platform" to row[DeviceTokens.platform]
                        )
                    }
                }
                
                call.respond(mapOf(
                    "userId" to targetUserId,
                    "deviceCount" to devices.size,
                    "devices" to devices
                ))
            }
        }
    }
}

// === CHAT ROUTES ===

@Serializable
data class CreateChatRequest(
    val type: String,
    val name: String,
    val participantIds: List<String>
)

@Serializable
data class ChatResponse(
    val id: String,
    val type: String,
    val name: String,
    val participants: List<String>,
    val participantNames: Map<String, String> = emptyMap(), // userId -> displayName
    val createdAt: Long
)

fun Route.chatRoutes() {
    route("/api/chats") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateChatRequest>()
                val allParticipants = (listOf(userId) + request.participantIds).distinct().sorted()
                
                // Для direct чатов проверяем существующий
                if (request.type == "direct" && request.participantIds.size == 1) {
                    val existingChat = transaction {
                        // Ищем существующий direct чат между этими пользователями
                        val userChats = (Chats innerJoin ChatParticipants)
                            .select { ChatParticipants.userId eq userId }
                            .filter { it[Chats.type] == "direct" }
                            .map { it[Chats.id] }
                        
                        userChats.firstOrNull { chatId ->
                            val chatParticipants = ChatParticipants
                                .select { ChatParticipants.chatId eq chatId }
                                .map { it[ChatParticipants.userId] }
                                .sorted()
                            chatParticipants == allParticipants
                        }?.let { chatId ->
                            val row = Chats.select { Chats.id eq chatId }.single()
                            val participants = ChatParticipants
                                .select { ChatParticipants.chatId eq chatId }
                                .map { it[ChatParticipants.userId] }
                            val participantNames = Users
                                .select { Users.id inList participants }
                                .associate { it[Users.id] to it[Users.displayName] }
                            val otherUserId = participants.find { it != userId }
                            val chatName = participantNames[otherUserId] ?: row[Chats.name]
                            
                            ChatResponse(
                                id = chatId,
                                type = row[Chats.type],
                                name = chatName,
                                participants = participants,
                                participantNames = participantNames,
                                createdAt = row[Chats.createdAt]
                            )
                        }
                    }
                    
                    if (existingChat != null) {
                        println("Returning existing chat: ${existingChat.id}")
                        return@post call.respond(HttpStatusCode.OK, existingChat)
                    }
                }
                
                val chatId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                val (chatName, participantNames) = transaction {
                    // Получаем имена всех участников
                    val names = Users
                        .select { Users.id inList allParticipants }
                        .associate { it[Users.id] to it[Users.displayName] }
                    
                    // Для личных чатов используем имя собеседника
                    val finalName = if (request.type == "direct" && request.participantIds.size == 1) {
                        names[request.participantIds.first()] ?: request.name
                    } else {
                        request.name
                    }
                    
                    Chats.insert {
                        it[id] = chatId
                        it[type] = request.type
                        it[name] = finalName
                        it[createdAt] = now
                        it[encryptionKeyId] = UUID.randomUUID().toString()
                    }
                    
                    // Добавляем создателя
                    ChatParticipants.insert {
                        it[ChatParticipants.chatId] = chatId
                        it[ChatParticipants.userId] = userId
                        it[role] = "admin"
                        it[joinedAt] = now
                    }
                    
                    // Добавляем участников
                    request.participantIds.forEach { participantId ->
                        if (participantId != userId) {
                            ChatParticipants.insert {
                                it[ChatParticipants.chatId] = chatId
                                it[ChatParticipants.userId] = participantId
                                it[role] = "member"
                                it[joinedAt] = now
                            }
                        }
                    }
                    
                    finalName to names
                }
                
                println("Created new chat: $chatId")
                call.respond(HttpStatusCode.Created, ChatResponse(
                    id = chatId,
                    type = request.type,
                    name = chatName,
                    participants = allParticipants,
                    participantNames = participantNames,
                    createdAt = now
                ))
            }
            
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val chats = transaction {
                    (Chats innerJoin ChatParticipants)
                        .select { ChatParticipants.userId eq userId }
                        .map { row ->
                            val chatId = row[Chats.id]
                            val participants = ChatParticipants
                                .select { ChatParticipants.chatId eq chatId }
                                .map { it[ChatParticipants.userId] }
                            
                            // Получаем имена участников
                            val participantNames = Users
                                .select { Users.id inList participants }
                                .associate { it[Users.id] to it[Users.displayName] }
                            
                            // Для личных чатов показываем имя собеседника
                            val chatName = if (row[Chats.type] == "direct" && participants.size == 2) {
                                val otherUserId = participants.find { it != userId }
                                participantNames[otherUserId] ?: row[Chats.name]
                            } else {
                                row[Chats.name]
                            }
                            
                            ChatResponse(
                                id = chatId,
                                type = row[Chats.type],
                                name = chatName,
                                participants = participants,
                                participantNames = participantNames,
                                createdAt = row[Chats.createdAt]
                            )
                        }
                }
                call.respond(chats)
            }
            
            // Удалить чат
            delete("/{chatId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val chatId = call.parameters["chatId"] 
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                // Проверяем что пользователь участник чата
                val isParticipant = transaction {
                    ChatParticipants.select { 
                        (ChatParticipants.chatId eq chatId) and 
                        (ChatParticipants.userId eq userId)
                    }.count() > 0
                }
                
                if (!isParticipant) {
                    return@delete call.respond(HttpStatusCode.Forbidden, "Not a participant")
                }
                
                transaction {
                    // Удаляем сообщения
                    Messages.deleteWhere { Messages.chatId eq chatId }
                    // Удаляем участников
                    ChatParticipants.deleteWhere { ChatParticipants.chatId eq chatId }
                    // Удаляем чат
                    Chats.deleteWhere { Chats.id eq chatId }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
    }
}

// === MESSAGE ROUTES ===

@Serializable
data class SendMessageRequest(
    val chatId: String,
    val encryptedContent: String,
    val nonce: String,
    val type: String = "TEXT"
)

@Serializable
data class MessageResponse(
    val id: String,
    val chatId: String,
    val senderId: String,
    val encryptedContent: String,
    val nonce: String,
    val timestamp: Long,
    val type: String,
    val status: String
)

fun Route.messageRoutes() {
    route("/api/messages") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<SendMessageRequest>()
                val messageId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    Messages.insert {
                        it[id] = messageId
                        it[chatId] = request.chatId
                        it[senderId] = userId
                        it[encryptedContent] = request.encryptedContent
                        it[nonce] = request.nonce
                        it[timestamp] = now
                        it[type] = request.type
                        it[status] = "SENT"
                    }
                }
                
                // Отправляем через WebSocket участникам чата
                val participants = transaction {
                    ChatParticipants
                        .select { ChatParticipants.chatId eq request.chatId }
                        .map { it[ChatParticipants.userId] }
                }
                
                // Получаем имя отправителя для уведомлений
                val senderName = transaction {
                    Users.select { Users.id eq userId }.singleOrNull()?.get(Users.displayName) ?: "Пользователь"
                }
                
                // Формируем полные данные сообщения для WebSocket
                val messagePayload = """{"id":"$messageId","chatId":"${request.chatId}","senderId":"$userId","senderName":"$senderName","content":"${request.encryptedContent}","nonce":"${request.nonce}","timestamp":$now,"type":"${request.type}","status":"SENT"}"""
                val wsMessage = WsMessage("new_message", messagePayload)
                
                participants.forEach { participantId ->
                    if (participantId != userId) {
                        // Пробуем отправить через WebSocket
                        val sent = ConnectionManager.sendToUser(participantId, wsMessage)
                        
                        // Если пользователь офлайн - отправляем FCM
                        if (!sent) {
                            val fcmToken = transaction {
                                Users.select { Users.id eq participantId }.singleOrNull()?.get(Users.fcmToken)
                            }
                            if (fcmToken != null) {
                                kotlinx.coroutines.GlobalScope.launch {
                                    com.pioneer.service.FcmService.sendMessageNotification(
                                        fcmToken = fcmToken,
                                        chatId = request.chatId,
                                        senderId = userId,
                                        senderName = senderName,
                                        messageText = "Новое сообщение"
                                    )
                                }
                            }
                        }
                    }
                }
                
                call.respond(HttpStatusCode.Created, MessageResponse(
                    id = messageId,
                    chatId = request.chatId,
                    senderId = userId,
                    encryptedContent = request.encryptedContent,
                    nonce = request.nonce,
                    timestamp = now,
                    type = request.type,
                    status = "SENT"
                ))
            }
            
            get("/{chatId}") {
                val chatId = call.parameters["chatId"] 
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val messages = transaction {
                    Messages.select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp, SortOrder.DESC)
                        .limit(100)
                        .map { row ->
                            MessageResponse(
                                id = row[Messages.id],
                                chatId = row[Messages.chatId],
                                senderId = row[Messages.senderId],
                                encryptedContent = row[Messages.encryptedContent],
                                nonce = row[Messages.nonce],
                                timestamp = row[Messages.timestamp],
                                type = row[Messages.type],
                                status = row[Messages.status]
                            )
                        }
                }
                call.respond(messages)
            }
        }
    }
}

// === TASK ROUTES ===

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String,
    val priority: String,
    val dueDate: Long? = null,
    val assigneeIds: List<String> = emptyList()
)

fun Route.taskRoutes() {
    route("/api/tasks") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateTaskRequest>()
                val taskId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    Tasks.insert {
                        it[id] = taskId
                        it[title] = request.title
                        it[description] = request.description
                        it[creatorId] = userId
                        it[status] = "PENDING"
                        it[priority] = request.priority
                        it[dueDate] = request.dueDate
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to taskId))
            }
            
            get {
                val tasks = transaction {
                    Tasks.selectAll()
                        .orderBy(Tasks.createdAt, SortOrder.DESC)
                        .map { row ->
                            mapOf(
                                "id" to row[Tasks.id],
                                "title" to row[Tasks.title],
                                "description" to row[Tasks.description],
                                "status" to row[Tasks.status],
                                "priority" to row[Tasks.priority],
                                "dueDate" to row[Tasks.dueDate],
                                "createdAt" to row[Tasks.createdAt]
                            )
                        }
                }
                call.respond(tasks)
            }
        }
    }
}

// === FINANCE ROUTES ===

@Serializable
data class CreateFinanceRequest(
    val type: String,
    val category: String,
    val amount: Double,
    val description: String
)

fun Route.financeRoutes() {
    route("/api/finance") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val request = call.receive<CreateFinanceRequest>()
                val recordId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    FinanceRecords.insert {
                        it[id] = recordId
                        it[type] = request.type
                        it[category] = request.category
                        it[amount] = request.amount
                        it[currency] = "RUB"
                        it[description] = request.description
                        it[createdBy] = userId
                        it[createdAt] = now
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to recordId))
            }
            
            get {
                val records = transaction {
                    FinanceRecords.selectAll()
                        .orderBy(FinanceRecords.createdAt, SortOrder.DESC)
                        .map { row ->
                            mapOf(
                                "id" to row[FinanceRecords.id],
                                "type" to row[FinanceRecords.type],
                                "category" to row[FinanceRecords.category],
                                "amount" to row[FinanceRecords.amount],
                                "description" to row[FinanceRecords.description],
                                "createdAt" to row[FinanceRecords.createdAt]
                            )
                        }
                }
                call.respond(records)
            }
        }
    }
}

// === MAP ROUTES ===

@Serializable
data class CreateMarkerRequest(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String?,
    val type: String
)

fun Route.mapRoutes() {
    route("/api/map") {
        authenticate("auth-jwt") {
            post("/markers") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val accessLevel = principal.payload.getClaim("accessLevel")?.asInt() ?: 1
                
                val request = call.receive<CreateMarkerRequest>()
                val markerId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                transaction {
                    MapMarkers.insert {
                        it[id] = markerId
                        it[latitude] = request.latitude
                        it[longitude] = request.longitude
                        it[title] = request.title
                        it[description] = request.description
                        it[type] = request.type
                        it[createdBy] = userId
                        it[MapMarkers.accessLevel] = accessLevel
                        it[createdAt] = now
                    }
                }
                
                call.respond(HttpStatusCode.Created, mapOf("id" to markerId))
            }
            
            get("/markers") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                val markers = transaction {
                    MapMarkers.select { MapMarkers.accessLevel lessEq accessLevel }
                        .map { row ->
                            mapOf(
                                "id" to row[MapMarkers.id],
                                "latitude" to row[MapMarkers.latitude],
                                "longitude" to row[MapMarkers.longitude],
                                "title" to row[MapMarkers.title],
                                "description" to row[MapMarkers.description],
                                "type" to row[MapMarkers.type]
                            )
                        }
                }
                call.respond(markers)
            }
        }
    }
}


// === REACTION ROUTES ===

@Serializable
data class AddReactionRequest(
    val emoji: String
)

@Serializable
data class ReactionResponse(
    val emoji: String,
    val count: Int,
    val users: List<String>,
    val hasReacted: Boolean
)

fun Route.reactionRoutes() {
    route("/api/messages") {
        authenticate("auth-jwt") {
            // Добавить реакцию на сообщение
            post("/{messageId}/reactions") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val messageId = call.parameters["messageId"] 
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val request = call.receive<AddReactionRequest>()
                val now = System.currentTimeMillis()
                
                // Проверяем существующую реакцию
                val existingReaction = transaction {
                    MessageReactions.select { 
                        (MessageReactions.messageId eq messageId) and 
                        (MessageReactions.userId eq userId) and 
                        (MessageReactions.emoji eq request.emoji)
                    }.singleOrNull()
                }
                
                if (existingReaction != null) {
                    // Удаляем реакцию (toggle)
                    transaction {
                        MessageReactions.deleteWhere { 
                            (MessageReactions.messageId eq messageId) and 
                            (MessageReactions.userId eq userId) and 
                            (MessageReactions.emoji eq request.emoji)
                        }
                    }
                    call.respond(mapOf("action" to "removed", "emoji" to request.emoji))
                } else {
                    // Добавляем реакцию
                    transaction {
                        MessageReactions.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[MessageReactions.messageId] = messageId
                            it[MessageReactions.userId] = userId
                            it[emoji] = request.emoji
                            it[createdAt] = now
                        }
                    }
                    call.respond(HttpStatusCode.Created, mapOf("action" to "added", "emoji" to request.emoji))
                }
                
                // Уведомляем участников чата через WebSocket
                val chatId = transaction {
                    Messages.select { Messages.id eq messageId }.singleOrNull()?.get(Messages.chatId)
                }
                
                if (chatId != null) {
                    val participants = transaction {
                        ChatParticipants.select { ChatParticipants.chatId eq chatId }
                            .map { it[ChatParticipants.userId] }
                    }
                    
                    val wsMessage = WsMessage("reaction_update", messageId)
                    participants.forEach { participantId ->
                        ConnectionManager.sendToUser(participantId, wsMessage)
                    }
                }
            }
            
            // Получить реакции на сообщение
            get("/{messageId}/reactions") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val messageId = call.parameters["messageId"] 
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val reactions = transaction {
                    MessageReactions.select { MessageReactions.messageId eq messageId }
                        .groupBy { it[MessageReactions.emoji] }
                        .map { (emoji, rows) ->
                            val users = rows.map { it[MessageReactions.userId] }
                            ReactionResponse(
                                emoji = emoji,
                                count = users.size,
                                users = users,
                                hasReacted = users.contains(userId)
                            )
                        }
                }
                
                call.respond(reactions)
            }
        }
    }
}

// ChannelRoutes перенесены в отдельный файл ChannelRoutes.kt
