package com.pioneer.messenger.data.crypto

import android.util.Base64
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signal Protocol Implementation
 * Double Ratchet Algorithm для Perfect Forward Secrecy
 * 
 * Каждое сообщение шифруется уникальным ключом.
 * Компрометация одного ключа не раскрывает другие сообщения.
 * 
 * Дополнительные функции безопасности:
 * - Верификация ключей через Safety Numbers
 * - Защита от replay-атак
 * - Проверка целостности сессии
 */
@Singleton
class SignalProtocol @Inject constructor() {
    
    companion object {
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_SIZE = 32 // 256 bits
        private const val MAX_SKIP = 1000 // Максимум пропущенных сообщений
        private const val SESSION_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L // 7 дней
        private const val MAX_MESSAGE_AGE_MS = 5 * 60 * 1000L // 5 минут для replay protection
    }
    
    private val secureRandom = SecureRandom()
    private val processedMessageIds = mutableSetOf<String>() // Для защиты от replay
    
    /**
     * Состояние сессии Double Ratchet
     */
    data class SessionState(
        val dhKeyPair: DHKeyPair,           // Наша текущая DH пара
        val remoteDHPublicKey: ByteArray?,  // Публичный ключ собеседника
        val rootKey: ByteArray,             // Корневой ключ
        val sendingChainKey: ByteArray?,    // Ключ цепочки отправки
        val receivingChainKey: ByteArray?,  // Ключ цепочки получения
        val sendingMessageNumber: Int = 0,  // Номер отправленного сообщения
        val receivingMessageNumber: Int = 0,// Номер полученного сообщения
        val previousSendingChainLength: Int = 0, // Длина предыдущей цепочки
        val skippedMessageKeys: Map<Pair<ByteArray, Int>, ByteArray> = emptyMap(), // Пропущенные ключи
        val createdAt: Long = System.currentTimeMillis(), // Время создания сессии
        val lastActivityAt: Long = System.currentTimeMillis() // Последняя активность
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > SESSION_EXPIRY_MS
        fun updateActivity(): SessionState = copy(lastActivityAt = System.currentTimeMillis())
    }
    
