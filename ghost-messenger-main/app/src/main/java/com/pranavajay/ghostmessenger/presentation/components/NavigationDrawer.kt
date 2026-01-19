package com.pranavajay.ghostmessenger.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranavajay.ghostmessenger.R
import com.pranavajay.ghostmessenger.ui.theme.*

@Composable
fun NavigationDrawer(
    userName: String = "User",
    userEmail: String = "",
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onChatClick: () -> Unit,
    onGroupClick: () -> Unit,
    onChannelClick: () -> Unit,
    onStatusClick: () -> Unit,
    onPostsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val colors = GhostTheme.colors

    ModalDrawerSheet(
        drawerContainerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ElectricBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.take(2).uppercase(),
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = userName,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Online", color = OnlineGreen, fontSize = 14.sp)
                }
            }

            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            DrawerMenuItem(Icons.Default.Chat, "Chats", colors.textSecondary, colors.textPrimary, onChatClick)
            DrawerMenuItem(Icons.Default.Group, "Groups", colors.textSecondary, colors.textPrimary, onGroupClick)
            DrawerMenuItem(Icons.Default.Campaign, "Channels", colors.textSecondary, colors.textPrimary, onChannelClick)
            DrawerMenuItem(Icons.Default.Circle, "Status", colors.textSecondary, colors.textPrimary, onStatusClick)
            DrawerMenuItem(Icons.Default.Article, "Posts", colors.textSecondary, colors.textPrimary, onPostsClick)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Theme Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleTheme)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(
                            id = if (isDarkMode) R.drawable.ic_moon else R.drawable.ic_sun
                        ),
                        contentDescription = "Theme",
                        tint = if (isDarkMode) Color(0xFFB0C4DE) else Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isDarkMode) "Dark Mode" else "Light Mode",
                        color = colors.textPrimary,
                        fontSize = 16.sp
                    )
                }
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ElectricBlue,
                        checkedTrackColor = ElectricBlue.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            DrawerMenuItem(Icons.Default.Settings, "Settings", colors.textSecondary, colors.textPrimary, onSettingsClick)
            DrawerMenuItem(Icons.Default.Logout, "Logout", ErrorRed, ErrorRed, onLogoutClick)
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconTint: Color = LightGrey,
    textColor: Color = White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = textColor, fontSize = 16.sp)
    }
}
