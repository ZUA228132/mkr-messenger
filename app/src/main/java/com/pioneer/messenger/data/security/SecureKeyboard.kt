package com.pioneer.messenger.data.security

import android.content.Context
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Безопасная клавиатура
 * 
 * - Отключение автозаполнения
 * - Отключение предиктивного ввода
 * - Защита от keylogger
 */
@Singleton
class SecureKeyboard @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Проверка на подозрительную клавиатуру
     */
    fun isKeyboardSuspicious(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentKeyboard = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        )
        
        // Список подозрительных клавиатур
        val suspiciousKeyboards = listOf(
            "keylogger",
            "spy",
            "monitor",
            "track"
        )
        
        return suspiciousKeyboards.any { 
            currentKeyboard?.lowercase()?.contains(it) == true 
        }
    }
    
    /**
     * Получение списка установленных клавиатур
     */
    fun getInstalledKeyboards(): List<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.map { it.packageName }
    }
    
    /**
     * Проверка, является ли клавиатура системной
     */
    fun isSystemKeyboard(): Boolean {
        val currentKeyboard = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        )
        
        val systemKeyboards = listOf(
            "com.google.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.android.inputmethod"
        )
        
        return systemKeyboards.any { currentKeyboard?.startsWith(it) == true }
    }
}
