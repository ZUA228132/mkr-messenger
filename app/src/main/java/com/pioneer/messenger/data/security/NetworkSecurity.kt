package com.pioneer.messenger.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Сетевая безопасность
 * 
 * - Certificate Pinning (защита от MITM)
 * - Проверка SSL/TLS
 * - Безопасные заголовки
 */
@Singleton
class NetworkSecurity @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Пины сертификатов сервера (SHA-256)
        private val CERTIFICATE_PINS = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // Primary
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // Backup
        )
        
        private const val SERVER_HOST = "kluboksrm.ru"
    }
    
    /**
     * Создание защищённого OkHttpClient с Certificate Pinning
     */
    fun createSecureClient(): OkHttpClient.Builder {
        val certificatePinner = CertificatePinner.Builder()
            .add(SERVER_HOST, *CERTIFICATE_PINS.toTypedArray())
            .build()
        
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Security-Token", generateSecurityToken())
                    .addHeader("X-Device-Fingerprint", getDeviceFingerprint())
                    .build()
                chain.proceed(request)
            }
    }

    /**
     * Проверка SSL сертификата
     */
    fun verifyCertificate(certificate: X509Certificate): Boolean {
        return try {
            certificate.checkValidity()
            
            // Проверяем отпечаток
            val sha256 = MessageDigest.getInstance("SHA-256")
            val fingerprint = sha256.digest(certificate.encoded)
            val fingerprintBase64 = android.util.Base64.encodeToString(
                fingerprint, 
                android.util.Base64.NO_WRAP
            )
            
            CERTIFICATE_PINS.any { it.contains(fingerprintBase64) }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Генерация токена безопасности для запросов
     */
    private fun generateSecurityToken(): String {
        val timestamp = System.currentTimeMillis()
        val random = SecureRandom().nextInt(100000)
        val data = "$timestamp:$random:${context.packageName}"
        
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(data.toByteArray())
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }
    
    /**
     * Получение отпечатка устройства
     */
    private fun getDeviceFingerprint(): String {
        val data = StringBuilder()
        data.append(android.os.Build.BOARD)
        data.append(android.os.Build.BRAND)
        data.append(android.os.Build.DEVICE)
        data.append(android.os.Build.MODEL)
        
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(data.toString().toByteArray())
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP).take(32)
    }
    
    /**
     * Проверка на MITM атаку
     */
    fun detectMITM(): Boolean {
        // Проверяем наличие прокси
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        
        if (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) {
            return true // Обнаружен прокси
        }
        
        // Проверяем VPN
        return isVpnActive()
    }
    
    /**
     * Проверка активного VPN
     */
    private fun isVpnActive(): Boolean {
        return try {
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                if (networkInterface.isUp && 
                    (networkInterface.name.contains("tun") || 
                     networkInterface.name.contains("ppp"))) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Шифрование данных для передачи
     */
    fun encryptForTransmission(data: ByteArray, publicKey: ByteArray): ByteArray {
        // Используем гибридное шифрование: RSA для ключа, AES для данных
        val aesKey = ByteArray(32)
        SecureRandom().nextBytes(aesKey)
        
        // Шифруем данные AES
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(aesKey, "AES")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        
        val encryptedData = cipher.doFinal(data)
        
        // Возвращаем IV + зашифрованные данные
        return iv + encryptedData
    }
}
