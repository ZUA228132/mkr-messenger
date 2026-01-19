package com.pioneer.messenger.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Утилита для тактильной обратной связи
 */
object HapticFeedback {
    
    /**
     * Лёгкий клик (для кнопок, переключателей)
     */
    fun lightClick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    
    /**
     * Средний клик (для важных действий)
     */
    fun mediumClick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    /**
     * Тяжёлый клик (для подтверждений, удалений)
     */
    fun heavyClick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    
    /**
     * Успешное действие
     */
    fun success(context: Context) {
        vibrate(context, 50)
    }
    
    /**
     * Ошибка
     */
    fun error(context: Context) {
        vibrate(context, longArrayOf(0, 50, 50, 50))
    }
    
    /**
     * Выбор элемента
     */
    fun selection(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    
    /**
     * Ввод PIN-кода
     */
    fun pinInput(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    
    /**
     * Неправильный PIN
     */
    fun pinError(context: Context) {
        vibrate(context, longArrayOf(0, 100, 50, 100))
    }
    
    private fun vibrate(context: Context, duration: Long) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
    }
    
    private fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }
    
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}

/**
 * Composable хелпер для виброотклика
 */
@Composable
fun rememberHapticFeedback(): HapticFeedbackHelper {
    val view = LocalView.current
    val context = LocalContext.current
    return remember { HapticFeedbackHelper(view, context) }
}

class HapticFeedbackHelper(
    private val view: View,
    private val context: Context
) {
    fun lightClick() = HapticFeedback.lightClick(view)
    fun mediumClick() = HapticFeedback.mediumClick(view)
    fun heavyClick() = HapticFeedback.heavyClick(view)
    fun success() = HapticFeedback.success(context)
    fun error() = HapticFeedback.error(context)
    fun selection() = HapticFeedback.selection(view)
    fun pinInput() = HapticFeedback.pinInput(view)
    fun pinError() = HapticFeedback.pinError(context)
}
