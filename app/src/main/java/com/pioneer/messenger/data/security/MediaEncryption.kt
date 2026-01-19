package com.pioneer.messenger.data.security

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шифрование медиафайлов (фото, видео, голосовые)
 * 
 * Все медиафайлы шифруются AES-256-GCM перед сохранением
 */
@Singleton
class MediaEncryption @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureDataManager: SecureDataManager
) {
    companion object {
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_SIZE = 32 // 256 bits
        private const val BUFFER_SIZE = 8192
        
        private const val ENCRYPTED_DIR = "encrypted_media"
        private const val FILE_EXTENSION = ".enc"
    }
    
    private val secureRandom = SecureRandom()
    
    /**
     * Шифрование файла
     * @return путь к зашифрованному файлу и ключ
     */
    fun encryptFile(inputUri: Uri): EncryptedFileResult {
        val key = generateKey()
        val iv = generateIV()
        
        val outputFile = createEncryptedFile()
        
        context.contentResolver.openInputStream(inputUri)?.use { input ->
            FileOutputStream(outputFile).use { output ->
                // Записываем IV в начало файла
                output.write(iv)
                
                val cipher = Cipher.getInstance(AES_GCM)
                val keySpec = SecretKeySpec(key, "AES")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                
                CipherOutputStream(output, cipher).use { cipherOut ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        cipherOut.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
        
        return EncryptedFileResult(
            encryptedPath = outputFile.absolutePath,
            encryptionKey = key,
            iv = iv
        )
    }
    
    /**
     * Шифрование файла по пути
     */
    fun encryptFile(inputPath: String): EncryptedFileResult {
        val key = generateKey()
        val iv = generateIV()
        
        val inputFile = File(inputPath)
        val outputFile = createEncryptedFile()
        
        FileInputStream(inputFile).use { input ->
            FileOutputStream(outputFile).use { output ->
                output.write(iv)
                
                val cipher = Cipher.getInstance(AES_GCM)
                val keySpec = SecretKeySpec(key, "AES")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                
                CipherOutputStream(output, cipher).use { cipherOut ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        cipherOut.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
        
        return EncryptedFileResult(
            encryptedPath = outputFile.absolutePath,
            encryptionKey = key,
            iv = iv
        )
    }
    
    /**
     * Расшифровка файла
     */
    fun decryptFile(encryptedPath: String, key: ByteArray): File {
        val inputFile = File(encryptedPath)
        val outputFile = createTempDecryptedFile()
        
        FileInputStream(inputFile).use { input ->
            // Читаем IV из начала файла
            val iv = ByteArray(GCM_IV_LENGTH)
            input.read(iv)
            
            val cipher = Cipher.getInstance(AES_GCM)
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            CipherInputStream(input, cipher).use { cipherIn ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
        
        return outputFile
    }
    
    /**
     * Расшифровка в ByteArray (для небольших файлов)
     */
    fun decryptToBytes(encryptedPath: String, key: ByteArray): ByteArray {
        val decryptedFile = decryptFile(encryptedPath, key)
        val bytes = decryptedFile.readBytes()
        
        // Безопасно удаляем временный файл
        secureDeleteFile(decryptedFile)
        
        return bytes
    }
    
    /**
     * Шифрование байтов
     */
    fun encryptBytes(data: ByteArray): EncryptedBytesResult {
        val key = generateKey()
        val iv = generateIV()
        
        val cipher = Cipher.getInstance(AES_GCM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        
        val encrypted = cipher.doFinal(data)
        
        return EncryptedBytesResult(
            encryptedData = iv + encrypted,
            encryptionKey = key
        )
    }
    
    /**
     * Расшифровка байтов
     */
    fun decryptBytes(encryptedData: ByteArray, key: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        
        val cipher = Cipher.getInstance(AES_GCM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Безопасное удаление файла
     */
    fun secureDeleteFile(file: File) {
        if (!file.exists()) return
        
        try {
            val length = file.length()
            if (length > 0) {
                val randomData = ByteArray(minOf(length.toInt(), BUFFER_SIZE))
                
                // 3 прохода перезаписи
                repeat(3) { pass ->
                    FileOutputStream(file).use { output ->
                        var remaining = length
                        while (remaining > 0) {
                            val toWrite = minOf(remaining, randomData.size.toLong()).toInt()
                            when (pass) {
                                0 -> secureRandom.nextBytes(randomData)
                                1 -> randomData.fill(0x00)
                                2 -> randomData.fill(0xFF.toByte())
                            }
                            output.write(randomData, 0, toWrite)
                            remaining -= toWrite
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            file.delete()
        }
    }
    
    /**
     * Очистка всех зашифрованных медиа
     */
    fun clearAllEncryptedMedia() {
        val encryptedDir = File(context.filesDir, ENCRYPTED_DIR)
        if (encryptedDir.exists()) {
            encryptedDir.listFiles()?.forEach { file ->
                secureDeleteFile(file)
            }
            encryptedDir.delete()
        }
    }
    
    /**
     * Получить размер зашифрованных медиа
     */
    fun getEncryptedMediaSize(): Long {
        val encryptedDir = File(context.filesDir, ENCRYPTED_DIR)
        if (!encryptedDir.exists()) return 0
        
        return encryptedDir.listFiles()?.sumOf { it.length() } ?: 0
    }
    
    private fun generateKey(): ByteArray {
        val key = ByteArray(KEY_SIZE)
        secureRandom.nextBytes(key)
        return key
    }
    
    private fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }
    
    private fun createEncryptedFile(): File {
        val encryptedDir = File(context.filesDir, ENCRYPTED_DIR)
        if (!encryptedDir.exists()) {
            encryptedDir.mkdirs()
        }
        
        val fileName = "${System.currentTimeMillis()}_${secureRandom.nextInt(10000)}$FILE_EXTENSION"
        return File(encryptedDir, fileName)
    }
    
    private fun createTempDecryptedFile(): File {
        val tempDir = File(context.cacheDir, "temp_decrypted")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        val fileName = "dec_${System.currentTimeMillis()}"
        return File(tempDir, fileName)
    }
    
    // ==================== DATA CLASSES ====================
    
    data class EncryptedFileResult(
        val encryptedPath: String,
        val encryptionKey: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedFileResult
            return encryptedPath == other.encryptedPath
        }
        
        override fun hashCode(): Int = encryptedPath.hashCode()
    }
    
    data class EncryptedBytesResult(
        val encryptedData: ByteArray,
        val encryptionKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedBytesResult
            return encryptedData.contentEquals(other.encryptedData)
        }
        
        override fun hashCode(): Int = encryptedData.contentHashCode()
    }
}
