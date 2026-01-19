package com.pioneer.messenger.ui.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val channel by viewModel.currentChannel.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showNewPostDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(channelId) {
        viewModel.loadChannel(channelId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFF2196F3)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Campaign, null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                channel?.name ?: "Канал",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatSubscriberCount(channel?.subscriberCount ?: 0)} подписчиков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Настройки")
                    }
                    
                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false }
                    ) {
                        if (channel?.isSubscribed == true) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        if (channel?.notificationsEnabled == true) "Выключить уведомления" 
                                        else "Включить уведомления"
                                    )
                                },
                                onClick = {
                                    channel?.let {
                                        viewModel.toggleNotifications(it.id, !it.notificationsEnabled)
                                    }
                                    showSettingsMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (channel?.notificationsEnabled == true) Icons.Default.NotificationsOff
                                        else Icons.Default.Notifications,
                                        null
                                    )
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { Text("Отписаться", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    channel?.let { viewModel.unsubscribe(it.id) }
                                    showSettingsMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Показываем FAB только если пользователь может публиковать
            if (channel?.canPost == true) {
                FloatingActionButton(
                    onClick = { showNewPostDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Edit, "Новый пост", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Информация о канале
            channel?.let { ch ->
                ChannelHeader(
                    channel = ch,
                    onSubscribe = { viewModel.subscribe(ch.id) },
                    onUnsubscribe = { viewModel.unsubscribe(ch.id) }
                )
            }
            
            Divider()
            
            // Посты
            if (isLoading && posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Article,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Нет публикаций",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        ChannelPostItem(
                            post = post,
                            channelName = channel?.name ?: "",
                            onReact = { emoji -> viewModel.addReactionToPost(post.messageId, emoji) }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
    
    // Диалог создания поста
    if (showNewPostDialog) {
        NewPostDialog(
            onDismiss = { showNewPostDialog = false },
            onPost = { content, allowComments ->
                channel?.let { viewModel.createPost(it.id, content, allowComments) }
                showNewPostDialog = false
            }
        )
    }
}

@Composable
fun ChannelHeader(
    channel: ChannelUiModel,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Описание
        if (!channel.description.isNullOrBlank()) {
            Text(
                channel.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Username
        if (channel.username != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AlternateEmail,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    channel.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Кнопка подписки
        if (channel.isSubscribed) {
            OutlinedButton(
                onClick = onUnsubscribe,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Вы подписаны")
            }
        } else {
            Button(
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Подписаться")
            }
        }
    }
}

@Composable
fun ChannelPostItem(
    post: ChannelPostUiModel,
    channelName: String,
    onReact: (String) -> Unit
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showReactionPicker = !showReactionPicker }
            .padding(16.dp)
    ) {
        // Заголовок поста
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = Color(0xFF2196F3)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channelName, fontWeight = FontWeight.SemiBold)
                Text(
                    formatPostTime(post.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (post.isPinned) {
                Icon(
                    Icons.Default.PushPin,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Контент
        Text(
            post.content,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Статистика и реакции
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Просмотры
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Visibility,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    formatViewCount(post.viewCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (post.allowComments) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${post.commentCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Реакции
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                post.reactions.forEach { reaction ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (reaction.hasReacted) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onReact(reaction.emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(reaction.emoji, fontSize = 14.sp)
                            if (reaction.count > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${reaction.count}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                
                // Кнопка добавления реакции
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { showReactionPicker = true }
                ) {
                    Icon(
                        Icons.Outlined.AddReaction,
                        null,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Быстрые реакции
        if (showReactionPicker) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("👍", "❤️", "🔥", "👏", "😂", "😮", "😢", "🎉").forEach { emoji ->
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                onReact(emoji)
                                showReactionPicker = false
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewPostDialog(
    onDismiss: () -> Unit,
    onPost: (content: String, allowComments: Boolean) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var allowComments by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая публикация", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("Текст публикации...") },
                    maxLines = 10
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Разрешить комментарии")
                    Switch(
                        checked = allowComments,
                        onCheckedChange = { allowComments = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(content, allowComments) },
                enabled = content.isNotBlank()
            ) {
                Text("Опубликовать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun formatSubscriberCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatViewCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatPostTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "только что"
        diff < 3600_000 -> "${diff / 60_000} мин. назад"
        diff < 86400_000 -> "${diff / 3600_000} ч. назад"
        diff < 604800_000 -> SimpleDateFormat("EEEE", Locale("ru")).format(Date(timestamp))
        else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(timestamp))
    }
}
