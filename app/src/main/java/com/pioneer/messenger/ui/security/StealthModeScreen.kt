package com.pioneer.messenger.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.pioneer.messenger.data.security.StealthMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class StealthModeViewModel @Inject constructor(
    private val stealthMode: StealthMode
) : ViewModel() {
    
    private val _isEnabled = MutableStateFlow(stealthMode.isStealthModeEnabled())
    val isEnabled = _isEnabled.asStateFlow()
    
    private val _currentDisguise = MutableStateFlow(stealthMode.getCurrentDisguise())
    val currentDisguise = _currentDisguise.asStateFlow()
    
    private val _secretCode = MutableStateFlow(stealthMode.getSecretCode() ?: "")
    val secretCode = _secretCode.asStateFlow()
    
    fun enableStealth(disguise: StealthMode.DisguiseType, code: String) {
        stealthMode.enableStealthMode(disguise, code)
        _isEnabled.value = true
        _currentDisguise.value = disguise
        _secretCode.value = code
    }
    
    fun disableStealth() {
        stealthMode.disableStealthMode()
        _isEnabled.value = false
        _currentDisguise.value = StealthMode.DisguiseType.NONE
    }
    
    fun generateCode(): String {
        val code = stealthMode.generateSecretCode()
        _secretCode.value = code
        return code
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StealthModeScreen(
    onBack: () -> Unit,
    viewModel: StealthModeViewModel = hiltViewModel()
) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val currentDisguise by viewModel.currentDisguise.collectAsState()
    val secretCode by viewModel.secretCode.collectAsState()
    
    var selectedDisguise by remember { mutableStateOf(currentDisguise) }
    var codeInput by remember { mutableStateOf(secretCode) }
    var showEnableDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Скрытый режим") },
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
                .padding(16.dp)
        ) {
            // Статус
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) 
                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        tint = if (isEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isEnabled) "Скрытый режим активен" else "Скрытый режим отключён",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isEnabled) {
                            Text(
                                "Маскировка: ${currentDisguise.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { 
                            if (it) showEnableDialog = true 
                            else showDisableDialog = true 
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Описание
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Скрытый режим маскирует приложение под обычное приложение " +
                        "(калькулятор, заметки и т.д.). Для входа в мессенджер нужно " +
                        "ввести секретный код в фейковом приложении.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Выбор маскировки
            Text(
                "Выберите маскировку",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            StealthMode.DisguiseType.entries.filter { it != StealthMode.DisguiseType.NONE }.forEach { disguise ->
                DisguiseOption(
                    disguise = disguise,
                    isSelected = selectedDisguise == disguise,
                    onClick = { selectedDisguise = disguise }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Секретный код
            if (isEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFC107).copy(alpha = 0.15f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                null,
                                tint = Color(0xFFFFC107)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Секретный код", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            secretCode,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Введите этот код в калькуляторе для входа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    
    // Диалог включения
    if (showEnableDialog) {
        AlertDialog(
            onDismissRequest = { showEnableDialog = false },
            title = { Text("Включить скрытый режим?") },
            text = {
                Column {
                    Text("Приложение будет замаскировано под ${selectedDisguise.displayName}.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { if (it.length <= 6) codeInput = it },
                        label = { Text("Секретный код (6 цифр)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { codeInput = viewModel.generateCode() }) {
                        Text("Сгенерировать код")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (codeInput.length == 6) {
                            viewModel.enableStealth(selectedDisguise, codeInput)
                            showEnableDialog = false
                        }
                    },
                    enabled = codeInput.length == 6
                ) {
                    Text("Включить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог отключения
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("Отключить скрытый режим?") },
            text = { Text("Приложение вернётся к обычному виду.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.disableStealth()
                    showDisableDialog = false
                }) {
                    Text("Отключить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DisguiseOption(
    disguise: StealthMode.DisguiseType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (disguise) {
        StealthMode.DisguiseType.CALCULATOR -> Icons.Outlined.Calculate
        StealthMode.DisguiseType.NOTES -> Icons.Outlined.Note
        StealthMode.DisguiseType.WEATHER -> Icons.Outlined.Cloud
        StealthMode.DisguiseType.NONE -> Icons.Outlined.Apps
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(disguise.displayName, fontWeight = FontWeight.Medium)
                Text(
                    "Иконка: ${disguise.iconName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
