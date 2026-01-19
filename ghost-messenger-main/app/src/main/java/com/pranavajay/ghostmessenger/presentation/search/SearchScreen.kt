package com.pranavajay.ghostmessenger.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pranavajay.ghostmessenger.data.models.Chat
import com.pranavajay.ghostmessenger.data.models.Message
import com.pranavajay.ghostmessenger.data.models.User
import com.pranavajay.ghostmessenger.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val state by viewModel.searchState.collectAsState()
    
    val tabs = listOf("All", "Messages", "Chats", "Users")
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                if (it.length >= 2) {
                                    viewModel.search(it)
                                }
                            },
                            placeholder = { Text("Search...", color = LightGrey) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = MediumGrey,
                                focusedTextColor = White,
                                unfocusedTextColor = White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = LightGrey)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = LightGrey)
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkGrey)
                )
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkGrey,
                    contentColor = ElectricBlue
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, color = if (selectedTab == index) ElectricBlue else LightGrey) }
                        )
                    }
                }
            }
        },
        containerColor = DarkBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ElectricBlue)
                        }
                    }
                }
                searchQuery.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Start typing to search", color = LightGrey)
                        }
                    }
                }
                else -> {
                    // Messages
                    if (selectedTab == 0 || selectedTab == 1) {
                        if (state.messages.isNotEmpty()) {
                            item {
                                Text(
                                    "Messages",
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            items(state.messages.take(if (selectedTab == 0) 3 else Int.MAX_VALUE)) { message ->
                                MessageSearchItem(message) {
                                    navController.navigate("chat/${message.chatId}")
                                }
                            }
                        }
                    }
                    
                    // Chats
                    if (selectedTab == 0 || selectedTab == 2) {
                        if (state.chats.isNotEmpty()) {
                            item {
                                Text(
                                    "Chats",
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            items(state.chats.take(if (selectedTab == 0) 3 else Int.MAX_VALUE)) { chat ->
                                ChatSearchItem(chat) {
                                    navController.navigate("chat/${chat.id}")
                                }
                            }
                        }
                    }
                    
                    // Users
                    if (selectedTab == 0 || selectedTab == 3) {
                        if (state.users.isNotEmpty()) {
                            item {
                                Text(
                                    "Users",
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            items(state.users.take(if (selectedTab == 0) 3 else Int.MAX_VALUE)) { user ->
                                UserSearchItem(user) {
                                    // Start new chat with user
                                }
                            }
                        }
                    }
                    
                    // No results
                    if (state.messages.isEmpty() && state.chats.isEmpty() && state.users.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No results found", color = LightGrey)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageSearchItem(message: Message, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Message,
            contentDescription = null,
            tint = ElectricBlue,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                message.content,
                color = White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatTime(message.timestamp),
                color = LightGrey,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ChatSearchItem(chat: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ElectricBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chat.username.take(2).uppercase(),
                color = White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.username, color = White, fontWeight = FontWeight.Medium)
            chat.lastMessage?.let {
                Text(
                    it,
                    color = LightGrey,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun UserSearchItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CyanAccent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.username.take(2).uppercase(),
                color = DarkBlack,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, color = White, fontWeight = FontWeight.Medium)
            user.bio?.let {
                Text(
                    it,
                    color = LightGrey,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (user.isOnline) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(OnlineGreen, CircleShape)
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date(timestamp))
}
