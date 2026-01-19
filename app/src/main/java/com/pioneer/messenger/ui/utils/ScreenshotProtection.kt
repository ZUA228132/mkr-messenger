package com.pioneer.messenger.ui.utils

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Защита от скриншотов для секретных чатов
 * Устанавливает FLAG_SECURE на окно, что делает скриншоты белыми/чёрными
 */
@Composable
fun SecureScreen(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    DisposableEffect(enabled) {
        val activity = context as? Activity
        val window = activity?.window
        
        if (enabled && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        
        onDispose {
            if (enabled && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
    
    content()
}

/**
 * Комбинированная защита:
 * - Для секретных чатов: FLAG_SECURE (белый экран на скриншоте)
 * - Для обычных чатов: без защиты (убрали водяной знак из-за крашей)
 */
@Composable
fun ChatScreenProtection(
    isSecretChat: Boolean,
    userId: String,
    content: @Composable () -> Unit
) {
    SecureScreen(enabled = isSecretChat) {
        content()
    }
}
