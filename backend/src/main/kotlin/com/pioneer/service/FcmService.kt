package com.pioneer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Сервис для отправки Firebase Cloud Messaging уведомлений (API V1)
 */
object FcmService {
    
    private var projectId: String = ""
    private var privateKey: String = ""
    private var clientEmail: String = ""
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    /**
     * Инициализация из файла Service Account
     */
    fun initFromFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                println("FCM: Service account file not found: $filePath")
                return false
            }
            
            val content = file.readText()
            val jsonObj = json.parseToJsonElement(content).jsonObject
            
            projectId = jsonObj["project_id"]?.jsonPrimitive?.content ?: ""
            privateKey = jsonObj["private_key"]?.jsonPrimitive?.content ?: ""
            clientEmail = jsonObj["client_email"]?.jsonPrimitive?.content ?: ""
            
            println("FCM: Initialized for project $projectId")
            true
        } catch (e: Exception) {
            println("FCM: Failed to init from file: ${e.message}")
            false
        }
    }
    
    /**
     * Инициализация из переменных окружения
     */
    fun initFromEnv() {
        projectId = System.getenv("FCM_PROJECT_ID") ?: ""
        clientEmail = System.getenv("FCM_CLIENT_EMAIL") ?: ""
        privateKey = System.getenv("FCM_PRIVATE_KEY")?.replace("\\n", "\n") ?: ""
        
        if (projectId.isNotEmpty()) {
            println("FCM: Initialized from env for project $projectId")
        }
    }
    
    private fun isConfigured(): Boolean = projectId.isNotEmpty() && privateKey.isNotEmpty()
    
    /**
     * Получить OAuth2 access token
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        
        // Проверяем кэшированный токен
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry - 60000) {
            return@withContext accessToken
        }
        
        try {
            val now = System.currentTimeMillis() / 1000
            val exp = now + 3600
            
            // Создаём JWT
            val header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"alg":"RS256","typ":"JWT"}""".toByteArray()
            )
            
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{
                    "iss":"$clientEmail",
                    "scope":"https://www.googleapis.com/auth/firebase.messaging",
                    "aud":"https://oauth2.googleapis.com/token",
                    "iat":$now,
                    "exp":$exp
                }""".trimIndent().toByteArray()
            )
            
            val signatureInput = "$header.$payload"
            val signature = signWithRSA(signatureInput, privateKey)
            val jwt = "$signatureInput.$signature"
            
            // Обмениваем JWT на access token
            val url = URL("https://oauth2.googleapis.com/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            
            val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
            OutputStreamWriter(connection.outputStream).use { it.write(body) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val tokenObj = json.parseToJsonElement(response).jsonObject
                accessToken = tokenObj["access_token"]?.jsonPrimitive?.content
                tokenExpiry = System.currentTimeMillis() + 3500000 // ~58 минут
                return@withContext accessToken
            } else {
                println("FCM: Token error: ${connection.errorStream?.bufferedReader()?.readText()}")
            }
        } catch (e: Exception) {
            println("FCM: Token exception: ${e.message}")
        }
        null
    }
    
    private fun signWithRSA(data: String, privateKeyPem: String): String {
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(
                privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
            )
        )
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val key = keyFactory.generatePrivate(keySpec)
        
        val signature = java.security.Signature.getInstance("SHA256withRSA")
        signature.initSign(key)
        signature.update(data.toByteArray())
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign())
    }
    
    /**
     * Отправить уведомление о входящем звонке
     */
    suspend fun sendIncomingCallNotification(
        fcmToken: String,
        callId: String,
        callerId: String,
        callerName: String,
        callerAvatar: String?,
        isVideo: Boolean,
        sdp: String
    ): Boolean {
        val data = mutableMapOf(
            "type" to "incoming_call",
            "callId" to callId,
            "callerId" to callerId,
            "callerName" to callerName,
            "isVideo" to isVideo.toString(),
            "sdp" to sdp
        )
        callerAvatar?.let { data["callerAvatar"] = it }
        
        return sendMessage(fcmToken, data)
    }
    
    /**
     * Отправить уведомление о новом сообщении
     */
    suspend fun sendMessageNotification(
        fcmToken: String,
        chatId: String,
        senderId: String,
        senderName: String,
        messageText: String
    ): Boolean {
        val data = mapOf(
            "type" to "message",
            "chatId" to chatId,
            "senderId" to senderId,
            "senderName" to senderName,
            "messageText" to messageText
        )
        return sendMessage(fcmToken, data)
    }
    
    /**
     * Отправить уведомление о завершении звонка
     */
    suspend fun sendCallEndedNotification(fcmToken: String, callId: String): Boolean {
        return sendMessage(fcmToken, mapOf("type" to "call_ended", "callId" to callId))
    }
    
    /**
     * Отправить уведомление об отклонении звонка
     */
    suspend fun sendCallRejectedNotification(fcmToken: String, callId: String): Boolean {
        return sendMessage(fcmToken, mapOf("type" to "call_rejected", "callId" to callId))
    }
    
    private suspend fun sendMessage(token: String, data: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            println("FCM: Not configured")
            return@withContext false
        }
        
        val accessToken = getAccessToken()
        if (accessToken == null) {
            println("FCM: Failed to get access token")
            return@withContext false
        }
        
        try {
            // Строим JSON вручную чтобы избежать проблем с сериализацией
            val dataJson = data.entries.joinToString(",") { (k, v) -> 
                "\"$k\":\"${v.replace("\"", "\\\"")}\""
            }
            
            val messageBody = """
            {
                "message": {
                    "token": "$token",
                    "data": {$dataJson},
                    "android": {
                        "priority": "high",
                        "ttl": "60s"
                    }
                }
            }
            """.trimIndent()
            
            val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            OutputStreamWriter(connection.outputStream).use { it.write(messageBody) }
            
            return@withContext if (connection.responseCode == 200) {
                println("FCM: Message sent successfully")
                true
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                println("FCM: Error (${connection.responseCode}): $error")
                false
            }
        } catch (e: Exception) {
            println("FCM: Exception: ${e.message}")
            false
        }
    }
}
