package com.pioneer.messenger.data.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stealth Mode - Скрытый режим
 * 
 * Маскирует приложение под калькулятор или другое безобидное приложение
 */
@Singleton
class StealthMode @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Activity aliases для разных иконок
        private const val MAIN_ACTIVITY = "com.pioneer.messenger.ui.MainActivity"
        private const val CALCULATOR_ALIAS = "com.pioneer.messenger.CalculatorAlias"
        private const val NOTES_ALIAS = "com.pioneer.messenger.NotesAlias"
        private const val WEATHER_ALIAS = "com.pioneer.messenger.WeatherAlias"
        
        private const val PREF_NAME = "stealth_prefs"
        private const val KEY_STEALTH_MODE = "stealth_mode"
        private const val KEY_DISGUISE_TYPE = "disguise_type"
        private const val KEY_SECRET_CODE = "secret_code"
    }
    
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Типы маскировки
     */
    enum class DisguiseType(val displayName: String, val iconName: String) {
        NONE("Без маскировки", "MKR"),
        CALCULATOR("Калькулятор", "Calculator"),
        NOTES("Заметки", "Notes"),
        WEATHER("Погода", "Weather")
    }
    
    /**
     * Включить скрытый режим
     */
    fun enableStealthMode(disguise: DisguiseType, secretCode: String) {
        prefs.edit()
            .putBoolean(KEY_STEALTH_MODE, true)
            .putString(KEY_DISGUISE_TYPE, disguise.name)
            .putString(KEY_SECRET_CODE, secretCode)
            .apply()
        
        // Меняем иконку приложения
        changeAppIcon(disguise)
    }
    
    /**
     * Отключить скрытый режим
     */
    fun disableStealthMode() {
        prefs.edit()
            .putBoolean(KEY_STEALTH_MODE, false)
            .putString(KEY_DISGUISE_TYPE, DisguiseType.NONE.name)
            .apply()
        
        changeAppIcon(DisguiseType.NONE)
    }
    
    /**
     * Проверить, включён ли скрытый режим
     */
    fun isStealthModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_STEALTH_MODE, false)
    }
    
    /**
     * Получить текущий тип маскировки
     */
    fun getCurrentDisguise(): DisguiseType {
        val name = prefs.getString(KEY_DISGUISE_TYPE, DisguiseType.NONE.name)
        return DisguiseType.valueOf(name ?: DisguiseType.NONE.name)
    }
    
    /**
     * Проверить секретный код
     */
    fun verifySecretCode(code: String): Boolean {
        val savedCode = prefs.getString(KEY_SECRET_CODE, null)
        return savedCode == code
    }
    
    /**
     * Получить секретный код (для отображения)
     */
    fun getSecretCode(): String? {
        return prefs.getString(KEY_SECRET_CODE, null)
    }

    /**
     * Изменить иконку приложения
     */
    private fun changeAppIcon(disguise: DisguiseType) {
        val pm = context.packageManager
        
        // Список всех alias
        val aliases = listOf(
            MAIN_ACTIVITY to DisguiseType.NONE,
            CALCULATOR_ALIAS to DisguiseType.CALCULATOR,
            NOTES_ALIAS to DisguiseType.NOTES,
            WEATHER_ALIAS to DisguiseType.WEATHER
        )
        
        aliases.forEach { (alias, type) ->
            val state = if (type == disguise) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(context, alias),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                // Alias может не существовать
            }
        }
    }
    
    /**
     * Генерация случайного секретного кода
     */
    fun generateSecretCode(): String {
        val chars = "0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
    
    /**
     * Проверка, нужно ли показывать фейковый экран
     */
    fun shouldShowFakeScreen(): Boolean {
        return isStealthModeEnabled()
    }
    
    /**
     * Получить название фейкового приложения
     */
    fun getFakeAppName(): String {
        return when (getCurrentDisguise()) {
            DisguiseType.CALCULATOR -> "Калькулятор"
            DisguiseType.NOTES -> "Заметки"
            DisguiseType.WEATHER -> "Погода"
            DisguiseType.NONE -> "MKR"
        }
    }
}
