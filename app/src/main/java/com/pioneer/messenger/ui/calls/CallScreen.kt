package com.pioneer.messenger.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pioneer.messenger.ui.utils.SecureScreen
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    callId: String,
    isVideo: Boolean = false,
    isIncoming: Boolean = false,
    callerName: String = "Пользователь",
    callerAvatarUrl: String? = null,
    isSecretChat: Boolean = false,
    onBack: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    
    // Video tracks
    val localVideoTrack by viewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    
    // Данные о собеседнике
    val realCallerName by viewModel.callerName.collectAsState()
    val realCallerAvatarUrl by viewModel.callerAvatarUrl.collectAsState()
    
    val displayName = if (realCallerName != "Пользователь") realCallerName else callerName
    val displayAvatarUrl = realCallerAvatarUrl ?: callerAvatarUrl
    
    val context = LocalContext.current
    
    // Запрос разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            if (isIncoming) {
                viewModel.acceptCall(callId, isVideo)
            } else {
                viewModel.startCall(callId, isVideo)
            }
        }
    }
    
    LaunchedEffect(callId) {
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) requiredPermissions.add(Manifest.permission.CAMERA)
        
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasPermissions) {
            if (isIncoming) {
                viewModel.acceptCall(callId, isVideo)
            } else {
                viewModel.startCall(callId, isVideo)
            }
        } else {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }
    
    // Автозакрытие при завершении
    LaunchedEffect(callState) {
        if (callState is CallUiState.Ended) {
            delay(1500)
            onBack()
        }
    }
    
    SecureScreen(enabled = isSecretChat) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D1B2A),
                            Color(0xFF1B263B),
                            Color(0xFF415A77)
                        )
                    )
                )
        ) {
            when (val state = callState) {
                is CallUiState.Idle, is CallUiState.Calling -> {
                    CallingContent(
                        callerName = displayName,
                        callerAvatarUrl = displayAvatarUrl,
                        isVideo = isVideo,
                        onCancel = { viewModel.endCall(); onBack() }
                    )
                }
                
                is CallUiState.Incoming -> {
                    IncomingCallContent(
                        callerName = displayName,
                        callerAvatarUrl = displayAvatarUrl,
                        isVideo = isVideo,
                        onAccept = { viewModel.acceptCall(callId, isVideo) },
                        onDecline = { viewModel.endCall(); onBack() }
                    )
                }
                
                is CallUiState.Connected -> {
                    ConnectedCallContent(
                        callerName = displayName,
                        callerAvatarUrl = displayAvatarUrl,
                        duration = callDuration,
                        isVideo = isVideo,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        isVideoEnabled = isVideoEnabled,
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onToggleVideo = { viewModel.toggleVideo() },
                        onSwitchCamera = { viewModel.switchCamera() },
                        onEndCall = { viewModel.endCall(); onBack() },
                        localVideoTrack = localVideoTrack,
                        remoteVideoTrack = remoteVideoTrack
                    )
                }
                
                is CallUiState.Ended -> {
                    EndedCallContent(reason = state.reason)
                }
            }
        }
    }
}

@Composable
private fun CallingContent(
    callerName: String,
    callerAvatarUrl: String?,
    isVideo: Boolean,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Аватар с пульсацией
            Box(contentAlignment = Alignment.Center) {
                // Пульсирующие круги
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale * 0.95f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                )
                
                // Аватар
                AvatarImage(
                    url = callerAvatarUrl,
                    name = callerName,
                    size = 120
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                callerName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dots"
                )
                
                Text(
                    if (isVideo) "Видеозвонок" else "Вызов",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    " • • •",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = dotAlpha)
                )
            }
        }
        
        // Кнопка отмены
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(
                onClick = onCancel,
                containerColor = Color(0xFFE53935),
                modifier = Modifier.size(72.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = "Завершить",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Отменить",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun IncomingCallContent(
    callerName: String,
    callerAvatarUrl: String?,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "incoming")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isVideo) "Видеозвонок" else "Входящий звонок",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                )
                
                AvatarImage(
                    url = callerAvatarUrl,
                    name = callerName,
                    size = 120
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                callerName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        
        // Кнопки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onDecline,
                    containerColor = Color(0xFFE53935),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Отклонить", color = Color.White.copy(alpha = 0.7f))
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = Color(0xFF4CAF50),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        null, tint = Color.White, modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Принять", color = Color.White.copy(alpha = 0.7f))
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}


@Composable
private fun ConnectedCallContent(
    callerName: String,
    callerAvatarUrl: String?,
    duration: Int,
    isVideo: Boolean,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    localVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    remoteVideoTrack: io.livekit.android.room.track.VideoTrack? = null
) {
    val minutes = duration / 60
    val seconds = duration % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Видео собеседника (на весь экран)
        if (isVideo && remoteVideoTrack != null) {
            AndroidView(
                factory = { context ->
                    io.livekit.android.renderer.TextureViewRenderer(context).apply {
                        remoteVideoTrack.addRenderer(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { renderer ->
                    remoteVideoTrack.removeRenderer(renderer)
                }
            )
        }
        
        // Локальное видео (маленькое окно в углу)
        if (isVideo && isVideoEnabled && localVideoTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { context ->
                        io.livekit.android.renderer.TextureViewRenderer(context).apply {
                            localVideoTrack.addRenderer(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { renderer ->
                        localVideoTrack.removeRenderer(renderer)
                    }
                )
            }
        }
        
        // Основной контент (поверх видео)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            // Аватар (показываем только если нет видео собеседника)
            if (!isVideo || remoteVideoTrack == null) {
                AvatarImage(
                    url = callerAvatarUrl,
                    name = callerName,
                    size = if (isVideo && isVideoEnabled) 80 else 120
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Имя
                Text(
                    callerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Таймер
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        timeString,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF81C784)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Кнопки управления
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "Микрофон",
                            isActive = isMuted,
                            activeColor = Color(0xFFE53935),
                            onClick = onToggleMute
                        )
                        
                        if (isVideo) {
                            CallButton(
                                icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = "Видео",
                                isActive = !isVideoEnabled,
                                activeColor = Color(0xFFE53935),
                                onClick = onToggleVideo
                            )
                        } else {
                            CallButton(
                                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                label = "Динамик",
                                isActive = isSpeakerOn,
                                activeColor = Color(0xFF2196F3),
                                onClick = onToggleSpeaker
                            )
                        }
                        
                        if (isVideo) {
                            CallButton(
                                icon = Icons.Default.Cameraswitch,
                                label = "Камера",
                                isActive = false,
                                onClick = onSwitchCamera
                            )
                        } else {
                            Spacer(modifier = Modifier.size(72.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    FloatingActionButton(
                        onClick = onEndCall,
                        containerColor = Color(0xFFE53935),
                        modifier = Modifier.size(72.dp),
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Завершить",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.15f),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AvatarImage(
    url: String?,
    name: String,
    size: Int
) {
    if (!url.isNullOrEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = "Аватар",
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = Modifier.size(size.dp),
            shape = CircleShape,
            color = Color(0xFF5C6BC0)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = (size / 2.5).sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun EndedCallContent(reason: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CallEnd,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Звонок завершён",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        
        if (reason.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
