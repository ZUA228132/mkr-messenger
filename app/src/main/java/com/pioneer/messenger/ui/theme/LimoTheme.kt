package com.pioneer.messenger.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Secure Messenger Colors
 * Строгий военный/спецслужбовский стиль
 */
object MKRColors {
    // Primary - тёмно-зелёный (военный/безопасность)
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
    
    // Gradient colors - тёмные тона
    val GradientStart = Color(0xFF1A237E)
    val GradientMiddle = Color(0xFF0D47A1)
    val GradientEnd = Color(0xFF006064)
    
    // Background - почти чёрный
    val BackgroundDark = Color(0xFF0D1117)
    val BackgroundLight = Color(0xFFF5F5F5)
    val SurfaceDark = Color(0xFF161B22)
    val SurfaceLight = Color(0xFFFFFFFF)
    
    // Glass effect colors
    val GlassDark = Color(0x30FFFFFF)
    val GlassLight = Color(0x40000000)
    val GlassBorder = Color(0xFF30363D)
    
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
    val Encrypted = Color(0xFF3FB950)
    val Secure = Color(0xFF58A6FF)
    val Danger = Color(0xFFF85149)
    val Classified = Color(0xFFD29922)
    
    // Social colors (legacy compatibility)
    val Like = Color(0xFF3FB950)
    val Comment = Color(0xFF58A6FF)
    val Share = Color(0xFF8B949E)
    val Save = Color(0xFFD29922)
}

private val SecureDarkColorScheme = darkColorScheme(
    primary = MKRColors.Primary,
    onPrimary = Color.Black,
    primaryContainer = MKRColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = MKRColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = MKRColors.SecondaryDark,
    onSecondaryContainer = Color.White,
    tertiary = MKRColors.Accent,
    onTertiary = Color.Black,
    background = MKRColors.BackgroundDark,
    onBackground = MKRColors.TextPrimary,
    surface = MKRColors.SurfaceDark,
    onSurface = MKRColors.TextPrimary,
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = MKRColors.TextSecondary,
    outline = Color(0xFF30363D),
    error = MKRColors.Error,
    onError = Color.White
)

private val SecureLightColorScheme = lightColorScheme(
    primary = MKRColors.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = MKRColors.PrimaryLight,
    onPrimaryContainer = Color.Black,
    secondary = MKRColors.SecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = MKRColors.SecondaryLight,
    onSecondaryContainer = Color.Black,
    tertiary = MKRColors.AccentDark,
    onTertiary = Color.Black,
    background = MKRColors.BackgroundLight,
    onBackground = Color.Black,
    surface = MKRColors.SurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    error = MKRColors.Error,
    onError = Color.White
)

@Composable
fun MKRTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = SecureDarkColorScheme // Всегда тёмная для безопасности
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MKRTypography,
        content = content
    )
}
