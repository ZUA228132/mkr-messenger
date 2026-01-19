package com.pioneer.messenger.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер шифрования с поддержкой:
 * - AES-256-GCM для симметричного шифрования
 * - Android Keystore для безопасного хранения мастер-ключа
 * - HKDF для деривации ключей
 * - Double Ratchet совместимая структура
 * - Perfect Forward Secrecy
 * 
 * ВНИМАНИЕ: Никаких бэкдоров или мастер-ключей!
 * Все ключи генерируются локально и никогда не покидают устройство.
 */
@Singleton
class CryptoManager @Inject constructor() {
    
    companion object {
        private const val KEYSTORE_ALIAS = "secure_messenger_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_SIZE = 256
        private const val HKDF_INFO = "secure-messenger-v2"
        
        /**
         * Генерация ключевой пары X25519 для обмена ключами
         * Возвращает публичный ключ в Base64
         */
        fun generateKeyPair(): String {
            val keyPairGenerator = KeyPairGenerator.getInstance("X25519")
            val keyPair = keyPairGenerator.generateKeyPair()
            return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        }
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val secureRandom = SecureRandom()
    
    init {
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            generateMasterKey()
        }
    }
    
    private fun generateMasterKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }
    
    private fun getMasterKey(): SecretKey {
        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
    
    /**
     * Генерация ключа сессии для чата (256 бит)
     */
    fun generateSessionKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(KEY_SIZE, secureRandom)
        return keyGen.generateKey().encoded
    }
    
    /**
     * Генерация случайных байтов
     */
    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    /**
     * Шифрование сообщения с AES-256-GCM
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedData {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }
    
    /**
     * Расшифровка сообщения
     */
    fun decrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encryptedData.ciphertext, Base64.NO_WRAP)
        
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Шифрование ключа сессии мастер-ключом (для локального хранения)
     */
    fun encryptSessionKey(sessionKey: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        
        val ciphertext = cipher.doFinal(sessionKey)
        val iv = cipher.iv
        
        return EncryptedData(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }
    
    /**
     * Расшифровка ключа сессии
     */
    fun decryptSessionKey(encryptedKey: EncryptedData): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        
        val iv = Base64.decode(encryptedKey.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encryptedKey.ciphertext, Base64.NO_WRAP)
        
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
        
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * HKDF - деривация ключа из общего секрета
     * Используется для получения ключей шифрования из результата Diffie-Hellman
     */
    fun deriveKey(inputKeyMaterial: ByteArray, salt: ByteArray? = null, info: String = HKDF_INFO, length: Int = 32): ByteArray {
        // HKDF-Extract
        val prk = if (salt != null && salt.isNotEmpty()) {
            hmacSha256(salt, inputKeyMaterial)
        } else {
            hmacSha256(ByteArray(32), inputKeyMaterial)
        }
        
        // HKDF-Expand
        return hkdfExpand(prk, info.toByteArray(), length)
    }
    
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32 // SHA-256
        val n = (length + hashLen - 1) / hashLen
        
        val result = ByteArray(length)
        var offset = 0
        var t = ByteArray(0)
        
        for (i in 1..n) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            
            val toCopy = minOf(hashLen, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
        }
        
        return result
    }
    
    /**
     * Деривация ключей для Double Ratchet
     * Возвращает пару: (chain key, message key)
     */
    fun deriveMessageKeys(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = hmacSha256(chainKey, byteArrayOf(0x01))
        val nextChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        return Pair(nextChainKey, messageKey)
    }
    
    /**
     * Генерация ключа доступа для приглашения
     */
    fun generateInviteKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }
    
    /**
     * SHA-256 хеш
     */
    fun hash(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Безопасное сравнение байтов (защита от timing attacks)
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    /**
     * Очистка чувствительных данных из памяти
     */
    fun secureWipe(data: ByteArray) {
        secureRandom.nextBytes(data)
        data.fill(0)
    }
    
    /**
     * Генерация эфемерного ключа для одноразового использования
     */
    fun generateEphemeralKey(): ByteArray {
        return generateSessionKey()
    }
    
    /**
     * Проверка целостности данных
     */
    fun verifyIntegrity(data: ByteArray, expectedMac: ByteArray, key: ByteArray): Boolean {
        val actualMac = hmacSha256(key, data)
        return constantTimeEquals(actualMac, expectedMac)
    }
    
    /**
     * Создание MAC для данных
     */
    fun createMac(data: ByteArray, key: ByteArray): ByteArray {
        return hmacSha256(key, data)
    }
}

data class EncryptedData(
    val ciphertext: String,
    val iv: String
)
