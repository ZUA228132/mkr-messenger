package com.pioneer.messenger.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Состояния соединения
enum class ConnectionState {
    CONNECTED,
    CONNECTING,
    WAITING_FOR_NETWORK,
    UPDATING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = onChatClick, // Для каналов
    onNavigateToTasks: () -> Unit,
    onNavigateToFinance: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onNavigateToReels: () -> Unit = {},
    onNavigateToCreateStory: () -> Unit = {},
    onNavigateToStatus: () -> Unit = {},
    onLockApp: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState(initial = emptyList())
    val connectionState by viewModel.connectionState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showNewChatDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    // Режим редактирования чатов
    var isEditMode by remember { mutableStateOf(false) }
    var selectedChats by remember { mutableStateOf(setOf<String>()) }
    
    // Контекстное меню для чата
    var selectedChat by remember { mutableStateOf<ChatUiModel?>(null) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                // Верхняя панель
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Левая часть - кнопка Изм./Готово
                    TextButton(onClick = { 
                        if (isEditMode) {
                            selectedChats = emptySet()
                        }
                        isEditMode = !isEditMode 
                    }) {
                        Text(
                            if (isEditMode) "Готово" else "Изм.",
                            color = com.pioneer.messenger.ui.theme.MKRColors.Primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Центр - замочек + Чаты
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onLockApp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                "Заблокировать",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            if (isEditMode && selectedChats.isNotEmpty()) 
                                "Выбрано: ${selectedChats.size}" 
                            else "Чаты",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Правая часть - иконки
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isEditMode && selectedChats.isNotEmpty()) {
                            // Кнопки действий в режиме редактирования
                            IconButton(
                                onClick = { 
                                    selectedChats.forEach { viewModel.togglePinChat(it) }
                                    selectedChats = emptySet()
                                    isEditMode = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    "Закрепить",
                                    tint = com.pioneer.messenger.ui.theme.MKRColors.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { 
                                    showDeleteDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Удалить",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // Обычные кнопки
                            IconButton(
                                onClick = { showNewChatDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.AddCircleOutline,
                                    "Новый чат",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    "Настройки",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                
                // Статус соединения
                AnimatedVisibility(
                    visible = connectionState != ConnectionState.CONNECTED,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    ConnectionStatusBar(connectionState)
                }
                
                // Убрана строка историй - не нужна для защищённого мессенджера
                
                // Поиск Liquid Glass
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onNavigateToSearch() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                "Поиск",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Поиск",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Liquid Glass нижняя навигация
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding() // Учитываем системную навигацию
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Фоновый слой с размытием
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                
                // Контент поверх
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Чаты
                    MKRNavItem(
                        icon = Icons.Outlined.Chat,
                        label = "Чаты",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    
                    // Каналы
                    MKRNavItem(
                        icon = Icons.Outlined.Campaign,
                        label = "Каналы",
                        selected = selectedTab == 1,
                        onClick = { 
                            selectedTab = 1
                            onNavigateToChannels()
                        }
                    )
                    
                    // Профиль
                    MKRNavItem(
                        icon = Icons.Outlined.Person,
                        label = "Профиль",
                        selected = selectedTab == 2,
                        onClick = { 
                            selectedTab = 2
                            onNavigateToProfile()
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                // Liquid Glass FAB
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = com.pioneer.messenger.ui.theme.MKRColors.Primary,
                    modifier = Modifier
                        .size(56.dp)
                ) {
                    Icon(Icons.Default.Edit, "Новый чат", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)
        
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.loadChatsFromServer() },
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = { state, trigger ->
                SwipeRefreshIndicator(
                    state = state,
                    refreshTriggerDistance = trigger,
                    contentColor = com.pioneer.messenger.ui.theme.MKRColors.Primary
                )
            }
        ) {
            if (chats.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Chat,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Нет чатов", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Нажмите + чтобы начать",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(chats.sortedByDescending { it.isPinned }, key = { it.id }) { chat ->
                        ChatListItem(
                            chat = chat, 
                            onClick = { 
                                if (isEditMode) {
                                    selectedChats = if (selectedChats.contains(chat.id)) {
                                        selectedChats - chat.id
                                    } else {
                                        selectedChats + chat.id
                                    }
                                } else {
                                    // Если это канал - открываем ChannelScreen
                                    if (chat.isChannel) {
                                        onChannelClick(chat.id)
                                    } else {
                                        onChatClick(chat.id) 
                                    }
                                }
                            },
                            onLongClick = {
                                if (!isEditMode) {
                                    selectedChat = chat
                                    showChatMenu = true
                                }
                            },
                            isEditMode = isEditMode,
                            isSelected = selectedChats.contains(chat.id)
                        )
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            onDismiss = { showNewChatDialog = false },
            onCreateChat = { type, name, autoDeleteDays, participantIds ->
                viewModel.createChat(type, name, autoDeleteDays, participantIds)
                showNewChatDialog = false
            }
        )
    }
    
    // Меню действий с чатом
    if (showChatMenu && selectedChat != null) {
        ChatContextMenu(
            chat = selectedChat!!,
            onDismiss = { showChatMenu = false; selectedChat = null },
            onPin = { 
                viewModel.togglePinChat(selectedChat!!.id)
                showChatMenu = false
                selectedChat = null
            },
            onMute = {
                viewModel.toggleMuteChat(selectedChat!!.id)
                showChatMenu = false
                selectedChat = null
            },
            onDelete = {
                showChatMenu = false
                showDeleteDialog = true
            }
        )
    }
    
    // Диалог удаления чата (для режима редактирования или одиночного)
    if (showDeleteDialog) {
        if (isEditMode && selectedChats.isNotEmpty()) {
            // Удаление нескольких чатов
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Удалить чаты?") },
                text = { Text("Удалить ${selectedChats.size} выбранных чатов?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedChats.forEach { viewModel.deleteChat(it, forEveryone = false) }
                            selectedChats = emptySet()
                            isEditMode = false
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Удалить", color = Color(0xFFFF5252))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        } else if (selectedChat != null) {
            DeleteChatDialog(
                chatName = selectedChat!!.name,
                onDismiss = { showDeleteDialog = false; selectedChat = null },
                onDeleteForMe = {
                    viewModel.deleteChat(selectedChat!!.id, forEveryone = false)
                    showDeleteDialog = false
                    selectedChat = null
                },
                onDeleteForEveryone = {
                    viewModel.deleteChat(selectedChat!!.id, forEveryone = true)
                    showDeleteDialog = false
                    selectedChat = null
                }
            )
        }
    }
}

@Composable
fun NewChatDialog(
    onDismiss: () -> Unit,
    onCreateChat: (type: String, name: String, autoDeleteDays: Int?, participantIds: List<String>) -> Unit
) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var chatName by remember { mutableStateOf("") }
    var autoDeleteDays by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }
    var selectedUsers by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Поиск пользователей
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isSearching = true
            kotlinx.coroutines.delay(300) // Debounce
            try {
                val result = com.pioneer.messenger.data.network.ApiClient.searchUsers(searchQuery)
                result.onSuccess { users ->
                    searchResults = users.map { UserSearchResult(it.id, it.username, it.displayName, it.avatarUrl, it.isVerified) }
                }
            } catch (e: Exception) {
                android.util.Log.e("NewChatDialog", "Search error: ${e.message}")
            }
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    selectedType == "SECRET" && autoDeleteDays == null -> "Автоудаление"
                    selectedType == "PRIVATE" || selectedType == "SECRET" -> "Найти пользователя"
                    selectedType == "GROUP" && selectedUsers.isEmpty() -> "Добавить участников"
                    selectedType == "GROUP" -> "Название группы"
                    selectedType == "CHANNEL" -> "Название канала"
                    else -> "Создать"
                },
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            when {
                selectedType == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChatTypeOption(Icons.Outlined.Person, "Новый чат", "Личное сообщение") { selectedType = "PRIVATE" }
                        ChatTypeOption(Icons.Outlined.Lock, "Секретный чат", "С автоудалением", Color(0xFF4CAF50)) { selectedType = "SECRET" }
                        ChatTypeOption(Icons.Outlined.Group, "Новая группа", "До 200 участников") { selectedType = "GROUP" }
                        ChatTypeOption(Icons.Outlined.Campaign, "Новый канал", "Для объявлений") { selectedType = "CHANNEL" }
                    }
                }
                selectedType == "SECRET" && autoDeleteDays == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "1 день", 3 to "3 дня", 7 to "7 дней").forEach { (days, label) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { autoDeleteDays = days },
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label)
                                }
                            }
                        }
                    }
                }
                // Поиск пользователя для личного или секретного чата
                (selectedType == "PRIVATE" || (selectedType == "SECRET" && autoDeleteDays != null)) -> {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Username или имя") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (searchResults.isNotEmpty()) {
                            Column(
                                modifier = Modifier.heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                searchResults.forEach { user ->
                                    UserSearchItem(
                                        user = user,
                                        isSelected = false,
                                        onClick = {
                                            // Создаём чат сразу
                                            onCreateChat(selectedType!!, user.displayName, autoDeleteDays, listOf(user.id))
                                        }
                                    )
                                }
                            }
                        } else if (searchQuery.length >= 2 && !isSearching) {
                            Text(
                                "Пользователи не найдены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                // Выбор участников для группы
                selectedType == "GROUP" && chatName.isEmpty() -> {
                    Column {
                        // Выбранные пользователи
                        if (selectedUsers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedUsers.forEach { user ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(user.displayName, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Close,
                                                null,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { selectedUsers = selectedUsers - user },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Text(
                                "Выбрано: ${selectedUsers.size} (минимум 2)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedUsers.size >= 2) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Добавить участника") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (searchResults.isNotEmpty()) {
                            Column(
                                modifier = Modifier.heightIn(max = 150.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                searchResults.filter { user -> selectedUsers.none { it.id == user.id } }.forEach { user ->
                                    UserSearchItem(
                                        user = user,
                                        isSelected = false,
                                        onClick = {
                                            selectedUsers = selectedUsers + user
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                // Название группы (после выбора участников)
                selectedType == "GROUP" && selectedUsers.size >= 2 -> {
                    Column {
                        Text(
                            "Участники: ${selectedUsers.joinToString { it.displayName }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = chatName,
                            onValueChange = { chatName = it },
                            label = { Text("Название группы") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                // Название канала
                selectedType == "CHANNEL" -> {
                    OutlinedTextField(
                        value = chatName,
                        onValueChange = { chatName = it },
                        label = { Text("Название канала") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            when {
                // Группа - переход к названию после выбора участников
                selectedType == "GROUP" && selectedUsers.size >= 2 && chatName.isEmpty() -> {
                    TextButton(onClick = { chatName = " " }) { // Триггер для показа поля названия
                        Text("Далее")
                    }
                }
                // Группа - создание
                selectedType == "GROUP" && selectedUsers.size >= 2 && chatName.isNotBlank() -> {
                    TextButton(onClick = { 
                        onCreateChat(selectedType!!, chatName.trim(), null, selectedUsers.map { it.id }) 
                    }) {
                        Text("Создать")
                    }
                }
                // Канал - создание
                selectedType == "CHANNEL" && chatName.isNotBlank() -> {
                    TextButton(onClick = { onCreateChat(selectedType!!, chatName, null, emptyList()) }) {
                        Text("Создать")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                when {
                    selectedType == "GROUP" && chatName.isNotBlank() -> chatName = ""
                    selectedType == "GROUP" && selectedUsers.isNotEmpty() -> { selectedUsers = emptyList(); searchQuery = "" }
                    selectedType == "SECRET" && autoDeleteDays != null -> autoDeleteDays = null
                    selectedType != null -> { selectedType = null; chatName = ""; autoDeleteDays = null; searchQuery = ""; selectedUsers = emptyList() }
                    else -> onDismiss()
                }
            }) {
                Text(if (selectedType != null) "Назад" else "Отмена")
            }
        }
    )
}

// Модель для результата поиска
data class UserSearchResult(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val isVerified: Boolean
)

@Composable
private fun UserSearchItem(
    user: UserSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.2f) 
               else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = com.pioneer.messenger.ui.theme.MKRColors.Primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        user.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.displayName, fontWeight = FontWeight.Medium)
                    if (user.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            null,
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = com.pioneer.messenger.ui.theme.MKRColors.Primary
                )
            }
        }
    }
}

@Composable
private fun ChatTypeOption(icon: ImageVector, title: String, subtitle: String, iconColor: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = iconColor.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: ChatUiModel, 
    onClick: () -> Unit, 
    onLongClick: () -> Unit = {},
    isEditMode: Boolean = false,
    isSelected: Boolean = false
) {
    // Liquid Glass карточка чата
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Фон с Liquid Glass эффектом
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.3f),
                                com.pioneer.messenger.ui.theme.MKRColors.Secondary.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Чекбокс в режиме редактирования
                if (isEditMode) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) com.pioneer.messenger.ui.theme.MKRColors.Primary
                                else Color.Transparent
                            )
                            .then(
                                if (!isSelected) Modifier.background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                // Индикатор закрепления
                if (chat.isPinned && !isEditMode) {
                    Icon(
                        Icons.Default.PushPin,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = com.pioneer.messenger.ui.theme.MKRColors.Primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                
                // Аватар с градиентом
                Box(
                    modifier = Modifier.size(52.dp)
                ) {
                    // Светящийся фон
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                when {
                                    chat.isSecret -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                                    chat.isChannel -> com.pioneer.messenger.ui.theme.MKRColors.Secondary.copy(alpha = 0.3f)
                                    else -> com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.3f)
                                },
                                CircleShape
                            )
                            .graphicsLayer { alpha = 0.6f }
                    )
                    
                    // Аватар
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = when {
                            chat.isSecret -> Color(0xFF4CAF50)
                            chat.isChannel -> com.pioneer.messenger.ui.theme.MKRColors.Secondary
                            else -> com.pioneer.messenger.ui.theme.MKRColors.Primary
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                chat.isChannel -> Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                chat.isGroup -> Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                else -> Text(chat.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (chat.isSecret) {
                                Icon(Icons.Default.Lock, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                chat.name,
                                fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (chat.isSecret) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chat.lastMessageStatus != null) {
                                Icon(
                                    if (chat.lastMessageStatus == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (chat.lastMessageStatus == "READ") com.pioneer.messenger.ui.theme.MKRColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                formatTime(chat.lastMessageTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            chat.lastMessage,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (chat.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape, 
                                color = com.pioneer.messenger.ui.theme.MKRColors.Primary
                            ) {
                                Text(
                                    if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(timestamp))
    }
}

data class ChatUiModel(
    val id: String,
    val name: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val lastMessageType: String? = null,
    val lastMessageStatus: String? = null,
    val unreadCount: Int,
    val isGroup: Boolean,
    val isChannel: Boolean,
    val isPinned: Boolean,
    val isSecret: Boolean = false,
    val autoDeleteDays: Int? = null,
    val isMuted: Boolean = false,
    val isSavedMessages: Boolean = false,
    val viewCount: Int = 0
)

@Composable
fun ChatContextMenu(
    chat: ChatUiModel,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chat.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                // Закрепить/Открепить
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPin),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (chat.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (chat.isPinned) "Открепить" else "Закрепить")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Без звука / Со звуком
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onMute),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (chat.isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (chat.isMuted) "Включить звук" else "Без звука")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Удалить
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Удалить чат", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DeleteChatDialog(
    chatName: String,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить чат?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Выберите как удалить чат \"$chatName\":")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Удалить для себя
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDeleteForMe),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Удалить для себя", fontWeight = FontWeight.Medium)
                            Text(
                                "Чат останется у собеседника",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Удалить для всех
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDeleteForEveryone),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Удалить для всех", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text(
                                "Чат будет удалён у всех участников",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ConnectionStatusBar(state: ConnectionState) {
    val infiniteTransition = rememberInfiniteTransition(label = "connection")
    
    // Анимация для точек загрузки
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        when (state) {
            ConnectionState.CONNECTING -> {
                Text(
                    "Соединение",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(".", color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha1), fontSize = 12.sp)
                Text(".", color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha2), fontSize = 12.sp)
                Text(".", color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha3), fontSize = 12.sp)
            }
            ConnectionState.WAITING_FOR_NETWORK -> {
                Icon(
                    Icons.Default.SignalWifiOff,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Ожидание сети",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(".", color = Color(0xFFFF9800).copy(alpha = dotAlpha1), fontSize = 12.sp)
                Text(".", color = Color(0xFFFF9800).copy(alpha = dotAlpha2), fontSize = 12.sp)
                Text(".", color = Color(0xFFFF9800).copy(alpha = dotAlpha3), fontSize = 12.sp)
            }
            ConnectionState.UPDATING -> {
                Icon(
                    Icons.Default.Sync,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Обновление",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(".", color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha1), fontSize = 12.sp)
                Text(".", color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha2), fontSize = 12.sp)
                Text(".", color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha3), fontSize = 12.sp)
            }
            ConnectionState.CONNECTED -> {
                // Ничего не показываем когда подключены
            }
        }
    }
}

@Composable
fun MKRNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) com.pioneer.messenger.ui.theme.MKRColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) com.pioneer.messenger.ui.theme.MKRColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// Компонент историй
@Composable
fun StoriesRow(
    onAddStory: () -> Unit,
    onStoryClick: (String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val stories by viewModel.stories.collectAsState()
    val myStories by viewModel.myStories.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadStories()
    }
    
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // Моя история
        item {
            StoryAvatar(
                name = "Моя история",
                initials = "МО",
                hasUnwatched = false,
                isMyStory = true,
                hasStory = myStories.isNotEmpty(),
                onClick = { 
                    if (myStories.isNotEmpty()) {
                        onStoryClick("my")
                    } else {
                        onAddStory()
                    }
                }
            )
        }
        
        // Истории других пользователей
        items(stories.size) { index ->
            val story = stories[index]
            StoryAvatar(
                name = story.userName,
                initials = story.userName.take(2).uppercase(),
                hasUnwatched = story.hasUnwatched,
                isMyStory = false,
                hasStory = true,
                onClick = { onStoryClick(story.oderId) }
            )
        }
    }
}

// Модель для историй
data class StoryUserModel(
    val oderId: String,
    val userName: String,
    val avatarUrl: String?,
    val hasUnwatched: Boolean
)

@Composable
fun StoryAvatar(
    name: String,
    initials: String,
    hasUnwatched: Boolean,
    isMyStory: Boolean,
    hasStory: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Кольцо вокруг аватара
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        brush = if (hasUnwatched || (isMyStory && hasStory)) {
                            Brush.linearGradient(
                                colors = listOf(
                                    com.pioneer.messenger.ui.theme.MKRColors.Primary,
                                    com.pioneer.messenger.ui.theme.MKRColors.Secondary
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray.copy(alpha = 0.5f),
                                    Color.Gray.copy(alpha = 0.3f)
                                )
                            )
                        },
                        shape = CircleShape
                    )
            )
            
            // Внутренний круг (отступ)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
            )
            
            // Аватар
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                com.pioneer.messenger.ui.theme.MKRColors.Primary.copy(alpha = 0.7f),
                                com.pioneer.messenger.ui.theme.MKRColors.Secondary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            // Плюсик для добавления истории
            if (isMyStory && !hasStory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(com.pioneer.messenger.ui.theme.MKRColors.Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            // Галочка для просмотренной своей истории
            if (isMyStory && hasStory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(com.pioneer.messenger.ui.theme.MKRColors.Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            name,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

