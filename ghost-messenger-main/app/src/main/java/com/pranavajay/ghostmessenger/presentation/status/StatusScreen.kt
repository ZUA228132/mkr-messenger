package com.pranavajay.ghostmessenger.presentation.status

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pranavajay.ghostmessenger.ui.theme.*

data class Status(val id: String, val username: String, val time: String, val viewed: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(navController: NavController) {
    val statuses = remember {
        listOf(
            Status("1", "John Doe", "2 hours ago", false),
            Status("2", "Jane Smith", "5 hours ago", true),
            Status("3", "Mike Johnson", "Yesterday", true)
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Status", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { }, containerColor = ElectricBlue) {
                Icon(Icons.Default.Add, contentDescription = "Add Status", tint = White)
            }
        },
        containerColor = DarkBlack
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                MyStatusItem()
            }
            item {
                Text("Recent updates", color = LightGrey, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
            items(statuses) { status ->
                StatusItem(status)
            }
        }
    }
}

@Composable
fun MyStatusItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .background(DarkBlack)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(ElectricBlue),
            contentAlignment = Alignment.Center
        ) {
            Text("PA", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("My Status", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Tap to add status update", color = LightGrey, fontSize = 14.sp)
        }
    }
    Divider(color = DarkGrey, thickness = 1.dp)
}

@Composable
fun StatusItem(status: Status) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .background(DarkBlack)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(2.dp, if (status.viewed) LightGrey else ElectricBlue, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(CyanAccent),
            contentAlignment = Alignment.Center
        ) {
            Text(status.username.first().toString(), color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(status.username, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(status.time, color = LightGrey, fontSize = 14.sp)
        }
    }
    Divider(color = DarkGrey, thickness = 1.dp)
}
