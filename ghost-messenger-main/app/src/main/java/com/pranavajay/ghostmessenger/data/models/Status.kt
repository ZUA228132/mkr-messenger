package com.pranavajay.ghostmessenger.data.models

data class Status(
    val id: String,
    val userId: String,
    val username: String? = null,
    val profileImage: String? = null,
    val content: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String = "IMAGE", // IMAGE, VIDEO, TEXT
    val backgroundColor: String? = null,
    val fontStyle: String? = null,
    val viewedBy: List<String> = emptyList(),
    val viewedByMe: Boolean = false,
    val viewCount: Int = 0,
    val reactions: Map<String, List<String>> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
)

data class StatusGroup(
    val userId: String,
    val username: String,
    val profileImage: String? = null,
    val statuses: List<Status>,
    val hasUnviewed: Boolean = false
)

data class CreateStatusRequest(
    val content: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String = "IMAGE",
    val backgroundColor: String? = null,
    val fontStyle: String? = null
)

data class StatusReaction(
    val statusId: String,
    val userId: String,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis()
)
