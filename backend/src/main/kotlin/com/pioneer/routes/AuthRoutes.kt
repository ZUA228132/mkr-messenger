package com.pioneer.routes

import com.pioneer.plugins.*
import com.pioneer.service.PhoneVerificationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.util.*

@Serializable
data class RegisterRequest(
    val inviteKey: String,
    val username: String,
    val displayName: String,
    val publicKey: String,
    val pin: String // 4-6 цифр PIN-код
)

// Регистрация по email
@Serializable
data class EmailRegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    val displayName: String
)

// Подтверждение email
@Serializable
data class VerifyEmailRequest(
    val email: String,
    val code: String
)

// Повторная отправка кода
@Serializable
data class ResendCodeRequest(
    val email: String
)

// Вход по email
@Serializable
data class EmailLoginRequest(
    val email: String,
    val password: String
)

// Регистрация по телефону
@Serializable
data class PhoneRegisterRequest(
    val phone: String,
    val username: String,
    val displayName: String,
    val password: String
)

// Ответ на инициацию верификации телефона
@Serializable
data class PhoneVerificationResponse(
    val status: String,
    val checkId: String? = null,
    val callPhone: String? = null,
    val callPhonePretty: String? = null,
    val message: String? = null
)

// Проверка статуса верификации телефона
@Serializable
data class PhoneVerifyStatusRequest(
    val checkId: String,
    val phone: String
)

