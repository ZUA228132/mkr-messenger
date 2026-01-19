package com.pranavajay.ghostmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Custom colors for app-specific theming
data class GhostColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val sentMessageBg: Color,
    val receivedMessageBg: Color,
    val divider: Color,
    val isDark: Boolean
)

val LocalGhostColors = compositionLocalOf {
    GhostColors(
        background = DarkBlack,
        surface = DarkGrey,
        surfaceVariant = MediumGrey,
        textPrimary = White,
        textSecondary = LightGrey,
        sentMessageBg = SentMessageBg,
        receivedMessageBg = ReceivedMessageBg,
        divider = DarkGrey,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = CyanAccent,
    tertiary = DeepBlue,
    background = DarkBlack,
    surface = DarkGrey,
    surfaceVariant = MediumGrey,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = White,
    onSurface = White,
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    secondary = CyanAccent,
    tertiary = DeepBlue,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = DarkText,
    onSurface = DarkText,
)

private val DarkGhostColors = GhostColors(
    background = DarkBlack,
    surface = DarkGrey,
    surfaceVariant = MediumGrey,
    textPrimary = White,
    textSecondary = LightGrey,
    sentMessageBg = SentMessageBg,
    receivedMessageBg = ReceivedMessageBg,
    divider = DarkGrey,
    isDark = true
)

private val LightGhostColors = GhostColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    textPrimary = DarkText,
    textSecondary = MediumText,
    sentMessageBg = LightSentMessageBg,
    receivedMessageBg = LightReceivedMessageBg,
    divider = LightSurfaceVariant,
    isDark = false
)

@Composable
fun GhostMessengerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val ghostColors = if (darkTheme) DarkGhostColors else LightGhostColors
    
    CompositionLocalProvider(LocalGhostColors provides ghostColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object GhostTheme {
    val colors: GhostColors
        @Composable
        get() = LocalGhostColors.current
}
