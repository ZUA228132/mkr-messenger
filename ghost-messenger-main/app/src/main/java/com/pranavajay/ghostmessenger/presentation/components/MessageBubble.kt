package com.pranavajay.ghostmessenger.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranavajay.ghostmessenger.ui.theme.*

@Composable
fun MessageBubble(
    message: String,
    isSent: Boolean,
    timestamp: String,
    isRead: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isSent) SentMessageBg else ReceivedMessageBg,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isSent) 16.dp else 4.dp,
                        bottomEnd = if (isSent) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                Text(text = message, color = White, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = timestamp, color = LightGrey, fontSize = 11.sp)
                    if (isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isRead) "✓✓" else "✓", color = if (isRead) CyanAccent else LightGrey, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
