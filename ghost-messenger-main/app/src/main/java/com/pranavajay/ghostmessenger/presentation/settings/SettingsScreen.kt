package com.pranavajay.ghostmessenger.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pranavajay.ghostmessenger.R
import com.pranavajay.ghostmessenger.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.settingsState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Account Section
            item {
                SettingsSection("Account") {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Profile",
                        subtitle = "Edit your profile information",
                        onClick = { navController.navigate("profile") }
                    )
                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = "Privacy",
                        subtitle = "Last seen, profile photo, about",
                        onClick = { }
                    )
                }
            }
            
            // Security Section
            item {
                SettingsSection("Security") {
                    SettingsToggleItem(
                        icon = Icons.Default.Lock,
                        title = "App Lock",
                        subtitle = "Require PIN to open app",
                        checked = state.appLockEnabled,
                        onCheckedChange = { viewModel.setAppLockEnabled(it) }
                    )
                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric Unlock",
                        subtitle = "Use fingerprint to unlock",
                        checked = state.biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        enabled = state.appLockEnabled && state.biometricAvailable
                    )
                    SettingsItem(
                        icon = Icons.Default.Password,
                        title = "Change PIN",
                        subtitle = "Update your security PIN",
                        onClick = { },
                        enabled = state.appLockEnabled
                    )
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Two-Factor Authentication",
                        subtitle = "Add extra security to your account",
                        onClick = { }
                    )
                }
            }
            
            // Appearance Section
            item {
                SettingsSection("Appearance") {
                    SettingsToggleItem(
                        icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        checked = isDarkMode,
                        onCheckedChange = { onToggleTheme() }
                    )
                    SettingsItem(
                        icon = Icons.Default.Wallpaper,
                        title = "Chat Wallpaper",
                        subtitle = "Change chat background",
                        onClick = { }
                    )
                    SettingsItem(
                        icon = Icons.Default.TextFields,
                        title = "Font Size",
                        subtitle = "Adjust text size",
                        onClick = { }
                    )
                }
            }
            
            // Notifications Section
            item {
                SettingsSection("Notifications") {
                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Push Notifications",
                        subtitle = "Receive message notifications",
                        checked = state.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                    SettingsToggleItem(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound",
                        subtitle = "Play notification sound",
                        checked = state.soundEnabled,
                        onCheckedChange = { viewModel.setSoundEnabled(it) }
                    )
                    SettingsToggleItem(
                        icon = Icons.Default.Vibration,
                        title = "Vibration",
                        subtitle = "Vibrate on notification",
                        checked = state.vibrationEnabled,
                        onCheckedChange = { viewModel.setVibrationEnabled(it) }
                    )
                }
            }
            
            // Storage Section
            item {
                SettingsSection("Storage & Data") {
                    SettingsItem(
                        icon = Icons.Default.Storage,
                        title = "Storage Usage",
                        subtitle = "Manage storage space",
                        onClick = { }
                    )
                    SettingsItem(
                        icon = Icons.Default.CloudDownload,
                        title = "Auto-Download Media",
                        subtitle = "Configure media download settings",
                        onClick = { }
                    )
                    SettingsItem(
                        icon = Icons.Default.Backup,
                        title = "Chat Backup",
                        subtitle = "Backup your messages",
                        onClick = { }
                    )
                }
            }
            
            // Ghost Mode Section
            item {
                SettingsSection("Ghost Mode") {
                    SettingsToggleItem(
                        icon = Icons.Default.VisibilityOff,
                        title = "Hide Online Status",
                        subtitle = "Others won't see when you're online",
                        checked = state.hideOnlineStatus,
                        onCheckedChange = { viewModel.setHideOnlineStatus(it) }
                    )
                    SettingsToggleItem(
                        icon = Icons.Default.DoneAll,
                        title = "Hide Read Receipts",
                        subtitle = "Others won't see blue ticks",
                        checked = state.hideReadReceipts,
                        onCheckedChange = { viewModel.setHideReadReceipts(it) }
                    )
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = "Default Disappearing Time",
                        subtitle = state.defaultDisappearTime,
                        onClick = { }
                    )
                }
            }
            
            // Help Section
            item {
                SettingsSection("Help & Support") {
                    SettingsItem(
                        icon = Icons.Default.Help,
                        title = "Help Center",
                        subtitle = "Get help with Ghost Messenger",
                        onClick = { }
                    )
                    SettingsItem(
                        icon = Icons.Default.BugReport,
                        title = "Report a Problem",
                        subtitle = "Let us know about issues",
                        onClick = { }
                    )
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About",
                        subtitle = "Version 1.0.0 • Spiral Tech",
                        onClick = { }
                    )
                }
            }
            
            // Logout
            item {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = { 
                        viewModel.logout()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = ErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", color = ErrorRed)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            color = ElectricBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkGrey)
        ) {
            Column(content = content)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) ElectricBlue else LightGrey,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) White else LightGrey,
                fontSize = 16.sp
            )
            Text(
                subtitle,
                color = LightGrey,
                fontSize = 14.sp
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LightGrey
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) ElectricBlue else LightGrey,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) White else LightGrey,
                fontSize = 16.sp
            )
            Text(
                subtitle,
                color = LightGrey,
                fontSize = 14.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = ElectricBlue,
                uncheckedThumbColor = LightGrey,
                uncheckedTrackColor = MediumGrey
            )
        )
    }
}
