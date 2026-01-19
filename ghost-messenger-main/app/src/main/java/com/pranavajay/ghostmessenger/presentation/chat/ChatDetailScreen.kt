package com.pranavajay.ghostmessenger.presentation.chat

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pranavajay.ghostmessenger.R
import com.pranavajay.ghostmessenger.data.models.Message
import com.pranavajay.ghostmessenger.presentation.components.LoadingIndicator
import com.pranavajay.ghostmessenger.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    navController: NavController,
    chatId: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val state by viewModel.chatDetailState.collectAsState()
    val listState = rememberLazyListState()
    
    // UI States
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showGhostModeDialog by remember { mutableStateOf(false) }
    
    val receiverId = state.chat?.userId ?: "user1"
    val receiverName = state.chat?.username ?: "User"
    val isOnline = state.chat?.isOnline ?: false
    val isGhostMode = state.chat?.disappearingMode ?: false
    
    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
        viewModel.loadChatDetails(chatId)
    }
    
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    // Voice recording timer
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingDuration = 0
            while (isRecordingVoice) {
                delay(1000)
                recordingDuration++
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                receiverName.take(2).uppercase(),
                                color = White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(receiverName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (isGhostMode) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.ic_ghost),
                                        contentDescription = "Ghost Mode",
                                        modifier = Modifier.size(16.dp),
                                        tint = CyanAccent
                                    )
                                }
                            }
                            Text(
                                when {
                                    state.isTyping -> "typing..."
                                    isOnline -> "Online"
                                    else -> "Offline"
                                },
                                fontSize = 12.sp,
                                color = when {
                                    state.isTyping -> CyanAccent
                                    isOnline -> OnlineGreen
                                    else -> LightGrey
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.initiateCall(receiverId, "VOICE") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_call),
                            contentDescription = "Voice Call",
                            tint = White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.initiateCall(receiverId, "VIDEO") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_video_call),
                            contentDescription = "Video Call",
                            tint = White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = White)
                    }
                    
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isGhostMode) "Disable Ghost Mode" else "Enable Ghost Mode") },
                            onClick = {
                                showMoreMenu = false
                                showGhostModeDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ghost),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Search in Chat") },
                            onClick = { showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat") },
                            onClick = { showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGrey,
                    titleContentColor = White
                )
            )
        },
        containerColor = DarkBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> LoadingIndicator()
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        reverseLayout = true
                    ) {
                        if (state.isTyping) {
                            item { TypingIndicatorBubble() }
                        }
                        items(state.messages.reversed()) { message ->
                            val isSent = message.senderId != receiverId
                            SwipeableMessageBubble(
                                message = message,
                                isSent = isSent,
                                onSwipeToReply = { replyToMessage = message },
                                onLongPress = {
                                    selectedMessageId = message.id
                                    showReactionPicker = true
                                },
                                onReactionClick = { emoji ->
                                    viewModel.addReaction(message.id, emoji)
                                }
                            )
                        }
                    }
                }
            }
            
            // Reply preview
            AnimatedVisibility(visible = replyToMessage != null) {
                replyToMessage?.let { reply ->
                    ReplyPreview(
                        message = reply,
                        onDismiss = { replyToMessage = null }
                    )
                }
            }
            
            // Reaction picker
            AnimatedVisibility(visible = showReactionPicker) {
                ReactionPicker(
                    onReactionSelected = { emoji ->
                        selectedMessageId?.let { viewModel.addReaction(it, emoji) }
                        showReactionPicker = false
                        selectedMessageId = null
                    },
                    onDismiss = {
                        showReactionPicker = false
                        selectedMessageId = null
                    }
                )
            }
            
            // Voice recording UI
            if (isRecordingVoice) {
                VoiceRecordingBar(
                    duration = recordingDuration,
                    onCancel = { isRecordingVoice = false },
                    onSend = {
                        isRecordingVoice = false
                        // TODO: Send voice message
                    }
                )
            } else {
                // Message input
                MessageInputBar(
                    messageText = messageText,
                    onMessageChange = {
                        messageText = it
                        viewModel.sendTyping(chatId, it.isNotEmpty())
                    },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(
                                chatId = chatId,
                                receiverId = receiverId,
                                content = messageText,
                                replyToId = replyToMessage?.id
                            )
                            messageText = ""
                            replyToMessage = null
                            viewModel.sendTyping(chatId, false)
                        }
                    },
                    onAttachClick = { showAttachmentMenu = true },
                    onMicPress = { isRecordingVoice = true },
                    onMicRelease = { isRecordingVoice = false }
                )
            }
            
            // Attachment menu
            AnimatedVisibility(visible = showAttachmentMenu) {
                AttachmentMenu(
                    onDismiss = { showAttachmentMenu = false },
                    onImageClick = { /* TODO */ },
                    onCameraClick = { /* TODO */ },
                    onDocumentClick = { /* TODO */ },
                    onLocationClick = { /* TODO */ },
                    onContactClick = { /* TODO */ }
                )
            }
        }
    }
    
    // Ghost Mode Dialog
    if (showGhostModeDialog) {
        GhostModeDialog(
            isEnabled = isGhostMode,
            onDismiss = { showGhostModeDialog = false },
            onConfirm = { duration ->
                viewModel.setGhostMode(chatId, duration)
                showGhostModeDialog = false
            }
        )
    }
}

