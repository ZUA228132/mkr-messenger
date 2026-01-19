package com.pioneer.messenger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pioneer.messenger.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramGreen = Color(0xFF4CAF50)
private val TelegramRed = Color(0xFFE53935)

enum class AppTheme {
    SYSTEM, LIGHT, DARK, AMOLED
}

class ThemeState(
    private val preferencesManager: PreferencesManager? = null
) {
    var currentTheme by mutableStateOf(AppTheme.SYSTEM)
    var accentColor by mutableStateOf(TelegramBlue)
    var isLoaded by mutableStateOf(false)
    
    init {
        // Загружаем синхронно при создании
        preferencesManager?.let { prefs ->
            runBlocking {
                try {
                    val themeName = prefs.theme.first()
                    currentTheme = try {
                        AppTheme.valueOf(themeName)
                    } catch (e: Exception) {
                        AppTheme.SYSTEM
                    }
                    
                    val colorLong = prefs.accentColor.first()
                    accentColor = Color(colorLong.toInt())
                } catch (e: Exception) {
                    currentTheme = AppTheme.SYSTEM
                    accentColor = TelegramBlue
                }
                isLoaded = true
            }
        }
    }
    
    fun setThemeAndSave(theme: AppTheme) {
        currentTheme = theme
        preferencesManager?.let {
            CoroutineScope(Dispatchers.IO).launch {
                it.setTheme(theme.name)
            }
        }
    }
    
    fun setAccentAndSave(color: Color) {
        accentColor = color
        preferencesManager?.let {
            CoroutineScope(Dispatchers.IO).launch {
                it.setAccentColor(color.toArgb().toLong())
            }
        }
    }
    
    suspend fun loadFromPreferences() {
        if (isLoaded) return
        preferencesManager?.let { prefs ->
            try {
                val themeName = prefs.theme.first()
                currentTheme = try {
                    AppTheme.valueOf(themeName)
                } catch (e: Exception) {
                    AppTheme.SYSTEM
                }
                
                val colorLong = prefs.accentColor.first()
                accentColor = Color(colorLong.toInt())
                isLoaded = true
            } catch (e: Exception) {
                currentTheme = AppTheme.SYSTEM
                accentColor = TelegramBlue
            }
        }
    }
}

val LocalThemeState = staticCompositionLocalOf { ThemeState() }

private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C3A5F),
    secondary = TelegramGreen,
    background = Color(0xFF17212B),
    surface = Color(0xFF1E2C3A),
    surfaceVariant = Color(0xFF242F3D),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAAB8C2),
    error = TelegramRed,
    outline = Color(0xFF30363D)
)

private val LightColorScheme = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    secondary = TelegramGreen,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F5),
    onBackground = Color(0xFF1F2328),
    onSurface = Color(0xFF1F2328),
    onSurfaceVariant = Color(0xFF707579),
    error = TelegramRed,
    outline = Color(0xFFD0D7DE)
)

private val AmoledColorScheme = darkColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A1929),
    secondary = TelegramGreen,
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF121212),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF8B949E),
    error = TelegramRed,
    outline = Color(0xFF1F1F1F)
)

@Composable
fun PioneerTheme(
    themeState: ThemeState = LocalThemeState.current,
    darkTheme: Boolean = true, // Принудительно тёмная тема по умолчанию
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    
    // Загружаем настройки при первом запуске
    LaunchedEffect(Unit) {
        themeState.loadFromPreferences()
    }
    
    // Если darkTheme = true, всегда используем тёмную тему
    val colorScheme = if (darkTheme) {
        DarkColorScheme.copy(primary = themeState.accentColor)
    } else {
        when (themeState.currentTheme) {
            AppTheme.SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
            AppTheme.LIGHT -> LightColorScheme
            AppTheme.DARK -> DarkColorScheme
            AppTheme.AMOLED -> AmoledColorScheme
        }.copy(primary = themeState.accentColor)
    }
    
    val isDark = darkTheme || when (themeState.currentTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
    }
    
    // Цвет для статус бара и навигации
    val systemBarsColor = when (themeState.currentTheme) {
        AppTheme.AMOLED -> Color.Black
        AppTheme.DARK -> Color(0xFF17212B)
        AppTheme.LIGHT -> Color.White
        AppTheme.SYSTEM -> if (systemDark) Color(0xFF17212B) else Color.White
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Устанавливаем цвет статус бара и навигации
            window.statusBarColor = systemBarsColor.toArgb()
            window.navigationBarColor = systemBarsColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }
    
    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

// Расширенная палитра акцентных цветов для персонализации
object AccentColors {
    // Основные
    val Blue = Color(0xFF2AABEE)
    val Purple = Color(0xFF5856D6)
    val Pink = Color(0xFFFF2D55)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Teal = Color(0xFF5AC8FA)
    
    // MKR цвета
    val MKRPrimary = Color(0xFFE040FB)
    val MKRSecondary = Color(0xFF7C4DFF)
    val MKRAccent = Color(0xFF00E5FF)
    
    // Дополнительные
    val Red = Color(0xFFFF3B30)
    val Yellow = Color(0xFFFFCC00)
    val Mint = Color(0xFF00C7BE)
    val Indigo = Color(0xFF5856D6)
    val Coral = Color(0xFFFF6B6B)
    val Lavender = Color(0xFFB388FF)
    val Gold = Color(0xFFFFD700)
    val Emerald = Color(0xFF2ECC71)
    
    // Список всех цветов для UI
    val allColors = listOf(
        Blue to "Синий",
        MKRPrimary to "MKR Розовый",
        MKRSecondary to "MKR Фиолетовый",
        Purple to "Фиолетовый",
        Pink to "Розовый",
        Red to "Красный",
        Coral to "Коралловый",
        Orange to "Оранжевый",
        Yellow to "Жёлтый",
        Gold to "Золотой",
        Green to "Зелёный",
        Emerald to "Изумрудный",
        Mint to "Мятный",
        Teal to "Бирюзовый",
        MKRAccent to "Голубой",
        Lavender to "Лавандовый",
        Indigo to "Индиго"
    )
}
