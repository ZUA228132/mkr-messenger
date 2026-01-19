package com.pioneer.messenger.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pioneer.messenger.ui.theme.MKRColors

data class Status(
    val id: String,
    val username: String,
    val time: String,
    val viewed: Boolean,
    val imageUrl: String? = null,
    val userId: String = "",
    val avatarUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onBack: () -> Unit,
    onStatusClick: (Status) -> Unit = {},
    onAddStatus: () -> Unit = {},
    viewModel: StatusViewModel = hiltViewModel()
) {
    val statuses by viewModel.statuses.collectAsState()
    val myStories by viewModel.myStories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadStories()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статусы", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MKRColors.SurfaceDark,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStatus, containerColor = MKRColors.Primary) {
                Icon(Icons.Default.Add, "Добавить статус", tint = Color.White)
            }
        },
        containerColor = MKRColors.BackgroundDark
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MKRColors.Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item {
                    MyStatusItem(
                        hasStory = myStories.isNotEmpty(),
                        onAddStatus = onAddStatus,
                        onViewMyStory = { 
                            myStories.firstOrNull()?.let { 
                                onStatusClick(Status(it.id, "Мой статус", it.time, false, it.mediaUrl, it.userId, it.avatarUrl))
                            }
                        }
                    )
                }
                
                val unviewedStatuses = statuses.filter { !it.viewed }
                val viewedStatuses = statuses.filter { it.viewed }
                
                if (unviewedStatuses.isNotEmpty()) {
                    item {
                        Text(
                            "Недавние обновления",
                            color = MKRColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    items(unviewedStatuses) { status ->
                        StatusItem(status, onClick = { onStatusClick(status) })
                    }
                }
                
                if (viewedStatuses.isNotEmpty()) {
                    item {
                        Text(
                            "Просмотренные",
                            color = MKRColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    items(viewedStatuses) { status ->
                        StatusItem(status, onClick = { onStatusClick(status) })
                    }
                }
                
                if (statuses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Нет историй от друзей",
                                color = MKRColors.TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyStatusItem(
    hasStory: Boolean,
    onAddStatus: () -> Unit,
    onViewMyStory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (hasStory) onViewMyStory else onAddStatus)
            .background(MKRColors.BackgroundDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasStory) MKRColors.Primary else MKRColors.SurfaceDark
                    )
                    .then(
                        if (hasStory) Modifier.border(2.dp, MKRColors.Primary, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Я", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            
            if (!hasStory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MKRColors.Success),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text("Мой статус", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (hasStory) "Нажмите, чтобы посмотреть" else "Нажмите, чтобы добавить статус",
                color = MKRColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
    
    Divider(color = MKRColors.SurfaceDark, thickness = 1.dp)
}

@Composable
fun StatusItem(status: Status, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MKRColors.BackgroundDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(
                    width = 2.dp,
                    color = if (status.viewed) MKRColors.TextSecondary else MKRColors.Primary,
                    shape = CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(MKRColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                status.username.first().toString(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                status.username,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(status.time, color = MKRColors.TextSecondary, fontSize = 14.sp)
        }
    }
    
    Divider(color = MKRColors.SurfaceDark, thickness = 1.dp)
}
