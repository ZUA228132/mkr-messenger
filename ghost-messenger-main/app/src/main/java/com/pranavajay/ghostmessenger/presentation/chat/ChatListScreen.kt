package com.pranavajay.ghostmessenger.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import com.pranavajay.ghostmessenger.data.models.Chat
import com.pranavajay.ghostmessenger.presentation.components.EmptyState
import com.pranavajay.ghostmessenger.presentation.components.LoadingIndicator
import com.pranavajay.ghostmessenger.presentation.components.NavigationDrawer
import com.pranavajay.ghostmessenger.presentation.components.OnlineStatusBadge
import com.pranavajay.ghostmessenger.presentation.navigation.Screen
import com.pranavajay.ghostmessenger.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController,
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val state by viewModel.chatListState.collectAsState()
    val colors = GhostTheme.colors

    LaunchedEffect(Unit) {
        viewModel.loadChats()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawer(
                userName = "User",
                userEmail = "",
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                onChatClick = { scope.launch { drawerState.close() } },
                onGroupClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate("group_list")
                },
                onChannelClick = { scope.launch { drawerState.close() } },
                onStatusClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate("status")
                },
                onPostsClick = { scope.launch { drawerState.close() } },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate("profile")
                },
                onLogoutClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate("login") {
                        popUpTo("chat_list") { inclusive = true }
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ghost Messenger", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = colors.textPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("search") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = "Search",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                painter = painterResource(
                                    id = if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon
                                ),
                                contentDescription = "Toggle Theme",
                                tint = if (isDarkMode) Color(0xFFFFD700) else Color(0xFFB0C4DE)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surface,
                        titleContentColor = colors.textPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* TODO: New chat dialog */ },
                    containerColor = ElectricBlue
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", tint = White)
                }
            },
            containerColor = colors.background
        ) { padding ->
            when {
                state.isLoading -> LoadingIndicator()
                state.chats.isEmpty() -> EmptyState("No chats yet. Start a conversation!")
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        items(state.chats) { chat ->
                            ChatItem(
                                chat = chat,
                                colors = colors,
                                onClick = {
                                    navController.navigate(Screen.ChatDetail.createRoute(chat.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatItem(chat: Chat, colors: GhostColors = GhostTheme.colors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(colors.background)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.username.first().toString(),
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                ) {
                    OnlineStatusBadge(isOnline = true)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = chat.username,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = formatTime(chat.lastMessageTime),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessage ?: "",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            color = White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(color = colors.divider, thickness = 1.dp)
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "Now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
