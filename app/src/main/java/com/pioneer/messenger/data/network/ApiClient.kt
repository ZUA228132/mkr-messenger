package com.pioneer.messenger.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * API клиент для взаимодействия с сервером Pioneer
 */
object ApiClient {
    
    const val BASE_URL = "https://kluboksrm.ru"
    const val WS_URL = "wss://kluboksrm.ru/ws"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    private var authToken: String? = null
    private var currentUserId: String? = null
    
    // Callback для обработки ошибок авторизации
    private var onAuthError: (() -> Unit)? = null
    
    init {
        // Настраиваем доверие к самоподписанным сертификатам (для IP)
        setupTrustAllCerts()
    }
    
    fun setOnAuthError(callback: () -> Unit) {
        onAuthError = callback
    }
    
    fun hasAuthToken(): Boolean = authToken != null
    
    /**
     * Настройка доверия ко всем сертификатам (для самоподписанных)
     */
    private fun setupTrustAllCerts() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            
            val allHostsValid = HostnameVerifier { _, _ -> true }
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getBaseUrl(): String = BASE_URL
    
    fun getWsUrl(): String = WS_URL
    
    fun getAvatarUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return if (path.startsWith("http")) path else "$BASE_URL$path"
    }
    
    fun getFileUrl(path: String): String {
        return if (path.startsWith("http")) path else "$BASE_URL$path"
    }
    
    fun setAuthToken(token: String) {
        authToken = token
    }
    
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }
    
    fun getCurrentUserId(): String? = currentUserId
    
    fun clearAuthToken() {
        authToken = null
        currentUserId = null
    }
    
    // === Auth API ===
    
    @Serializable
    data class RegisterRequest(
        val inviteKey: String,
        val username: String,
        val displayName: String,
        val publicKey: String,
        val pin: String
    )
    
    @Serializable
    data class EmailRegisterRequest(
        val email: String,
        val password: String,
        val username: String,
        val displayName: String
    )
    
    @Serializable
    data class EmailLoginRequest(
        val email: String,
        val password: String
    )
    
    @Serializable
    data class VerifyEmailRequest(
        val email: String,
        val code: String
    )
    
    @Serializable
    data class ResendCodeRequest(
        val email: String
    )
    
    @Serializable
    data class AuthResponse(
        val userId: String,
        val token: String,
        val accessLevel: Int
    )
    
    @Serializable
    data class EmailRegisterResponse(
        val status: String,
        val message: String
    )
    
    @Serializable
    data class GenerateKeyRequest(
        val accessLevel: Int,
        val expiresInHours: Int? = null
    )
    
    @Serializable
    data class InviteKeyResponse(
        val key: String,
        val expiresAt: Long?
    )
    
    // Регистрация по email - шаг 1: отправка кода
    suspend fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        displayName: String
    ): Result<EmailRegisterResponse> = withContext(Dispatchers.IO) {
        try {
            val request = EmailRegisterRequest(email, password, username, displayName)
            val response = post("/api/auth/register/email", json.encodeToString(request))
            Result.success(json.decodeFromString<EmailRegisterResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Регистрация по email - шаг 2: подтверждение кода
    suspend fun verifyEmail(email: String, code: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = VerifyEmailRequest(email, code)
            val response = post("/api/auth/verify/email", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Повторная отправка кода
    suspend fun resendVerificationCode(email: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = ResendCodeRequest(email)
            val response = post("/api/auth/resend/code", json.encodeToString(request))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Вход по email
    suspend fun loginWithEmail(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = EmailLoginRequest(email, password)
            val response = post("/api/auth/login/email", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Регистрация по телефону ===
    
    @Serializable
    data class PhoneRegisterRequest(
        val phone: String,
        val username: String,
        val displayName: String,
        val password: String
    )
    
    @Serializable
    data class PhoneVerificationResponse(
        val status: String,
        val checkId: String? = null,
        val callPhone: String? = null,
        val callPhonePretty: String? = null,
        val message: String? = null
    )
    
    @Serializable
    data class PhoneVerifyStatusRequest(
        val checkId: String,
        val phone: String
    )
    
    @Serializable
    data class PhoneLoginRequest(
        val phone: String,
        val password: String
    )
    
    // Регистрация по телефону - шаг 1: инициация звонка
    suspend fun registerWithPhone(
        phone: String,
        username: String,
        displayName: String,
        password: String
    ): Result<PhoneVerificationResponse> = withContext(Dispatchers.IO) {
        try {
            val request = PhoneRegisterRequest(phone, username, displayName, password)
            val response = post("/api/auth/register/phone", json.encodeToString(request))
            Result.success(json.decodeFromString<PhoneVerificationResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Регистрация по телефону - шаг 2: проверка статуса звонка
    suspend fun verifyPhone(checkId: String, phone: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = PhoneVerifyStatusRequest(checkId, phone)
            val response = post("/api/auth/verify/phone", json.encodeToString(request))
            // Может вернуть AuthResponse или статус pending
            val jsonElement = json.parseToJsonElement(response)
            val status = jsonElement.jsonObject["status"]?.jsonPrimitive?.content
            if (status == "pending") {
                Result.failure(Exception("pending"))
            } else {
                Result.success(json.decodeFromString<AuthResponse>(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Вход по телефону
    suspend fun loginWithPhone(phone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = PhoneLoginRequest(phone, password)
            val response = post("/api/auth/login/phone", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun register(
        inviteKey: String,
        username: String,
        displayName: String,
        publicKey: String,
        pin: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterRequest(inviteKey, username, displayName, publicKey, pin)
            val response = post("/api/auth/register", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    @Serializable
    data class LoginRequest(
        val username: String,
        val pin: String
    )
    
    suspend fun login(username: String, pin: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = LoginRequest(username, pin)
            val response = post("/api/auth/login", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun generateInviteKey(accessLevel: Int): Result<InviteKeyResponse> = withContext(Dispatchers.IO) {
        try {
            val request = GenerateKeyRequest(accessLevel)
            val response = post("/api/auth/generate-key", json.encodeToString(request))
            Result.success(json.decodeFromString<InviteKeyResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Простая регистрация по позывному + пароль ===
    
    @Serializable
    data class SimpleRegisterRequest(
        val callsign: String,
        val displayName: String,
        val password: String
    )
    
    @Serializable
    data class SimpleLoginRequest(
        val callsign: String,
        val password: String
    )
    
    suspend fun registerSimple(
        callsign: String,
        displayName: String,
        password: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = SimpleRegisterRequest(callsign, displayName, password)
            val response = post("/api/auth/register/simple", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun loginSimple(callsign: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = SimpleLoginRequest(callsign, password)
            val response = post("/api/auth/login/simple", json.encodeToString(request))
            Result.success(json.decodeFromString<AuthResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Users API ===
    
    @Serializable
    data class UserResponse(
        val id: String,
        val username: String,
        val displayName: String,
        val publicKey: String = "",
        val accessLevel: Int,
        val isOnline: Boolean,
        val avatarUrl: String? = null,
        val bio: String? = null,
        val isVerified: Boolean = false,
        val isBanned: Boolean = false,
        val banReason: String? = null,
        val emojiStatus: String? = null,
        val createdAt: Long = 0
    )
    
    suspend fun getUsers(): Result<List<UserResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/users")
            Result.success(json.decodeFromString<List<UserResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUser(userId: String): Result<UserResponse> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/users/$userId")
            Result.success(json.decodeFromString<UserResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun searchUsers(query: String): Result<List<UserResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/users/search?q=$query")
            Result.success(json.decodeFromString<List<UserResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Profile API ===
    
    @Serializable
    data class UpdateProfileRequest(
        val displayName: String,
        val bio: String? = null,
        val phone: String? = null,
        val email: String? = null,
        val avatarUrl: String? = null
    )
    
    @Serializable
    data class UploadResponse(
        val url: String,
        val filename: String? = null,
        val fileName: String? = null,
        val fileSize: Long? = null,
        val mimeType: String? = null
    )
    
    suspend fun updateProfile(
        displayName: String,
        bio: String? = null,
        phone: String? = null,
        email: String? = null,
        avatarUrl: String? = null
    ): Result<UserResponse> = withContext(Dispatchers.IO) {
        try {
            val request = UpdateProfileRequest(displayName, bio, phone, email, avatarUrl)
            val response = post("/api/users/me", json.encodeToString(request))
            Result.success(json.decodeFromString<UserResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === FCM Token API ===
    
    @Serializable
    data class FcmTokenRequest(
        val token: String,
        val deviceId: String? = null,
        val deviceName: String? = null
    )
    
    suspend fun updateFcmToken(token: String, deviceId: String? = null, deviceName: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = FcmTokenRequest(token, deviceId, deviceName)
            post("/api/users/fcm-token", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Chats API ===
    
    @Serializable
    data class CreateChatRequest(
        val type: String,
        val name: String,
        val participantIds: List<String>
    )
    
    @Serializable
    data class ChatResponse(
        val id: String,
        val type: String,
        val name: String,
        val participants: List<String>,
        val participantNames: Map<String, String> = emptyMap(),
        val createdAt: Long
    )
    
    suspend fun createChat(
        type: String,
        name: String,
        participantIds: List<String>
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CreateChatRequest(type, name, participantIds)
            val response = post("/api/chats", json.encodeToString(request))
            Result.success(json.decodeFromString<ChatResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getChats(): Result<List<ChatResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/chats")
            Result.success(json.decodeFromString<List<ChatResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteChat(chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            delete("/api/chats/$chatId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Reactions API ===
    
    @Serializable
    data class AddReactionRequest(val emoji: String)
    
    @Serializable
    data class ReactionActionResponse(val action: String, val emoji: String)
    
    suspend fun addReaction(messageId: String, emoji: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = AddReactionRequest(emoji)
            val response = post("/api/messages/$messageId/reactions", json.encodeToString(request))
            val parsed = json.decodeFromString<ReactionActionResponse>(response)
            Result.success(parsed.action)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    @Serializable
    data class ReactionResponse(
        val emoji: String,
        val count: Int,
        val users: List<String>,
        val hasReacted: Boolean
    )
    
    suspend fun getReactions(messageId: String): Result<List<ReactionResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/messages/$messageId/reactions")
            Result.success(json.decodeFromString<List<ReactionResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Messages API ===
    
    @Serializable
    data class SendMessageRequest(
        val chatId: String,
        val encryptedContent: String,
        val nonce: String,
        val type: String = "TEXT"
    )
    
    @Serializable
    data class MessageResponse(
        val id: String,
        val chatId: String,
        val senderId: String,
        val encryptedContent: String,
        val nonce: String,
        val timestamp: Long,
        val type: String,
        val status: String
    )
    
    suspend fun sendMessage(
        chatId: String,
        encryptedContent: String,
        nonce: String,
        type: String = "TEXT"
    ): Result<MessageResponse> = withContext(Dispatchers.IO) {
        try {
            val request = SendMessageRequest(chatId, encryptedContent, nonce, type)
            val response = post("/api/messages", json.encodeToString(request))
            Result.success(json.decodeFromString<MessageResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMessages(chatId: String): Result<List<MessageResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/messages/$chatId")
            Result.success(json.decodeFromString<List<MessageResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifyChat(chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            post("/api/chats/$chatId/verify", "{}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === LiveKit Calls API ===
    
    @Serializable
    data class CallTokenRequest(
        val calleeId: String,
        val isVideo: Boolean = false
    )
    
    @Serializable
    data class CallTokenResponse(
        val token: String,
        val roomName: String,
        val callId: String
    )
    
    @Serializable
    data class JoinCallResponse(
        val token: String
    )
    
    suspend fun getCallToken(calleeId: String, isVideo: Boolean): Result<CallTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CallTokenRequest(calleeId, isVideo)
            val response = post("/api/calls/token", json.encodeToString(request))
            Result.success(json.decodeFromString<CallTokenResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun joinCall(roomName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = post("/api/calls/join/$roomName", "{}")
            val parsed = json.decodeFromString<JoinCallResponse>(response)
            Result.success(parsed.token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Verification API ===
    
    suspend fun verifyUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            post("/api/users/$userId/verify", "{}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unverifyUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            delete("/api/users/$userId/verify")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Ban API ===
    
    @Serializable
    data class BanUserRequest(val reason: String)
    
    @Serializable
    data class AppealRequest(val message: String)
    
    suspend fun banUser(userId: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = BanUserRequest(reason)
            post("/api/users/$userId/ban", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unbanUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            delete("/api/users/$userId/ban")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun submitAppeal(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = AppealRequest(message)
            post("/api/users/appeal", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Web Sessions API ===
    
    @Serializable
    data class WebSession(
        val sessionId: String,
        val deviceInfo: String?,
        val authorizedAt: Long
    )
    
    @Serializable
    data class AuthorizeWebSessionRequest(
        val sessionCode: String,
        val deviceInfo: String? = null
    )
    
    suspend fun getWebSessions(): List<WebSession> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/web/sessions")
            json.decodeFromString<List<WebSession>>(response)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun authorizeWebSession(sessionCode: String, deviceInfo: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = AuthorizeWebSessionRequest(sessionCode, deviceInfo)
            post("/api/web/session/authorize", json.encodeToString(request))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun terminateWebSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            delete("/api/web/session/$sessionId")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // === HTTP helpers ===
    
    @Serializable
    data class ErrorResponse(
        val error: String? = null,
        val message: String? = null
    )
    
    private fun get(endpoint: String): String {
        val url = URL("${getBaseUrl()}$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        authToken?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        
        val responseCode = connection.responseCode
        
        // Обработка ошибки авторизации
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            onAuthError?.invoke()
            throw Exception("Требуется авторизация")
        }
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            val errorMessage = try {
                json.decodeFromString<ErrorResponse>(errorBody).error 
                    ?: json.decodeFromString<ErrorResponse>(errorBody).message
                    ?: "Ошибка сервера"
            } catch (e: Exception) {
                "Ошибка подключения"
            }
            throw Exception(errorMessage)
        }
        
        return connection.inputStream.bufferedReader().use(BufferedReader::readText)
    }
    
    private fun post(endpoint: String, body: String): String {
        val url = URL("${getBaseUrl()}$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        authToken?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body)
        }
        
        val responseCode = connection.responseCode
        
        // Обработка ошибки авторизации
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            onAuthError?.invoke()
            throw Exception("Требуется авторизация")
        }
        
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
            val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            val errorMessage = try {
                json.decodeFromString<ErrorResponse>(errorBody).error 
                    ?: json.decodeFromString<ErrorResponse>(errorBody).message
                    ?: "Ошибка сервера"
            } catch (e: Exception) {
                "Ошибка подключения"
            }
            throw Exception(errorMessage)
        }
        
        return connection.inputStream.bufferedReader().use(BufferedReader::readText)
    }
    
    private fun delete(endpoint: String): String {
        val url = URL("${getBaseUrl()}$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        authToken?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        
        val responseCode = connection.responseCode
        
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            onAuthError?.invoke()
            throw Exception("Требуется авторизация")
        }
        
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
            val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            val errorMessage = try {
                json.decodeFromString<ErrorResponse>(errorBody).error 
                    ?: json.decodeFromString<ErrorResponse>(errorBody).message
                    ?: "Ошибка сервера"
            } catch (e: Exception) {
                "Ошибка подключения"
            }
            throw Exception(errorMessage)
        }
        
        return connection.inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
    }
    
    private fun uploadMultipart(endpoint: String, fileBytes: ByteArray, filename: String): String {
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        val url = URL("${getBaseUrl()}$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        authToken?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        
        connection.outputStream.use { output ->
            val writer = output.bufferedWriter()
            
            // File part
            writer.write("--$boundary\r\n")
            writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
            writer.write("Content-Type: image/jpeg\r\n\r\n")
            writer.flush()
            
            output.write(fileBytes)
            output.flush()
            
            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
        }
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
            val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            throw Exception("HTTP $responseCode: $errorBody")
        }
        
        return connection.inputStream.bufferedReader().use(BufferedReader::readText)
    }
    
    // === File Upload API ===
    
    @Serializable
    data class FileUploadResponse(
        val url: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String? = null
    )
    
    suspend fun uploadVoice(fileBytes: ByteArray, filename: String): Result<FileUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = uploadMultipartFile("/api/files/upload/voice", fileBytes, filename, "audio/mp4")
            Result.success(json.decodeFromString<FileUploadResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun uploadVideoNote(fileBytes: ByteArray, filename: String): Result<FileUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = uploadMultipartFile("/api/files/upload/video-note", fileBytes, filename, "video/mp4")
            Result.success(json.decodeFromString<FileUploadResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun uploadFile(fileBytes: ByteArray, filename: String, mimeType: String): Result<FileUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = uploadMultipartFile("/api/files/upload", fileBytes, filename, mimeType)
            Result.success(json.decodeFromString<FileUploadResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun uploadMultipartFile(endpoint: String, fileBytes: ByteArray, filename: String, mimeType: String): String {
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        val url = URL("${getBaseUrl()}$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        authToken?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        
        connection.outputStream.use { output ->
            val writer = output.bufferedWriter()
            
            writer.write("--$boundary\r\n")
            writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
            writer.write("Content-Type: $mimeType\r\n\r\n")
            writer.flush()
            
            output.write(fileBytes)
            output.flush()
            
            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
        }
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
            val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            throw Exception("HTTP $responseCode: $errorBody")
        }
        
        return connection.inputStream.bufferedReader().use(BufferedReader::readText)
    }
    
    // === Channels API ===
    
    @Serializable
    data class ChannelApiResponse(
        val id: String,
        val chatId: String,
        val name: String,
        val username: String? = null,
        val description: String? = null,
        val avatarUrl: String? = null,
        val isPublic: Boolean = true,
        val subscriberCount: Int = 0,
        val allowComments: Boolean = true,
        val isSubscribed: Boolean = false,
        val notificationsEnabled: Boolean = true
    )
    
    @Serializable
    data class CreateChannelRequest(
        val name: String,
        val username: String? = null,
        val description: String? = null,
        val isPublic: Boolean = true,
        val allowComments: Boolean = true
    )
    
    @Serializable
    data class ChannelPostApiResponse(
        val id: String,
        val messageId: String,
        val content: String,
        val senderId: String = "",
        val senderName: String = "",
        val timestamp: Long = 0,
        val viewCount: Int = 0,
        val isPinned: Boolean = false,
        val allowComments: Boolean = true,
        val commentCount: Int = 0,
        val reactions: List<ReactionResponse> = emptyList()
    )
    
    @Serializable
    data class CreatePostRequest(
        val content: String,
        val allowComments: Boolean = true
    )
    
    @Serializable
    data class NotificationToggleRequest(val enabled: Boolean)
    
    suspend fun getChannels(): Result<List<ChannelApiResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/channels")
            Result.success(json.decodeFromString<List<ChannelApiResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getChannel(channelId: String): Result<ChannelApiResponse> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/channels/$channelId")
            Result.success(json.decodeFromString<ChannelApiResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createChannel(
        name: String,
        username: String? = null,
        description: String? = null,
        isPublic: Boolean = true,
        allowComments: Boolean = true
    ): Result<ChannelApiResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CreateChannelRequest(name, username, description, isPublic, allowComments)
            val response = post("/api/channels", json.encodeToString(request))
            Result.success(json.decodeFromString<ChannelApiResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun subscribeToChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            post("/api/channels/$channelId/subscribe", "{}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unsubscribeFromChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            delete("/api/channels/$channelId/subscribe")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun toggleChannelNotifications(channelId: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = NotificationToggleRequest(enabled)
            post("/api/channels/$channelId/notifications", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getChannelPosts(channelId: String): Result<List<ChannelPostApiResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/channels/$channelId/posts")
            Result.success(json.decodeFromString<List<ChannelPostApiResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createChannelPost(channelId: String, content: String, allowComments: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = CreatePostRequest(content, allowComments)
            post("/api/channels/$channelId/posts", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === Stories API ===
    
    @Serializable
    data class StoryApiResponse(
        val id: String,
        val userId: String,
        val username: String,
        val displayName: String,
        val avatarUrl: String? = null,
        val mediaUrl: String,
        val mediaType: String,
        val thumbnailUrl: String? = null,
        val duration: Int = 5,
        val text: String? = null,
        val textColor: String? = null,
        val textPosition: String? = null,
        val backgroundColor: String? = null,
        val musicUrl: String? = null,
        val musicTitle: String? = null,
        val viewCount: Int = 0,
        val isViewed: Boolean = false,
        val expiresAt: Long = 0,
        val createdAt: Long = 0
    )
    
    @Serializable
    data class UserStoriesApiResponse(
        val userId: String,
        val username: String,
        val displayName: String,
        val avatarUrl: String? = null,
        val hasUnwatched: Boolean = false,
        val stories: List<StoryApiResponse> = emptyList()
    )
    
    @Serializable
    data class CreateStoryRequest(
        val mediaUrl: String,
        val mediaType: String,
        val thumbnailUrl: String? = null,
        val duration: Int = 5,
        val text: String? = null,
        val textColor: String? = null,
        val textPosition: String? = null,
        val backgroundColor: String? = null,
        val musicUrl: String? = null,
        val musicTitle: String? = null
    )
    
    suspend fun getStories(): Result<List<UserStoriesApiResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/stories")
            // Обрабатываем пустой ответ
            if (response.isBlank() || response == "[]") {
                Result.success(emptyList())
            } else {
                Result.success(json.decodeFromString<List<UserStoriesApiResponse>>(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMyStories(): Result<List<StoryApiResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/stories/my")
            // Обрабатываем пустой ответ
            if (response.isBlank() || response == "[]") {
                Result.success(emptyList())
            } else {
                Result.success(json.decodeFromString<List<StoryApiResponse>>(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createStory(
        mediaUrl: String,
        mediaType: String,
        thumbnailUrl: String? = null,
        duration: Int = 5,
        text: String? = null,
        textColor: String? = null,
        textPosition: String? = null,
        backgroundColor: String? = null,
        musicUrl: String? = null,
        musicTitle: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = CreateStoryRequest(
                mediaUrl, mediaType, thumbnailUrl, duration,
                text, textColor, textPosition, backgroundColor,
                musicUrl, musicTitle
            )
            val response = post("/api/stories", json.encodeToString(request))
            // Сервер возвращает {"id": "...", "expiresAt": 123456}
            val jsonElement = json.parseToJsonElement(response)
            val id = jsonElement.jsonObject["id"]?.jsonPrimitive?.content ?: ""
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun viewStory(storyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            post("/api/stories/$storyId/view", "{}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteStory(storyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            delete("/api/stories/$storyId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === Call API ===
    
    @Serializable
    data class EndCallRequest(
        val roomName: String,
        val duration: Int,
        val status: String
    )
    
    suspend fun endCall(roomName: String, duration: Int, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = EndCallRequest(roomName, duration, status)
            post("/api/calls/end", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Reels API ===
    
    @Serializable
    data class ReelResponse(
        val id: String,
        val userId: String,
        val username: String,
        val displayName: String,
        val avatarUrl: String? = null,
        val videoUrl: String,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val duration: Int = 0,
        val viewCount: Int = 0,
        val likeCount: Int = 0,
        val commentCount: Int = 0,
        val shareCount: Int = 0,
        val isLiked: Boolean = false,
        val createdAt: Long = 0
    )
    
    @Serializable
    data class CreateReelRequest(
        val videoUrl: String,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val duration: Int = 0
    )
    
    suspend fun getReels(): Result<List<ReelResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/reels")
            Result.success(json.decodeFromString<List<ReelResponse>>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createReel(videoUrl: String, thumbnailUrl: String?, description: String?, duration: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = CreateReelRequest(videoUrl, thumbnailUrl, description, duration)
            val response = post("/api/reels", json.encodeToString(request))
            val parsed = json.decodeFromString<Map<String, String>>(response)
            Result.success(parsed["id"] ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun likeReel(reelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = post("/api/reels/$reelId/like", "{}")
            val parsed = json.decodeFromString<Map<String, String>>(response)
            Result.success(parsed["action"] ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun viewReel(reelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            post("/api/reels/$reelId/view", "{}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // === Location & Privacy API ===
    
    @Serializable
    data class LocationUpdateRequest(
        val latitude: Double,
        val longitude: Double
    )
    
    @Serializable
    data class NearbyUserResponse(
        val userId: String,
        val username: String,
        val displayName: String,
        val avatarUrl: String? = null,
        val latitude: Double,
        val longitude: Double,
        val lastSeen: String
    )
    
    @Serializable
    data class PrivacySettingsResponse(
        val whoCanCall: String = "everyone",
        val whoCanSeeAvatar: String = "everyone",
        val whoCanMessage: String = "everyone",
        val whoCanFindMe: String = "everyone",
        val ghostMode: Boolean = false
    )
    
    @Serializable
    data class PrivacySettingsRequest(
        val whoCanCall: String,
        val whoCanSeeAvatar: String,
        val whoCanMessage: String,
        val whoCanFindMe: String,
        val ghostMode: Boolean
    )
    
    suspend fun updateLocation(latitude: Double, longitude: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = LocationUpdateRequest(latitude, longitude)
            post("/api/location/update", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getNearbyUsers(): Result<List<NearbyUserResponse>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/location/nearby")
            if (response.isBlank() || response == "[]") {
                Result.success(emptyList())
            } else {
                Result.success(json.decodeFromString<List<NearbyUserResponse>>(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getPrivacySettings(): Result<PrivacySettingsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/privacy/settings")
            Result.success(json.decodeFromString<PrivacySettingsResponse>(response))
        } catch (e: Exception) {
            Result.success(PrivacySettingsResponse())
        }
    }
    
    suspend fun updatePrivacySettings(
        whoCanCall: String,
        whoCanSeeAvatar: String,
        whoCanMessage: String,
        whoCanFindMe: String,
        ghostMode: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = PrivacySettingsRequest(
                whoCanCall, whoCanSeeAvatar, whoCanMessage, whoCanFindMe, ghostMode
            )
            post("/api/privacy/settings", json.encodeToString(request))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Отзыв всех токенов авторизации (для Panic Button)
     * Делает недействительными все сессии на всех устройствах
     */
    suspend fun revokeAllTokens(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            post("/api/auth/revoke-all", "{}")
            clearAuthToken()
            Result.success(Unit)
        } catch (e: Exception) {
            // Игнорируем ошибки - главное очистить локальные данные
            clearAuthToken()
            Result.failure(e)
        }
    }
    
    /**
     * Загрузка аватара
     */
    suspend fun uploadAvatar(imageBytes: ByteArray): Result<UploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = uploadMultipartFile("/api/upload/avatar", imageBytes, "avatar.jpg", "image/jpeg")
            Result.success(json.decodeFromString<UploadResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Загрузка изображения
     */
    suspend fun uploadImage(imageBytes: ByteArray, filename: String): Result<UploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = uploadMultipartFile("/api/upload/image", imageBytes, filename, "image/jpeg")
            Result.success(json.decodeFromString<UploadResponse>(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
