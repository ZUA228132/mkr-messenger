package com.pioneer.messenger.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUser(
    val id: String,
    val name: String,
    val username: String,
    val role: String,
    val accessLevel: Int,
    val isActive: Boolean,
    val lastSeen: String,
    val isVerified: Boolean = false,
    val isBanned: Boolean = false,
    val banReason: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    
    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users = _users.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    private val _generatedKey = MutableStateFlow<String?>(null)
    val generatedKey = _generatedKey.asStateFlow()
    
    init {
        // Убеждаемся что сессия восстановлена перед загрузкой
        viewModelScope.launch {
            authManager.restoreSession()
            loadUsers()
        }
    }
    
    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Проверяем авторизацию
            if (!ApiClient.hasAuthToken()) {
                authManager.restoreSession()
            }
            
            try {
                val result = ApiClient.getUsers()
                result.fold(
                    onSuccess = { serverUsers ->
                        _users.value = serverUsers.map { user ->
                            AdminUser(
                                id = user.id,
                                name = user.displayName,
                                username = user.username,
                                role = when (user.accessLevel) {
                                    10 -> "Администратор"
                                    5 -> "Модератор"
                                    else -> "Пользователь"
                                },
                                accessLevel = user.accessLevel,
                                isActive = user.isOnline,
                                lastSeen = if (user.isOnline) "Онлайн" else "Был(а) недавно",
                                isVerified = user.isVerified,
                                isBanned = user.isBanned,
                                banReason = user.banReason
                            )
                        }
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Ошибка загрузки"
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка подключения к серверу"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun generateInviteKey(accessLevel: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Проверяем авторизацию
            if (!ApiClient.hasAuthToken()) {
                authManager.restoreSession()
            }
            
            try {
                val result = ApiClient.generateInviteKey(accessLevel)
                result.fold(
                    onSuccess = { response ->
                        _generatedKey.value = response.key
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка генерации ключа"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearGeneratedKey() {
        _generatedKey.value = null
    }
    
    fun verifyUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = ApiClient.verifyUser(userId)
                result.fold(
                    onSuccess = {
                        loadUsers() // Перезагружаем список
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка верификации"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun unverifyUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = ApiClient.unverifyUser(userId)
                result.fold(
                    onSuccess = {
                        loadUsers()
                    },
                    onFailure = { err: Throwable ->
                        _error.value = err.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка снятия верификации"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun banUser(userId: String, reason: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = ApiClient.banUser(userId, reason)
                result.fold(
                    onSuccess = {
                        loadUsers()
                    },
                    onFailure = { err: Throwable ->
                        _error.value = err.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка блокировки"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun unbanUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = ApiClient.unbanUser(userId)
                result.fold(
                    onSuccess = {
                        loadUsers()
                    },
                    onFailure = { err: Throwable ->
                        _error.value = err.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка разблокировки"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Публикация в канал MKR
    private val _postSuccess = MutableStateFlow(false)
    val postSuccess = _postSuccess.asStateFlow()
    
    fun publishToMKRChannel(content: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _postSuccess.value = false
            
            try {
                // ID канала MKR - channelId из таблицы Channels
                val mkrChannelId = "mkr-channel-info"
                
                val result = ApiClient.createChannelPost(mkrChannelId, content, allowComments = true)
                result.fold(
                    onSuccess = {
                        _postSuccess.value = true
                    },
                    onFailure = { err ->
                        _error.value = err.message ?: "Ошибка публикации"
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка публикации: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearPostSuccess() {
        _postSuccess.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showGenerateKeyDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var selectedUserForVerify by remember { mutableStateOf<AdminUser?>(null) }
    
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val generatedKey by viewModel.generatedKey.collectAsState()
    
    val clipboardManager = LocalClipboardManager.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Администрирование", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showGenerateKeyDialog = true }) {
                        Icon(Icons.Default.VpnKey, "Создать ключ")
                    }
                    IconButton(onClick = { viewModel.loadUsers() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
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
        ) {
            // Статистика
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Всего",
                    value = "${users.size}",
                    icon = Icons.Outlined.People,
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Онлайн",
                    value = "${users.count { it.isActive }}",
                    icon = Icons.Outlined.Circle,
                    color = Color(0xFF4CAF50)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Админы",
                    value = "${users.count { it.accessLevel >= 10 }}",
                    icon = Icons.Outlined.Shield,
                    color = Color(0xFF9C27B0)
                )
            }
            
            // Табы
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Пользователи") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Верификация") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Баны") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Ключи") }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("MKR") }
                )
            }
            
            // Загрузка
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadUsers() }) {
                            Text("Повторить")
                        }
                    }
                }
            } else {
                var showBanDialog by remember { mutableStateOf(false) }
                var selectedUserForBan by remember { mutableStateOf<AdminUser?>(null) }
                
                when (selectedTab) {
                    0 -> UsersList(users = users, onUserClick = { user ->
                        selectedUserForVerify = user
                        showVerifyDialog = true
                    })
                    1 -> VerificationTab(users = users, onVerifyUser = { user ->
                        selectedUserForVerify = user
                        showVerifyDialog = true
                    })
                    2 -> BansTab(
                        users = users,
                        onBanUser = { user ->
                            selectedUserForBan = user
                            showBanDialog = true
                        },
                        onUnbanUser = { user ->
                            viewModel.unbanUser(user.id)
                        }
                    )
                    3 -> KeysTab(onGenerateKey = { showGenerateKeyDialog = true })
                    4 -> MKRChannelTab(viewModel = viewModel)
                }
                
                // Диалог бана
                if (showBanDialog && selectedUserForBan != null) {
                    var banReason by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showBanDialog = false },
                        icon = {
                            Icon(
                                Icons.Default.Block,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                        },
                        title = { Text("Заблокировать пользователя", fontWeight = FontWeight.SemiBold) },
                        text = {
                            Column {
                                Text("${selectedUserForBan!!.name} (@${selectedUserForBan!!.username})")
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = banReason,
                                    onValueChange = { banReason = it },
                                    label = { Text("Причина блокировки") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.banUser(selectedUserForBan!!.id, banReason)
                                    showBanDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                enabled = banReason.isNotBlank()
                            ) {
                                Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Заблокировать")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBanDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Диалог верификации
    if (showVerifyDialog && selectedUserForVerify != null) {
        val user = selectedUserForVerify!!
        AlertDialog(
            onDismissRequest = { showVerifyDialog = false },
            icon = {
                Icon(
                    Icons.Default.Verified,
                    null,
                    tint = Color(0xFF1DA1F2),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { 
                Text(
                    if (user.isVerified) "Снять верификацию" else "Верификация пользователя", 
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ) 
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "@${user.username}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (user.isVerified) "Снять синюю галочку верификации?" else "Выдать синюю галочку верификации?",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Верифицированные аккаунты отображаются с галочкой в поиске и профиле",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (user.isVerified) {
                            viewModel.unverifyUser(user.id)
                        } else {
                            viewModel.verifyUser(user.id)
                        }
                        showVerifyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isVerified) MaterialTheme.colorScheme.error else Color(0xFF1DA1F2)
                    )
                ) {
                    Icon(
                        if (user.isVerified) Icons.Default.Close else Icons.Default.Verified, 
                        null, 
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (user.isVerified) "Снять" else "Верифицировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог генерации ключа
    if (showGenerateKeyDialog) {
        var selectedLevel by remember { mutableStateOf(1) }
        
        AlertDialog(
            onDismissRequest = { showGenerateKeyDialog = false },
            title = { Text("Создать ключ приглашения", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Уровень доступа:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    listOf(
                        1 to "Пользователь",
                        5 to "Модератор",
                        10 to "Администратор"
                    ).forEach { (level, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLevel = level },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLevel == level,
                                onClick = { selectedLevel = level }
                            )
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.generateInviteKey(selectedLevel)
                    showGenerateKeyDialog = false
                }) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateKeyDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог с созданным ключом и QR-кодом
    generatedKey?.let { key ->
        var showQrCode by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { viewModel.clearGeneratedKey() },
            title = { Text("Ключ создан!", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showQrCode) {
                        // QR-код
                        Text(
                            "Отсканируйте QR-код:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Простой QR-код placeholder (в реальном приложении использовать ZXing)
                        Surface(
                            modifier = Modifier.size(200.dp),
                            color = Color.White,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.QrCode2,
                                        null,
                                        modifier = Modifier.size(120.dp),
                                        tint = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "pioneer://invite/$key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(onClick = { showQrCode = false }) {
                            Text("Показать ключ")
                        }
                    } else {
                        Text("Скопируйте ключ и передайте пользователю:")
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = key,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(onClick = { showQrCode = true }) {
                            Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Показать QR-код")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(key))
                    viewModel.clearGeneratedKey()
                }) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Копировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearGeneratedKey() }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UsersList(users: List<AdminUser>, onUserClick: (AdminUser) -> Unit = {}) {
    if (users.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.People,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Нет пользователей", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users) { user ->
                UserCard(user = user, onClick = { onUserClick(user) })
            }
        }
    }
}

@Composable
private fun UserCard(user: AdminUser, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            user.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                            color = Color.White
                        )
                    }
                }
                if (user.isActive) {
                    Surface(
                        modifier = Modifier.size(14.dp).align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = Color(0xFF4CAF50)
                    ) { }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (user.accessLevel) {
                            10 -> Color(0xFF9C27B0).copy(alpha = 0.2f)
                            5 -> Color(0xFF2196F3).copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            user.role,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (user.accessLevel) {
                                10 -> Color(0xFF9C27B0)
                                5 -> Color(0xFF2196F3)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        user.lastSeen,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (user.isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationTab(users: List<AdminUser>, onVerifyUser: (AdminUser) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1DA1F2).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Verified,
                        null,
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Верификация аккаунтов",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Выдайте синюю галочку пользователям и каналам",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Выберите пользователя для верификации:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        items(users) { user ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVerifyUser(user) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                user.name.take(2).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.name, fontWeight = FontWeight.Medium)
                            if (user.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    null,
                                    tint = Color(0xFF1DA1F2),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            "@${user.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (user.isVerified) {
                        Surface(
                            color = Color(0xFF1DA1F2).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "Верифицирован",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1DA1F2)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onVerifyUser(user) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Выдать", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeysTab(onGenerateKey: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.VpnKey,
            null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Ключи приглашения",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Создайте ключ для приглашения нового пользователя в систему",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onGenerateKey,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(56.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Создать ключ приглашения", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onGenerateKey,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Default.QrCode2, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Создать с QR-кодом")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Информация
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Как это работает", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1. Создайте ключ с нужным уровнем доступа\n" +
                    "2. Передайте ключ или QR-код пользователю\n" +
                    "3. Пользователь вводит ключ при регистрации",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BansTab(
    users: List<AdminUser>,
    onBanUser: (AdminUser) -> Unit,
    onUnbanUser: (AdminUser) -> Unit
) {
    val bannedUsers = users.filter { it.isBanned }
    val activeUsers = users.filter { !it.isBanned }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Block,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Управление блокировками",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Заблокировано: ${bannedUsers.size} пользователей",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Заблокированные пользователи
        if (bannedUsers.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Заблокированные пользователи",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            items(bannedUsers) { user ->
                BannedUserCard(user = user, onUnban = { onUnbanUser(user) })
            }
        }
        
        // Активные пользователи для бана
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Активные пользователи",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        items(activeUsers) { user ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBanUser(user) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                user.name.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Medium)
                        Text(
                            "@${user.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = { onBanUser(user) }) {
                        Icon(
                            Icons.Default.Block,
                            "Заблокировать",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannedUserCard(user: AdminUser, onUnban: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Block,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!user.banReason.isNullOrEmpty()) {
                    Text(
                        "Причина: ${user.banReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
            
            Button(
                onClick = onUnban,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Разбан", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
private fun MKRChannelTab(viewModel: AdminViewModel) {
    var postContent by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()
    val postSuccess by viewModel.postSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Сбрасываем поле после успешной публикации
    LaunchedEffect(postSuccess) {
        if (postSuccess) {
            postContent = ""
            kotlinx.coroutines.delay(2000)
            viewModel.clearPostSuccess()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE040FB).copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📢", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Канал MKR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Публикация новостей и обновлений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Поле ввода
        OutlinedTextField(
            value = postContent,
            onValueChange = { postContent = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            label = { Text("Текст публикации") },
            placeholder = { Text("Напишите новость или обновление...") },
            maxLines = 10
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Подсказки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { postContent += "🎉 " },
                label = { Text("🎉") }
            )
            SuggestionChip(
                onClick = { postContent += "📢 " },
                label = { Text("📢") }
            )
            SuggestionChip(
                onClick = { postContent += "🔥 " },
                label = { Text("🔥") }
            )
            SuggestionChip(
                onClick = { postContent += "✨ " },
                label = { Text("✨") }
            )
            SuggestionChip(
                onClick = { postContent += "🚀 " },
                label = { Text("🚀") }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Кнопка публикации
        Button(
            onClick = { viewModel.publishToMKRChannel(postContent) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = postContent.isNotBlank() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE040FB)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Send, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Опубликовать в канал MKR", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Статус
        if (postSuccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
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
                        "Публикация успешно отправлена!",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        error?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        err,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Информация
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Информация", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Публикации видны всем подписчикам канала MKR\n" +
                    "• Используйте эмодзи для привлечения внимания\n" +
                    "• Публикуйте важные новости и обновления",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
