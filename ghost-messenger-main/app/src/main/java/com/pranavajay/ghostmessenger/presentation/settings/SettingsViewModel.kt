package com.pranavajay.ghostmessenger.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavajay.ghostmessenger.data.local.SecurityManager
import com.pranavajay.ghostmessenger.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val hideOnlineStatus: Boolean = false,
    val hideReadReceipts: Boolean = false,
    val defaultDisappearTime: String = "24 hours"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val sessionManager: SessionManager
) : ViewModel() {
    
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            val appLockEnabled = securityManager.isAppLockEnabled.first()
            val biometricEnabled = securityManager.isBiometricEnabled.first()
            val biometricAvailable = securityManager.isBiometricAvailable()
            
            _settingsState.value = _settingsState.value.copy(
                appLockEnabled = appLockEnabled,
                biometricEnabled = biometricEnabled,
                biometricAvailable = biometricAvailable
            )
        }
    }
    
    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityManager.setAppLockEnabled(enabled)
            _settingsState.value = _settingsState.value.copy(appLockEnabled = enabled)
            if (!enabled) {
                securityManager.setBiometricEnabled(false)
                _settingsState.value = _settingsState.value.copy(biometricEnabled = false)
            }
        }
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityManager.setBiometricEnabled(enabled)
            _settingsState.value = _settingsState.value.copy(biometricEnabled = enabled)
        }
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        _settingsState.value = _settingsState.value.copy(notificationsEnabled = enabled)
    }
    
    fun setSoundEnabled(enabled: Boolean) {
        _settingsState.value = _settingsState.value.copy(soundEnabled = enabled)
    }
    
    fun setVibrationEnabled(enabled: Boolean) {
        _settingsState.value = _settingsState.value.copy(vibrationEnabled = enabled)
    }
    
    fun setHideOnlineStatus(enabled: Boolean) {
        _settingsState.value = _settingsState.value.copy(hideOnlineStatus = enabled)
    }
    
    fun setHideReadReceipts(enabled: Boolean) {
        _settingsState.value = _settingsState.value.copy(hideReadReceipts = enabled)
    }
    
    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
        }
    }
}
