package com.pranavajay.ghostmessenger.data.local

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_prefs")

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val biometricManager = BiometricManager.from(context)
    
    companion object {
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        private val LOCK_TIMEOUT = stringPreferencesKey("lock_timeout") // immediate, 1min, 5min, 30min
    }
    
    // Check if biometric is available
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
    
    // Check if any biometric is enrolled
    fun hasBiometricEnrolled(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }
    
    // Biometric enabled preference
    val isBiometricEnabled: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        prefs[BIOMETRIC_ENABLED] ?: false
    }
    
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED] = enabled
        }
    }
    
    // App lock enabled preference
    val isAppLockEnabled: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        prefs[APP_LOCK_ENABLED] ?: false
    }
    
    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[APP_LOCK_ENABLED] = enabled
        }
    }
    
    // PIN management
    suspend fun setPin(pin: String) {
        context.securityDataStore.edit { prefs ->
            prefs[APP_LOCK_PIN] = pin // In production, hash this!
        }
    }
    
    val hasPin: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        prefs[APP_LOCK_PIN]?.isNotEmpty() ?: false
    }
    
    suspend fun verifyPin(pin: String): Boolean {
        var storedPin = ""
        context.securityDataStore.data.collect { prefs ->
            storedPin = prefs[APP_LOCK_PIN] ?: ""
        }
        return pin == storedPin
    }
    
    // Lock timeout
    val lockTimeout: Flow<String> = context.securityDataStore.data.map { prefs ->
        prefs[LOCK_TIMEOUT] ?: "immediate"
    }
    
    suspend fun setLockTimeout(timeout: String) {
        context.securityDataStore.edit { prefs ->
            prefs[LOCK_TIMEOUT] = timeout
        }
    }
    
    // Show biometric prompt
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Unlock Ghost Messenger",
        subtitle: String = "Use your fingerprint to unlock",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }
        
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
