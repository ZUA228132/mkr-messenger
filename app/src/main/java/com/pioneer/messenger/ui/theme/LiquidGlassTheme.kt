package com.pioneer.messenger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Liquid Glass цвета - iOS 26 стиль
object LiquidGlassColors {
    // Основные цвета
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF30D158)
    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9500)
    val OrangeDark = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFCC00)
    val YellowDark = Color(0xFFFFD60A)
    val Purple = Color(0xFFAF52DE)
    val PurpleDark = Color(0xFFBF5AF2)
    val Pink = Color(0xFFFF2D55)
    val PinkDark = Color(0xFFFF375F)
    val Teal = Color(0xFF5AC8FA)
    val TealDark = Color(0xFF64D2FF)
    
    // Фоновые цвета
    val BackgroundLight = Color(0xFFF2F2F7)
    val BackgroundDark = Color(0xFF000000)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF1C1C1E)
    val SecondaryBackgroundLight = Color(0xFFFFFFFF)
    val SecondaryBackgroundDark = Color(0xFF2C2C2E)
    val TertiaryBackgroundLight = Color(0xFFFFFFFF)
    val TertiaryBackgroundDark = Color(0xFF3A3A3C)
    
    // Текстовые цвета
    val LabelLight = Color(0xFF000000)
    val LabelDark = Color(0xFFFFFFFF)
    val SecondaryLabelLight = Color(0x993C3C43)
    val SecondaryLabelDark = Color(0x99EBEBF5)
    val TertiaryLabelLight = Color(0x4D3C3C43)
    val TertiaryLabelDark = Color(0x4DEBEBF5)
    
    // Разделители
    val SeparatorLight = Color(0x4D3C3C43)
    val SeparatorDark = Color(0x99545458)
    
    // Glass эффекты
    val GlassLight = Color(0xCCFFFFFF)
    val GlassDark = Color(0x331C1C1E)
    val GlassBorderLight = Color(0x33FFFFFF)
    val GlassBorderDark = Color(0x33FFFFFF)
}

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = LiquidGlassColors.Blue,
    onPrimary = Color.White,
    primaryContainer = LiquidGlassColors.Blue.copy(alpha = 0.1f),
    onPrimaryContainer = LiquidGlassColors.Blue,
    secondary = LiquidGlassColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = LiquidGlassColors.Purple.copy(alpha = 0.1f),
    onSecondaryContainer = LiquidGlassColors.Purple,
    tertiary = LiquidGlassColors.Teal,
    onTertiary = Color.White,
    tertiaryContainer = LiquidGlassColors.Teal.copy(alpha = 0.1f),
    onTertiaryContainer = LiquidGlassColors.Teal,
    error = LiquidGlassColors.Red,
    onError = Color.White,
    errorContainer = LiquidGlassColors.Red.copy(alpha = 0.1f),
    onErrorContainer = LiquidGlassColors.Red,
    background = LiquidGlassColors.BackgroundLight,
    onBackground = LiquidGlassColors.LabelLight,
    surface = LiquidGlassColors.SurfaceLight,
    onSurface = LiquidGlassColors.LabelLight,
    surfaceVariant = LiquidGlassColors.SecondaryBackgroundLight,
    onSurfaceVariant = LiquidGlassColors.SecondaryLabelLight,
    outline = LiquidGlassColors.SeparatorLight,
    outlineVariant = LiquidGlassColors.SeparatorLight.copy(alpha = 0.5f),
    inverseSurface = LiquidGlassColors.SurfaceDark,
    inverseOnSurface = LiquidGlassColors.LabelDark,
    inversePrimary = LiquidGlassColors.BlueDark
)

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = LiquidGlassColors.BlueDark,
    onPrimary = Color.White,
    primaryContainer = LiquidGlassColors.BlueDark.copy(alpha = 0.2f),
    onPrimaryContainer = LiquidGlassColors.BlueDark,
    secondary = LiquidGlassColors.PurpleDark,
    onSecondary = Color.White,
    secondaryContainer = LiquidGlassColors.PurpleDark.copy(alpha = 0.2f),
    onSecondaryContainer = LiquidGlassColors.PurpleDark,
    tertiary = LiquidGlassColors.TealDark,
    onTertiary = Color.White,
    tertiaryContainer = LiquidGlassColors.TealDark.copy(alpha = 0.2f),
    onTertiaryContainer = LiquidGlassColors.TealDark,
    error = LiquidGlassColors.RedDark,
    onError = Color.White,
    errorContainer = LiquidGlassColors.RedDark.copy(alpha = 0.2f),
    onErrorContainer = LiquidGlassColors.RedDark,
    background = LiquidGlassColors.BackgroundDark,
    onBackground = LiquidGlassColors.LabelDark,
    surface = LiquidGlassColors.SurfaceDark,
    onSurface = LiquidGlassColors.LabelDark,
    surfaceVariant = LiquidGlassColors.SecondaryBackgroundDark,
    onSurfaceVariant = LiquidGlassColors.SecondaryLabelDark,
    outline = LiquidGlassColors.SeparatorDark,
    outlineVariant = LiquidGlassColors.SeparatorDark.copy(alpha = 0.5f),
    inverseSurface = LiquidGlassColors.SurfaceLight,
    inverseOnSurface = LiquidGlassColors.LabelLight,
    inversePrimary = LiquidGlassColors.Blue
)

@Composable
fun LiquidGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Используем динамические цвета на Android 12+
            if (darkTheme) LiquidGlassDarkColorScheme else LiquidGlassLightColorScheme
        }
        darkTheme -> LiquidGlassDarkColorScheme
        else -> LiquidGlassLightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
