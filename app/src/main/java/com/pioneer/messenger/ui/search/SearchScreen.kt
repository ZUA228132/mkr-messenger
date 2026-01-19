package com.pioneer.messenger.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.util.HapticFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUser(
    val id: String,
    val username: String,
    val displayName: String,
    val isOnline: Boolean,
    val accessLevel: Int,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    
    private val _users = MutableStateFlow<List<SearchUser>>(emptyList())
    val users = _users.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    private var allUsers: List<SearchUser> = emptyList()
    
    init {
        viewModelScope.launch {
            authManager.restoreSession()
            loadUsers()
        }
    }
    
    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            if (!ApiClient.hasAuthToken()) {
                authManager.restoreSession()
            }
            
            try {
                val result = ApiClient.getUsers()
                result.fold(
                    onSuccess = { serverUsers ->
                        allUsers = serverUsers.map { user ->
                            SearchUser(
                                id = user.id,
                                username = user.username,
                                displayName = user.displayName,
                                isOnline = user.isOnline,
                                accessLevel = user.accessLevel,
                                avatarUrl = user.avatarUrl,
                                isVerified = user.accessLevel >= 5 // Модераторы и админы верифицированы
                            )
                        }
                        // Не показываем всех пользователей сразу - только при поиске
                        _users.value = emptyList()
                    },
                    onFailure = { e ->
                        _error.value = e.message
                    }
                )
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun search(query: String) {
        _searchQuery.value = query
        
        if (query.length < 2) {
            _users.value = emptyList()
            return
        }
        
        _users.value = allUsers.filter { user ->
            user.username.contains(query, ignoreCase = true) ||
            user.displayName.contains(query, ignoreCase = true)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onStartChat: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val view = LocalView.current
    
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    LaunchedEffect(searchQuery) {
        viewModel.search(searchQuery)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по @username или имени") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { 
                                    HapticFeedback.lightClick(view)
                                    searchQuery = "" 
                                }) {
                                    Icon(Icons.Default.Close, "Очистить")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        HapticFeedback.lightClick(view)
                        onBack() 
                    }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
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
            }
            searchQuery.isEmpty() -> {
                // Пустой поиск - показываем подсказку
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Введите имя или @username",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Минимум 2 символа для поиска",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            searchQuery.length < 2 -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Введите минимум 2 символа",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            users.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Пользователь не найден",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item {
                        Text(
                            "Найдено: ${users.size}",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    items(users) { user ->
                        UserSearchItem(
                            user = user,
                            onProfileClick = { 
                                HapticFeedback.mediumClick(view)
                                onUserClick(user.id) 
                            },
                            onChatClick = { 
                                HapticFeedback.mediumClick(view)
                                onStartChat(user.id) 
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun UserSearchItem(
    user: SearchUser,
    onProfileClick: () -> Unit,
    onChatClick: () -> Unit
) {
    val context = LocalContext.current
    val avatarUrl = ApiClient.getAvatarUrl(user.avatarUrl)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        Box {
            if (avatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Аватар ${user.displayName}",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            user.displayName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
            
            // Индикатор онлайн
            if (user.isOnline) {
                Surface(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd),
                    shape = CircleShape,
                    color = Color(0xFF4CAF50)
                ) { }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Информация
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Галочка верификации
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Верифицирован",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF1DA1F2) // Синяя галочка как в Twitter/Instagram
                    )
                }
                
                // Бейдж админа
                if (user.accessLevel >= 10) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF9C27B0).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Админ",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9C27B0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Text(
                "@${user.username}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Кнопка написать
        FilledTonalIconButton(
            onClick = onChatClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Chat,
                "Написать",
                modifier = Modifier.size(20.dp)
            )
        }
    }
    
    Divider(modifier = Modifier.padding(start = 80.dp))
}
