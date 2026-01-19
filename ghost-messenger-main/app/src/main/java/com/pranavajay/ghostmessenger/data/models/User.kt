package com.pranavajay.ghostmessenger.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val phone: String? = null,
    val profileImage: String? = null,
    val bio: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val phone: String? = null
)

data class OtpRequest(
    val email: String,
    val otp: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val user: User? = null,
    val message: String? = null
)
