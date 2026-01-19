package com.pioneer.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

// Tables
object Users : Table("users") {
    val id = varchar("id", 36)
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex().nullable() // Email для регистрации
    val emailVerified = bool("email_verified").default(false) // Подтверждён ли email
    val emailVerificationCode = varchar("email_verification_code", 10).nullable() // Код подтверждения
    val emailVerificationExpires = long("email_verification_expires").nullable() // Срок действия кода
    val phone = varchar("phone", 20).uniqueIndex().nullable() // Телефон для регистрации
    val phoneVerified = bool("phone_verified").default(false) // Подтверждён ли телефон
    val phoneVerificationCheckId = varchar("phone_verification_check_id", 50).nullable() // ID проверки sms.ru
    val phoneVerificationExpires = long("phone_verification_expires").nullable() // Срок действия проверки
    val passwordHash = varchar("password_hash", 128).nullable() // Хеш пароля
    val displayName = varchar("display_name", 100)
    val publicKey = text("public_key")
    val accessLevel = integer("access_level").default(1)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val bio = text("bio").nullable()
    val isVerified = bool("is_verified").default(false)
    val isBanned = bool("is_banned").default(false) // Заблокирован ли пользователь
    val banReason = text("ban_reason").nullable() // Причина блокировки
    val bannedAt = long("banned_at").nullable() // Когда заблокирован
    val bannedBy = varchar("banned_by", 36).nullable() // Кто заблокировал
    val emojiStatus = varchar("emoji_status", 10).nullable() // Эмодзи-статус пользователя
    val pinHash = varchar("pin_hash", 128).nullable() // Хеш PIN-кода
    val failedAttempts = integer("failed_attempts").default(0) // Неудачные попытки входа
    val lockedUntil = long("locked_until").nullable() // Блокировка до
    val fcmToken = varchar("fcm_token", 500).nullable() // Firebase Cloud Messaging token
    val createdAt = long("created_at")
    val lastSeen = long("last_seen").nullable()
    
    // Геолокация
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val locationUpdatedAt = long("location_updated_at").nullable()
    
    // Настройки приватности
    val ghostMode = bool("ghost_mode").default(false) // Скрыть с карты
    val whoCanCall = varchar("who_can_call", 20).default("everyone") // everyone, contacts, nobody
    val whoCanSeeAvatar = varchar("who_can_see_avatar", 20).default("everyone")
    val whoCanMessage = varchar("who_can_message", 20).default("everyone")
    val whoCanFindMe = varchar("who_can_find_me", 20).default("everyone")
    
    override val primaryKey = PrimaryKey(id)
}

object Chats : Table("chats") {
    val id = varchar("id", 36)
    val type = varchar("type", 20)
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val isVerified = bool("is_verified").default(false)
    val createdAt = long("created_at")
    val encryptionKeyId = varchar("encryption_key_id", 36)
    
    override val primaryKey = PrimaryKey(id)
}

object ChatParticipants : Table("chat_participants") {
    val chatId = varchar("chat_id", 36).references(Chats.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val role = varchar("role", 20).default("member")
    val joinedAt = long("joined_at")
    
    override val primaryKey = PrimaryKey(chatId, userId)
}

object Messages : Table("messages") {
    val id = varchar("id", 36)
    val chatId = varchar("chat_id", 36).references(Chats.id)
    val senderId = varchar("sender_id", 36).references(Users.id)
    val encryptedContent = text("encrypted_content")
    val nonce = varchar("nonce", 50)
    val timestamp = long("timestamp")
    val type = varchar("type", 20)
    val status = varchar("status", 20)
    
