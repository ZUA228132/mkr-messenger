package com.pioneer.messenger.ui.chat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Превью видеокружка в чате с возможностью расширения
 */
@Composable
fun VideoNoteBubbleExpandable(
    filePath: String?,
    duration: String?,
    durationMs: Long = 0,
    isOwn: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Анимация расширения
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // Создаём ExoPlayer для локальных файлов и URL
    val exoPlayer = remember(filePath) {
        if (filePath != null) {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = if (filePath.startsWith("http")) {
                    // URL с сервера
                    MediaItem.fromUri(Uri.parse(filePath))
                } else if (File(filePath).exists()) {
                    // Локальный файл
                    MediaItem.fromUri(Uri.fromFile(File(filePath)))
                } else {
                    null
                }
                
                mediaItem?.let {
                    setMediaItem(it)
                    prepare()
                    repeatMode = Player.REPEAT_MODE_OFF
                }
            }
        } else null
    }
    
    // Слушаем прогресс воспроизведения
    LaunchedEffect(exoPlayer, isPlaying) {
        if (exoPlayer != null && isPlaying) {
            while (isPlaying) {
                val totalDuration = exoPlayer.duration.coerceAtLeast(1L)
                val position = exoPlayer.currentPosition
                progress = (position.toFloat() / totalDuration).coerceIn(0f, 1f)
                
                if (position >= totalDuration - 100) {
                    isPlaying = false
                    isExpanded = false
                    exoPlayer.seekTo(0)
                    progress = 0f
                }
                
                kotlinx.coroutines.delay(50)
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Основной кружок
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .background(if (isOwn) Color(0xFF2196F3).copy(alpha = 0.3f) else Color(0xFF424242).copy(alpha = 0.3f))
                .clickable {
                    if (exoPlayer != null && filePath != null) {
                        if (isExpanded && isPlaying) {
                            // Пауза
                            exoPlayer.pause()
                            isPlaying = false
                        } else if (isExpanded && !isPlaying) {
                            // Закрыть
                            isExpanded = false
                            progress = 0f
                            exoPlayer.seekTo(0)
                        } else {
                            // Расширить и воспроизвести
                            isExpanded = true
                            isPlaying = true
                            exoPlayer.play()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (exoPlayer != null && isExpanded) {
                // Видео плеер
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Превью с иконкой
                if (filePath != null) {
                    Icon(
                        Icons.Default.PlayCircle,
                        "Воспроизвести",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.VideocamOff,
                        "Видео недоступно",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        // Круговой прогресс при воспроизведении
        if (isExpanded && isPlaying) {
            Canvas(
                modifier = Modifier
                    .size(160.dp * scale + 8.dp)
            ) {
                // Фоновый круг
                drawArc(
                    color = Color.White.copy(alpha = 0.3f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Прогресс
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        
        // Длительность (только когда не расширен)
        if (!isExpanded && duration != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 70.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = duration,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Старый диалог для совместимости (можно удалить позже)
 */
@Composable
fun VideoNotePlayerDialog(
    filePath: String,
    onDismiss: () -> Unit
) {
    // Используем новый компонент
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        VideoNoteBubbleExpandable(
            filePath = filePath,
            duration = null,
            isOwn = true
        )
        
        // Кнопка закрытия
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                "Закрыть",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Превью видеокружка в чате (старый вариант для совместимости)
 */
@Composable
fun VideoNoteBubble(
    filePath: String?,
    duration: String?,
    isOwn: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .background(if (isOwn) Color(0xFF2196F3).copy(alpha = 0.3f) else Color(0xFF424242).copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (filePath != null && File(filePath).exists()) {
            Icon(
                Icons.Default.PlayCircle,
                "Воспроизвести",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Icon(
                Icons.Default.Videocam,
                "Видео",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
        }
        
        duration?.let {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
