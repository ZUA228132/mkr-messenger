package com.pioneer.messenger.ui.settings

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.crypto.CryptoManager
import com.pioneer.messenger.data.local.PreferencesManager
import com.pioneer.messenger.data.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val cryptoManager: CryptoManager,
    private val appLockManager: AppLockManager
) : ViewModel() {
    
    val pinEnabled = appLockManager.isPinSet
    val biometricEnabled = appLockManager.isBiometricEnabled
    
    private val _publicKey = MutableStateFlow("")
    val publicKey = _publicKey.asStateFlow()
    
    private val _userId = MutableStateFlow("")
    val userId = _userId.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            // Генерируем публичный ключ для отображения
            _publicKey.value = CryptoManager.generateKeyPair()
            
            // Получаем ID пользователя
            authManager.currentUser.collect { user ->
                _userId.value = user?.id ?: ""
            }
        }
    }
    
    fun clearPin() {
        appLockManager.clearPin()
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        appLockManager.setBiometricEnabled(enabled)
    }
    
    fun isBiometricAvailable(): Boolean {
        return appLockManager.isBiometricAvailable()
    }
    
    fun generateNewKeyPair(): String {
        val newKey = CryptoManager.generateKeyPair()
        _publicKey.value = newKey
        return newKey
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    onSetupPin: () -> Unit,
    onNavigateToPanicButton: () -> Unit = {},
    onNavigateToSecurityCheck: () -> Unit = {},
    onNavigateToStealthMode: () -> Unit = {},
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val pinEnabled by viewModel.pinEnabled.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val publicKey by viewModel.publicKey.collectAsState()
    val userId by viewModel.userId.collectAsState()
    
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearPinDialog by remember { mutableStateOf(false) }
    var showDeviceIdDialog by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Безопасность", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // PANIC BUTTON - Экстренное удаление
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onNavigateToPanicButton() },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF5252).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "PANIC BUTTON",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                        Text(
                            "Экстренное удаление всех данных",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = Color(0xFFFF5252)
                    )
                }
            }
            
            // Проверка безопасности устройства
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onNavigateToSecurityCheck() },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2196F3).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Проверка безопасности",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            "Root, отладчик, эмулятор",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = Color(0xFF2196F3)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Скрытый режим
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onNavigateToStealthMode() },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF9C27B0).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        null,
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Скрытый режим",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9C27B0)
                        )
                        Text(
                            "Маскировка под калькулятор",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = Color(0xFF9C27B0)
                    )
                }
            }
            
            // Блокировка приложения
            SettingsSection(title = "Блокировка приложения") {
                if (pinEnabled) {
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "PIN-код установлен",
                        subtitle = "Нажмите чтобы изменить",
                        onClick = onSetupPin
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.LockOpen,
                        title = "Удалить PIN-код",
                        subtitle = "Отключить блокировку",
                        onClick = { showClearPinDialog = true },
                        iconTint = Color(0xFFE53935)
                    )
                    
                    SettingsSwitch(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Биометрия",
                        subtitle = "Разблокировка отпечатком",
                        checked = biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "Установить PIN-код",
                        subtitle = "Защитите приложение",
                        onClick = onSetupPin
                    )
                }
            }
            
            // Защита данных
            SettingsSection(title = "Защита данных") {
                // Информация о защите скриншотов (без переключателя - всегда включено)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Screenshot,
                        null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Блокировка скриншотов", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Всегда включена для защиты",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Информация об автоудалении
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автоудаление сообщений", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Настраивается при создании чата",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Шифрование
            SettingsSection(title = "Шифрование") {
                SettingsItem(
                    icon = Icons.Outlined.Key,
                    title = "Мой ключ шифрования",
                    subtitle = "Просмотреть публичный ключ",
                    onClick = { showKeyDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.VpnKey,
                    title = "ID устройства",
                    subtitle = if (userId.length > 16) userId.take(16) + "..." else userId,
                    onClick = { showDeviceIdDialog = true }
                )
            }
            
            // Информация о безопасности
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Signal Protocol + E2E",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Все сообщения защищены протоколом Signal с Perfect Forward Secrecy. " +
                            "Каждое сообщение шифруется уникальным ключом. " +
                            "Компрометация одного ключа не раскрывает другие сообщения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Диалог просмотра ключа
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { 
                Text("Публичный ключ", fontWeight = FontWeight.SemiBold) 
            },
            text = {
                Column {
                    Text(
                        "Этот ключ используется для шифрования сообщений, отправляемых вам:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = publicKey,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(publicKey))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Копировать")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
    
    // Диалог удаления PIN
    if (showClearPinDialog) {
        AlertDialog(
            onDismissRequest = { showClearPinDialog = false },
            title = { Text("Удалить PIN-код?") },
            text = { Text("Приложение больше не будет защищено блокировкой.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPin()
                        showClearPinDialog = false
                    }
                ) {
                    Text("Удалить", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPinDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог ID устройства
    if (showDeviceIdDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceIdDialog = false },
            title = { 
                Text("ID устройства", fontWeight = FontWeight.SemiBold) 
            },
            text = {
                Column {
                    Text(
                        "Уникальный идентификатор вашего аккаунта:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = userId.ifEmpty { "Не авторизован" },
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = {
                            if (userId.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(userId))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Копировать")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceIdDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
