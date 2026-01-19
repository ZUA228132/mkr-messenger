package com.pioneer.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.util.*
import javax.crypto.SecretKey

/**
 * Сервис для генерации LiveKit токенов
 */
object LiveKitService {
    
    private const val API_KEY = "APIKFWAWnPxqi3i"
    private const val API_SECRET = "2FvMLY3FakatsmhAhRNlBC8cTNCgeZKuFKIVqLyH8aH"
    
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(API_SECRET.toByteArray())
    
    /**
     * Генерирует токен для подключения к комнате
     * @param roomName имя комнаты (callId)
     * @param participantName имя участника
     * @param participantIdentity уникальный ID участника (userId)
     * @param canPublish может публиковать аудио/видео
     * @param canSubscribe может получать аудио/видео
     */
    fun generateToken(
        roomName: String,
        participantName: String,
        participantIdentity: String,
        canPublish: Boolean = true,
        canSubscribe: Boolean = true
    ): String {
        val now = Date()
        val expiry = Date(now.time + 6 * 60 * 60 * 1000) // 6 часов
        
        val videoGrant = mapOf(
            "room" to roomName,
            "roomJoin" to true,
            "canPublish" to canPublish,
            "canSubscribe" to canSubscribe,
            "canPublishData" to true
        )
        
        return Jwts.builder()
            .setHeaderParam("typ", "JWT")
            .setIssuer(API_KEY)
            .setSubject(participantIdentity)
            .setIssuedAt(now)
            .setNotBefore(now)
            .setExpiration(expiry)
            .setId(UUID.randomUUID().toString())
            .claim("name", participantName)
            .claim("video", videoGrant)
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact()
    }
    
    /**
     * Генерирует имя комнаты для звонка между двумя пользователями
     */
    fun generateRoomName(userId1: String, userId2: String): String {
        // Сортируем ID чтобы комната была одинаковой независимо от того кто звонит
        val sorted = listOf(userId1, userId2).sorted()
        return "call_${sorted[0]}_${sorted[1]}"
    }
}
