package com.pranavajay.ghostmessenger.data.models

data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val creatorId: String,
    val admins: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    val memberCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessageSender: String? = null,
    val unreadCount: Int = 0,
    val disappearingMode: Boolean = false,
    val disappearAfter: Long = 24 * 60 * 60 * 1000,
    val onlyAdminsCanPost: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class GroupMember(
    val userId: String,
    val username: String,
    val profileImage: String? = null,
    val isAdmin: Boolean = false,
    val isOnline: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
)

data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val members: List<String>,
    val disappearingMode: Boolean = false
)

data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null,
    val image: String? = null,
    val onlyAdminsCanPost: Boolean? = null,
    val disappearingMode: Boolean? = null,
    val disappearAfter: Long? = null
)

data class UpdateProfileRequest(
    val username: String? = null,
    val bio: String? = null,
    val profileImage: String? = null,
    val phone: String? = null
)