    override val primaryKey = PrimaryKey(id)
}

object Tasks : Table("tasks") {
    val id = varchar("id", 36)
    val title = varchar("title", 200)
    val description = text("description")
    val creatorId = varchar("creator_id", 36).references(Users.id)
    val status = varchar("status", 20)
    val priority = varchar("priority", 20)
    val dueDate = long("due_date").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    
    override val primaryKey = PrimaryKey(id)
}

object FinanceRecords : Table("finance_records") {
    val id = varchar("id", 36)
    val type = varchar("type", 20)
    val category = varchar("category", 50)
    val amount = double("amount")
    val currency = varchar("currency", 10)
    val description = text("description")
    val createdBy = varchar("created_by", 36).references(Users.id)
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

object MapMarkers : Table("map_markers") {
    val id = varchar("id", 36)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val title = varchar("title", 100)
    val description = text("description").nullable()
    val type = varchar("type", 20)
    val createdBy = varchar("created_by", 36).references(Users.id)
    val accessLevel = integer("access_level")
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

object InviteKeys : Table("invite_keys") {
    val id = varchar("id", 36)
    val keyHash = varchar("key_hash", 64).uniqueIndex()
    val accessLevel = integer("access_level")
    val createdBy = varchar("created_by", 36).references(Users.id)
    val usedBy = varchar("used_by", 36).references(Users.id).nullable()
    val createdAt = long("created_at")
    val usedAt = long("used_at").nullable()
    val expiresAt = long("expires_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

// Device tokens for FCM push notifications (supports multiple devices per user)
object DeviceTokens : Table("device_tokens") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(Users.id)
    val fcmToken = varchar("fcm_token", 500)
    val deviceId = varchar("device_id", 100) // Unique device identifier
    val deviceName = varchar("device_name", 100).nullable()
    val platform = varchar("platform", 20).default("android") // android, ios, web
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        uniqueIndex(userId, deviceId) // One token per device per user
    }
}

// Web sessions for QR code login
object WebSessions : Table("web_sessions") {
    val id = varchar("id", 36)
    val sessionCode = varchar("session_code", 64).uniqueIndex() // 6-digit code or QR token
    val userId = varchar("user_id", 36).references(Users.id).nullable()
    val token = varchar("token", 500).nullable() // JWT token after auth
    val status = varchar("status", 20).default("pending") // pending, authorized, expired
    val deviceInfo = varchar("device_info", 200).nullable()
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val authorizedAt = long("authorized_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

// Call history for logging calls
object CallHistory : Table("call_history") {
    val id = varchar("id", 36)
    val callerId = varchar("caller_id", 36).references(Users.id)
    val calleeId = varchar("callee_id", 36).references(Users.id)
    val chatId = varchar("chat_id", 36).references(Chats.id).nullable()
    val roomName = varchar("room_name", 100)
    val isVideo = bool("is_video").default(false)
    val status = varchar("status", 20) // initiated, answered, missed, declined, ended
    val startedAt = long("started_at")
    val answeredAt = long("answered_at").nullable()
    val endedAt = long("ended_at").nullable()
    val duration = integer("duration").default(0) // in seconds
    
    override val primaryKey = PrimaryKey(id)
}

// Message reactions (emoji reactions like Telegram/Discord)
object MessageReactions : Table("message_reactions") {
    val id = varchar("id", 36)
    val messageId = varchar("message_id", 36).references(Messages.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val emoji = varchar("emoji", 10) // Emoji character
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        uniqueIndex(messageId, userId, emoji) // One reaction per emoji per user per message
    }
}

// Channels - extended chat functionality
object Channels : Table("channels") {
    val id = varchar("id", 36)
    val chatId = varchar("chat_id", 36).references(Chats.id).uniqueIndex()
    val username = varchar("username", 50).uniqueIndex().nullable() // @channel_name
    val description = text("description").nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val isPublic = bool("is_public").default(true)
    val subscriberCount = integer("subscriber_count").default(0)
    val allowComments = bool("allow_comments").default(true)
    val slowMode = integer("slow_mode").default(0) // seconds between messages
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Channel subscriptions
object ChannelSubscriptions : Table("channel_subscriptions") {
    val id = varchar("id", 36)
    val channelId = varchar("channel_id", 36).references(Channels.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val notificationsEnabled = bool("notifications_enabled").default(true)
    val subscribedAt = long("subscribed_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        uniqueIndex(channelId, userId)
    }
}

// Channel posts (messages in channels with extra features)
object ChannelPosts : Table("channel_posts") {
    val id = varchar("id", 36)
    val channelId = varchar("channel_id", 36).references(Channels.id)
    val messageId = varchar("message_id", 36).references(Messages.id)
    val viewCount = integer("view_count").default(0)
    val isPinned = bool("is_pinned").default(false)
    val allowComments = bool("allow_comments").default(true)
    val commentCount = integer("comment_count").default(0)
    
    override val primaryKey = PrimaryKey(id)
}

// Post comments
object PostComments : Table("post_comments") {
    val id = varchar("id", 36)
    val postId = varchar("post_id", 36).references(ChannelPosts.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val content = text("content")
    val replyToId = varchar("reply_to_id", 36).nullable() // Reply to another comment
    val createdAt = long("created_at")
    val editedAt = long("edited_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

// Reels - короткие видео
object Reels : Table("reels") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(Users.id)
    val videoUrl = varchar("video_url", 500)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val description = text("description").nullable()
    val duration = integer("duration").default(0) // в секундах
    val viewCount = integer("view_count").default(0)
    val likeCount = integer("like_count").default(0)
    val commentCount = integer("comment_count").default(0)
    val shareCount = integer("share_count").default(0)
    val isPublic = bool("is_public").default(true)
    val allowComments = bool("allow_comments").default(true)
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Лайки Reels
object ReelLikes : Table("reel_likes") {
    val id = varchar("id", 36)
    val reelId = varchar("reel_id", 36).references(Reels.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        uniqueIndex(reelId, userId)
    }
}

// Комментарии к Reels
object ReelComments : Table("reel_comments") {
    val id = varchar("id", 36)
    val reelId = varchar("reel_id", 36).references(Reels.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val content = text("content")
    val replyToId = varchar("reply_to_id", 36).nullable()
    val likeCount = integer("like_count").default(0)
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Stories - истории на 24 часа
object Stories : Table("stories") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(Users.id)
    val mediaUrl = varchar("media_url", 500)
    val mediaType = varchar("media_type", 20) // image, video
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val duration = integer("duration").default(5) // длительность показа в секундах
    val text = text("text").nullable() // Текст на истории
    val textColor = varchar("text_color", 20).nullable()
    val textPosition = varchar("text_position", 50).nullable() // JSON: {x, y}
    val backgroundColor = varchar("background_color", 20).nullable()
    val musicUrl = varchar("music_url", 500).nullable()
    val musicTitle = varchar("music_title", 200).nullable()
    val viewCount = integer("view_count").default(0)
    val replyCount = integer("reply_count").default(0)
    val isHighlight = bool("is_highlight").default(false) // Закреплённая история
    val expiresAt = long("expires_at") // Время истечения (24 часа)
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Просмотры историй
object StoryViews : Table("story_views") {
    val id = varchar("id", 36)
    val storyId = varchar("story_id", 36).references(Stories.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val viewedAt = long("viewed_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        uniqueIndex(storyId, userId)
    }
}

// Реакции на истории
object StoryReactions : Table("story_reactions") {
    val id = varchar("id", 36)
    val storyId = varchar("story_id", 36).references(Stories.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val emoji = varchar("emoji", 10)
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Highlights - закреплённые истории
object StoryHighlights : Table("story_highlights") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(Users.id)
    val title = varchar("title", 100)
    val coverUrl = varchar("cover_url", 500).nullable()
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Связь историй с highlights
object HighlightStories : Table("highlight_stories") {
    val highlightId = varchar("highlight_id", 36).references(StoryHighlights.id)
    val storyId = varchar("story_id", 36).references(Stories.id)
    val addedAt = long("added_at")
    
    override val primaryKey = PrimaryKey(highlightId, storyId)
}

// Музыкальные комнаты - совместное прослушивание
object MusicRooms : Table("music_rooms") {
    val id = varchar("id", 36)
    val chatId = varchar("chat_id", 36).references(Chats.id)
    val hostId = varchar("host_id", 36).references(Users.id) // Кто создал комнату
    val currentTrackUrl = varchar("current_track_url", 500).nullable()
    val currentTrackTitle = varchar("current_track_title", 200).nullable()
    val currentTrackArtist = varchar("current_track_artist", 200).nullable()
    val currentTrackCover = varchar("current_track_cover", 500).nullable()
    val currentPosition = long("current_position").default(0) // Позиция в мс
    val isPlaying = bool("is_playing").default(false)
    val lastSyncAt = long("last_sync_at") // Последняя синхронизация
    val createdAt = long("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

// Участники музыкальной комнаты
object MusicRoomParticipants : Table("music_room_participants") {
    val roomId = varchar("room_id", 36).references(MusicRooms.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val joinedAt = long("joined_at")
    
    override val primaryKey = PrimaryKey(roomId, userId)
}

// История треков в комнате
object MusicRoomHistory : Table("music_room_history") {
    val id = varchar("id", 36)
    val roomId = varchar("room_id", 36).references(MusicRooms.id)
    val trackUrl = varchar("track_url", 500)
    val trackTitle = varchar("track_title", 200)
    val trackArtist = varchar("track_artist", 200).nullable()
    val addedBy = varchar("added_by", 36).references(Users.id)
    val playedAt = long("played_at")
    
    override val primaryKey = PrimaryKey(id)
}

fun Application.configureDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl = System.getenv("DB_URL") 
            ?: environment.config.propertyOrNull("database.url")?.getString() 
            ?: "jdbc:postgresql://localhost:5432/pioneer"
        driverClassName = "org.postgresql.Driver"
        username = System.getenv("DB_USER") 
            ?: environment.config.propertyOrNull("database.user")?.getString() 
            ?: "pioneer"
        password = System.getenv("DB_PASSWORD") 
            ?: environment.config.propertyOrNull("database.password")?.getString() 
            ?: "Pioneer2024"
        maximumPoolSize = 10
    }
    
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
    
    transaction {
        SchemaUtils.create(
            Users,
            Chats,
            ChatParticipants,
            Messages,
            Tasks,
            FinanceRecords,
            MapMarkers,
            InviteKeys,
            DeviceTokens,
            WebSessions,
            CallHistory,
            MessageReactions,
            Channels,
            ChannelSubscriptions,
            ChannelPosts,
            PostComments,
            Reels,
            ReelLikes,
            ReelComments,
            Stories,
            StoryViews,
            StoryReactions,
            StoryHighlights,
            HighlightStories,
            MusicRooms,
            MusicRoomParticipants,
            MusicRoomHistory
        )
        
        // Миграция: добавляем новые колонки для приватности и геолокации
        try {
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS location_updated_at BIGINT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS ghost_mode BOOLEAN DEFAULT false")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS who_can_call VARCHAR(20) DEFAULT 'everyone'")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS who_can_see_avatar VARCHAR(20) DEFAULT 'everyone'")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS who_can_message VARCHAR(20) DEFAULT 'everyone'")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS who_can_find_me VARCHAR(20) DEFAULT 'everyone'")
            println("Privacy columns migration completed")
        } catch (e: Exception) {
            println("Privacy columns already exist or migration error: ${e.message}")
        }
        
        // Создаём системного пользователя если его нет
        val systemUserId = "system-admin"
        val existingSystem = Users.select { Users.id eq systemUserId }.singleOrNull()
        if (existingSystem == null) {
            Users.insert {
                it[id] = systemUserId
                it[username] = "system"
                it[displayName] = "System"
                it[publicKey] = ""
                it[accessLevel] = 100
                it[isVerified] = true
                it[createdAt] = System.currentTimeMillis()
            }
        }
        
        // Создаём СУПЕР-АДМИНА с email makarov@lfrp.ru
        val superAdminId = "super-admin-makarov"
        val superAdminEmail = "makarov@lfrp.ru"
        val existingSuperAdmin = Users.select { Users.id eq superAdminId }.singleOrNull()
        if (existingSuperAdmin == null) {
            val superAdminPasswordHash = hashKey("MKRAdmin2024!") // Пароль по умолчанию
            Users.insert {
                it[id] = superAdminId
                it[username] = "makarov"
                it[email] = superAdminEmail
                it[emailVerified] = true // Уже подтверждён
                it[passwordHash] = superAdminPasswordHash
                it[displayName] = "Makarov"
                it[publicKey] = ""
                it[accessLevel] = 100 // Максимальный уровень
                it[isVerified] = true
                it[createdAt] = System.currentTimeMillis()
            }
            println("SUPER ADMIN created: $superAdminEmail with password: MKRAdmin2024!")
        }
        
        // ПОСТОЯННЫЙ мастер-ключ админа (никогда не истекает, можно использовать много раз)
        val masterAdminKey = "PIONEER-ADMIN-2024-MASTER"
        val masterKeyHash = hashKey(masterAdminKey)
        val existingMasterKey = InviteKeys.select { InviteKeys.keyHash eq masterKeyHash }.singleOrNull()
        if (existingMasterKey == null) {
            InviteKeys.insert {
                it[id] = "master-admin-key"
                it[keyHash] = masterKeyHash
                it[accessLevel] = 10
                it[createdBy] = systemUserId
                it[createdAt] = System.currentTimeMillis()
                it[expiresAt] = null // Никогда не истекает
                // usedBy остаётся null - ключ многоразовый
            }
        }
        
        // Создаём официальный канал MKR если его нет
        val mkrChatId = "mkr-official-channel"
        val mkrChannelId = "mkr-channel-info"
        val existingMkrChat = Chats.select { Chats.id eq mkrChatId }.singleOrNull()
        val existingMkrChannel = Channels.select { Channels.id eq mkrChannelId }.singleOrNull()
        
        if (existingMkrChat == null) {
            // Создаём чат канала
            Chats.insert {
                it[id] = mkrChatId
                it[type] = "channel"
                it[name] = "MKR"
                it[description] = "Официальный канал MKR Messenger. Новости, обновления и анонсы."
                it[isVerified] = true
                it[createdAt] = System.currentTimeMillis()
                it[encryptionKeyId] = "mkr-channel-key"
            }
            println("MKR Official Chat created")
        }
        
        if (existingMkrChannel == null) {
            // Создаём запись в таблице Channels
            Channels.insert {
                it[id] = mkrChannelId
                it[chatId] = mkrChatId
                it[username] = "mkr"
                it[description] = "Официальный канал MKR Messenger. Новости, обновления и анонсы."
                it[avatarUrl] = null // Будет использоваться иконка приложения
                it[isPublic] = true
                it[subscriberCount] = 1_200_000 // 1.2M подписчиков
                it[allowComments] = false // Только админ может писать
                it[slowMode] = 0
                it[createdAt] = System.currentTimeMillis()
            }
            println("MKR Official Channel info created with 1.2M subscribers")
        }
        
        // Проверяем участника канала
        val existingParticipant = ChatParticipants.select { 
            (ChatParticipants.chatId eq mkrChatId) and (ChatParticipants.userId eq "super-admin-makarov") 
        }.singleOrNull()
        
        if (existingParticipant == null) {
            // Добавляем супер-админа как владельца канала
            ChatParticipants.insert {
                it[chatId] = mkrChatId
                it[userId] = "super-admin-makarov"
                it[role] = "owner"
                it[joinedAt] = System.currentTimeMillis()
            }
            println("Super admin added to MKR channel")
        }
    }
}

private fun hashKey(key: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(key.toByteArray())
    return java.util.Base64.getEncoder().encodeToString(hash)
}
