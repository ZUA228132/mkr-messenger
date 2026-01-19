package com.pioneer.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val publicKey: String,
    val avatarUrl: String? = null,
    val accessLevel: AccessLevel = AccessLevel.USER,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0
)

@Serializable
enum class AccessLevel(val level: Int) {
    VIEWER(0),      // Только просмотр
    USER(1),        // Обычный пользователь
    MODERATOR(2),   // Модератор
    ADMIN(3),       // Администратор
    OWNER(4)        // Владелец системы
}
