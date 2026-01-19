package com.pioneer.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: EncryptedContent,
    val timestamp: Long,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val replyToId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val isEdited: Boolean = false,
    val editedAt: Long? = null
)

@Serializable
data class EncryptedContent(
    val ciphertext: String,
    val nonce: String,
    val senderKeyId: String
)

@Serializable
enum class MessageType {
    TEXT, IMAGE, FILE, AUDIO, VOICE, LOCATION, TASK_UPDATE, SYSTEM
}

@Serializable
enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

@Serializable
data class Attachment(
    val id: String,
    val type: AttachmentType,
    val name: String,
    val size: Long,
    val mimeType: String,
    val encryptedUrl: String,
    val thumbnailUrl: String? = null
)

@Serializable
enum class AttachmentType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE
}
