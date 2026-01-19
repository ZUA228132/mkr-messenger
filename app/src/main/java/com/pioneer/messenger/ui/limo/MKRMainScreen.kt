package com.pioneer.messenger.ui.limo

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pioneer.messenger.ui.components.liquidglass.LiquidGlassSurface
import com.pioneer.messenger.ui.reels.ReelsScreen
import com.pioneer.messenger.ui.theme.MKRColors

enum class MKRTab {
    HOME, REELS, CREATE, MAP, MESSAGES, PROFILE
}

@Composable
fun MKRMainScreen(
    onNavigateToProfile: (String) -> Unit,
    onNavigateToComments: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToMap: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(MKRTab.REELS) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MKRColors.BackgroundDark,
                        Color(0xFF0D0D1A)
                    )
                )
            )
    ) {
        // Контент в зависимости от выбранной вкладки
        when (selectedTab) {
            MKRTab.HOME -> HomeTab()
            MKRTab.REELS -> ReelsScreen(
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToComments = onNavigateToComments
            )
            MKRTab.CREATE -> CreateTab()
            MKRTab.MAP -> {
                // Переход на карту
                LaunchedEffect(Unit) {
                    onNavigateToMap()
                    selectedTab = MKRTab.REELS // Возвращаемся на Reels после перехода
                }
                MapPlaceholder()
            }
            MKRTab.MESSAGES -> MessagesTab(onNavigateToChat)
            MKRTab.PROFILE -> ProfileTab()
        }
        
        // Нижняя навигация с Liquid Glass эффектом
        MKRBottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        )
    }
}

@Composable
fun MKRBottomNavigation(
    selectedTab: MKRTab,
    onTabSelected: (MKRTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        blurRadius = 20.dp,
        surfaceColor = MKRColors.SurfaceDark.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(35.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MKRNavItem(
                icon = Icons.Filled.Home,
                label = "Главная",
                selected = selectedTab == MKRTab.HOME,
                onClick = { onTabSelected(MKRTab.HOME) }
            )
            
            MKRNavItem(
                icon = Icons.Filled.PlayArrow,
                label = "Reels",
                selected = selectedTab == MKRTab.REELS,
                onClick = { onTabSelected(MKRTab.REELS) }
            )
            
            // Центральная кнопка создания
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MKRColors.GradientStart,
                                MKRColors.GradientEnd
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { onTabSelected(MKRTab.CREATE) }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Создать",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            MKRNavItem(
                icon = Icons.Filled.Map,
                label = "Карта",
                selected = selectedTab == MKRTab.MAP,
                onClick = { onTabSelected(MKRTab.MAP) }
            )
            
            MKRNavItem(
                icon = Icons.Filled.ChatBubble,
                label = "Чаты",
                selected = selectedTab == MKRTab.MESSAGES,
                onClick = { onTabSelected(MKRTab.MESSAGES) }
            )
            
            MKRNavItem(
                icon = Icons.Filled.Person,
                label = "Профиль",
                selected = selectedTab == MKRTab.PROFILE,
                onClick = { onTabSelected(MKRTab.PROFILE) }
            )
        }
    }
}

@Composable
fun MKRNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MKRColors.Primary else MKRColors.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun HomeTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Главная лента",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun CreateTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Создать контент",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun MessagesTab(onNavigateToChat: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Сообщения",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun ProfileTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Профиль",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun MapPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MKRColors.Primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Загрузка карты...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
