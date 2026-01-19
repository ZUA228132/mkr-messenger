package com.pioneer.messenger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Secure Messenger Theme
 * Строгий военный/спецслужбовский стиль
 * Тёмные тона, минимализм, акцент на безопасность
 */
object SecureColors {
    // Primary - тёмно-зелёный (военный)
    val Primary = Color(0xFF00C853)
    val PrimaryDark = Color(0xFF009624)
    val PrimaryLight = Color(0xFF5EFC82)
    
    // Secondary - стальной синий
    val Secondary = Color(0xFF546E7A)
    val SecondaryDark = Color(0xFF29434E)
    val SecondaryLight = Color(0xFF819CA9)
    
    // Accent - янтарный (предупреждения)
    val Accent = Color(0xFFFFAB00)
    val AccentDark = Color(0xFFC67C00)
    
    // Background - почти чёрный
    val BackgroundDark = Color(0xFF0D1117)
    val BackgroundLight = Color(0xFFF5F5F5)
    val SurfaceDark = Color(0xFF161B22)
    val SurfaceLight = Color(0xFFFFFFFF)
    
    // Card/Container
    val CardDark = Color(0xFF21262D)
    val CardBorder = Color(0xFF30363D)
    
    // Text
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextTertiary = Color(0xFF6E7681)
    
    // Status colors
    val Success = Color(0xFF3FB950)
    val Error = Color(0xFFF85149)
    val Warning = Color(0xFFD29922)
    val Info = Color(0xFF58A6FF)
    
    // Security indicators
    val Encrypted = Color(0xFF3FB950)  // Зелёный - зашифровано
    val Secure = Color(0xFF58A6FF)     // Синий - безопасно
    val Danger = Color(0xFFF85149)     // Красный - опасность
    val Classified = Color(0xFFD29922) // Жёлтый - секретно
    
    // Online status
    val Online = Color(0xFF3FB950)
    val Away = Color(0xFFD29922)
    val Offline = Color(0xFF6E7681)
}

private val SecureDarkColorScheme = darkColorScheme(
    primary = SecureColors.Primary,
    onPrimary = Color.Black,
    primaryContainer = SecureColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = SecureColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecureColors.SecondaryDark,
    onSecondaryContainer = Color.White,
    tertiary = SecureColors.Accent,
    onTertiary = Color.Black,
    background = SecureColors.BackgroundDark,
    onBackground = SecureColors.TextPrimary,
    surface = SecureColors.SurfaceDark,
    onSurface = SecureColors.TextPrimary,
    surfaceVariant = SecureColors.CardDark,
    onSurfaceVariant = SecureColors.TextSecondary,
    outline = SecureColors.CardBorder,
    error = SecureColors.Error,
    onError = Color.White
)

private val SecureLightColorScheme = lightColorScheme(
    primary = SecureColors.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = SecureColors.PrimaryLight,
    onPrimaryContainer = Color.Black,
    secondary = SecureColors.SecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = SecureColors.SecondaryLight,
    onSecondaryContainer = Color.Black,
    tertiary = SecureColors.AccentDark,
    onTertiary = Color.Black,
    background = SecureColors.BackgroundLight,
    onBackground = Color.Black,
    surface = SecureColors.SurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    error = SecureColors.Error,
    onError = Color.White
)

@Composable
fun SecureTheme(
    darkTheme: Boolean = true, // По умолчанию тёмная тема
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SecureDarkColorScheme else SecureLightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecureTypography,
        content = content
    )
}
