package com.pioneer.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Сервис верификации телефона через sms.ru callcheck API
 * Пользователь звонит на указанный номер для подтверждения
 */
object PhoneVerificationService {
    
    private const val API_ID = "70D4B157-DA05-F2CA-5D07-66CBEC8B8735"
    private const val BASE_URL = "https://sms.ru/callcheck"
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Serializable
    data class CallCheckAddResponse(
        val status: String,
        val status_code: Int,
        val check_id: String? = null,
        val call_phone: String? = null,
        val call_phone_pretty: String? = null,
        val call_phone_html: String? = null
    )
    
    @Serializable
    data class CallCheckStatusResponse(
        val status: String,
        val status_code: Int,
        val check_status: String? = null,
        val check_status_text: String? = null
    )
    
    /**
     * Инициировать верификацию телефона
     * @param phone Номер телефона пользователя (с которого он будет звонить)
     * @return check_id и номер для звонка, или null при ошибке
     */
    suspend fun initiateVerification(phone: String): CallCheckResult? {
        return try {
            val normalizedPhone = normalizePhone(phone)
            
            val url = URL("$BASE_URL/add?api_id=$API_ID&phone=$normalizedPhone&json=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                println("PHONE_VERIFY: HTTP error $responseCode")
                return null
            }
            
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            println("PHONE_VERIFY: Response for $normalizedPhone: $body")
            
            val result = json.decodeFromString<CallCheckAddResponse>(body)
            
            if (result.status == "OK" && result.status_code == 100) {
                CallCheckResult(
                    checkId = result.check_id!!,
                    callPhone = result.call_phone!!,
                    callPhonePretty = result.call_phone_pretty ?: result.call_phone!!
                )
            } else {
                println("PHONE_VERIFY: Error - ${result.status_code}")
                null
            }
        } catch (e: Exception) {
            println("PHONE_VERIFY: Exception - ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Проверить статус верификации
     * @param checkId ID проверки от initiateVerification
     * @return статус: "pending", "verified", "expired", "error"
     */
    suspend fun checkStatus(checkId: String): VerificationStatus {
        return try {
            val url = URL("$BASE_URL/status?api_id=$API_ID&check_id=$checkId&json=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return VerificationStatus.ERROR
            }
            
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            println("PHONE_VERIFY: Status check for $checkId: $body")
            
            val result = json.decodeFromString<CallCheckStatusResponse>(body)
            
            when (result.check_status) {
                "401" -> VerificationStatus.VERIFIED
                "400" -> VerificationStatus.PENDING
                "402" -> VerificationStatus.EXPIRED
                else -> VerificationStatus.ERROR
            }
        } catch (e: Exception) {
            println("PHONE_VERIFY: Status check exception - ${e.message}")
            VerificationStatus.ERROR
        }
    }
    
    /**
     * Нормализация номера телефона (убираем +, пробелы, скобки)
     */
    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }
    
    data class CallCheckResult(
        val checkId: String,
        val callPhone: String,
        val callPhonePretty: String
    )
    
    enum class VerificationStatus {
        PENDING,
        VERIFIED,
        EXPIRED,
        ERROR
    }
}