    data class DHKeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    )
    
    data class MessageHeader(
        val dhPublicKey: ByteArray,
        val previousChainLength: Int,
        val messageNumber: Int
    )
    
    data class EncryptedMessage(
        val header: MessageHeader,
        val ciphertext: ByteArray,
        val iv: ByteArray
    )
    
    /**
     * Генерация X25519 ключевой пары
     */
    fun generateDHKeyPair(): DHKeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("X25519")
        val keyPair = keyPairGenerator.generateKeyPair()
        return DHKeyPair(
            publicKey = keyPair.public.encoded,
            privateKey = keyPair.private.encoded
        )
    }
    
    /**
     * Инициализация сессии (отправитель)
     */
    fun initializeSessionAsSender(
        ourIdentityKey: DHKeyPair,
        theirIdentityPublicKey: ByteArray,
        theirSignedPreKey: ByteArray
    ): SessionState {
        // Вычисляем общий секрет через X3DH
        val sharedSecret = x3dhSender(
            ourIdentityKey,
            theirIdentityPublicKey,
            theirSignedPreKey
        )
        
        // Генерируем эфемерную пару для первого ratchet
        val ephemeralKeyPair = generateDHKeyPair()
        
        // Вычисляем DH с их signed prekey
        val dhOutput = computeDH(ephemeralKeyPair.privateKey, theirSignedPreKey)
        
        // Деривация корневого ключа и ключа цепочки
        val (rootKey, chainKey) = kdfRootKey(sharedSecret, dhOutput)
        
        return SessionState(
            dhKeyPair = ephemeralKeyPair,
            remoteDHPublicKey = theirSignedPreKey,
            rootKey = rootKey,
            sendingChainKey = chainKey,
            receivingChainKey = null
        )
    }
    
    /**
     * Инициализация сессии (получатель)
     */
    fun initializeSessionAsReceiver(
        ourIdentityKey: DHKeyPair,
        ourSignedPreKey: DHKeyPair,
        theirIdentityPublicKey: ByteArray,
        theirEphemeralPublicKey: ByteArray
    ): SessionState {
        // Вычисляем общий секрет через X3DH
        val sharedSecret = x3dhReceiver(
            ourIdentityKey,
            ourSignedPreKey,
            theirIdentityPublicKey,
            theirEphemeralPublicKey
        )
        
        // Вычисляем DH
        val dhOutput = computeDH(ourSignedPreKey.privateKey, theirEphemeralPublicKey)
        
        // Деривация корневого ключа и ключа цепочки
        val (rootKey, chainKey) = kdfRootKey(sharedSecret, dhOutput)
        
        return SessionState(
            dhKeyPair = ourSignedPreKey,
            remoteDHPublicKey = theirEphemeralPublicKey,
            rootKey = rootKey,
            sendingChainKey = null,
            receivingChainKey = chainKey
        )
    }
    
    /**
     * Шифрование сообщения
     */
    fun encrypt(state: SessionState, plaintext: ByteArray): Pair<SessionState, EncryptedMessage> {
        var currentState = state
        
        // Если нет ключа цепочки отправки, делаем DH ratchet
        if (currentState.sendingChainKey == null) {
            currentState = performDHRatchet(currentState, currentState.remoteDHPublicKey!!)
        }
        
        // Деривация ключа сообщения
        val (newChainKey, messageKey) = kdfChainKey(currentState.sendingChainKey!!)
        
        // Шифрование
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        
        val cipher = Cipher.getInstance(AES_GCM)
        val keySpec = SecretKeySpec(messageKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        // Создаём заголовок
        val header = MessageHeader(
            dhPublicKey = currentState.dhKeyPair.publicKey,
            previousChainLength = currentState.previousSendingChainLength,
            messageNumber = currentState.sendingMessageNumber
        )
        
        // Обновляем состояние
        val newState = currentState.copy(
            sendingChainKey = newChainKey,
            sendingMessageNumber = currentState.sendingMessageNumber + 1
        )
        
        // Очищаем ключ сообщения из памяти
        secureWipe(messageKey)
        
        return Pair(newState, EncryptedMessage(header, ciphertext, iv))
    }
    
    /**
     * Расшифровка сообщения
     */
    fun decrypt(state: SessionState, message: EncryptedMessage): Pair<SessionState, ByteArray> {
        var currentState = state
        
        // Проверяем, нужен ли DH ratchet
        if (!message.header.dhPublicKey.contentEquals(currentState.remoteDHPublicKey)) {
            // Сохраняем пропущенные ключи из текущей цепочки
            currentState = skipMessageKeys(currentState, message.header.previousChainLength)
            
            // Выполняем DH ratchet
            currentState = performDHRatchet(currentState, message.header.dhPublicKey)
        }
        
        // Сохраняем пропущенные ключи до нужного номера
        currentState = skipMessageKeys(currentState, message.header.messageNumber)
        
        // Деривация ключа сообщения
        val (newChainKey, messageKey) = kdfChainKey(currentState.receivingChainKey!!)
        
        // Расшифровка
        val cipher = Cipher.getInstance(AES_GCM)
        val keySpec = SecretKeySpec(messageKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, message.iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        val plaintext = cipher.doFinal(message.ciphertext)
        
        // Обновляем состояние
        val newState = currentState.copy(
            receivingChainKey = newChainKey,
            receivingMessageNumber = currentState.receivingMessageNumber + 1
        )
        
        // Очищаем ключ сообщения
        secureWipe(messageKey)
        
        return Pair(newState, plaintext)
    }
    
    /**
     * DH Ratchet - обновление ключей
     */
    private fun performDHRatchet(state: SessionState, theirPublicKey: ByteArray): SessionState {
        // Вычисляем DH с их новым ключом
        val dhOutput = computeDH(state.dhKeyPair.privateKey, theirPublicKey)
        
        // Деривация нового корневого ключа и ключа цепочки получения
        val (newRootKey, receivingChainKey) = kdfRootKey(state.rootKey, dhOutput)
        
        // Генерируем новую DH пару
        val newDHKeyPair = generateDHKeyPair()
        
        // Вычисляем DH с нашим новым ключом
        val dhOutput2 = computeDH(newDHKeyPair.privateKey, theirPublicKey)
        
        // Деривация ключа цепочки отправки
        val (finalRootKey, sendingChainKey) = kdfRootKey(newRootKey, dhOutput2)
        
        return state.copy(
            dhKeyPair = newDHKeyPair,
            remoteDHPublicKey = theirPublicKey,
            rootKey = finalRootKey,
            sendingChainKey = sendingChainKey,
            receivingChainKey = receivingChainKey,
            previousSendingChainLength = state.sendingMessageNumber,
            sendingMessageNumber = 0,
            receivingMessageNumber = 0
        )
    }
    
    /**
     * Сохранение пропущенных ключей сообщений
     */
    private fun skipMessageKeys(state: SessionState, until: Int): SessionState {
        if (state.receivingChainKey == null) return state
        if (until - state.receivingMessageNumber > MAX_SKIP) {
            throw SecurityException("Too many skipped messages")
        }
        
        var currentState = state
        var chainKey = state.receivingChainKey
        val skippedKeys = state.skippedMessageKeys.toMutableMap()
        
        while (currentState.receivingMessageNumber < until) {
            val (newChainKey, messageKey) = kdfChainKey(chainKey!!)
            skippedKeys[Pair(state.remoteDHPublicKey!!, currentState.receivingMessageNumber)] = messageKey
            chainKey = newChainKey
            currentState = currentState.copy(
                receivingMessageNumber = currentState.receivingMessageNumber + 1
            )
        }
        
        return currentState.copy(
            receivingChainKey = chainKey,
            skippedMessageKeys = skippedKeys
        )
    }
    
    /**
     * X3DH Key Agreement (отправитель)
     */
    private fun x3dhSender(
        ourIdentityKey: DHKeyPair,
        theirIdentityPublicKey: ByteArray,
        theirSignedPreKey: ByteArray
    ): ByteArray {
        val dh1 = computeDH(ourIdentityKey.privateKey, theirSignedPreKey)
        val dh2 = computeDH(ourIdentityKey.privateKey, theirIdentityPublicKey)
        
        return kdfX3DH(dh1 + dh2)
    }
    
    /**
     * X3DH Key Agreement (получатель)
     */
    private fun x3dhReceiver(
        ourIdentityKey: DHKeyPair,
        ourSignedPreKey: DHKeyPair,
        theirIdentityPublicKey: ByteArray,
        theirEphemeralPublicKey: ByteArray
    ): ByteArray {
        val dh1 = computeDH(ourSignedPreKey.privateKey, theirIdentityPublicKey)
        val dh2 = computeDH(ourIdentityKey.privateKey, theirEphemeralPublicKey)
        
        return kdfX3DH(dh1 + dh2)
    }
    
    /**
     * Вычисление Diffie-Hellman
     */
    private fun computeDH(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("X25519")
        
        val privKeySpec = PKCS8EncodedKeySpec(privateKey)
        val privKey = keyFactory.generatePrivate(privKeySpec)
        
        val pubKeySpec = X509EncodedKeySpec(publicKey)
        val pubKey = keyFactory.generatePublic(pubKeySpec)
        
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(privKey)
        keyAgreement.doPhase(pubKey, true)
        
        return keyAgreement.generateSecret()
    }
    
    /**
     * KDF для корневого ключа
     */
    private fun kdfRootKey(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        val input = rootKey + dhOutput
        val output = hkdf(input, "signal-root-key".toByteArray(), 64)
        return Pair(output.copyOfRange(0, 32), output.copyOfRange(32, 64))
    }
    
    /**
     * KDF для ключа цепочки
     */
    private fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val newChainKey = hmacSha256(chainKey, byteArrayOf(0x01))
        val messageKey = hmacSha256(chainKey, byteArrayOf(0x02))
        return Pair(newChainKey, messageKey)
    }
    
    /**
     * KDF для X3DH
     */
    private fun kdfX3DH(input: ByteArray): ByteArray {
        return hkdf(input, "signal-x3dh".toByteArray(), 32)
    }
    
    /**
     * HKDF
     */
    private fun hkdf(input: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Extract
        val prk = hmacSha256(ByteArray(32), input)
        
        // Expand
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter: Byte = 1
        
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter)
            t = mac.doFinal()
            
            val toCopy = minOf(32, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        
        return result
    }
    
    /**
     * HMAC-SHA256
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    /**
     * Безопасная очистка памяти
     */
    fun secureWipe(data: ByteArray) {
        secureRandom.nextBytes(data)
        data.fill(0)
    }
    
    /**
     * Сериализация состояния сессии
     */
    fun serializeSession(state: SessionState): String {
        // Для хранения в зашифрованной БД
        val builder = StringBuilder()
        builder.append(Base64.encodeToString(state.dhKeyPair.publicKey, Base64.NO_WRAP))
        builder.append("|")
        builder.append(Base64.encodeToString(state.dhKeyPair.privateKey, Base64.NO_WRAP))
        builder.append("|")
        builder.append(state.remoteDHPublicKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
        builder.append("|")
        builder.append(Base64.encodeToString(state.rootKey, Base64.NO_WRAP))
        builder.append("|")
        builder.append(state.sendingChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
        builder.append("|")
        builder.append(state.receivingChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
        builder.append("|")
        builder.append(state.sendingMessageNumber)
        builder.append("|")
        builder.append(state.receivingMessageNumber)
        builder.append("|")
        builder.append(state.previousSendingChainLength)
        return builder.toString()
    }
    
    /**
     * Десериализация состояния сессии
     */
    fun deserializeSession(data: String): SessionState {
        val parts = data.split("|")
        return SessionState(
            dhKeyPair = DHKeyPair(
                publicKey = Base64.decode(parts[0], Base64.NO_WRAP),
                privateKey = Base64.decode(parts[1], Base64.NO_WRAP)
            ),
            remoteDHPublicKey = if (parts[2].isNotEmpty()) Base64.decode(parts[2], Base64.NO_WRAP) else null,
            rootKey = Base64.decode(parts[3], Base64.NO_WRAP),
            sendingChainKey = if (parts[4].isNotEmpty()) Base64.decode(parts[4], Base64.NO_WRAP) else null,
            receivingChainKey = if (parts[5].isNotEmpty()) Base64.decode(parts[5], Base64.NO_WRAP) else null,
            sendingMessageNumber = parts[6].toInt(),
            receivingMessageNumber = parts[7].toInt(),
            previousSendingChainLength = parts[8].toInt()
        )
    }
    
    // ==================== ДОПОЛНИТЕЛЬНЫЕ ФУНКЦИИ БЕЗОПАСНОСТИ ====================
    
    /**
     * Генерация Safety Number для верификации ключей
     * Пользователи могут сравнить эти номера для подтверждения подлинности
     */
    fun generateSafetyNumber(
        ourIdentityKey: ByteArray,
        theirIdentityKey: ByteArray,
        ourUserId: String,
        theirUserId: String
    ): String {
        // Сортируем по userId для консистентности
        val (firstKey, secondKey) = if (ourUserId < theirUserId) {
            ourIdentityKey to theirIdentityKey
        } else {
            theirIdentityKey to ourIdentityKey
        }
        
        val combined = firstKey + secondKey
        val hash = sha256(combined)
        
        // Конвертируем в 60-значный номер (12 групп по 5 цифр)
        val sb = StringBuilder()
        for (i in 0 until 30) {
            val value = ((hash[i].toInt() and 0xFF) * 256 + (hash[i + 1].toInt() and 0xFF)) % 100000
            sb.append(String.format("%05d", value))
            if ((i + 1) % 5 == 0 && i < 29) sb.append(" ")
        }
        
        return sb.toString()
    }
    
    /**
     * Генерация QR-кода данных для верификации
     */
    fun generateVerificationQRData(
        ourIdentityKey: ByteArray,
        theirIdentityKey: ByteArray
    ): ByteArray {
        return ourIdentityKey + theirIdentityKey
    }
    
    /**
     * Проверка на replay-атаку
     */
    fun isReplayAttack(messageId: String, timestamp: Long): Boolean {
        // Проверяем возраст сообщения
        val age = System.currentTimeMillis() - timestamp
        if (age > MAX_MESSAGE_AGE_MS || age < -MAX_MESSAGE_AGE_MS) {
            return true // Сообщение слишком старое или из будущего
        }
        
        // Проверяем, не обрабатывали ли мы уже это сообщение
        if (processedMessageIds.contains(messageId)) {
            return true
        }
        
        // Добавляем в обработанные
        processedMessageIds.add(messageId)
        
        // Очищаем старые ID (держим только последние 10000)
        if (processedMessageIds.size > 10000) {
            val toRemove = processedMessageIds.take(5000)
            processedMessageIds.removeAll(toRemove.toSet())
        }
        
        return false
    }
    
    /**
     * Генерация уникального ID сообщения
     */
    fun generateMessageId(): String {
        val timestamp = System.currentTimeMillis()
        val random = ByteArray(8)
        secureRandom.nextBytes(random)
        return "$timestamp-${Base64.encodeToString(random, Base64.NO_WRAP)}"
    }
    
    /**
     * SHA-256 хеш
     */
    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
    
    /**
     * Проверка целостности сессии
     */
    fun verifySessionIntegrity(state: SessionState): Boolean {
        // Проверяем, что сессия не истекла
        if (state.isExpired()) {
            return false
        }
        
        // Проверяем наличие необходимых ключей
        if (state.rootKey.isEmpty()) {
            return false
        }
        
        // Проверяем корректность номеров сообщений
        if (state.sendingMessageNumber < 0 || state.receivingMessageNumber < 0) {
            return false
        }
        
        return true
    }
    
    /**
     * Принудительная ротация ключей
     */
    fun forceKeyRotation(state: SessionState): SessionState {
        val newKeyPair = generateDHKeyPair()
        
        return state.copy(
            dhKeyPair = newKeyPair,
            sendingChainKey = null, // Будет пересоздан при следующем сообщении
            sendingMessageNumber = 0,
            previousSendingChainLength = state.sendingMessageNumber
        )
    }
    
    /**
     * Очистка всех данных сессии из памяти
     */
    fun destroySession(state: SessionState) {
        secureWipe(state.dhKeyPair.privateKey)
        secureWipe(state.rootKey)
        state.sendingChainKey?.let { secureWipe(it) }
        state.receivingChainKey?.let { secureWipe(it) }
        state.skippedMessageKeys.values.forEach { secureWipe(it) }
    }
}
