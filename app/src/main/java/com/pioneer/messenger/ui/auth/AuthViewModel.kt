package com.pioneer.messenger.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    
    val isAuthenticated = authManager.isAuthenticated.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState
    
    // Email для верификации (сохраняем между шагами)
    private var pendingEmail: String = ""
    
    // Phone verification data
    private var pendingPhone: String = ""
    private var pendingCheckId: String = ""
    
    fun registerWithKey(inviteKey: String, username: String, displayName: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.registerWithInviteKey(inviteKey, username, displayName, pin)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("PIN", ignoreCase = true) == true ->
                            error.message ?: "Неверный PIN"
                        error.message?.contains("Invalid", ignoreCase = true) == true ||
                        error.message?.contains("invalid", ignoreCase = true) == true -> 
                            "Неверный или использованный ключ приглашения"
                        error.message?.contains("expired", ignoreCase = true) == true -> 
                            "Срок действия ключа истёк"
                        error.message?.contains("username", ignoreCase = true) == true ||
                        error.message?.contains("taken", ignoreCase = true) == true -> 
                            "Имя пользователя уже занято"
                        error.message?.contains("Connection", ignoreCase = true) == true ||
                        error.message?.contains("timeout", ignoreCase = true) == true ||
                        error.message?.contains("Unable to resolve", ignoreCase = true) == true -> 
                            "Нет подключения к серверу"
                        error.message?.contains("HTTP 4", ignoreCase = true) == true -> 
                            "Ошибка авторизации: ${error.message}"
                        error.message?.contains("HTTP 5", ignoreCase = true) == true -> 
                            "Ошибка сервера. Попробуйте позже"
                        else -> error.message ?: "Ошибка регистрации"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    // Регистрация по email - шаг 1
    fun registerWithEmail(email: String, password: String, username: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.registerWithEmail(email, password, username, displayName)
            
            _uiState.value = result.fold(
                onSuccess = { message ->
                    pendingEmail = email
                    AuthUiState.VerificationRequired(email, message)
                },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("email", ignoreCase = true) == true &&
                        error.message?.contains("зарегистрирован", ignoreCase = true) == true ->
                            "Email уже зарегистрирован"
                        error.message?.contains("email", ignoreCase = true) == true ->
                            error.message ?: "Неверный формат email"
                        error.message?.contains("пароль", ignoreCase = true) == true ||
                        error.message?.contains("password", ignoreCase = true) == true ->
                            error.message ?: "Пароль должен быть минимум 6 символов"
                        error.message?.contains("username", ignoreCase = true) == true ||
                        error.message?.contains("занято", ignoreCase = true) == true ->
                            "Имя пользователя уже занято"
                        error.message?.contains("Connection", ignoreCase = true) == true ->
                            "Нет подключения к серверу"
                        else -> error.message ?: "Ошибка регистрации"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    // Подтверждение email кодом - шаг 2
    fun verifyEmail(code: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.verifyEmail(pendingEmail, code)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("истёк", ignoreCase = true) == true ||
                        error.message?.contains("expired", ignoreCase = true) == true ->
                            "Код истёк. Запросите новый"
                        error.message?.contains("неверный", ignoreCase = true) == true ||
                        error.message?.contains("invalid", ignoreCase = true) == true ->
                            "Неверный код"
                        else -> error.message ?: "Ошибка подтверждения"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    // Повторная отправка кода
    fun resendCode() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.resendVerificationCode(pendingEmail)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.VerificationRequired(pendingEmail, "Код отправлен повторно") },
                onFailure = { AuthUiState.Error(it.message ?: "Ошибка отправки кода") }
            )
        }
    }
    
    // Вход по email
    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.loginWithEmail(email, password)
            
            _uiState.value = result.fold(
                onSuccess = { 
                    // Проверяем бан после успешного входа
                    checkBanStatus()
                },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("не найден", ignoreCase = true) == true ||
                        error.message?.contains("not found", ignoreCase = true) == true ->
                            "Пользователь не найден"
                        error.message?.contains("пароль", ignoreCase = true) == true ||
                        error.message?.contains("password", ignoreCase = true) == true ->
                            error.message ?: "Неверный пароль"
                        error.message?.contains("заблокирован", ignoreCase = true) == true ->
                            error.message ?: "Аккаунт заблокирован"
                        error.message?.contains("Connection", ignoreCase = true) == true ->
                            "Нет подключения к серверу"
                        else -> error.message ?: "Ошибка входа"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    // === Регистрация по телефону ===
    
    // Шаг 1: Инициировать верификацию
    fun registerWithPhone(phone: String, username: String, displayName: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.registerWithPhone(phone, username, displayName, password)
            
            _uiState.value = result.fold(
                onSuccess = { response ->
                    pendingPhone = phone
                    pendingCheckId = response.checkId ?: ""
                    AuthUiState.PhoneCallRequired(
                        phone = phone,
                        checkId = response.checkId ?: "",
                        callPhone = response.callPhone ?: "",
                        callPhonePretty = response.callPhonePretty ?: ""
                    )
                },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("зарегистрирован", ignoreCase = true) == true ->
                            "Этот номер уже зарегистрирован"
                        error.message?.contains("username", ignoreCase = true) == true ||
                        error.message?.contains("занято", ignoreCase = true) == true ->
                            "Имя пользователя уже занято"
                        error.message?.contains("формат", ignoreCase = true) == true ->
                            "Неверный формат номера телефона"
                        error.message?.contains("Connection", ignoreCase = true) == true ->
                            "Нет подключения к серверу"
                        else -> error.message ?: "Ошибка регистрации"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    // Шаг 2: Проверить статус звонка
    fun checkPhoneVerification() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.verifyPhone(pendingCheckId, pendingPhone)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { error ->
                    if (error.message == "pending") {
                        // Ещё ждём звонок
                        AuthUiState.PhoneCallRequired(
                            phone = pendingPhone,
                            checkId = pendingCheckId,
                            callPhone = "",
                            callPhonePretty = "",
                            message = "Ожидаем звонок..."
                        )
                    } else {
                        val message = when {
                            error.message?.contains("истекло", ignoreCase = true) == true ||
                            error.message?.contains("expired", ignoreCase = true) == true ->
                                "Время на звонок истекло. Попробуйте снова"
                            else -> error.message ?: "Ошибка верификации"
                        }
                        AuthUiState.Error(message)
                    }
                }
            )
        }
    }
    
    // Автоматическая проверка статуса (polling)
    fun startPhoneVerificationPolling() {
        viewModelScope.launch {
            repeat(30) { // 30 попыток по 5 секунд = 2.5 минуты
                delay(5000)
                val result = authManager.verifyPhone(pendingCheckId, pendingPhone)
                result.fold(
                    onSuccess = {
                        _uiState.value = AuthUiState.Success
                        return@launch
                    },
                    onFailure = { error ->
                        if (error.message != "pending") {
                            _uiState.value = AuthUiState.Error(error.message ?: "Ошибка")
                            return@launch
                        }
                    }
                )
            }
            _uiState.value = AuthUiState.Error("Время на звонок истекло")
        }
    }
    
    // Вход по телефону
    fun loginWithPhone(phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.loginWithPhone(phone, password)
            
            _uiState.value = result.fold(
                onSuccess = { checkBanStatus() },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("не найден", ignoreCase = true) == true ->
                            "Пользователь не найден"
                        error.message?.contains("не подтверждён", ignoreCase = true) == true ->
                            "Телефон не подтверждён"
                        error.message?.contains("пароль", ignoreCase = true) == true ->
                            error.message ?: "Неверный пароль"
                        error.message?.contains("заблокирован", ignoreCase = true) == true ->
                            error.message ?: "Аккаунт заблокирован"
                        else -> error.message ?: "Ошибка входа"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    private suspend fun checkBanStatus(): AuthUiState {
        // Получаем данные текущего пользователя
        val user = authManager.currentUser.first()
        if (user != null) {
            try {
                val result = com.pioneer.messenger.data.network.ApiClient.getUser(user.id)
                result.onSuccess { userData ->
                    if (userData.isBanned) {
                        _uiState.value = AuthUiState.Banned(userData.banReason)
                        return AuthUiState.Banned(userData.banReason)
                    }
                }
            } catch (e: Exception) { }
        }
        _uiState.value = AuthUiState.Success
        return AuthUiState.Success
    }
    
    fun loginWithQr(qrData: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.loginWithQR(qrData)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { AuthUiState.Error(it.message ?: "Ошибка входа") }
            )
        }
    }
    
    fun login(username: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.login(username, pin)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("PIN", ignoreCase = true) == true ->
                            error.message ?: "Неверный PIN"
                        error.message?.contains("заблокирован", ignoreCase = true) == true ->
                            error.message ?: "Аккаунт заблокирован"
                        error.message?.contains("не найден", ignoreCase = true) == true ||
                        error.message?.contains("not found", ignoreCase = true) == true -> 
                            "Пользователь не найден"
                        error.message?.contains("Connection", ignoreCase = true) == true ||
                        error.message?.contains("timeout", ignoreCase = true) == true -> 
                            "Нет подключения к серверу"
                        else -> error.message ?: "Ошибка входа"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    // === Простая регистрация по позывному + пароль ===
    
    fun registerSimple(callsign: String, displayName: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.registerSimple(callsign, displayName, password)
            
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.PinSetupRequired },  // Navigate to PIN setup after registration
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("занят", ignoreCase = true) == true ||
                        error.message?.contains("taken", ignoreCase = true) == true ->
                            "Этот позывной уже занят"
                        error.message?.contains("пароль", ignoreCase = true) == true ||
                        error.message?.contains("password", ignoreCase = true) == true ->
                            error.message ?: "Пароль должен быть минимум 6 символов"
                        error.message?.contains("Connection", ignoreCase = true) == true ->
                            "Нет подключения к серверу"
                        else -> error.message ?: "Ошибка регистрации"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
    
    fun loginSimple(callsign: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authManager.loginSimple(callsign, password)
            
            _uiState.value = result.fold(
                onSuccess = { checkBanStatus() },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("не найден", ignoreCase = true) == true ||
                        error.message?.contains("not found", ignoreCase = true) == true ->
                            "Позывной не найден"
                        error.message?.contains("пароль", ignoreCase = true) == true ||
                        error.message?.contains("password", ignoreCase = true) == true ||
                        error.message?.contains("Неверный", ignoreCase = true) == true ->
                            "Неверный пароль"
                        error.message?.contains("заблокирован", ignoreCase = true) == true ->
                            error.message ?: "Аккаунт заблокирован"
                        error.message?.contains("Connection", ignoreCase = true) == true ->
                            "Нет подключения к серверу"
                        else -> error.message ?: "Ошибка входа"
                    }
                    AuthUiState.Error(message)
                }
            )
        }
    }
}

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data object PinSetupRequired : AuthUiState()  // New state for PIN setup after registration
    data class VerificationRequired(val email: String, val message: String) : AuthUiState()
    data class PhoneCallRequired(
        val phone: String,
        val checkId: String,
        val callPhone: String,
        val callPhonePretty: String,
        val message: String? = null
    ) : AuthUiState()
    data class Banned(val reason: String?) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
