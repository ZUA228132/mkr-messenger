package com.pranavajay.ghostmessenger.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pranavajay.ghostmessenger.ui.theme.DarkBlack
import com.pranavajay.ghostmessenger.ui.theme.OnlineGreen

@Composable
fun OnlineStatusBadge(isOnline: Boolean) {
    if (isOnline) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(2.dp, DarkBlack, CircleShape)
                .background(OnlineGreen, CircleShape)
        )
    }
}
