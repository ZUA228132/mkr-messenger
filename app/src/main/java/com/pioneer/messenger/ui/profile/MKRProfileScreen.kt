package com.pioneer.messenger.ui.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pioneer.messenger.ui.theme.MKRColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MKRProfileScreen(
    userId: String? = null,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    onStartChat: (String) -> Unit = {},
    onStartCall: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOwnProfile = userId == null
    val profile = uiState.profile
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MKRColors.Primary)
            }
        } else if (profile != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Верхняя панель
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            "Назад",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        "Профиль",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    if (isOwnProfile) {
                        IconButton(onClick = onEditProfile) {
                            Icon(
                                Icons.Outlined.Edit,
                                "Редактировать",
                                tint = MKRColors.Primary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Аватар
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Градиентное кольцо
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MKRColors.Primary,
                                        MKRColors.Secondary,
                                        MKRColors.Accent
                                    )
                                )
                            )
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Black)
                                .padding(3.dp)
                        ) {
                            if (profile.avatarUrl != null) {
                                AsyncImage(
                                    model = profile.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MKRColors.Primary,
                                                    MKRColors.Secondary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.name.firstOrNull()?.uppercase() ?: "?",
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Имя и username
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (profile.isVerified) {
                            Icon(
                                Icons.Filled.Verified,
                                null,
                                tint = MKRColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "@${profile.username}",
                        fontSize = 16.sp,
                        color = MKRColors.TextSecondary
                    )
                    
                    // Статус онлайн
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (profile.isOnline) Color(0xFF4CAF50) else Color.Gray)
                        )
                        Text(
                            text = if (profile.isOnline) "В сети" else "Был(а) недавно",
                            fontSize = 14.sp,
                            color = if (profile.isOnline) Color(0xFF4CAF50) else MKRColors.TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопки действий для чужого профиля
                if (!isOwnProfile) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileActionButton(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = "Написать",
                            modifier = Modifier.weight(1f),
                            isPrimary = true,
                            onClick = { onStartChat(profile.id) }
                        )
                        
                        ProfileActionButton(
                            icon = Icons.Outlined.Call,
                            label = "Позвонить",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartCall(profile.id, false) }
                        )
                        
                        ProfileActionButton(
                            icon = Icons.Outlined.Videocam,
                            label = "Видео",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartCall(profile.id, true) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // Информация
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Bio
                    if (profile.bio.isNotEmpty()) {
                        ProfileInfoSection(
                            title = "О себе",
                            content = profile.bio
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Контактная информация
                    ProfileInfoCard(
                        items = listOf(
                            ProfileInfoItem(
                                icon = Icons.Outlined.Phone,
                                title = "Телефон",
                                value = profile.phone.ifEmpty { "Не указан" }
                            ),
                            ProfileInfoItem(
                                icon = Icons.Outlined.Email,
                                title = "Email",
                                value = profile.email.ifEmpty { "Не указан" }
                            ),
                            ProfileInfoItem(
                                icon = Icons.Outlined.Cake,
                                title = "День рождения",
                                value = "Не указан"
                            )
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Настройки (только для своего профиля)
                    if (isOwnProfile) {
                        // Статистика или другая информация вместо настроек
                        ProfileInfoCard(
                            items = listOf(
                                ProfileInfoItem(
                                    icon = Icons.Outlined.DateRange,
                                    title = "В MKR с",
                                    value = "Января 2026"
                                )
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Кнопка выхода
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF331111))
                                .clickable { showLogoutDialog = true }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Logout,
                                    null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "Выйти из аккаунта",
                                    color = Color(0xFFFF5252),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        // Диалог подтверждения выхода
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Выйти из аккаунта?", fontWeight = FontWeight.SemiBold) },
                text = { Text("Вы уверены, что хотите выйти из аккаунта?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            viewModel.logout()
                            onLogout()
                        }
                    ) {
                        Text("Выйти", color = Color(0xFFFF5252))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Отмена")
                    }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }
    }
}

@Composable
fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPrimary) {
                    Brush.horizontalGradient(
                        colors = listOf(MKRColors.Primary, MKRColors.Secondary)
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ProfileInfoSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MKRColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

data class ProfileInfoItem(
    val icon: ImageVector,
    val title: String,
    val value: String
)

@Composable
fun ProfileInfoCard(items: List<ProfileInfoItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MKRColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        color = MKRColors.TextSecondary
                    )
                    Text(
                        text = item.value,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
            if (index < items.lastIndex) {
                Divider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}

data class ProfileMenuItem(
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit
)

@Composable
fun ProfileMenuCard(items: List<ProfileMenuItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MKRColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    null,
                    tint = MKRColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (index < items.lastIndex) {
                Divider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}
