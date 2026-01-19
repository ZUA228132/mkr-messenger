package com.pioneer.messenger.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер безопасного хранения данных
 * 
 * - Шифрование всех локальных данных
 * - Безопасное удаление
 * - Panic button функционал
 */
@Singleton
class SecureDataManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_ALIAS = "secure_messenger_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        
        // Файлы для безопасного удаления
        private val SENSITIVE_DIRS = listOf(
            "databases",
            "shared_prefs", 
            "files",
            "cache",
            "app_webview"
        )
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
        
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        
        // Требуем аутентификацию для доступа к ключу (опционально)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        
        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }
    
    private fun getMasterKey(): SecretKey {
        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
    
    /**
     * Шифрование данных
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        
        // Формат: IV (12 bytes) + Ciphertext
        return iv + ciphertext
    }
    
    /**
     * Расшифровка данных
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        
        val cipher = Cipher.getInstance(AES_GCM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
        
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Шифрование строки
     */
    fun encryptString(plaintext: String): String {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    
    /**
     * Расшифровка строки
     */
    fun decryptString(encryptedBase64: String): String {
        val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decrypted = decrypt(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }
    
    /**
     * PANIC BUTTON - Экстренное удаление всех данных
     * 
     * Выполняет:
     * 1. Удаление ключей из Keystore
     * 2. Безопасное затирание файлов
     * 3. Очистка SharedPreferences
     * 4. Очистка кэша
     */
    fun panicWipe(): Boolean {
        return try {
            // 1. Удаляем ключи из Keystore
            wipeKeystore()
            
            // 2. Безопасно затираем все файлы
            SENSITIVE_DIRS.forEach { dir ->
                val directory = File(context.dataDir, dir)
                if (directory.exists()) {
                    secureDeleteDirectory(directory)
                }
            }
            
            // 3. Очищаем внешнее хранилище приложения
            context.getExternalFilesDir(null)?.let { secureDeleteDirectory(it) }
            context.externalCacheDir?.let { secureDeleteDirectory(it) }
            
            // 4. Очищаем кэш
            context.cacheDir?.let { secureDeleteDirectory(it) }
            
            // 5. Очищаем все SharedPreferences
            clearAllSharedPreferences()
            
            true
        } catch (e: Exception) {
            android.util.Log.e("SecureDataManager", "Panic wipe failed: ${e.message}")
            false
        }
    }
    
    /**
     * Удаление всех ключей из Keystore
     */
    private fun wipeKeystore() {
        try {
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                keyStore.deleteEntry(alias)
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureDataManager", "Keystore wipe error: ${e.message}")
        }
    }
    
    /**
     * Безопасное удаление директории
     * Перезаписывает файлы случайными данными перед удалением
     */
    private fun secureDeleteDirectory(directory: File) {
        if (!directory.exists()) return
        
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                secureDeleteDirectory(file)
            } else {
                secureDeleteFile(file)
            }
        }
        
        directory.delete()
    }
    
    /**
     * Безопасное удаление файла
     * Перезаписывает содержимое случайными данными 3 раза
     */
    private fun secureDeleteFile(file: File) {
        if (!file.exists() || !file.isFile) return
        
        try {
            val length = file.length()
            if (length > 0) {
                val randomData = ByteArray(minOf(length.toInt(), 1024 * 1024)) // Max 1MB chunks
                
                // 3 прохода перезаписи
                repeat(3) { pass ->
                    file.outputStream().use { output ->
                        var remaining = length
                        while (remaining > 0) {
                            val toWrite = minOf(remaining, randomData.size.toLong()).toInt()
                            when (pass) {
                                0 -> secureRandom.nextBytes(randomData) // Случайные данные
                                1 -> randomData.fill(0x00) // Нули
                                2 -> randomData.fill(0xFF.toByte()) // Единицы
                            }
                            output.write(randomData, 0, toWrite)
                            remaining -= toWrite
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureDataManager", "Secure delete error: ${e.message}")
        } finally {
            file.delete()
        }
    }
    
    /**
     * Очистка всех SharedPreferences
     */
    private fun clearAllSharedPreferences() {
        val prefsDir = File(context.dataDir, "shared_prefs")
        if (prefsDir.exists()) {
            prefsDir.listFiles()?.forEach { file ->
                val prefName = file.nameWithoutExtension
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }
        }
    }
    
    /**
     * Удаление конкретного чата и всех связанных данных
     */
    fun deleteChat(chatId: String) {
        // Удаляем медиафайлы чата
        val mediaDir = File(context.filesDir, "media/$chatId")
        if (mediaDir.exists()) {
            secureDeleteDirectory(mediaDir)
        }
        
        // Удаляем кэш чата
        val cacheDir = File(context.cacheDir, "chat_$chatId")
        if (cacheDir.exists()) {
            secureDeleteDirectory(cacheDir)
        }
    }
    
    /**
     * Безопасная очистка памяти
     */
    fun secureWipe(data: ByteArray) {
        secureRandom.nextBytes(data)
        data.fill(0)
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
     * Проверка целостности данных (для обнаружения tampering)
     */
    fun verifyIntegrity(data: ByteArray, expectedHash: ByteArray): Boolean {
        val actualHash = hash(data)
        return constantTimeEquals(actualHash, expectedHash)
    }
    
    /**
     * SHA-256 хеш
     */
    fun hash(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
    
    /**
     * Безопасное сравнение (защита от timing attacks)
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
