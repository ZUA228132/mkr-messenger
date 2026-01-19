package com.pranavajay.ghostmessenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

@Singleton
class ThemeManager @Inject constructor(private val context: Context) {
    
    companion object {
        private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }
    
    val isDarkMode: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[IS_DARK_MODE] ?: true // Default to dark mode
    }
    
    suspend fun toggleTheme() {
        context.themeDataStore.edit { prefs ->
            val current = prefs[IS_DARK_MODE] ?: true
            prefs[IS_DARK_MODE] = !current
        }
    }
    
    suspend fun setDarkMode(isDark: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[IS_DARK_MODE] = isDark
        }
    }
}
