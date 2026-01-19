package com.pranavajay.ghostmessenger.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null,
    val replyToId: String? = null,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val isSent: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, VOICE
}

data class SendMessageRequest(
    val chatId: String,
    val receiverId: String,
    val content: String,
    val type: String = "TEXT",
    val mediaUrl: String? = null,
    val replyToId: String? = null
)

data class Chat(
    val id: String,
    val userId: String,
    val username: String,
    val profileImage: String?,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val disappearingMode: Boolean = false,
    val disappearAfter: Long? = null
)

// Request models
data class CreateChatRequest(
    val participantId: String,
    val disappearingMode: Boolean = false,
    val disappearAfter: Long? = null
)

data class DisappearingSettingsRequest(
    val duration: Long?
)

data class GhostModeRequest(
    val enabled: Boolean
)

data class PinChatRequest(
    val pinned: Boolean
)

data class ArchiveChatRequest(
    val archived: Boolean
)

data class ForwardMessageRequest(
    val messageId: String,
    val toChatIds: List<String>
)

data class AddReactionRequest(
    val messageId: String,
    val emoji: String
)

data class RemoveReactionRequest(
    val messageId: String,
    val emoji: String
)

data class InitiateCallRequest(
    val receiverId: String,
    val type: String = "VOICE",
    val groupId: String? = null
)

data class CallResponse(
    val callId: String,
    val roomId: String,
    val iceServers: List<IceServer>
)

data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class CallEntity(
    val id: String,
    val callerId: String,
    val receiverId: String,
    val groupId: String? = null,
    val type: String = "VOICE",
    val status: String = "INITIATED",
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val duration: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class SearchResult(
    val messages: List<Message>,
    val users: List<User>,
    val chats: List<Chat>
)

data class Channel(
    val id: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val creatorId: String,
    val subscriberCount: Int = 0,
    val isPublic: Boolean = true,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis()
)

data class CreateChannelRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = true
)

data class Reaction(
    val emoji: String,
    val userIds: List<String>
)
