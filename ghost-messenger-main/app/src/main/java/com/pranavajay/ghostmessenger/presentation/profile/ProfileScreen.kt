package com.pranavajay.ghostmessenger.presentation.profile

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pranavajay.ghostmessenger.R
import com.pranavajay.ghostmessenger.presentation.auth.AuthViewModel
import com.pranavajay.ghostmessenger.presentation.navigation.Screen
import com.pranavajay.ghostmessenger.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val state by authViewModel.state.collectAsState()
    val colors = GhostTheme.colors

    LaunchedEffect(state.isLoggedIn) {
        if (!state.isLoggedIn) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.user?.username?.take(2)?.uppercase() ?: "PA",
                    color = White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                state.user?.username ?: "User",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                state.user?.email ?: "",
                color = colors.textSecondary,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Available", color = OnlineGreen, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(32.dp))

            ProfileOption(Icons.Default.Person, "Edit Profile", colors) { }
            ProfileOption(Icons.Default.Notifications, "Notifications", colors) { }
            ProfileOption(Icons.Default.Lock, "Privacy", colors) { }
            ProfileOption(Icons.Default.Storage, "Storage", colors) { }

            // Theme Toggle Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                onClick = onToggleTheme
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        if (isDarkMode) "Dark Mode" else "Light Mode",
                        color = colors.textPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElectricBlue,
                            checkedTrackColor = ElectricBlue.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            ProfileOption(Icons.Default.Help, "Help", colors) { }
            ProfileOption(Icons.Default.Info, "About", colors) { }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { authViewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", color = White)
            }
        }
    }
}

@Composable
fun ProfileOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    colors: GhostColors = GhostTheme.colors,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = ElectricBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = colors.textPrimary, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = colors.textSecondary)
        }
    }
}
