package com.pranavajay.ghostmessenger.presentation.group

import androidx.compose.foundation.background
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

data class Group(val id: String, val name: String, val members: Int, val lastMessage: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(navController: NavController) {
    val groups = remember {
        listOf(
            Group("1", "Family", 5, "Mom: Dinner at 8?"),
            Group("2", "Work Team", 12, "John: Meeting tomorrow"),
            Group("3", "Friends", 8, "Sarah: Party this weekend!")
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups", fontWeight = FontWeight.Bold) },
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
                Icon(Icons.Default.Add, contentDescription = "Create Group", tint = White)
            }
        },
        containerColor = DarkBlack
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(groups) { group ->
                GroupItem(group)
            }
        }
    }
}

@Composable
fun GroupItem(group: Group) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .background(DarkBlack)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(CyanAccent),
            contentAlignment = Alignment.Center
        ) {
            Text(group.name.first().toString(), color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${group.members} members", color = LightGrey, fontSize = 12.sp)
            Text(group.lastMessage, color = LightGrey, fontSize = 14.sp, maxLines = 1)
        }
    }
    Divider(color = DarkGrey, thickness = 1.dp)
}
