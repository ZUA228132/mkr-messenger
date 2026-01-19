package com.pioneer.messenger.ui.admin

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Расширенная панель администратора с дашбордом
 */
@Composable
fun AdminDashboardCard(
    totalUsers: Int,
    onlineUsers: Int,
    newUsersToday: Int,
    totalMessages: Int,
    bannedUsers: Int,
    verifiedUsers: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF667eea),
                                    Color(0xFF764ba2)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Dashboard,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Панель управления",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Статистика в реальном времени",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Статистика в сетке
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.People,
                    value = totalUsers.toString(),
                    label = "Всего",
                    color = Color(0xFF2196F3)
                )
                DashboardStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Circle,
                    value = onlineUsers.toString(),
                    label = "Онлайн",
                    color = Color(0xFF4CAF50)
                )
                DashboardStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.PersonAdd,
                    value = "+$newUsersToday",
                    label = "Сегодня",
                    color = Color(0xFF9C27B0)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Message,
                    value = formatNumber(totalMessages),
                    label = "Сообщений",
                    color = Color(0xFF00BCD4)
                )
                DashboardStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Verified,
                    value = verifiedUsers.toString(),
                    label = "Верифиц.",
                    color = Color(0xFF1DA1F2)
                )
                DashboardStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Block,
                    value = bannedUsers.toString(),
                    label = "Забанено",
                    color = Color(0xFFFF5252)
                )
            }
        }
    }
}

@Composable
private fun DashboardStatItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> "${num / 1_000_000}M"
        num >= 1_000 -> "${num / 1_000}K"
        else -> num.toString()
    }
}


/**
 * Быстрые действия администратора
 */
@Composable
fun AdminQuickActions(
    onGenerateKey: () -> Unit,
    onBroadcast: () -> Unit,
    onSecurityCheck: () -> Unit,
    onBackup: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Быстрые действия",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuickActionButton(
                        icon = Icons.Default.VpnKey,
                        label = "Ключ",
                        color = Color(0xFF4CAF50),
                        onClick = onGenerateKey
                    )
                }
                item {
                    QuickActionButton(
                        icon = Icons.Default.Campaign,
                        label = "Рассылка",
                        color = Color(0xFF2196F3),
                        onClick = onBroadcast
                    )
                }
                item {
                    QuickActionButton(
                        icon = Icons.Default.Security,
                        label = "Безопасность",
                        color = Color(0xFFFF9800),
                        onClick = onSecurityCheck
                    )
                }
                item {
                    QuickActionButton(
                        icon = Icons.Default.Backup,
                        label = "Бэкап",
                        color = Color(0xFF9C27B0),
                        onClick = onBackup
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = color.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Карточка последней активности
 */
@Composable
fun RecentActivityCard(
    activities: List<AdminActivity>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Последняя активность",
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { }) {
                    Text("Все")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            activities.take(5).forEach { activity ->
                ActivityItem(activity)
                if (activity != activities.last()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(activity: AdminActivity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(activity.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                activity.icon,
                null,
                tint = activity.color,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                activity.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                activity.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            activity.time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class AdminActivity(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val time: String,
    val color: Color
)

/**
 * Карточка системного статуса
 */
@Composable
fun SystemStatusCard(
    serverStatus: ServerStatus,
    dbStatus: ServerStatus,
    wsStatus: ServerStatus
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Статус системы",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            StatusRow("API Сервер", serverStatus)
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow("База данных", dbStatus)
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow("WebSocket", wsStatus)
        }
    }
}

@Composable
private fun StatusRow(name: String, status: ServerStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            ServerStatus.ONLINE -> Color(0xFF4CAF50)
                            ServerStatus.WARNING -> Color(0xFFFFC107)
                            ServerStatus.OFFLINE -> Color(0xFFFF5252)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when (status) {
                    ServerStatus.ONLINE -> "Онлайн"
                    ServerStatus.WARNING -> "Предупреждение"
                    ServerStatus.OFFLINE -> "Офлайн"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (status) {
                    ServerStatus.ONLINE -> Color(0xFF4CAF50)
                    ServerStatus.WARNING -> Color(0xFFFFC107)
                    ServerStatus.OFFLINE -> Color(0xFFFF5252)
                }
            )
        }
    }
}

enum class ServerStatus {
    ONLINE, WARNING, OFFLINE
}

/**
 * Диалог массовой рассылки
 */
@Composable
fun BroadcastDialog(
    onDismiss: () -> Unit,
    onSend: (String, BroadcastTarget) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(BroadcastTarget.ALL) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Campaign,
                null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(48.dp)
            )
        },
        title = { 
            Text("Массовая рассылка", fontWeight = FontWeight.SemiBold) 
        },
        text = {
            Column {
                Text(
                    "Кому отправить:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                BroadcastTarget.entries.forEach { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = t }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = target == t,
                            onClick = { target = t }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (t) {
                                BroadcastTarget.ALL -> "Всем пользователям"
                                BroadcastTarget.VERIFIED -> "Только верифицированным"
                                BroadcastTarget.ADMINS -> "Только администраторам"
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Сообщение") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(message, target) },
                enabled = message.isNotBlank()
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

enum class BroadcastTarget {
    ALL, VERIFIED, ADMINS
}