// Вход по телефону
@Serializable
data class PhoneLoginRequest(
    val phone: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val userId: String,
    val token: String,
    val accessLevel: Int
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

fun Route.authRoutes() {
    route("/api/auth") {
        
        // Регистрация по email (без ключа приглашения) - отправляет код подтверждения
        post("/register/email") {
            val request = call.receive<EmailRegisterRequest>()
            
            // Валидация email
            if (!request.email.contains("@") || request.email.length < 5) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Неверный формат email"))
                return@post
            }
            
            // Валидация пароля
            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Пароль должен быть минимум 6 символов"))
                return@post
            }
            
            // Валидация username
            if (request.username.length < 3 || !request.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Имя пользователя должно быть минимум 3 символа (только буквы, цифры и _)"))
                return@post
            }
            
            val passwordHash = hashKey(request.password)
            val verificationCode = com.pioneer.service.EmailService.generateVerificationCode()
            val codeExpires = System.currentTimeMillis() + 10 * 60 * 1000 // 10 минут
            
            val result = transaction {
                // Проверяем уникальность email
                val existingEmail = Users.select { Users.email eq request.email }.singleOrNull()
                if (existingEmail != null) {
                    // Если email уже есть но не подтверждён - обновляем код
                    val isVerified = existingEmail[Users.emailVerified]
                    if (!isVerified) {
                        Users.update({ Users.email eq request.email }) {
                            it[emailVerificationCode] = verificationCode
                            it[emailVerificationExpires] = codeExpires
                            it[Users.passwordHash] = passwordHash
                            it[username] = request.username
                            it[displayName] = request.displayName
                        }
                        return@transaction "pending" to existingEmail[Users.id]
                    }
                    return@transaction null to "Email уже зарегистрирован"
                }
                
                // Проверяем уникальность username
                val existingUser = Users.select { Users.username eq request.username }.singleOrNull()
                if (existingUser != null) {
                    return@transaction null to "Имя пользователя уже занято"
                }
                
                val userId = UUID.randomUUID().toString()
                val accessLevel = 1 // Обычный пользователь
                val now = System.currentTimeMillis()
                
                // Создаём пользователя (не подтверждён)
                Users.insert {
                    it[id] = userId
                    it[username] = request.username
                    it[email] = request.email
                    it[emailVerified] = false
                    it[emailVerificationCode] = verificationCode
                    it[emailVerificationExpires] = codeExpires
                    it[Users.passwordHash] = passwordHash
                    it[displayName] = request.displayName
                    it[publicKey] = ""
                    it[Users.accessLevel] = accessLevel
                    it[isVerified] = false
                    it[createdAt] = now
                }
                
                "pending" to userId
            }
            
            val (status, data) = result
            if (status == "pending") {
                // Отправляем код на email
                val emailSent = com.pioneer.service.EmailService.sendVerificationCode(request.email, verificationCode)
                if (emailSent) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "verification_required",
                        "message" to "Код подтверждения отправлен на ${request.email}"
                    ))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "verification_required",
                        "message" to "Код: $verificationCode (email не отправлен)"
                    ))
                }
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to data))
            }
        }
        
        // Подтверждение email кодом
        post("/verify/email") {
            val request = call.receive<VerifyEmailRequest>()
            
            val result = transaction {
                val user = Users.select { Users.email eq request.email }.singleOrNull()
                    ?: return@transaction null to "Пользователь не найден"
                
                val storedCode = user[Users.emailVerificationCode]
                val codeExpires = user[Users.emailVerificationExpires]
                
                if (storedCode == null || codeExpires == null) {
                    return@transaction null to "Код не найден. Запросите новый"
                }
                
                if (System.currentTimeMillis() > codeExpires) {
                    return@transaction null to "Код истёк. Запросите новый"
                }
                
                if (storedCode != request.code) {
                    return@transaction null to "Неверный код"
                }
                
                val userId = user[Users.id]
                val accessLevel = user[Users.accessLevel]
                val displayName = user[Users.displayName]
                
                // Подтверждаем email
                Users.update({ Users.email eq request.email }) {
                    it[emailVerified] = true
                    it[emailVerificationCode] = null
                    it[emailVerificationExpires] = null
                }
                
                // Подписываем на официальный канал MKR
                val mkrChatId = "mkr-official-channel"
                val mkrChat = Chats.select { Chats.id eq mkrChatId }.singleOrNull()
                if (mkrChat != null) {
                    val alreadySubscribed = ChatParticipants.select { 
                        (ChatParticipants.chatId eq mkrChatId) and (ChatParticipants.userId eq userId)
                    }.count() > 0
                    
                    if (!alreadySubscribed) {
                        ChatParticipants.insert {
                            it[chatId] = mkrChatId
                            it[ChatParticipants.userId] = userId
                            it[role] = "member"
                            it[joinedAt] = System.currentTimeMillis()
                        }
                    }
                    
                    // Также подписываем в таблице ChannelSubscriptions
                    val channelInfo = Channels.select { Channels.chatId eq mkrChatId }.singleOrNull()
                    if (channelInfo != null) {
                        val channelId = channelInfo[Channels.id]
                        val alreadySubChannel = ChannelSubscriptions.select {
                            (ChannelSubscriptions.channelId eq channelId) and (ChannelSubscriptions.userId eq userId)
                        }.count() > 0
                        
                        if (!alreadySubChannel) {
                            ChannelSubscriptions.insert {
                                it[id] = UUID.randomUUID().toString()
                                it[ChannelSubscriptions.channelId] = channelId
                                it[ChannelSubscriptions.userId] = userId
                                it[notificationsEnabled] = true
                                it[subscribedAt] = System.currentTimeMillis()
                            }
                        }
                    }
                }
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                // Отправляем приветственное письмо
                com.pioneer.service.EmailService.sendWelcomeEmail(request.email, displayName)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
            }
        }
        
        // Повторная отправка кода
        post("/resend/code") {
            val request = call.receive<ResendCodeRequest>()
            
            val verificationCode = com.pioneer.service.EmailService.generateVerificationCode()
            val codeExpires = System.currentTimeMillis() + 10 * 60 * 1000
            
            val result = transaction {
                val user = Users.select { Users.email eq request.email }.singleOrNull()
                    ?: return@transaction false to "Пользователь не найден"
                
                if (user[Users.emailVerified]) {
                    return@transaction false to "Email уже подтверждён"
                }
                
                Users.update({ Users.email eq request.email }) {
                    it[emailVerificationCode] = verificationCode
                    it[emailVerificationExpires] = codeExpires
                }
                
                true to verificationCode
            }
            
            val (success, data) = result
            if (success) {
                val emailSent = com.pioneer.service.EmailService.sendVerificationCode(request.email, data)
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to if (emailSent) "Код отправлен" else "Код: $data"
                ))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to data))
            }
        }
        
        // Вход по email
        post("/login/email") {
            val request = call.receive<EmailLoginRequest>()
            
            val result = transaction {
                val user = Users.select { Users.email eq request.email }.singleOrNull()
                    ?: return@transaction null to "Пользователь не найден"
                
                val userId = user[Users.id]
                val accessLevel = user[Users.accessLevel]
                val storedPasswordHash = user[Users.passwordHash]
                val lockedUntil = user[Users.lockedUntil]
                val failedAttempts = user[Users.failedAttempts]
                
                // Проверяем блокировку
                if (lockedUntil != null && lockedUntil > System.currentTimeMillis()) {
                    val remainingMinutes = (lockedUntil - System.currentTimeMillis()) / 60000
                    return@transaction null to "Аккаунт заблокирован. Попробуйте через $remainingMinutes мин."
                }
                
                // Проверяем пароль
                val passwordHash = hashKey(request.password)
                if (storedPasswordHash != null && storedPasswordHash != passwordHash) {
                    val newFailedAttempts = failedAttempts + 1
                    
                    if (newFailedAttempts >= 5) {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = 0
                            it[Users.lockedUntil] = System.currentTimeMillis() + 15 * 60 * 1000
                        }
                        return@transaction null to "Неверный пароль. Аккаунт заблокирован на 15 минут"
                    } else {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = newFailedAttempts
                        }
                        return@transaction null to "Неверный пароль. Осталось попыток: ${5 - newFailedAttempts}"
                    }
                }
                
                // Сбрасываем счётчик и обновляем lastSeen
                Users.update({ Users.id eq userId }) {
                    it[lastSeen] = System.currentTimeMillis()
                    it[Users.failedAttempts] = 0
                    it[Users.lockedUntil] = null
                }
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to error))
            }
        }
        
        // === Регистрация по телефону ===
        
        // Шаг 1: Инициировать верификацию телефона
        post("/register/phone") {
            val request = call.receive<PhoneRegisterRequest>()
            
            // Валидация телефона
            val normalizedPhone = request.phone.replace(Regex("[^0-9]"), "")
            if (normalizedPhone.length < 10 || normalizedPhone.length > 15) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Неверный формат номера телефона"))
                return@post
            }
            
            // Валидация пароля
            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Пароль должен быть минимум 6 символов"))
                return@post
            }
            
            // Валидация username
            if (request.username.length < 3 || !request.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Имя пользователя должно быть минимум 3 символа"))
                return@post
            }
            
            // Проверяем уникальность
            val existingUser = transaction {
                Users.select { 
                    (Users.phone eq normalizedPhone) or (Users.username eq request.username)
                }.singleOrNull()
            }
            
            if (existingUser != null) {
                val isPhoneMatch = existingUser[Users.phone] == normalizedPhone
                val isVerified = existingUser[Users.phoneVerified]
                
                if (isPhoneMatch && isVerified) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Этот номер уже зарегистрирован"))
                    return@post
                }
                
                if (!isPhoneMatch) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Имя пользователя уже занято"))
                    return@post
                }
            }
            
            // Инициируем верификацию через sms.ru callcheck
            val verificationResult = PhoneVerificationService.initiateVerification(normalizedPhone)
            
            if (verificationResult == null) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка сервиса верификации"))
                return@post
            }
            
            val passwordHash = hashKey(request.password)
            val now = System.currentTimeMillis()
            val checkExpires = now + 5 * 60 * 1000 // 5 минут на звонок
            
            // Сохраняем или обновляем пользователя
            transaction {
                val existing = Users.select { Users.phone eq normalizedPhone }.singleOrNull()
                
                if (existing != null) {
                    Users.update({ Users.phone eq normalizedPhone }) {
                        it[phoneVerificationCheckId] = verificationResult.checkId
                        it[phoneVerificationExpires] = checkExpires
                        it[Users.passwordHash] = passwordHash
                        it[username] = request.username
                        it[displayName] = request.displayName
                    }
                } else {
                    val userId = UUID.randomUUID().toString()
                    Users.insert {
                        it[id] = userId
                        it[username] = request.username
                        it[phone] = normalizedPhone
                        it[phoneVerified] = false
                        it[phoneVerificationCheckId] = verificationResult.checkId
                        it[phoneVerificationExpires] = checkExpires
                        it[Users.passwordHash] = passwordHash
                        it[displayName] = request.displayName
                        it[publicKey] = ""
                        it[accessLevel] = 1
                        it[isVerified] = false
                        it[createdAt] = now
                    }
                }
            }
            
            call.respond(HttpStatusCode.OK, PhoneVerificationResponse(
                status = "call_required",
                checkId = verificationResult.checkId,
                callPhone = verificationResult.callPhone,
                callPhonePretty = verificationResult.callPhonePretty,
                message = "Позвоните на номер ${verificationResult.callPhonePretty} для подтверждения"
            ))
        }
        
        // Шаг 2: Проверить статус верификации телефона
        post("/verify/phone") {
            val request = call.receive<PhoneVerifyStatusRequest>()
            
            val normalizedPhone = request.phone.replace(Regex("[^0-9]"), "")
            
            // Проверяем статус в sms.ru
            val status = PhoneVerificationService.checkStatus(request.checkId)
            
            when (status) {
                PhoneVerificationService.VerificationStatus.VERIFIED -> {
                    // Верификация прошла успешно
                    val result = transaction {
                        val user = Users.select { Users.phone eq normalizedPhone }.singleOrNull()
                            ?: return@transaction null to "Пользователь не найден"
                        
                        val userId = user[Users.id]
                        val accessLevel = user[Users.accessLevel]
                        val displayName = user[Users.displayName]
                        
                        // Подтверждаем телефон
                        Users.update({ Users.phone eq normalizedPhone }) {
                            it[phoneVerified] = true
                            it[phoneVerificationCheckId] = null
                            it[phoneVerificationExpires] = null
                        }
                        
                        // Подписываем на официальный канал MKR
                        subscribeToMkrChannel(userId)
                        
                        val token = JwtConfig.generateToken(userId, accessLevel)
                        
                        AuthResponse(userId, token, accessLevel) to null
                    }
                    
                    val (response, error) = result
                    if (response != null) {
                        call.respond(HttpStatusCode.OK, response)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                    }
                }
                PhoneVerificationService.VerificationStatus.PENDING -> {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "pending",
                        "message" to "Ожидаем звонок..."
                    ))
                }
                PhoneVerificationService.VerificationStatus.EXPIRED -> {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Время на звонок истекло. Попробуйте снова"
                    ))
                }
                PhoneVerificationService.VerificationStatus.ERROR -> {
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to "Ошибка проверки статуса"
                    ))
                }
            }
        }
        
        // Вход по телефону
        post("/login/phone") {
            val request = call.receive<PhoneLoginRequest>()
            
            val normalizedPhone = request.phone.replace(Regex("[^0-9]"), "")
            
            val result = transaction {
                val user = Users.select { Users.phone eq normalizedPhone }.singleOrNull()
                    ?: return@transaction null to "Пользователь не найден"
                
                if (!user[Users.phoneVerified]) {
                    return@transaction null to "Телефон не подтверждён"
                }
                
                val userId = user[Users.id]
                val accessLevel = user[Users.accessLevel]
                val storedPasswordHash = user[Users.passwordHash]
                val lockedUntil = user[Users.lockedUntil]
                val failedAttempts = user[Users.failedAttempts]
                
                // Проверяем блокировку
                if (lockedUntil != null && lockedUntil > System.currentTimeMillis()) {
                    val remainingMinutes = (lockedUntil - System.currentTimeMillis()) / 60000
                    return@transaction null to "Аккаунт заблокирован. Попробуйте через $remainingMinutes мин."
                }
                
                // Проверяем пароль
                val passwordHash = hashKey(request.password)
                if (storedPasswordHash != null && storedPasswordHash != passwordHash) {
                    val newFailedAttempts = failedAttempts + 1
                    
                    if (newFailedAttempts >= 5) {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = 0
                            it[Users.lockedUntil] = System.currentTimeMillis() + 15 * 60 * 1000
                        }
                        return@transaction null to "Неверный пароль. Аккаунт заблокирован на 15 минут"
                    } else {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = newFailedAttempts
                        }
                        return@transaction null to "Неверный пароль. Осталось попыток: ${5 - newFailedAttempts}"
                    }
                }
                
                // Сбрасываем счётчик и обновляем lastSeen
                Users.update({ Users.id eq userId }) {
                    it[lastSeen] = System.currentTimeMillis()
                    it[Users.failedAttempts] = 0
                    it[Users.lockedUntil] = null
                }
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to error))
            }
        }
        
        // Регистрация по ключу приглашения (старый метод)
        post("/register") {
            val request = call.receive<RegisterRequest>()
            
            // Валидация PIN
            if (request.pin.length !in 4..6 || !request.pin.all { it.isDigit() }) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "PIN должен быть 4-6 цифр"))
                return@post
            }
            
            // Хешируем ключ для поиска
            val keyHash = hashKey(request.inviteKey)
            val pinHash = hashKey(request.pin) // Хешируем PIN
            
            val result = transaction {
                // Проверяем ключ
                val inviteKey = InviteKeys.select { 
                    InviteKeys.keyHash eq keyHash
                }.singleOrNull()
                
                if (inviteKey == null) {
                    return@transaction null to "Неверный ключ приглашения"
                }
                
                // Проверяем срок действия
                val expiresAt = inviteKey[InviteKeys.expiresAt]
                if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
                    return@transaction null to "Срок действия ключа истёк"
                }
                
                // Проверяем использован ли ключ (кроме мастер-ключа)
                val isMasterKey = inviteKey[InviteKeys.id] == "master-admin-key"
                if (!isMasterKey && inviteKey[InviteKeys.usedBy] != null) {
                    return@transaction null to "Ключ уже использован"
                }
                
                // Проверяем уникальность username
                val existingUser = Users.select { Users.username eq request.username }.singleOrNull()
                if (existingUser != null) {
                    return@transaction null to "Имя пользователя уже занято"
                }
                
                val userId = UUID.randomUUID().toString()
                val accessLevel = inviteKey[InviteKeys.accessLevel]
                val now = System.currentTimeMillis()
                
                // Создаём пользователя с PIN
                Users.insert {
                    it[id] = userId
                    it[username] = request.username
                    it[displayName] = request.displayName
                    it[publicKey] = request.publicKey
                    it[Users.accessLevel] = accessLevel
                    it[isVerified] = accessLevel >= 10 // Админы автоматически верифицированы
                    it[Users.pinHash] = pinHash
                    it[createdAt] = now
                }
                
                // Помечаем ключ как использованный (кроме мастер-ключа)
                if (!isMasterKey) {
                    InviteKeys.update({ InviteKeys.keyHash eq keyHash }) {
                        it[usedBy] = userId
                        it[usedAt] = now
                    }
                }
                
                // Подписываем на официальный канал MKR
                val mkrChatId = "mkr-official-channel"
                val mkrChat = Chats.select { Chats.id eq mkrChatId }.singleOrNull()
                if (mkrChat != null) {
                    ChatParticipants.insert {
                        it[chatId] = mkrChatId
                        it[ChatParticipants.userId] = userId
                        it[role] = if (accessLevel >= 10) "admin" else "member"
                        it[joinedAt] = now
                    }
                    
                    // Также подписываем в таблице ChannelSubscriptions
                    val channelInfo = Channels.select { Channels.chatId eq mkrChatId }.singleOrNull()
                    if (channelInfo != null) {
                        val channelId = channelInfo[Channels.id]
                        ChannelSubscriptions.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[ChannelSubscriptions.channelId] = channelId
                            it[ChannelSubscriptions.userId] = userId
                            it[notificationsEnabled] = true
                            it[subscribedAt] = now
                        }
                    }
                }
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.Created, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
            }
        }
        
        // Генерация ключа приглашения
        post("/generate-key") {
            val request = call.receive<GenerateKeyRequest>()
            
            val key = generateInviteKey()
            val keyHash = hashKey(key)
            val now = System.currentTimeMillis()
            val expiresAt = request.expiresInHours?.let { now + it * 60 * 60 * 1000L }
            
            transaction {
                // Создаём системного пользователя если его нет
                val systemUserId = "system-admin"
                val existingSystem = Users.select { Users.id eq systemUserId }.singleOrNull()
                if (existingSystem == null) {
                    Users.insert {
                        it[id] = systemUserId
                        it[username] = "system"
                        it[displayName] = "System"
                        it[publicKey] = ""
                        it[accessLevel] = 100
                        it[createdAt] = now
                    }
                }
                
                InviteKeys.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[InviteKeys.keyHash] = keyHash
                    it[accessLevel] = request.accessLevel
                    it[createdBy] = systemUserId
                    it[createdAt] = now
                    it[InviteKeys.expiresAt] = expiresAt
                }
            }
            
            call.respond(InviteKeyResponse(key, expiresAt))
        }
        
        // Вход существующего пользователя по username + PIN
        post("/login") {
            val request = call.receive<LoginRequest>()
            
            val result = transaction {
                val user = Users.select { Users.username eq request.username }.singleOrNull()
                    ?: return@transaction null to "Пользователь не найден"
                
                val userId = user[Users.id]
                val accessLevel = user[Users.accessLevel]
                val storedPinHash = user[Users.pinHash]
                val lockedUntil = user[Users.lockedUntil]
                val failedAttempts = user[Users.failedAttempts]
                
                // Проверяем блокировку
                if (lockedUntil != null && lockedUntil > System.currentTimeMillis()) {
                    val remainingMinutes = (lockedUntil - System.currentTimeMillis()) / 60000
                    return@transaction null to "Аккаунт заблокирован. Попробуйте через $remainingMinutes мин."
                }
                
                // Проверяем PIN
                val pinHash = hashKey(request.pin)
                if (storedPinHash != null && storedPinHash != pinHash) {
                    val newFailedAttempts = failedAttempts + 1
                    
                    if (newFailedAttempts >= 3) {
                        // Блокируем на 15 минут
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = 0
                            it[Users.lockedUntil] = System.currentTimeMillis() + 15 * 60 * 1000
                        }
                        return@transaction null to "Неверный PIN. Аккаунт заблокирован на 15 минут"
                    } else {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = newFailedAttempts
                        }
                        return@transaction null to "Неверный PIN. Осталось попыток: ${3 - newFailedAttempts}"
                    }
                }
                
                // Сбрасываем счётчик неудачных попыток и обновляем lastSeen
                Users.update({ Users.id eq userId }) {
                    it[lastSeen] = System.currentTimeMillis()
                    it[Users.failedAttempts] = 0
                    it[Users.lockedUntil] = null
                }
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to error))
            }
        }
        
        // === Простая регистрация по позывному + пароль (MKR) ===
        
        // Регистрация по позывному
        post("/register/simple") {
            val request = call.receive<SimpleRegisterRequest>()
            
            // Валидация позывного (латиница, кириллица, цифры, подчеркивание)
            if (request.callsign.length < 3 || !request.callsign.matches(Regex("^[a-zA-Zа-яА-ЯёЁ0-9_]+$"))) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Позывной должен быть минимум 3 символа (буквы, цифры и _)"))
                return@post
            }
            
            // Валидация пароля
            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Пароль должен быть минимум 6 символов"))
                return@post
            }
            
            val passwordHash = hashKey(request.password)
            val now = System.currentTimeMillis()
            
            val result = transaction {
                // Проверяем уникальность позывного
                val existingUser = Users.select { Users.username eq request.callsign.lowercase() }.singleOrNull()
                if (existingUser != null) {
                    return@transaction null to "Этот позывной уже занят"
                }
                
                val userId = UUID.randomUUID().toString()
                val accessLevel = 1 // Обычный пользователь
                
                // Создаём пользователя
                Users.insert {
                    it[id] = userId
                    it[username] = request.callsign.lowercase()
                    it[displayName] = request.displayName
                    it[publicKey] = ""
                    it[Users.passwordHash] = passwordHash
                    it[Users.accessLevel] = accessLevel
                    it[isVerified] = false
                    it[createdAt] = now
                }
                
                // Подписываем на официальный канал MKR
                subscribeToMkrChannel(userId)
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.Created, response)
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
            }
        }
        
        // Вход по позывному + пароль (или email для админов)
        post("/login/simple") {
            val request = call.receive<SimpleLoginRequest>()
            
            val isEmail = request.callsign.contains("@")
            
            val result = transaction {
                val user = if (isEmail) {
                    // Вход по email (для админов)
                    Users.select { Users.email eq request.callsign }.singleOrNull()
                } else {
                    // Вход по позывному
                    Users.select { Users.username eq request.callsign.lowercase() }.singleOrNull()
                }
                
                if (user == null) {
                    return@transaction null to if (isEmail) "Пользователь не найден" else "Позывной не найден"
                }
                
                val userId = user[Users.id]
                val accessLevel = user[Users.accessLevel]
                val storedPasswordHash = user[Users.passwordHash]
                val lockedUntil = user[Users.lockedUntil]
                val failedAttempts = user[Users.failedAttempts]
                val isBanned = user[Users.isBanned]
                val banReason = user[Users.banReason]
                
                // Проверяем бан
                if (isBanned) {
                    return@transaction null to "Аккаунт заблокирован: ${banReason ?: "нарушение правил"}"
                }
                
                // Проверяем блокировку
                if (lockedUntil != null && lockedUntil > System.currentTimeMillis()) {
                    val remainingMinutes = (lockedUntil - System.currentTimeMillis()) / 60000
                    return@transaction null to "Аккаунт заблокирован. Попробуйте через $remainingMinutes мин."
                }
                
                // Проверяем пароль
                val passwordHash = hashKey(request.password)
                if (storedPasswordHash != null && storedPasswordHash != passwordHash) {
                    val newFailedAttempts = failedAttempts + 1
                    
                    if (newFailedAttempts >= 5) {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = 0
                            it[Users.lockedUntil] = System.currentTimeMillis() + 15 * 60 * 1000
                        }
                        return@transaction null to "Неверный пароль. Аккаунт заблокирован на 15 минут"
                    } else {
                        Users.update({ Users.id eq userId }) {
                            it[Users.failedAttempts] = newFailedAttempts
                        }
                        return@transaction null to "Неверный пароль. Осталось попыток: ${5 - newFailedAttempts}"
                    }
                }
                
                // Сбрасываем счётчик и обновляем lastSeen
                Users.update({ Users.id eq userId }) {
                    it[lastSeen] = System.currentTimeMillis()
                    it[Users.failedAttempts] = 0
                    it[Users.lockedUntil] = null
                }
                
                val token = JwtConfig.generateToken(userId, accessLevel)
                
                AuthResponse(userId, token, accessLevel) to null
            }
            
            val (response, error) = result
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to error))
            }
        }
    }
}

