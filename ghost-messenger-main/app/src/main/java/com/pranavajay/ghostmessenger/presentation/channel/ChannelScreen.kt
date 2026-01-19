package com.pranavajay.ghostmessenger.presentation.channel

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
import com.pranavajay.ghostmessenger.data.models.Channel
import com.pranavajay.ghostmessenger.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    navController: NavController,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val state by viewModel.channelState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadChannels()
    }
    
    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                if (it.length >= 2) viewModel.searchChannels(it)
                            },
                            placeholder = { Text("Search channels...", color = LightGrey) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = MediumGrey,
                                focusedTextColor = White,
                                unfocusedTextColor = White
                            ),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkGrey)
                )
            } else {
                TopAppBar(
                    title = { Text("Channels", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search", tint = White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkGrey,
                        titleContentColor = White
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ElectricBlue
            ) {
                Icon(Icons.Default.Add, "Create Channel", tint = White)
            }
        },
        containerColor = DarkBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // My Channels
            if (state.myChannels.isNotEmpty()) {
                item {
                    Text(
                        "My Channels",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(state.myChannels) { channel ->
                    ChannelItem(
                        channel = channel,
                        onClick = { navController.navigate("channel/${channel.id}") }
                    )
                }
            }
            
            // Subscribed Channels
            if (state.subscribedChannels.isNotEmpty()) {
                item {
                    Text(
                        "Subscribed",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(state.subscribedChannels) { channel ->
                    ChannelItem(
                        channel = channel,
                        onClick = { navController.navigate("channel/${channel.id}") }
                    )
                }
            }
            
            // Search Results
            if (searchQuery.isNotEmpty() && state.searchResults.isNotEmpty()) {
                item {
                    Text(
                        "Search Results",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(state.searchResults) { channel ->
                    ChannelItem(
                        channel = channel,
                        onClick = { },
                        showSubscribeButton = true,
                        onSubscribe = { viewModel.subscribeChannel(channel.id) }
                    )
                }
            }
            
            // Discover Section
            if (state.discoverChannels.isNotEmpty() && searchQuery.isEmpty()) {
                item {
                    Text(
                        "Discover",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(state.discoverChannels) { channel ->
                    ChannelItem(
                        channel = channel,
                        onClick = { },
                        showSubscribeButton = true,
                        onSubscribe = { viewModel.subscribeChannel(channel.id) }
                    )
                }
            }
            
            // Empty state
            if (state.myChannels.isEmpty() && state.subscribedChannels.isEmpty() && !state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = LightGrey,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No channels yet", color = LightGrey, fontSize = 16.sp)
                            Text(
                                "Create or subscribe to channels",
                                color = LightGrey.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Create Channel Dialog
    if (showCreateDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, isPublic ->
                viewModel.createChannel(name, desc, isPublic)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ChannelItem(
    channel: Channel,
    onClick: () -> Unit,
    showSubscribeButton: Boolean = false,
    onSubscribe: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CyanAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                tint = DarkBlack,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!channel.isPublic) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Private",
                        tint = LightGrey,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                "${formatSubscribers(channel.subscriberCount)} subscribers",
                color = LightGrey,
                fontSize = 14.sp
            )
            channel.description?.let {
                Text(
                    it,
                    color = LightGrey.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        if (showSubscribeButton) {
            Button(
                onClick = onSubscribe,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Subscribe", fontSize = 12.sp)
            }
        }
    }
    
    HorizontalDivider(color = DarkGrey, thickness = 1.dp)
}

@Composable
fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel", color = White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name", color = LightGrey) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = MediumGrey,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = LightGrey) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = MediumGrey,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    minLines = 2
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Public Channel", color = White)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = White,
                            checkedTrackColor = ElectricBlue
                        )
                    )
                }
                
                Text(
                    if (isPublic) "Anyone can find and subscribe" else "Only invited users can join",
                    color = LightGrey,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, description.ifBlank { null }, isPublic) },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = if (name.isNotBlank()) ElectricBlue else LightGrey)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LightGrey)
            }
        },
        containerColor = DarkGrey
    )
}

private fun formatSubscribers(count: Int): String {
    return when {
        count >= 1000000 -> "${count / 1000000}M"
        count >= 1000 -> "${count / 1000}K"
        else -> count.toString()
    }
}
