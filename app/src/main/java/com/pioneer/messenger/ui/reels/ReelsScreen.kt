package com.pioneer.messenger.ui.reels

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.pioneer.messenger.ui.components.liquidglass.LiquidGlassSurface
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.launch

data class Reel(
    val id: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val description: String,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val musicName: String = "",
    val musicAuthor: String = ""
)

@Composable
fun ReelsScreen(
    onNavigateToProfile: (String) -> Unit,
    onNavigateToComments: (String) -> Unit,
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val reels by viewModel.reels.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Вертикальный пейджер для видео
        ReelsVerticalPager(
            reels = reels,
            currentIndex = currentIndex,
            onIndexChanged = { viewModel.setCurrentIndex(it) },
            onLike = { viewModel.toggleLike(it) },
            onComment = { onNavigateToComments(it) },
            onShare = { viewModel.shareReel(it) },
            onSave = { viewModel.toggleSave(it) },
            onProfileClick = { onNavigateToProfile(it) }
        )
    }
}

@Composable
fun ReelsVerticalPager(
    reels: List<Reel>,
    currentIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onShare: (String) -> Unit,
    onSave: (String) -> Unit,
    onProfileClick: (String) -> Unit
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragOffset > 200 && currentIndex > 0) {
                                onIndexChanged(currentIndex - 1)
                            } else if (dragOffset < -200 && currentIndex < reels.size - 1) {
                                onIndexChanged(currentIndex + 1)
                            }
                            dragOffset = 0f
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                    }
                )
            }
    ) {
        if (reels.isNotEmpty() && currentIndex < reels.size) {
            ReelItem(
                reel = reels[currentIndex],
                onLike = { onLike(reels[currentIndex].id) },
                onComment = { onComment(reels[currentIndex].id) },
                onShare = { onShare(reels[currentIndex].id) },
                onSave = { onSave(reels[currentIndex].id) },
                onProfileClick = { onProfileClick(reels[currentIndex].authorId) }
            )
        }
    }
}

@Composable
fun ReelItem(
    reel: Reel,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(reel.isLiked) }
    var isSaved by remember { mutableStateOf(reel.isSaved) }
    var showHeart by remember { mutableStateOf(false) }
    
    val heartScale by animateFloatAsState(
        targetValue = if (showHeart) 1.5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heart"
    )
    
    // ExoPlayer для видео
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // Если есть videoUrl, загружаем видео
            if (reel.videoUrl.isNotEmpty()) {
                setMediaItem(MediaItem.fromUri(reel.videoUrl))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        isLiked = true
                        showHeart = true
                        onLike()
                    }
                )
            }
    ) {
        // Видео или изображение фон
        if (reel.videoUrl.isNotEmpty()) {
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
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Градиент снизу
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 800f
                    )
                )
        )
        
        // Анимация сердца при двойном тапе
        if (showHeart) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((100 * heartScale).dp),
                tint = Color.White
            )
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(800)
                showHeart = false
            }
        }
        
        // Правая панель с действиями
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Аватар автора
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = reel.authorAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Лайк
            ReelActionButton(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                count = reel.likes,
                tint = if (isLiked) MKRColors.Like else Color.White,
                onClick = {
                    isLiked = !isLiked
                    onLike()
                }
            )
            
            // Комментарии
            ReelActionButton(
                icon = Icons.Filled.ChatBubble,
                count = reel.comments,
                tint = Color.White,
                onClick = onComment
            )
            
            // Поделиться
            ReelActionButton(
                icon = Icons.Filled.Send,
                count = reel.shares,
                tint = Color.White,
                onClick = onShare
            )
            
            // Сохранить
            ReelActionButton(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                count = null,
                tint = if (isSaved) MKRColors.Save else Color.White,
                onClick = {
                    isSaved = !isSaved
                    onSave()
                }
            )
        }
        
        // Информация о видео внизу
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .padding(bottom = 60.dp)
        ) {
            // Имя автора
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "@${reel.authorName}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Кнопка подписаться
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MKRColors.Primary)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Подписаться",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Описание
            Text(
                text = reel.description,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2
            )
            
            if (reel.musicName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Музыка
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${reel.musicName} • ${reel.musicAuthor}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ReelActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Фон с эффектом стекла
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        if (count != null && count > 0) {
            Text(
                text = formatCount(count),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
