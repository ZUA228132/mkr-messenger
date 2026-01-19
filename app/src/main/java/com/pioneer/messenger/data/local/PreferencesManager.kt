package com.pioneer.messenger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pioneer_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        // Тема
        val THEME_KEY = stringPreferencesKey("theme")
        val ACCENT_COLOR_KEY = longPreferencesKey("accent_color")
        
        // PIN
        val PIN_CODE_KEY = stringPreferencesKey("pin_code")
        val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        val APP_LOCKED_KEY = booleanPreferencesKey("app_locked")
        
        // Профиль
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_PHONE_KEY = stringPreferencesKey("user_phone")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val USER_BIO_KEY = stringPreferencesKey("user_bio")
        val USER_USERNAME_KEY = stringPreferencesKey("user_username")
        val USER_AVATAR_URL_KEY = stringPreferencesKey("user_avatar_url")
        
        // Уведомления
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
    }

    // Тема
    val theme: Flow<String> = dataStore.data.map { it[THEME_KEY] ?: "SYSTEM" }
    val accentColor: Flow<Long> = dataStore.data.map { it[ACCENT_COLOR_KEY] ?: 0xFF2AABEE.toLong() }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun setAccentColor(color: Long) {
        dataStore.edit { it[ACCENT_COLOR_KEY] = color }
    }

    // PIN
    val pinCode: Flow<String?> = dataStore.data.map { it[PIN_CODE_KEY] }
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[BIOMETRIC_ENABLED_KEY] ?: false }
    val appLocked: Flow<Boolean> = dataStore.data.map { it[APP_LOCKED_KEY] ?: false }

    suspend fun setPinCode(pin: String?) {
        dataStore.edit { 
            if (pin != null) it[PIN_CODE_KEY] = pin
            else it.remove(PIN_CODE_KEY)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[BIOMETRIC_ENABLED_KEY] = enabled }
    }

    suspend fun setAppLocked(locked: Boolean) {
        dataStore.edit { it[APP_LOCKED_KEY] = locked }
    }

    // Профиль
    val userName: Flow<String> = dataStore.data.map { it[USER_NAME_KEY] ?: "" }
    val userPhone: Flow<String> = dataStore.data.map { it[USER_PHONE_KEY] ?: "" }
    val userEmail: Flow<String> = dataStore.data.map { it[USER_EMAIL_KEY] ?: "" }
    val userBio: Flow<String> = dataStore.data.map { it[USER_BIO_KEY] ?: "" }
    val userUsername: Flow<String> = dataStore.data.map { it[USER_USERNAME_KEY] ?: "" }
    val userAvatarUrl: Flow<String> = dataStore.data.map { it[USER_AVATAR_URL_KEY] ?: "" }

    suspend fun saveProfile(name: String, phone: String, email: String, bio: String, username: String) {
        dataStore.edit {
            it[USER_NAME_KEY] = name
            it[USER_PHONE_KEY] = phone
            it[USER_EMAIL_KEY] = email
            it[USER_BIO_KEY] = bio
            it[USER_USERNAME_KEY] = username
        }
    }
    
    suspend fun saveUserAvatarUrl(url: String) {
        dataStore.edit { it[USER_AVATAR_URL_KEY] = url }
    }

    // Уведомления
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED_KEY] ?: true }
    val soundEnabled: Flow<Boolean> = dataStore.data.map { it[SOUND_ENABLED_KEY] ?: true }
    val vibrationEnabled: Flow<Boolean> = dataStore.data.map { it[VIBRATION_ENABLED_KEY] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATION_ENABLED_KEY] = enabled }
    }
}