@Composable
fun ReplyPreview(message: Message, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkGrey)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(ElectricBlue, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Reply to", fontSize = 12.sp, color = ElectricBlue)
            Text(
                message.content,
                fontSize = 14.sp,
                color = LightGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = LightGrey)
        }
    }
}

@Composable
fun ReactionPicker(onReactionSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val reactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "👎", "🔥")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGrey),
        shape = RoundedCornerShape(24.dp)
    ) {
        LazyRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items(reactions) { emoji ->
                TextButton(onClick = { onReactionSelected(emoji) }) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableMessageBubble(
    message: Message,
    isSent: Boolean,
    onSwipeToReply: () -> Unit,
    onLongPress: () -> Unit,
    onReactionClick: (String) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 100) onSwipeToReply()
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(0f, 150f)
                    }
                )
            }
    ) {
        // Reply indicator
        if (offsetX > 50) {
            Icon(
                Icons.Default.Reply,
                contentDescription = "Reply",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                tint = ElectricBlue
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = offsetX.dp)
                .combinedClickable(
                    onClick = { },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSent) ElectricBlue else DarkGrey
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSent) 16.dp else 4.dp,
                    bottomEnd = if (isSent) 4.dp else 16.dp
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Reply reference
                    message.replyToId?.let {
                        Box(
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                "Replied message",
                                fontSize = 12.sp,
                                color = White.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Text(
                        message.content,
                        color = White,
                        fontSize = 15.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            formatMessageTime(message.timestamp),
                            fontSize = 11.sp,
                            color = White.copy(alpha = 0.7f)
                        )
                        if (isSent) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.isRead) CyanAccent else White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkGrey)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachClick) {
            Icon(
                painter = painterResource(R.drawable.ic_attachment),
                contentDescription = "Attach",
                tint = CyanAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...", color = LightGrey) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = MediumGrey,
                focusedTextColor = White,
                unfocusedTextColor = White
            ),
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                IconButton(onClick = { }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_emoji),
                        contentDescription = "Emoji",
                        tint = LightGrey,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (messageText.isEmpty()) {
            IconButton(
                onClick = onMicPress,
                modifier = Modifier
                    .size(48.dp)
                    .background(ElectricBlue, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = "Voice",
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            IconButton(
                onClick = onSendClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(ElectricBlue, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = "Send",
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceRecordingBar(duration: Int, onCancel: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkGrey)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = ErrorRed)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Recording indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(ErrorRed, CircleShape)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            String.format("%02d:%02d", duration / 60, duration % 60),
            color = White,
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text("Slide to cancel", color = LightGrey, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.width(16.dp))
        
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(48.dp)
                .background(ElectricBlue, CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Send",
                tint = White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AttachmentMenu(
    onDismiss: () -> Unit,
    onImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGrey),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachmentOption(
                icon = R.drawable.ic_image,
                label = "Gallery",
                color = Color(0xFF4CAF50),
                onClick = { onImageClick(); onDismiss() }
            )
            AttachmentOption(
                icon = R.drawable.ic_camera,
                label = "Camera",
                color = Color(0xFFE91E63),
                onClick = { onCameraClick(); onDismiss() }
            )
            AttachmentOption(
                icon = R.drawable.ic_document,
                label = "Document",
                color = Color(0xFF9C27B0),
                onClick = { onDocumentClick(); onDismiss() }
            )
            AttachmentOption(
                icon = R.drawable.ic_location,
                label = "Location",
                color = Color(0xFF2196F3),
                onClick = { onLocationClick(); onDismiss() }
            )
            AttachmentOption(
                icon = R.drawable.ic_contact,
                label = "Contact",
                color = Color(0xFFFF9800),
                onClick = { onContactClick(); onDismiss() }
            )
        }
    }
}

@Composable
fun AttachmentOption(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(color, CircleShape)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = LightGrey)
    }
}

@Composable
fun TypingIndicatorBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    var alpha by remember { mutableStateOf(0.3f) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(index * 200L)
                            alpha = 1f
                            delay(300)
                            alpha = 0.3f
                            delay(600)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(LightGrey.copy(alpha = alpha), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun GhostModeDialog(
    isEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    var selectedDuration by remember { mutableStateOf(if (isEnabled) null else 24 * 60 * 60 * 1000L) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_ghost),
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ghost Mode", color = White)
            }
        },
        text = {
            Column {
                Text(
                    if (isEnabled) "Disable disappearing messages?" 
                    else "Messages will disappear after the selected time",
                    color = LightGrey
                )
                if (!isEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    listOf(
                        "5 minutes" to 5 * 60 * 1000L,
                        "1 hour" to 60 * 60 * 1000L,
                        "24 hours" to 24 * 60 * 60 * 1000L,
                        "7 days" to 7 * 24 * 60 * 60 * 1000L
                    ).forEach { (label, duration) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDuration == duration,
                                onClick = { selectedDuration = duration },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ElectricBlue
                                )
                            )
                            Text(label, color = White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (isEnabled) null else selectedDuration) }) {
                Text(if (isEnabled) "Disable" else "Enable", color = ElectricBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LightGrey)
            }
        },
        containerColor = DarkGrey
    )
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}
