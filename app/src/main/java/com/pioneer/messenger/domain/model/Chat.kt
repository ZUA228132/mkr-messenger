package com.pioneer.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: String,
    val type: ChatType,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val participants: List<String>,
    val admins: List<String> = emptyList(),
    val createdAt: Long,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val encryptionKeyId: String
)

@Serializable
enum class ChatType {
    PRIVATE,    // Личный чат
    GROUP,      // Групповая беседа
    CHANNEL     // Канал (только админы пишут)
}

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val description: String,
    val avatarUrl: String? = null,
    val ownerId: String,
    val admins: List<String>,
    val subscribersCount: Int,
    val isPublic: Boolean = false,
    val createdAt: Long
)
