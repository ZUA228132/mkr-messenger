package com.pioneer.messenger.data.security

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Блокировка скриншотов и записи экрана
 * 
 * Защищает конфиденциальные данные от:
 * - Скриншотов
 * - Записи экрана
 * - Просмотра в Recent Apps
 */
@Singleton
class ScreenshotBlocker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private var isEnabled = true
    
    /**
     * Включить защиту от скриншотов для Activity
     */
    fun enableProtection(activity: Activity) {
        if (isEnabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }
    
    /**
     * Отключить защиту от скриншотов для Activity
     */
    fun disableProtection(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    
    /**
     * Временно отключить защиту (например, для экспорта)
     */
    fun temporarilyDisable() {
        isEnabled = false
    }
    
    /**
     * Восстановить защиту
     */
    fun restore() {
        isEnabled = true
    }
    
    /**
     * Проверить, включена ли защита
     */
    fun isProtectionEnabled(): Boolean = isEnabled
    
    /**
     * Установить состояние защиты
     */
    fun setProtectionEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}
