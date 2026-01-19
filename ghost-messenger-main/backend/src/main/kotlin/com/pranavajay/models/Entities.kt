package com.pranavajay.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId

@Serializable
data class UserEntity(
    @BsonId val id: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val phone: String? = null,
    val profileImage: String? = null,
    val bio: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val fcmToken: String? = null,
    val publicKey: String? = null, // For E2E encryption
    val contacts: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(),
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class MessageEntity(
    @BsonId val id: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val type: String = "TEXT", // TEXT, IMAGE, VIDEO, AUDIO, VOICE, DOCUMENT, LOCATION, CONTACT, STICKER
    val mediaUrl: String? = null,
    val mediaThumbnail: String? = null,
    val mediaDuration: Int? = null, // For voice/video in seconds
    val mediaSize: Long? = null,
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val forwardedFrom: String? = null,
    val isForwarded: Boolean = false,
    val forwardedFromId: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> list of userIds
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedFor: List<String> = emptyList(), // User IDs who deleted this message for themselves
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    // Disappearing messages (Ghost Mode)
    val disappearAfter: Long? = null, // Duration in milliseconds (null = permanent)
    val expiresAt: Long? = null, // Calculated expiry timestamp
    val isScreenshotted: Boolean = false,
    val screenshottedBy: List<String> = emptyList(),
    // Encryption
    val isEncrypted: Boolean = false,
    val encryptedContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatEntity(
    @BsonId val id: String,
    val participants: List<String>,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessageType: String = "TEXT",
    val unreadCount: Map<String, Int> = emptyMap(),
    // Ghost Mode settings per chat
    val disappearingMode: Boolean = false,
    val disappearAfter: Long = 24 * 60 * 60 * 1000, // Default 24 hours
    val isEncrypted: Boolean = false,
    val encryptionKey: String? = null,
    val isPinned: Map<String, Boolean> = emptyMap(),
    val isMuted: Map<String, Boolean> = emptyMap(),
    val isArchived: Map<String, Boolean> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class GroupEntity(
    @BsonId val id: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val creatorId: String,
    val admins: List<String>,
    val members: List<String>,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val disappearingMode: Boolean = false,
    val disappearAfter: Long = 24 * 60 * 60 * 1000,
    val onlyAdminsCanPost: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class StatusEntity(
    @BsonId val id: String,
    val userId: String,
    val content: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String = "IMAGE", // IMAGE, VIDEO, TEXT
    val backgroundColor: String? = null,
    val fontStyle: String? = null,
    val viewedBy: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
)

@Serializable
data class ChannelEntity(
    @BsonId val id: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val creatorId: String,
    val admins: List<String>,
    val subscribers: List<String>,
    val subscriberCount: Int = 0,
    val isPublic: Boolean = true,
    val inviteLink: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CallEntity(
    @BsonId val id: String,
    val callerId: String,
    val receiverId: String,
    val groupId: String? = null,
    val type: String = "VOICE", // VOICE, VIDEO
    val status: String = "INITIATED", // INITIATED, RINGING, ONGOING, ENDED, MISSED, DECLINED
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val duration: Int = 0, // in seconds
    val participants: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ReactionEntity(
    @BsonId val id: String,
    val messageId: String,
    val userId: String,
    val emoji: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Request/Response DTOs
@Serializable
data class CreateChatRequest(
    val participantId: String,
    val disappearingMode: Boolean = false,
    val disappearAfter: Long? = null
)

@Serializable
data class SendMessageRequest(
    val chatId: String,
    val receiverId: String,
    val content: String,
    val type: String = "TEXT",
    val mediaUrl: String? = null,
    val mediaThumbnail: String? = null,
    val mediaDuration: Int? = null,
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val forwardedFrom: String? = null,
    val disappearAfter: Long? = null,
    val isEncrypted: Boolean = false,
    val encryptedContent: String? = null
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val members: List<String>,
    val disappearingMode: Boolean = false
)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = true
)

@Serializable
data class CreateStatusRequest(
    val content: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String = "IMAGE",
    val backgroundColor: String? = null,
    val fontStyle: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val bio: String? = null,
    val profileImage: String? = null,
    val phone: String? = null,
    val publicKey: String? = null
)

@Serializable
data class UpdateFcmTokenRequest(
    val token: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val participantId: String,
    val participantName: String,
    val participantImage: String?,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val lastMessageType: String = "TEXT",
    val unreadCount: Int,
    val isOnline: Boolean,
    val disappearingMode: Boolean = false,
    val disappearAfter: Long? = null,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)

@Serializable
data class AddReactionRequest(
    val messageId: String,
    val emoji: String
)

@Serializable
data class RemoveReactionRequest(
    val messageId: String,
    val emoji: String
)

@Serializable
data class UpdateDisappearingModeRequest(
    val chatId: String,
    val enabled: Boolean,
    val duration: Long? = null // in milliseconds
)

@Serializable
data class InitiateCallRequest(
    val receiverId: String,
    val type: String = "VOICE", // VOICE or VIDEO
    val groupId: String? = null
)

@Serializable
data class CallResponse(
    val callId: String,
    val roomId: String,
    val iceServers: List<IceServer>
)

@Serializable
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class ForwardMessageRequest(
    val messageId: String,
    val toChatIds: List<String>
)

@Serializable
data class SearchRequest(
    val query: String,
    val chatId: String? = null, // null = global search
    val type: String? = null // TEXT, IMAGE, VIDEO, etc.
)

@Serializable
data class SearchResult(
    val messages: List<MessageEntity>,
    val users: List<User>,
    val chats: List<ChatResponse>
)

@Serializable
data class OtpVerifyRequest(
    val email: String,
    val otp: String
)

@Serializable
data class UploadResponse(
    val url: String,
    val key: String,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val size: Long? = null
)

@Serializable
data class ScreenshotNotification(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TypingIndicator(
    val chatId: String,
    val userId: String,
    val isTyping: Boolean
)

@Serializable
data class VoiceMessageRequest(
    val chatId: String,
    val receiverId: String,
    val audioUrl: String,
    val duration: Int,
    val waveform: List<Float>? = null
)

@Serializable
data class DisappearingSettingsRequest(
    val duration: Long? // null = disable, value in milliseconds
)

@Serializable
data class GhostModeRequest(
    val enabled: Boolean
)

@Serializable
data class PinChatRequest(
    val pinned: Boolean
)

@Serializable
data class ArchiveChatRequest(
    val archived: Boolean
)
