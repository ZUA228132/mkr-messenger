package com.pioneer.messenger.ui.stories

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.delay

data class StoryItem(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val mediaUrl: String,
    val mediaType: String,
    val text: String?,
    val timestamp: Long,
    val isViewed: Boolean
)

@Composable
fun StoryViewerScreen(
    stories: List<StoryItem>,
    initialIndex: Int = 0,
    onClose: () -> Unit,
    onStoryViewed: (String) -> Unit = {}
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    
    val currentStory = stories.getOrNull(currentIndex)
    
    // Автопрогресс
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused && currentStory != null) {
            progress = 0f
            onStoryViewed(currentStory.id)
            
            while (progress < 1f) {
                delay(50)
                progress += 0.01f
            }
            
            // Переход к следующей истории
            if (currentIndex < stories.size - 1) {
                currentIndex++
            } else {
                onClose()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 3) {
                            // Предыдущая история
                            if (currentIndex > 0) {
                                currentIndex--
                            }
                        } else if (offset.x > width * 2 / 3) {
                            // Следующая история
                            if (currentIndex < stories.size - 1) {
                                currentIndex++
                            } else {
                                onClose()
                            }
                        }
                    },
                    onLongPress = { isPaused = true },
                    onPress = {
                        tryAwaitRelease()
                        isPaused = false
                    }
                )
            }
    ) {
        // Контент истории
        currentStory?.let { story ->
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Текст на истории
            story.text?.let { text ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Градиент сверху
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )
        
        // Верхняя панель
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // Прогресс-бары
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { index, _ ->
                    val progressValue = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> progress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = progressValue,
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Информация о пользователе
            currentStory?.let { story ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Аватар
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MKRColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (story.userAvatar != null) {
                            AsyncImage(
                                model = story.userAvatar,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                story.userName.firstOrNull()?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            story.userName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            formatStoryTime(story.timestamp),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    
                    // Кнопка закрытия
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        // Нижняя панель с реакциями
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("❤️", "🔥", "😂", "😮", "😢", "👏").forEach { emoji ->
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { /* Отправить реакцию */ },
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

private fun formatStoryTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "только что"
        diff < 3600_000 -> "${diff / 60_000} мин. назад"
        diff < 86400_000 -> "${diff / 3600_000} ч. назад"
        else -> "Вчера"
    }
}