// Helper функция для подписки на канал MKR
private fun subscribeToMkrChannel(userId: String) {
    val mkrChatId = "mkr-official-channel"
    val mkrChat = Chats.select { Chats.id eq mkrChatId }.singleOrNull()
    if (mkrChat != null) {
        val alreadySubscribed = ChatParticipants.select { 
            (ChatParticipants.chatId eq mkrChatId) and (ChatParticipants.userId eq userId)
        }.count() > 0
        
        if (!alreadySubscribed) {
            ChatParticipants.insert {
                it[chatId] = mkrChatId
                it[ChatParticipants.userId] = userId
                it[role] = "member"
                it[joinedAt] = System.currentTimeMillis()
            }
        }
        
        // Также подписываем в таблице ChannelSubscriptions
        val channelInfo = Channels.select { Channels.chatId eq mkrChatId }.singleOrNull()
        if (channelInfo != null) {
            val channelId = channelInfo[Channels.id]
            val alreadySubChannel = ChannelSubscriptions.select {
                (ChannelSubscriptions.channelId eq channelId) and (ChannelSubscriptions.userId eq userId)
            }.count() > 0
            
            if (!alreadySubChannel) {
                ChannelSubscriptions.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[ChannelSubscriptions.channelId] = channelId
                    it[ChannelSubscriptions.userId] = userId
                    it[notificationsEnabled] = true
                    it[subscribedAt] = System.currentTimeMillis()
                }
            }
        }
    }
}

