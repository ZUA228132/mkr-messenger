package com.pioneer.messenger.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.PreferencesManager
import com.pioneer.messenger.ui.theme.*
import com.pioneer.messenger.util.HapticFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _userName = MutableStateFlow("Пользователь")
    val userName = _userName.asStateFlow()
    
    private val _userUsername = MutableStateFlow("")
    val userUsername = _userUsername.asStateFlow()
    
    private val _accessLevel = MutableStateFlow(0)
    val accessLevel = _accessLevel.asStateFlow()
    
    init {
        loadUserData()
    }
    
    private fun loadUserData() {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _userName.value = user?.displayName ?: preferencesManager.userName.first().ifEmpty { "Пользователь" }
                _userUsername.value = user?.username ?: ""
                _accessLevel.value = user?.accessLevel ?: 0
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToSetupPin: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToLinkedDevices: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    themeState: ThemeState,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val view = LocalView.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }
    
    val userName by viewModel.userName.collectAsState()
    val userUsername by viewModel.userUsername.collectAsState()
    val accessLevel by viewModel.accessLevel.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        HapticFeedback.lightClick(view)
                        onBack() 
                    }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Профиль с Liquid Glass
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.15f),
                                    com.pioneer.messenger.ui.theme.MKRColors.Secondary.copy(alpha = 0.15f)
                                )
                            )
                        )
                        .clickable {
                            HapticFeedback.mediumClick(view)
                            onNavigateToProfile()
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Аватар с градиентом
                        Box(modifier = Modifier.size(60.dp)) {
                            // Светящийся фон
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(
                                        com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                                    .graphicsLayer { alpha = 0.6f }
                            )
                            
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = CircleShape,
                                color = com.pioneer.messenger.ui.theme.MKRColors.Primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        userName.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                userName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (userUsername.isNotEmpty()) {
                                Text(
                                    "@$userUsername",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = com.pioneer.messenger.ui.theme.MKRColors.Primary
                        )
                    }
                }
            }
            
            // Внешний вид
            SettingsSection(title = "Внешний вид") {
                SettingsItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Тема",
                    subtitle = when (themeState.currentTheme) {
                        AppTheme.SYSTEM -> "Системная"
                        AppTheme.LIGHT -> "Светлая"
                        AppTheme.DARK -> "Тёмная"
                        AppTheme.AMOLED -> "AMOLED"
                    },
                    onClick = { 
                        HapticFeedback.lightClick(view)
                        showThemeDialog = true 
                    }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = "Акцентный цвет",
                    subtitle = "Основной цвет интерфейса",
                    onClick = { 
                        HapticFeedback.lightClick(view)
                        showAccentDialog = true 
                    },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(themeState.accentColor)
                        )
                    }
                )
            }
            
            // Уведомления
            SettingsSection(title = "Уведомления") {
                var notificationsEnabled by remember { mutableStateOf(true) }
                var soundEnabled by remember { mutableStateOf(true) }
                var vibrationEnabled by remember { mutableStateOf(true) }
                
                SettingsSwitch(
                    icon = Icons.Outlined.Notifications,
                    title = "Уведомления",
                    checked = notificationsEnabled,
                    onCheckedChange = { 
                        HapticFeedback.lightClick(view)
                        notificationsEnabled = it 
                    }
                )
                
                SettingsSwitch(
                    icon = Icons.Outlined.VolumeUp,
                    title = "Звук",
                    checked = soundEnabled,
                    onCheckedChange = { 
                        HapticFeedback.lightClick(view)
                        soundEnabled = it 
                    }
                )
                
                SettingsSwitch(
                    icon = Icons.Outlined.Vibration,
                    title = "Вибрация",
                    checked = vibrationEnabled,
                    onCheckedChange = { 
                        HapticFeedback.lightClick(view)
                        vibrationEnabled = it 
                    }
                )
            }
            
            // Безопасность
            SettingsSection(title = "Безопасность") {
                SettingsItem(
                    icon = Icons.Outlined.Security,
                    title = "Безопасность и конфиденциальность",
                    subtitle = "PIN, биометрия, ключи шифрования",
                    onClick = { 
                        HapticFeedback.mediumClick(view)
                        onNavigateToSecurity() 
                    }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Приватность",
                    subtitle = "Кто может звонить, писать, видеть на карте",
                    onClick = { 
                        HapticFeedback.mediumClick(view)
                        onNavigateToPrivacy() 
                    }
                )
            }
            
            // Связанные устройства
            SettingsSection(title = "Устройства") {
                SettingsItem(
                    icon = Icons.Outlined.Devices,
                    title = "Связанные устройства",
                    subtitle = "Pioneer Web и другие устройства",
                    onClick = { 
                        HapticFeedback.mediumClick(view)
                        onNavigateToLinkedDevices() 
                    }
                )
            }
            
            // Администрирование (только для админов)
            if (accessLevel >= 10) {
                SettingsSection(title = "Администрирование") {
                    SettingsItem(
                        icon = Icons.Outlined.AdminPanelSettings,
                        title = "Панель администратора",
                        subtitle = "Управление пользователями",
                        onClick = { 
                            HapticFeedback.mediumClick(view)
                            onNavigateToAdmin() 
                        }
                    )
                }
            }
            
            // О приложении
            SettingsSection(title = "О приложении") {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Версия",
                    subtitle = "1.0.0 (build 1)",
                    onClick = { 
                        HapticFeedback.lightClick(view)
                        versionClickCount++
                        if (versionClickCount >= 10) {
                            HapticFeedback.heavyClick(view)
                            showEasterEgg = true
                            versionClickCount = 0
                        }
                    }
                )
            }
            
            // Отладка FCM (временно)
            SettingsSection(title = "Отладка") {
                var fcmStatus by remember { mutableStateOf("Нажмите для отправки") }
                val context = view.context
                
                SettingsItem(
                    icon = Icons.Outlined.Send,
                    title = "Отправить FCM токен",
                    subtitle = fcmStatus,
                    onClick = { 
                        HapticFeedback.mediumClick(view)
                        fcmStatus = "Отправка..."
                        android.widget.Toast.makeText(context, "Отправка FCM токена...", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // Получаем токен и отправляем
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token ->
                                android.widget.Toast.makeText(context, "Токен: ${token.take(15)}...", android.widget.Toast.LENGTH_LONG).show()
                                fcmStatus = "Токен получен, отправка..."
                                
                                // Отправляем напрямую
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        val deviceId = android.provider.Settings.Secure.getString(
                                            context.contentResolver,
                                            android.provider.Settings.Secure.ANDROID_ID
                                        ) ?: "unknown"
                                        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                        
                                        val result = com.pioneer.messenger.data.network.ApiClient.updateFcmToken(token, deviceId, deviceName)
                                        
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (result.isSuccess) {
                                                fcmStatus = "✓ Успешно отправлено!"
                                                android.widget.Toast.makeText(context, "✓ FCM токен отправлен!", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                fcmStatus = "✗ Ошибка: ${result.exceptionOrNull()?.message}"
                                                android.widget.Toast.makeText(context, "✗ Ошибка: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            fcmStatus = "✗ Exception: ${e.message}"
                                            android.widget.Toast.makeText(context, "✗ Exception: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                fcmStatus = "✗ Не удалось получить токен: ${e.message}"
                                android.widget.Toast.makeText(context, "✗ FCM ошибка: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Пасхалка ZOV
    if (showEasterEgg) {
        ZovEasterEggDialog(onDismiss = { showEasterEgg = false })
    }
    
    // Диалог выбора темы
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Выберите тему", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    HapticFeedback.lightClick(view)
                                    themeState.setThemeAndSave(theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeState.currentTheme == theme,
                                onClick = {
                                    HapticFeedback.lightClick(view)
                                    themeState.setThemeAndSave(theme)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                when (theme) {
                                    AppTheme.SYSTEM -> "Системная"
                                    AppTheme.LIGHT -> "Светлая"
                                    AppTheme.DARK -> "Тёмная"
                                    AppTheme.AMOLED -> "AMOLED (чёрная)"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    HapticFeedback.lightClick(view)
                    showThemeDialog = false 
                }) {
                    Text("Закрыть")
                }
            }
        )
    }
    
    // Диалог выбора акцентного цвета
    if (showAccentDialog) {
        // Используем расширенную палитру цветов
        val colors = AccentColors.allColors
        
        AlertDialog(
            onDismissRequest = { showAccentDialog = false },
            title = { Text("Акцентный цвет", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    colors.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (color, name) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            HapticFeedback.mediumClick(view)
                                            themeState.setAccentAndSave(color)
                                            showAccentDialog = false
                                        }
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (themeState.accentColor == color) {
                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        name, 
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    HapticFeedback.lightClick(view)
                    showAccentDialog = false 
                }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
fun ZovEasterEggDialog(onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "zov")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ZOV",
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(scale),
                style = LocalTextStyle.current.copy(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha),
                            Color(0xFF0039A6).copy(alpha = alpha), // Синий
                            Color(0xFFD52B1E).copy(alpha = alpha)  // Красный
                        )
                    )
                )
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    // Liquid Glass карточка настройки
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка с градиентным фоном
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.2f),
                                    com.pioneer.messenger.ui.theme.MKRColors.Secondary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = com.pioneer.messenger.ui.theme.MKRColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trailing?.invoke() ?: Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // Liquid Glass карточка переключателя
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка с градиентным фоном
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.2f),
                                    com.pioneer.messenger.ui.theme.MKRColors.Secondary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = com.pioneer.messenger.ui.theme.MKRColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.pioneer.messenger.ui.theme.MKRColors.Primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}
