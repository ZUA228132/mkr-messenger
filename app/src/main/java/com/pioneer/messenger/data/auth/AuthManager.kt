package com.pioneer.messenger.data.auth

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pioneer.messenger.data.crypto.CryptoManager
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.authDataStore
    
    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_ACCESS_LEVEL = stringPreferencesKey("access_level")
    }
    
    val isAuthenticated: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TOKEN] != null
    }
    
    val currentUserId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_USER_ID]
    }
    
    val currentUsername: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_USERNAME]
    }
    
    data class CurrentUser(
        val id: String,
        val username: String,
        val displayName: String,
        val accessLevel: Int
    )
    
    val currentUser: Flow<CurrentUser?> = dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID]
        val username = prefs[KEY_USERNAME]
        val displayName = prefs[KEY_DISPLAY_NAME]
        val accessLevel = prefs[KEY_ACCESS_LEVEL]?.toIntOrNull()
        
        if (id != null && username != null && displayName != null && accessLevel != null) {
            CurrentUser(id, username, displayName, accessLevel)
        } else {
            null
        }
    }
    
    suspend fun registerWithInviteKey(
        inviteKey: String,
        username: String,
        displayName: String,
        pin: String
    ): Result<Unit> {
        return try {
            // Валидация PIN
            if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
                return Result.failure(Exception("PIN должен быть 4-6 цифр"))
            }
            
            // Генерируем ключевую пару для шифрования
            val publicKey = CryptoManager.generateKeyPair()
            
            // Регистрируемся на сервере
            val result = ApiClient.register(
                inviteKey = inviteKey,
                username = username,
                displayName = displayName,
                publicKey = publicKey,
                pin = pin
            )
            
            result.fold(
                onSuccess = { response ->
                    // Сохраняем данные авторизации
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = username,
                        displayName = displayName,
                        accessLevel = response.accessLevel
                    )
                    
                    // Устанавливаем токен в API клиент
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    // Отправляем FCM токен на сервер
                    android.util.Log.d("AuthManager", "Sending FCM token after registration...")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Регистрация по email - шаг 1: отправка кода
    suspend fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        displayName: String
    ): Result<String> {
        return try {
            // Валидация email
            if (!email.contains("@") || email.length < 5) {
                return Result.failure(Exception("Неверный формат email"))
            }
            
            // Валидация пароля
            if (password.length < 6) {
                return Result.failure(Exception("Пароль должен быть минимум 6 символов"))
            }
            
            // Валидация username
            if (username.length < 3) {
                return Result.failure(Exception("Имя пользователя должно быть минимум 3 символа"))
            }
            
            val result = ApiClient.registerWithEmail(
                email = email,
                password = password,
                username = username,
                displayName = displayName
            )
            
            result.fold(
                onSuccess = { response ->
                    // Возвращаем сообщение о необходимости подтверждения
                    Result.success(response.message)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Регистрация по email - шаг 2: подтверждение кода
    suspend fun verifyEmail(email: String, code: String): Result<Unit> {
        return try {
            val result = ApiClient.verifyEmail(email, code)
            
            result.fold(
                onSuccess = { response ->
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = email.substringBefore("@"),
                        displayName = email.substringBefore("@"),
                        accessLevel = response.accessLevel
                    )
                    
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    android.util.Log.d("AuthManager", "Email verified, sending FCM token...")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Повторная отправка кода
    suspend fun resendVerificationCode(email: String): Result<Unit> {
        return try {
            val result = ApiClient.resendVerificationCode(email)
            result.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Вход по email
    suspend fun loginWithEmail(email: String, password: String): Result<Unit> {
        return try {
            val result = ApiClient.loginWithEmail(email, password)
            
            result.fold(
                onSuccess = { response ->
                    android.util.Log.d("AuthManager", "Email login successful, userId: ${response.userId}")
                    
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = email.substringBefore("@"),
                        displayName = email.substringBefore("@"),
                        accessLevel = response.accessLevel
                    )
                    
                    android.util.Log.d("AuthManager", "Sending FCM token after email login...")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("AuthManager", "Email login failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Email login exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    // === Регистрация по телефону ===
    
    // Шаг 1: Инициировать верификацию
    suspend fun registerWithPhone(
        phone: String,
        username: String,
        displayName: String,
        password: String
    ): Result<ApiClient.PhoneVerificationResponse> {
        return try {
            // Валидация
            val normalizedPhone = phone.replace(Regex("[^0-9]"), "")
            if (normalizedPhone.length < 10) {
                return Result.failure(Exception("Неверный формат номера телефона"))
            }
            
            if (password.length < 6) {
                return Result.failure(Exception("Пароль должен быть минимум 6 символов"))
            }
            
            if (username.length < 3) {
                return Result.failure(Exception("Имя пользователя должно быть минимум 3 символа"))
            }
            
            val result = ApiClient.registerWithPhone(
                phone = normalizedPhone,
                username = username,
                displayName = displayName,
                password = password
            )
            
            result.fold(
                onSuccess = { response ->
                    Result.success(response)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Шаг 2: Проверить статус звонка
    suspend fun verifyPhone(checkId: String, phone: String): Result<Unit> {
        return try {
            val normalizedPhone = phone.replace(Regex("[^0-9]"), "")
            val result = ApiClient.verifyPhone(checkId, normalizedPhone)
            
            result.fold(
                onSuccess = { response ->
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = normalizedPhone,
                        displayName = normalizedPhone,
                        accessLevel = response.accessLevel
                    )
                    
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    android.util.Log.d("AuthManager", "Phone verified, sending FCM token...")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Вход по телефону
    suspend fun loginWithPhone(phone: String, password: String): Result<Unit> {
        return try {
            val normalizedPhone = phone.replace(Regex("[^0-9]"), "")
            val result = ApiClient.loginWithPhone(normalizedPhone, password)
            
            result.fold(
                onSuccess = { response ->
                    android.util.Log.d("AuthManager", "Phone login successful, userId: ${response.userId}")
                    
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = normalizedPhone,
                        displayName = normalizedPhone,
                        accessLevel = response.accessLevel
                    )
                    
                    android.util.Log.d("AuthManager", "Sending FCM token after phone login...")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("AuthManager", "Phone login failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Phone login exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun loginWithQR(qrData: String): Result<Unit> {
        // QR содержит ключ приглашения
        // Для простоты используем тот же метод регистрации
        return try {
            // Парсим QR данные (формат: pioneer://invite/KEY)
            val inviteKey = qrData.substringAfterLast("/")
            
            // Генерируем временные данные
            val username = "user_${System.currentTimeMillis()}"
            val displayName = "Новый пользователь"
            val pin = "0000" // Временный PIN, пользователь должен сменить
            
            registerWithInviteKey(inviteKey, username, displayName, pin)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(username: String, pin: String): Result<Unit> {
        return try {
            // Валидация PIN
            if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
                return Result.failure(Exception("PIN должен быть 4-6 цифр"))
            }
            
            val result = ApiClient.login(username, pin)
            
            result.fold(
                onSuccess = { response ->
                    android.util.Log.d("AuthManager", "Login successful for $username, userId: ${response.userId}")
                    
                    // Устанавливаем токен в API клиент СРАЗУ
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    // Сохраняем данные авторизации
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = username,
                        displayName = username,
                        accessLevel = response.accessLevel
                    )
                    
                    // Отправляем FCM токен на сервер ПОСЛЕ установки auth токена
                    android.util.Log.d("AuthManager", "Sending FCM token after login...")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("AuthManager", "Login failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Login exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        ApiClient.clearAuthToken()
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun restoreSession(): Boolean {
        val token = dataStore.data.first()[KEY_TOKEN]
        val userId = dataStore.data.first()[KEY_USER_ID]
        return if (token != null) {
            ApiClient.setAuthToken(token)
            userId?.let { ApiClient.setCurrentUserId(it) }
            android.util.Log.d("AuthManager", "Session restored, sending FCM token...")
            // Отправляем FCM токен при восстановлении сессии
            com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
            true
        } else {
            false
        }
    }
    
    suspend fun getAccessLevel(): Int {
        return dataStore.data.first()[KEY_ACCESS_LEVEL]?.toIntOrNull() ?: 0
    }
    
    // === Простая регистрация по позывному + пароль ===
    
    suspend fun registerSimple(callsign: String, displayName: String, password: String): Result<Unit> {
        return try {
            if (callsign.length < 3) {
                return Result.failure(Exception("Позывной должен быть минимум 3 символа"))
            }
            if (password.length < 6) {
                return Result.failure(Exception("Пароль должен быть минимум 6 символов"))
            }
            
            val result = ApiClient.registerSimple(callsign, displayName, password)
            
            result.fold(
                onSuccess = { response ->
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = callsign,
                        displayName = displayName,
                        accessLevel = response.accessLevel
                    )
                    
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    android.util.Log.d("AuthManager", "Simple registration successful")
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun loginSimple(callsign: String, password: String): Result<Unit> {
        return try {
            val result = ApiClient.loginSimple(callsign, password)
            
            result.fold(
                onSuccess = { response ->
                    android.util.Log.d("AuthManager", "Simple login successful, userId: ${response.userId}")
                    
                    ApiClient.setAuthToken(response.token)
                    ApiClient.setCurrentUserId(response.userId)
                    
                    saveAuthData(
                        userId = response.userId,
                        token = response.token,
                        username = callsign,
                        displayName = callsign,
                        accessLevel = response.accessLevel
                    )
                    
                    com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(context)
                    
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("AuthManager", "Simple login failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Simple login exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun saveAuthData(
        userId: String,
        token: String,
        username: String,
        displayName: String,
        accessLevel: Int
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_ACCESS_LEVEL] = accessLevel.toString()
        }
    }
    
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown-${System.currentTimeMillis()}"
        }
    }
}
