package com.pioneer.messenger.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pioneer.messenger.ui.utils.ChatScreenProtection
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit = {},
    onCallClick: (isVideo: Boolean, isSecretChat: Boolean) -> Unit = { _, _ -> },
    onChatInfoLoaded: (name: String, oderId: String) -> Unit = { _, _ -> },
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val chatInfo by viewModel.chatInfo.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val videoNoteToPlay by viewModel.videoNoteToPlay.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val typingUserName by viewModel.typingUserName.collectAsState()
    
    // Передаём информацию о чате наверх когда она загрузится
    LaunchedEffect(chatInfo) {
        chatInfo?.let { info ->
            onChatInfoLoaded(info.name, info.otherUserId ?: chatId)
        }
    }
    
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showVideoNoteDialog by remember { mutableStateOf(false) }
    var showUserInfoCard by remember { mutableStateOf(true) } // Показывать карточку пользователя
    
    // Состояние для удаления сообщений
    var selectedMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var showDeleteMessageDialog by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startVoiceRecording()
        }
    }
    
    // Лаунчеры для выбора файлов
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendPhoto(it) }
        viewModel.resetPickPhoto()
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendVideo(it) }
        viewModel.resetPickVideo()
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendFile(it) }
        viewModel.resetPickFile()
    }
    
    // События выбора файлов
    val pickPhotoEvent by viewModel.pickPhotoEvent.collectAsState()
    val pickVideoEvent by viewModel.pickVideoEvent.collectAsState()
    val pickFileEvent by viewModel.pickFileEvent.collectAsState()
    val shareLocationEvent by viewModel.shareLocationEvent.collectAsState()
    
    LaunchedEffect(pickPhotoEvent) {
        if (pickPhotoEvent) {
            photoPickerLauncher.launch("image/*")
        }
    }
    
    LaunchedEffect(pickVideoEvent) {
        if (pickVideoEvent) {
            videoPickerLauncher.launch("video/*")
        }
    }
    
    LaunchedEffect(pickFileEvent) {
        if (pickFileEvent) {
            filePickerLauncher.launch("*/*")
        }
    }
    
    LaunchedEffect(shareLocationEvent) {
        if (shareLocationEvent) {
            // Простая геолокация - отправляем координаты Москвы как заглушку
            // TODO: Реализовать получение реальной геолокации
            viewModel.sendLocation(55.7558, 37.6173)
            viewModel.resetShareLocation()
        }
    }
    
    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
    }
    
    // Защита от скриншотов
    ChatScreenProtection(
        isSecretChat = chatInfo?.isSecret == true,
        userId = currentUserId
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    chatInfo = chatInfo,
                    onBack = onBack,
                    onProfileClick = { chatInfo?.let { onProfileClick(it.otherUserId ?: it.id) } },
                    onCallClick = { 
                        // Сразу аудиозвонок без меню выбора
                        val isSecretChat = chatInfo?.isSecret == true
                        onCallClick(false, isSecretChat) 
                    },
                    showMoreMenu = showMoreMenu,
                    onShowMoreMenu = { showMoreMenu = it },
                    isTyping = isTyping,
                    typingUserName = typingUserName
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Сообщения
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            isOwnMessage = message.isOwn,
                            isChannel = chatInfo?.isChannel == true,
                            onPlayVoice = { viewModel.playVoiceMessage(message.id) },
                            onPlayVideo = { viewModel.playVideoNote(message.id) },
                            onLongClick = {
                                selectedMessage = message
                                showDeleteMessageDialog = true
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // Карточка пользователя в конце списка (при первом сообщении)
                    if (showUserInfoCard && chatInfo != null && !chatInfo!!.isChannel && messages.size <= 5) {
                        item {
                            UserInfoCard(
                                userName = chatInfo!!.name,
                                isVerified = chatInfo!!.isVerified,
                                createdAt = chatInfo!!.createdAt,
                                avatarUrl = chatInfo!!.avatarUrl,
                                onDismiss = { showUserInfoCard = false }
                            )
                        }
                    }
                }
                
                // Индикатор записи
                if (recordingState.isRecording) {
                    RecordingBar(
                        isVideo = recordingState.isVideo,
                        duration = recordingState.duration,
                        onCancel = { viewModel.cancelRecording() },
                        onSend = { viewModel.stopAndSendRecording() }
                    )
                } else {
                    // Поле ввода
                    ChatInputBar(
                        messageText = messageText,
                        onMessageChange = { 
                            messageText = it
                            viewModel.sendTyping() // Отправляем typing при изменении текста
                        },
                        onSend = {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                        },
                        onAttachClick = { showAttachmentMenu = true },
                        onVoiceHold = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasPermission) {
                                viewModel.startVoiceRecording()
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        onVoiceRelease = { viewModel.stopAndSendRecording() },
                        onVideoNoteClick = { showVideoNoteDialog = true },
                        isChannel = chatInfo?.isChannel == true,
                        recipientName = chatInfo?.name
                    )
                }
            }
        }
    }
    
    // Меню вложений
    if (showAttachmentMenu) {
        AttachmentBottomSheet(
            onDismiss = { showAttachmentMenu = false },
            onPhotoClick = { showAttachmentMenu = false; viewModel.pickPhoto() },
            onVideoClick = { showAttachmentMenu = false; viewModel.pickVideo() },
            onFileClick = { showAttachmentMenu = false; viewModel.pickFile() },
            onLocationClick = { showAttachmentMenu = false; viewModel.shareLocation() },
            onContactClick = { showAttachmentMenu = false; viewModel.shareContact() }
        )
    }
    
    // Плеер видеокружка
    videoNoteToPlay?.let { filePath ->
        VideoNotePlayerDialog(
            filePath = filePath,
            onDismiss = { viewModel.dismissVideoNote() }
        )
    }
    
    // Прямой аудиозвонок (без меню выбора)
    // Видеозвонки отключены
    
    // Диалог записи видеокружка с CameraX
    if (showVideoNoteDialog) {
    
    // Диалог удаления сообщения
    if (showDeleteMessageDialog && selectedMessage != null) {
        DeleteMessageDialog(
            message = selectedMessage!!,
            onDismiss = { 
                showDeleteMessageDialog = false
                selectedMessage = null
            },
            onDeleteForMe = {
                viewModel.deleteMessage(selectedMessage!!.id, forEveryone = false)
                showDeleteMessageDialog = false
                selectedMessage = null
            },
            onDeleteForEveryone = {
                viewModel.deleteMessage(selectedMessage!!.id, forEveryone = true)
                showDeleteMessageDialog = false
                selectedMessage = null
            },
            onReact = { emoji ->
                viewModel.addReaction(selectedMessage!!.id, emoji)
            }
        )
    }
    
    // Диалог записи видеокружка с CameraX (продолжение)
    if (showVideoNoteDialog) {
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                // Разрешения получены, диалог уже открыт
            }
        }
        
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasCameraPermission && hasAudioPermission) {
            VideoNoteRecorderDialog(
                onDismiss = { showVideoNoteDialog = false },
                onVideoRecorded = { file ->
                    viewModel.sendVideoNote(file)
                    showVideoNoteDialog = false
                }
            )
        } else {
            LaunchedEffect(Unit) {
                cameraPermissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
            // Показываем простой диалог пока нет разрешений
            AlertDialog(
                onDismissRequest = { showVideoNoteDialog = false },
                title = { Text("Разрешения") },
                text = { Text("Для записи видеокружка нужен доступ к камере и микрофону") },
                confirmButton = {
                    TextButton(onClick = {
                        cameraPermissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    }) {
                        Text("Разрешить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVideoNoteDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    chatInfo: ChatInfoUiModel?,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onCallClick: () -> Unit,
    showMoreMenu: Boolean,
    onShowMoreMenu: (Boolean) -> Unit,
    isTyping: Boolean = false,
    typingUserName: String? = null
) {
    val context = LocalContext.current
    
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.clickable(onClick = onProfileClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Аватар с эмодзи-статусом
                Box {
                    val avatarUrl = chatInfo?.avatarUrl?.let { 
                        com.pioneer.messenger.data.network.ApiClient.getAvatarUrl(it) 
                    }
                    
                    if (!avatarUrl.isNullOrEmpty()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Аватар",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            loading = {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            (chatInfo?.name ?: "?").take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            error = {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            (chatInfo?.name ?: "?").take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = when {
                                chatInfo?.isSecret == true -> Color(0xFF4CAF50)
                                chatInfo?.isChannel == true -> Color(0xFF2196F3)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (chatInfo?.isChannel == true) {
                                    Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text(
                                        (chatInfo?.name ?: "?").take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    // Эмодзи-статус или индикатор онлайн
                    if (chatInfo?.isChannel != true) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (chatInfo?.emojiStatus != null) {
                                Text(chatInfo.emojiStatus, fontSize = 10.sp)
                            } else if (chatInfo?.isOnline == true) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chatInfo?.isSecret == true) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            chatInfo?.name ?: "Чат",
                            fontWeight = FontWeight.SemiBold,
                            color = if (chatInfo?.isSecret == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        // Галочка верификации
                        if (chatInfo?.isVerified == true) {
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
                        text = when {
                            isTyping -> typingUserName?.let { "$it печатает..." } ?: "печатает..."
                            chatInfo?.isChannel == true -> "${chatInfo.subscriberCount} подписчиков"
                            chatInfo?.isOnline == true -> "в сети"
                            chatInfo?.lastSeen != null -> "был(а) в ${chatInfo.lastSeen}"
                            else -> "был(а) недавно"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isTyping -> MaterialTheme.colorScheme.primary
                            chatInfo?.isOnline == true -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
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
            if (chatInfo?.isChannel != true) {
                IconButton(onClick = onCallClick) {
                    Icon(Icons.Default.Call, "Позвонить")
                }
            }
            IconButton(onClick = { onShowMoreMenu(true) }) {
                Icon(Icons.Default.MoreVert, "Ещё")
            }
            
            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { onShowMoreMenu(false) }) {
                DropdownMenuItem(
                    text = { Text("Поиск") },
                    onClick = { onShowMoreMenu(false) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }
                )
                if (chatInfo?.isSecret == true) {
                    DropdownMenuItem(
                        text = { Text("Таймер: ${chatInfo.autoDeleteDays ?: 1} дн.") },
                        onClick = { onShowMoreMenu(false) },
                        leadingIcon = { Icon(Icons.Outlined.Timer, null) }
                    )
                }
            }
        }
    )
}


@Composable
fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceHold: () -> Unit,
    onVoiceRelease: () -> Unit,
    onVideoNoteClick: () -> Unit,
    isChannel: Boolean,
    recipientName: String? = null
) {
    var isHoldingVoice by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(), // Отступ для клавиатуры
        tonalElevation = 2.dp
    ) {
        Column {
            // Эмодзи пикер
            if (showEmojiPicker) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        onMessageChange(messageText + emoji)
                    },
                    onDismiss = { showEmojiPicker = false }
                )
            }
            
            // Показываем кому пишем
            if (!recipientName.isNullOrEmpty() && messageText.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Reply,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Сообщение для $recipientName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка эмодзи
                IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                    Icon(
                        Icons.Outlined.EmojiEmotions, 
                        "Эмодзи", 
                        tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onAttachClick) {
                    Icon(Icons.Default.AttachFile, "Прикрепить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                TextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            when {
                                isChannel -> "Сообщение в канал..."
                                !recipientName.isNullOrEmpty() -> "Написать $recipientName..."
                                else -> "Сообщение"
                            }
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                if (messageText.isNotBlank()) {
                    FilledIconButton(onClick = onSend) {
                        Icon(Icons.Default.Send, "Отправить")
                    }
                } else {
                    // Видеокружок
                    IconButton(onClick = onVideoNoteClick) {
                        Icon(Icons.Outlined.Circle, "Видеокружок", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    // Голосовое (удержание)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isHoldingVoice) MaterialTheme.colorScheme.error else Color.Transparent)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHoldingVoice = true
                                        onVoiceHold()
                                        tryAwaitRelease()
                                        isHoldingVoice = false
                                        onVoiceRelease()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            "Голосовое",
                            tint = if (isHoldingVoice) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingBar(
    isVideo: Boolean,
    duration: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "alpha"
    )
    
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Красная точка
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Icon(
                if (isVideo) Icons.Default.Videocam else Icons.Default.Mic,
                null,
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Таймер
            Text(
                text = String.format("%d:%02d", duration / 60, duration % 60),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                if (isVideo) "Видеокружок" else "Голосовое",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Отмена
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Delete, "Отмена", tint = MaterialTheme.colorScheme.error)
            }
            
            // Отправить
            FilledIconButton(
                onClick = onSend,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Send, "Отправить")
            }
        }
    }
}

@Composable
fun VideoNoteRecordDialog(
    onDismiss: () -> Unit,
    onRecord: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Видеокружок",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Превью камеры (заглушка)
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Нажмите для записи круглого видео до 60 секунд",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Button(onClick = onRecord) {
                        Icon(Icons.Default.FiberManualRecord, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Записать")
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    onDismiss: () -> Unit,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    onFileClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Прикрепить", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachmentOption(Icons.Default.Photo, "Фото", Color(0xFF2196F3), onPhotoClick)
                AttachmentOption(Icons.Default.Videocam, "Видео", Color(0xFFE91E63), onVideoClick)
                AttachmentOption(Icons.Default.InsertDriveFile, "Файл", Color(0xFF9C27B0), onFileClick)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachmentOption(Icons.Default.LocationOn, "Геопозиция", Color(0xFF4CAF50), onLocationClick)
                AttachmentOption(Icons.Default.Person, "Контакт", Color(0xFFFF9800), onContactClick)
                Box(modifier = Modifier.size(72.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = color) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageUiModel,
    isOwnMessage: Boolean,
    isChannel: Boolean = false,
    onPlayVoice: () -> Unit = {},
    onPlayVideo: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = android.view.HapticFeedbackConstants.LONG_PRESS
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    // Обычный клик - ничего не делаем для текстовых сообщений
                },
                onLongClick = {
                    // Вибрация при долгом нажатии
                    (context as? android.app.Activity)?.window?.decorView?.performHapticFeedback(haptic)
                    onLongClick()
                }
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOwnMessage && !isChannel) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwnMessage && !isChannel) {
            Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) {
                    Text(message.senderName.take(1).uppercase(), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        when (message.type) {
            "VOICE" -> VoiceMessageBubble(message, isOwnMessage, onPlayVoice, onLongClick)
            "VIDEO_NOTE" -> VideoNoteBubble(message, isOwnMessage, onPlayVideo, onLongClick)
            "IMAGE", "PHOTO" -> PhotoMessageBubble(message, isOwnMessage, isChannel, onLongClick)
            "VIDEO" -> VideoMessageBubble(message, isOwnMessage, onLongClick)
            "FILE" -> FileMessageBubble(message, isOwnMessage, onLongClick)
            else -> TextMessageBubble(message, isOwnMessage, isChannel, onLongClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextMessageBubble(message: MessageUiModel, isOwnMessage: Boolean, isChannel: Boolean, onLongClick: () -> Unit = {}) {
    val bubbleColor = when {
        isChannel -> MaterialTheme.colorScheme.surfaceVariant
        isOwnMessage -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Column(
        modifier = Modifier.combinedClickable(
            onClick = { },
            onLongClick = onLongClick
        )
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isOwnMessage && !isChannel) 16.dp else 4.dp,
                bottomEnd = if (isOwnMessage && !isChannel) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = if (isChannel) 400.dp else 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isOwnMessage && !isChannel) {
                    Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwnMessage && !isChannel) Color.White else MaterialTheme.colorScheme.onSurface
                )
                
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (isChannel) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("${message.viewCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOwnMessage && !isChannel) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOwnMessage && !isChannel) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.status == "READ") Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        // Отображение реакций
        if (message.reactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                message.reactions.forEach { reaction ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (reaction.hasReacted) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(reaction.emoji, fontSize = 12.sp)
                            if (reaction.count > 1) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "${reaction.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceMessageBubble(message: MessageUiModel, isOwnMessage: Boolean, onPlay: () -> Unit, onLongClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOwnMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongClick
            )
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(
                    if (isOwnMessage) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            ) {
                Icon(
                    if (message.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Волна
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(20) { i ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((8 + (i * 7) % 16).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isOwnMessage) Color.White.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        message.duration ?: "0:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOwnMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMessageTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOwnMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isOwnMessage) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoNoteBubble(message: MessageUiModel, isOwnMessage: Boolean, onPlay: () -> Unit, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    
    // Определяем источник видео - локальный файл или URL
    val videoSource = remember(message.localFilePath, message.fileUrl, message.content) {
        when {
            !message.localFilePath.isNullOrEmpty() && java.io.File(message.localFilePath).exists() -> message.localFilePath
            !message.fileUrl.isNullOrEmpty() -> com.pioneer.messenger.data.network.ApiClient.getFileUrl(message.fileUrl)
            !message.content.isNullOrEmpty() && message.content.startsWith("/uploads") -> 
                com.pioneer.messenger.data.network.ApiClient.getFileUrl(message.content)
            else -> null
        }
    }
    
    Column(
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
        modifier = Modifier.combinedClickable(
            onClick = onPlay,
            onLongClick = onLongClick
        )
    ) {
        // Используем VideoNoteBubbleExpandable для воспроизведения
        VideoNoteBubbleExpandable(
            filePath = videoSource,
            duration = message.duration,
            durationMs = message.durationMs ?: 0,
            isOwn = isOwnMessage
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message.duration ?: "0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(formatMessageTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isOwnMessage) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = if (message.status == "READ") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoMessageBubble(message: MessageUiModel, isOwnMessage: Boolean, isChannel: Boolean, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    var showFullScreen by remember { mutableStateOf(false) }
    
    // Определяем URL изображения
    val imageUrl = remember(message.fileUrl, message.content, message.localFilePath) {
        when {
            !message.localFilePath.isNullOrEmpty() && java.io.File(message.localFilePath).exists() -> 
                message.localFilePath
            !message.fileUrl.isNullOrEmpty() -> 
                com.pioneer.messenger.data.network.ApiClient.getFileUrl(message.fileUrl)
            message.content.startsWith("/uploads") -> 
                com.pioneer.messenger.data.network.ApiClient.getFileUrl(message.content)
            message.content.startsWith("http") -> 
                message.content
            else -> null
        }
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp), 
        modifier = Modifier
            .widthIn(max = if (isChannel) 400.dp else 250.dp)
            .combinedClickable(
                onClick = { if (imageUrl != null) showFullScreen = true },
                onLongClick = onLongClick
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Фото",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.BrokenImage, 
                                        null, 
                                        modifier = Modifier.size(48.dp), 
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Не удалось загрузить",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                } else {
                    Icon(
                        Icons.Default.Photo, 
                        null, 
                        modifier = Modifier.size(48.dp), 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Время и статус
            Surface(
                color = if (isOwnMessage && !isChannel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOwnMessage && !isChannel) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOwnMessage && !isChannel) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.status == "READ") Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
    
    // Полноэкранный просмотр
    if (showFullScreen && imageUrl != null) {
        Dialog(onDismissRequest = { showFullScreen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                coil.compose.SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Фото",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFullScreen = false },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Не удалось загрузить изображение",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                )
                
                // Кнопка закрытия
                IconButton(
                    onClick = { showFullScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Закрыть",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoMessageBubble(message: MessageUiModel, isOwnMessage: Boolean, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    
    // Определяем URL видео
    val videoUrl = remember(message.fileUrl, message.content, message.localFilePath) {
        when {
            !message.localFilePath.isNullOrEmpty() && java.io.File(message.localFilePath).exists() -> 
                message.localFilePath
            !message.fileUrl.isNullOrEmpty() -> 
                com.pioneer.messenger.data.network.ApiClient.getFileUrl(message.fileUrl)
            message.content.startsWith("/uploads") -> 
                com.pioneer.messenger.data.network.ApiClient.getFileUrl(message.content)
            message.content.startsWith("http") -> 
                message.content
            else -> null
        }
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOwnMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .combinedClickable(
                onClick = { 
                    // Открываем видео во внешнем плеере
                    videoUrl?.let { url ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                if (url.startsWith("http")) {
                                    setDataAndType(android.net.Uri.parse(url), "video/*")
                                } else {
                                    setDataAndType(
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            java.io.File(url)
                                        ),
                                        "video/*"
                                    )
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoMessageBubble", "Failed to open video: ${e.message}")
                        }
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwnMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isOwnMessage) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileMessageBubble(message: MessageUiModel, isOwnMessage: Boolean, onLongClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOwnMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOwnMessage) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    message.fileName ?: "Файл",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    message.fileSize ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwnMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class MessageUiModel(
    val id: String,
    val content: String,
    val senderName: String,
    val timestamp: Long,
    val isOwn: Boolean,
    val status: String,
    val type: String = "TEXT",
    val duration: String? = null,
    val durationMs: Long? = null, // Длительность в миллисекундах
    val fileName: String? = null,
    val fileSize: String? = null,
    val isPlaying: Boolean = false,
    val viewCount: Int = 0,
    val localFilePath: String? = null,
    val fileUrl: String? = null, // URL файла на сервере
    val reactions: List<ReactionUiModel> = emptyList()
)

data class ReactionUiModel(
    val emoji: String,
    val count: Int,
    val hasReacted: Boolean
)

data class ChatInfoUiModel(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val isSecret: Boolean = false,
    val isChannel: Boolean = false,
    val autoDeleteDays: Int? = null,
    val subscriberCount: Int = 0,
    val isVerified: Boolean = false,
    val emojiStatus: String? = null,
    val lastSeen: String? = null,
    val avatarUrl: String? = null,
    val otherUserId: String? = null,
    val createdAt: Long = 0, // Дата регистрации
    val showUserCard: Boolean = false // Показывать карточку при первом сообщении
)

data class RecordingState(
    val isRecording: Boolean = false,
    val isVideo: Boolean = false,
    val duration: Int = 0
)

@Composable
fun DeleteMessageDialog(
    message: MessageUiModel,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onReact: (String) -> Unit = {}
) {
    // Популярные эмодзи для реакций
    val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "🎉")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Действия", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                // Быстрые реакции
                Text(
                    "Реакции",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    quickReactions.forEach { emoji ->
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { 
                                    onReact(emoji)
                                    onDismiss()
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider()
                
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
                                "Сообщение останется у собеседника",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Удалить для всех (только для своих сообщений)
                if (message.isOwn) {
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
                                    "Сообщение будет удалено у всех",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
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


// Карточка пользователя при первом сообщении
@Composable
fun UserInfoCard(
    userName: String,
    isVerified: Boolean,
    createdAt: Long,
    avatarUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale("ru")) }
    val registrationDate = remember(createdAt) {
        if (createdAt > 0) dateFormat.format(Date(createdAt)) else "Неизвестно"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Аватар
            Box(modifier = Modifier.size(64.dp)) {
                val fullAvatarUrl = avatarUrl?.let { 
                    com.pioneer.messenger.data.network.ApiClient.getAvatarUrl(it) 
                }
                
                if (!fullAvatarUrl.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = fullAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                userName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Имя с галочкой
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Верифицирован",
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Статус верификации
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isVerified) Color(0xFF4CAF50).copy(alpha = 0.15f) 
                       else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isVerified) Icons.Default.VerifiedUser else Icons.Outlined.Person,
                        contentDescription = null,
                        tint = if (isVerified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isVerified) "Верифицированный аккаунт" else "Обычный аккаунт",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isVerified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Дата регистрации
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Зарегистрирован: $registrationDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Кнопка закрытия
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    }
}


/**
 * Простой эмодзи пикер
 */
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val emojiCategories = remember {
        mapOf(
            "😀" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐"),
            "❤️" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️", "💋", "💌", "💐", "🌹", "🥀", "🌺", "🌸", "🌷", "🌻", "🌼"),
            "👍" to listOf("👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤙", "💪", "🦾", "🙏", "🤝", "👏", "🙌"),
            "🎉" to listOf("🎉", "🎊", "🎈", "🎁", "🎀", "🎄", "🎃", "🎗️", "🎟️", "🎫", "🏆", "🥇", "🥈", "🥉", "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎮", "🎲", "🎯", "🎳", "🎸", "🎹", "🎺", "🎻", "🎬", "🎤"),
            "🍕" to listOf("🍕", "🍔", "🍟", "🌭", "🍿", "🧂", "🥓", "🥚", "🍳", "🧇", "🥞", "🧈", "🍞", "🥐", "🥖", "🥨", "🧀", "🥗", "🥙", "🥪", "🌮", "🌯", "🫔", "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱"),
            "🐶" to listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴"),
            "🚗" to listOf("🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵", "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖", "✈️", "🛫", "🛬", "🚀", "🛸", "🚁", "⛵"),
            "⚡" to listOf("⚡", "🔥", "💥", "✨", "🌟", "⭐", "🌈", "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️", "☃️", "⛄", "🌬️", "💨", "🌪️", "🌫️", "🌊", "💧", "💦", "☔", "🌙", "🌛")
        )
    }
    
    var selectedCategory by remember { mutableStateOf("😀") }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            // Категории
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                emojiCategories.keys.forEach { category ->
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { selectedCategory = category },
                        color = if (selectedCategory == category) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                        else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(category, fontSize = 20.sp)
                        }
                    }
                }
            }
            
            Divider()
            
            // Эмодзи
            val emojis = emojiCategories[selectedCategory] ?: emptyList()
            
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(emojis.size) { index ->
                    val emoji = emojis[index]
                    Surface(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEmojiSelected(emoji) },
                        color = Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