@Serializable
data class LoginRequest(
    val username: String,
    val pin: String // PIN-код для входа
)

// Простая регистрация по позывному + пароль (MKR)
@Serializable
data class SimpleRegisterRequest(
    val callsign: String,
    val displayName: String,
    val password: String
)

// Простой вход по позывному + пароль
@Serializable
data class SimpleLoginRequest(
    val callsign: String,
    val password: String
)

// Web session routes for QR code login
fun Route.webSessionRoutes() {
    route("/api/web") {
        // Создать новую web-сессию (вызывается web-клиентом)
        post("/session/create") {
            val sessionId = UUID.randomUUID().toString()
            val sessionCode = generateSessionCode() // 6-значный код
            val now = System.currentTimeMillis()
            val expiresAt = now + 5 * 60 * 1000 // 5 минут
            
            transaction {
                WebSessions.insert {
                    it[id] = sessionId
                    it[WebSessions.sessionCode] = sessionCode
                    it[status] = "pending"
                    it[createdAt] = now
                    it[WebSessions.expiresAt] = expiresAt
                }
            }
            
            call.respond(WebSessionResponse(
                sessionId = sessionId,
                sessionCode = sessionCode,
                expiresAt = expiresAt
            ))
        }
        
        // Проверить статус сессии (polling от web-клиента)
        get("/session/{sessionId}/status") {
            val sessionId = call.parameters["sessionId"] 
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            
            val session = transaction {
                WebSessions.select { WebSessions.id eq sessionId }.singleOrNull()
            }
            
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }
            
            val status = session[WebSessions.status]
            val expiresAt = session[WebSessions.expiresAt]
            
            // Проверяем истечение
            if (expiresAt < System.currentTimeMillis() && status == "pending") {
                transaction {
                    WebSessions.update({ WebSessions.id eq sessionId }) {
                        it[WebSessions.status] = "expired"
                    }
                }
                call.respond(WebSessionStatusResponse(status = "expired", token = null, userId = null))
                return@get
            }
            
            if (status == "authorized") {
                val token = session[WebSessions.token]
                val userId = session[WebSessions.userId]
                
                // Получаем данные пользователя
                val user = transaction {
                    Users.select { Users.id eq userId!! }.singleOrNull()
                }
                
                call.respond(WebSessionStatusResponse(
                    status = "authorized",
                    token = token,
                    userId = userId,
                    username = user?.get(Users.username),
                    displayName = user?.get(Users.displayName),
                    avatarUrl = user?.get(Users.avatarUrl),
                    accessLevel = user?.get(Users.accessLevel)
                ))
            } else {
                call.respond(WebSessionStatusResponse(status = status, token = null, userId = null))
            }
        }
        
        // Авторизовать сессию (вызывается мобильным приложением после сканирования QR)
        authenticate("auth-jwt") {
            post("/session/authorize") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val accessLevel = principal.payload.getClaim("accessLevel")?.asInt() ?: 1
                
                val request = call.receive<AuthorizeWebSessionRequest>()
                
                val result = transaction {
                    val session = WebSessions.select { 
                        WebSessions.sessionCode eq request.sessionCode 
                    }.singleOrNull()
                    
                    if (session == null) {
                        return@transaction "Session not found" to null
                    }
                    
                    if (session[WebSessions.status] != "pending") {
                        return@transaction "Session already used or expired" to null
                    }
                    
                    if (session[WebSessions.expiresAt] < System.currentTimeMillis()) {
                        return@transaction "Session expired" to null
                    }
                    
                    // Генерируем токен для web-сессии
                    val webToken = JwtConfig.generateToken(userId, accessLevel)
                    
                    WebSessions.update({ WebSessions.sessionCode eq request.sessionCode }) {
                        it[WebSessions.userId] = userId
                        it[token] = webToken
                        it[status] = "authorized"
                        it[authorizedAt] = System.currentTimeMillis()
                        it[deviceInfo] = request.deviceInfo
                    }
                    
                    null to webToken
                }
                
                val (error, token) = result
                if (error != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                } else {
                    call.respond(mapOf("success" to true))
                }
            }
        }
        
        // Получить активные web-сессии пользователя
        authenticate("auth-jwt") {
            get("/sessions") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val sessions = transaction {
                    WebSessions.select { 
                        (WebSessions.userId eq userId) and (WebSessions.status eq "authorized")
                    }.map { row ->
                        ActiveWebSession(
                            sessionId = row[WebSessions.id],
                            deviceInfo = row[WebSessions.deviceInfo],
                            authorizedAt = row[WebSessions.authorizedAt] ?: 0
                        )
                    }
                }
                
                call.respond(sessions)
            }
            
            // Завершить web-сессию
            delete("/session/{sessionId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() 
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                
                val sessionId = call.parameters["sessionId"] 
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    WebSessions.deleteWhere { 
                        (WebSessions.id eq sessionId) and (WebSessions.userId eq userId)
                    }
                }
                
                call.respond(mapOf("success" to true))
            }
        }
        
        // Отзыв всех токенов (для Panic Button)
        authenticate("auth-jwt") {
            post("/revoke-all") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                transaction {
                    // Удаляем все FCM токены пользователя
                    DeviceTokens.deleteWhere { DeviceTokens.userId eq userId }
                    
                    // Удаляем все web-сессии
                    WebSessions.deleteWhere { WebSessions.userId eq userId }
                    
                    // Очищаем FCM токен в таблице Users
                    Users.update({ Users.id eq userId }) {
                        it[fcmToken] = null
                    }
                }
                
                println("All tokens revoked for user: $userId")
                call.respond(mapOf("success" to true, "message" to "All tokens revoked"))
            }
        }
    }
}

@Serializable
data class WebSessionResponse(
    val sessionId: String,
    val sessionCode: String,
    val expiresAt: Long
)

@Serializable
data class WebSessionStatusResponse(
    val status: String,
    val token: String?,
    val userId: String?,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val accessLevel: Int? = null
)

@Serializable
data class AuthorizeWebSessionRequest(
    val sessionCode: String,
    val deviceInfo: String? = null
)

@Serializable
data class ActiveWebSession(
    val sessionId: String,
    val deviceInfo: String?,
    val authorizedAt: Long
)

private fun generateSessionCode(): String {
    val random = java.security.SecureRandom()
    return (1..6).map { random.nextInt(10) }.joinToString("")
}

private fun generateInviteKey(): String {
    val bytes = ByteArray(24)
    java.security.SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun hashKey(key: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(key.toByteArray())
    return Base64.getEncoder().encodeToString(hash)
}
