package com.pioneer.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import com.pioneer.messenger.data.network.ApiClient
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// Кэшированный OkHttpClient для Coil
private val trustAllOkHttpClient: OkHttpClient by lazy {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, SecureRandom())
    
    OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/**
 * Универсальный компонент аватара с поддержкой загрузки изображений
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 48.dp,
    backgroundColor: Color? = null,
    textColor: Color = Color.White,
    showOnlineIndicator: Boolean = false,
    isOnline: Boolean = false,
    userId: String? = null,
    showStatusIcon: Boolean = false,
    statusIcon: StatusIcon? = null
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    // Генерируем цвет на основе userId или имени
    val avatarColor = backgroundColor ?: run {
        val seed = userId ?: name
        StatusColors.getColor(seed, isDarkTheme)
    }
    
    // Кэшированный ImageLoader с поддержкой самоподписанных сертификатов
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .okHttpClient(trustAllOkHttpClient)
            .crossfade(true)
            .build()
    }
    
    Box(modifier = modifier) {
        // Полный URL аватарки
        val fullImageUrl = remember(imageUrl) {
            when {
                imageUrl.isNullOrEmpty() -> null
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> imageUrl
                imageUrl.startsWith("/uploads/") -> "${ApiClient.BASE_URL}$imageUrl"
                imageUrl.startsWith("/") -> "${ApiClient.BASE_URL}$imageUrl"
                else -> "${ApiClient.BASE_URL}/uploads/$imageUrl"
            }
        }
        
        android.util.Log.d("Avatar", "Avatar URL: original='$imageUrl', full='$fullImageUrl'")
        
        if (!fullImageUrl.isNullOrEmpty()) {
            // Загружаем изображение с fallback
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fullImageUrl)
                    .crossfade(true)
                    .memoryCacheKey(fullImageUrl)
                    .diskCacheKey(fullImageUrl)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Аватар $name",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    // Показываем инициалы пока загружается
                    AvatarPlaceholder(name, size, avatarColor, textColor)
                },
                error = {
                    android.util.Log.e("Avatar", "Failed to load: $fullImageUrl")
                    // Показываем инициалы если ошибка загрузки
                    AvatarPlaceholder(name, size, avatarColor, textColor)
                }
            )
        } else {
            // Показываем инициалы
            AvatarPlaceholder(name, size, avatarColor, textColor)
        }
        
        // Индикатор онлайн
        if (showOnlineIndicator && isOnline) {
            Surface(
                modifier = Modifier
                    .size(size / 4)
                    .align(Alignment.BottomEnd),
                shape = CircleShape,
                color = Color(0xFF4CAF50)
            ) { }
        }
        
        // Статус-иконка
        if (showStatusIcon) {
            val icon = statusIcon ?: userId?.let { getStatusIconForUser(it) } ?: StatusIcon.STAR
            val iconColor = StatusColors.getColor(userId ?: name, isDarkTheme)
            
            StatusIconView(
                icon = icon,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
                size = size / 3,
                color = iconColor,
                backgroundColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun AvatarPlaceholder(
    name: String,
    size: Dp,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = getInitials(name),
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.5).sp
            )
        }
    }
}

/**
 * Аватар для чата с возможностью показа иконки для каналов/групп
 */
@Composable
fun ChatAvatar(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
    isChannel: Boolean = false,
    isSecret: Boolean = false,
    isOnline: Boolean = false,
    userId: String? = null,
    showStatusIcon: Boolean = false
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    val backgroundColor = when {
        isSecret -> Color(0xFF4CAF50)
        isChannel -> Color(0xFF2196F3)
        isGroup -> Color(0xFF9C27B0)
        else -> StatusColors.getColor(userId ?: name, isDarkTheme)
    }
    
    Avatar(
        name = name,
        modifier = modifier,
        imageUrl = imageUrl,
        size = size,
        backgroundColor = backgroundColor,
        showOnlineIndicator = !isGroup && !isChannel,
        isOnline = isOnline,
        userId = userId,
        showStatusIcon = showStatusIcon && !isGroup && !isChannel
    )
}

/**
 * Получить инициалы из имени
 */
private fun getInitials(name: String): String {
    if (name.isBlank()) return "?"
    
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> words.take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    }
}

/**
 * Генерация цвета на основе имени (для разнообразия)
 */
fun generateAvatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF3F51B5), // Indigo
        Color(0xFF009688), // Teal
        Color(0xFF795548)  // Brown
    )
    
    val hash = name.hashCode()
    return colors[kotlin.math.abs(hash) % colors.size]
}

// Extension для проверки яркости цвета
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
