package com.pioneer.messenger.data.security

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер блокировки приложения
 * Собственная реализация без внешних библиотек
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "pioneer_security"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEYSTORE_ALIAS = "pioneer_biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()
    
    private val _isPinSet = MutableStateFlow(false)
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()
    
    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()
    
    init {
        loadState()
    }
    
    private fun loadState() {
        _isPinSet.value = prefs.contains(KEY_PIN_HASH)
        _isBiometricEnabled.value = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        _isLocked.value = prefs.getBoolean(KEY_LOCK_ENABLED, false) && _isPinSet.value
    }
    
    /**
     * Установить PIN-код
     */
    fun setPin(pin: String): Boolean {
        if (!PinValidator.isValidPin(pin)) {
            return false
        }
        
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .apply()
        
        _isPinSet.value = true
        _isLocked.value = false
        
        return true
    }
    
    /**
     * Проверить PIN-код
     */
    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        
        val inputHash = hashPin(pin, salt)
        val isValid = constantTimeEquals(savedHash, inputHash)
        
        if (isValid) {
            _isLocked.value = false
        }
        
        return isValid
    }
    
    /**
     * Удалить PIN-код
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
        
        _isPinSet.value = false
        _isBiometricEnabled.value = false
        _isLocked.value = false
    }
    
    /**
     * Включить/выключить биометрию
     */
    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !_isPinSet.value) {
            return // Нельзя включить биометрию без PIN
        }
        
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
        
        _isBiometricEnabled.value = enabled
        
        if (enabled) {
            generateBiometricKey()
        }
    }
    
    /**
     * Заблокировать приложение
     */
    fun lock() {
        if (_isPinSet.value) {
            _isLocked.value = true
        }
    }
    
    /**
     * Разблокировать приложение
     */
    fun unlock() {
        _isLocked.value = false
    }
    
    /**
     * Проверить доступность биометрии на устройстве
     */
    fun isBiometricAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        
        return try {
            val biometricManager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                biometricManager?.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG) == 
                    android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                biometricManager?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Показать биометрический промпт
     */
    fun showBiometricPrompt(
        activity: android.app.Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Биометрия не поддерживается")
            return
        }
        
        val cancellationSignal = CancellationSignal()
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlock()
                onSuccess()
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
                    10 -> onCancel() // BIOMETRIC_ERROR_NEGATIVE_BUTTON = 10
                    else -> onError(errString.toString())
                }
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Не вызываем onError, пользователь может попробовать снова
            }
        }
        
        val promptInfo = BiometricPrompt.Builder(activity)
            .setTitle("Pioneer Messenger")
            .setSubtitle("Используйте отпечаток для разблокировки")
            .setNegativeButton("Использовать PIN", ContextCompat.getMainExecutor(activity)) { _, _ ->
                onCancel()
            }
            .build()
        
        promptInfo.authenticate(cancellationSignal, ContextCompat.getMainExecutor(activity), callback)
    }
    
    // === Криптографические функции ===
    
    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    private fun hashPin(pin: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        
        // PBKDF2-подобное хеширование
        var hash = saltBytes + pinBytes
        repeat(10000) {
            val digest = MessageDigest.getInstance("SHA-256")
            hash = digest.digest(hash)
        }
        
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
    
    private fun generateBiometricKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
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
                    .setUserAuthenticationRequired(true)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        }
                    }
                    .build()
                
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
