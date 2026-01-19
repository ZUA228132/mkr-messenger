package com.pioneer.messenger.ui.settings

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.ui.theme.MKRColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacySettingsUiState(
    val isLoading: Boolean = false,
    val whoCanCall: String = "everyone",
    val whoCanSeeAvatar: String = "everyone",
    val whoCanMessage: String = "everyone",
    val whoCanFindMe: String = "everyone",
    val ghostMode: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(PrivacySettingsUiState())
    val uiState: StateFlow<PrivacySettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val result = ApiClient.getPrivacySettings()
                result.fold(
                    onSuccess = { settings ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            whoCanCall = settings.whoCanCall,
                            whoCanSeeAvatar = settings.whoCanSeeAvatar,
                            whoCanMessage = settings.whoCanMessage,
                            whoCanFindMe = settings.whoCanFindMe,
                            ghostMode = settings.ghostMode
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Ошибка загрузки: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.message}"
                )
            }
        }
    }
    
    fun updateWhoCanCall(value: String) {
        _uiState.value = _uiState.value.copy(whoCanCall = value)
        saveSettings()
    }
    
    fun updateWhoCanSeeAvatar(value: String) {
        _uiState.value = _uiState.value.copy(whoCanSeeAvatar = value)
        saveSettings()
    }
    
    fun updateWhoCanMessage(value: String) {
        _uiState.value = _uiState.value.copy(whoCanMessage = value)
        saveSettings()
    }
    
    fun updateWhoCanFindMe(value: String) {
        _uiState.value = _uiState.value.copy(whoCanFindMe = value)
        saveSettings()
    }
    
    fun updateGhostMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(ghostMode = enabled)
        saveSettings()
    }
    
    private fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null, saved = false)
            
            try {
                val state = _uiState.value
                val result = ApiClient.updatePrivacySettings(
                    whoCanCall = state.whoCanCall,
                    whoCanSeeAvatar = state.whoCanSeeAvatar,
                    whoCanMessage = state.whoCanMessage,
                    whoCanFindMe = state.whoCanFindMe,
                    ghostMode = state.ghostMode
                )
                
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(saved = true)
                        kotlinx.coroutines.delay(2000)
                        _uiState.value = _uiState.value.copy(saved = false)
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            error = "Ошибка сохранения: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка: ${e.message}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    viewModel: PrivacySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приватность", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // Уведомление о сохранении
            if (uiState.saved) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Настройки сохранены",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Ошибка
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Загрузка
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MKRColors.Primary)
                }
            } else {
                // Ghost Mode - главная фича
                PrivacySection(title = "Карта") {
                    GhostModeCard(
                        enabled = uiState.ghostMode,
                        onToggle = viewModel::updateGhostMode
                    )
                }
                
                // Кто может звонить
                PrivacySection(title = "Звонки") {
                    PrivacyOptionCard(
                        icon = Icons.Outlined.Call,
                        title = "Кто может звонить",
                        subtitle = "Управление входящими звонками",
                        currentValue = uiState.whoCanCall,
                        onValueChange = viewModel::updateWhoCanCall
                    )
                }
                
                // Кто может видеть аватар
                PrivacySection(title = "Профиль") {
                    PrivacyOptionCard(
                        icon = Icons.Outlined.AccountCircle,
                        title = "Кто видит мою фотографию",
                        subtitle = "Видимость аватара",
                        currentValue = uiState.whoCanSeeAvatar,
                        onValueChange = viewModel::updateWhoCanSeeAvatar
                    )
                }
                
                // Кто может писать
                PrivacySection(title = "Сообщения") {
                    PrivacyOptionCard(
                        icon = Icons.Outlined.Chat,
                        title = "Кто может писать",
                        subtitle = "Получение сообщений",
                        currentValue = uiState.whoCanMessage,
                        onValueChange = viewModel::updateWhoCanMessage
                    )
                }
                
                // Кто может найти в поиске
                PrivacySection(title = "Поиск") {
                    PrivacyOptionCard(
                        icon = Icons.Outlined.Search,
                        title = "Кто может найти меня",
                        subtitle = "Видимость в поиске",
                        currentValue = uiState.whoCanFindMe,
                        onValueChange = viewModel::updateWhoCanFindMe
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MKRColors.Primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun GhostModeCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) 
                MKRColors.Primary.copy(alpha = 0.15f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MKRColors.Primary else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (enabled) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                    null,
                    tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Режим невидимки",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    if (enabled) "Ты скрыт с карты" else "Тебя видят на карте",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MKRColors.Primary
                )
            )
        }
    }
}

@Composable
private fun PrivacyOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MKRColors.Primary.copy(alpha = 0.2f),
                                    MKRColors.Secondary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = MKRColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Текущее значение
                Surface(
                    color = MKRColors.Primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        getValueLabel(currentValue),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MKRColors.Primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
            
            // Выпадающее меню
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PrivacyOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    option.icon,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (currentValue == option.value) 
                                        MKRColors.Primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(option.label)
                            }
                        },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        },
                        leadingIcon = {
                            if (currentValue == option.value) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = MKRColors.Primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private enum class PrivacyOption(
    val value: String,
    val label: String,
    val icon: ImageVector
) {
    EVERYONE("everyone", "Все", Icons.Outlined.Public),
    CONTACTS("contacts", "Только контакты", Icons.Outlined.People),
    NOBODY("nobody", "Никто", Icons.Outlined.Block)
}

private fun getValueLabel(value: String): String {
    return when (value) {
        "everyone" -> "Все"
        "contacts" -> "Контакты"
        "nobody" -> "Никто"
        else -> "Все"
    }
}
