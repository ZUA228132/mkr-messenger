package com.pranavajay.ghostmessenger.presentation.call

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pranavajay.ghostmessenger.R
import com.pranavajay.ghostmessenger.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    navController: NavController,
    callId: String,
    callType: String,
    callerName: String,
    isIncoming: Boolean,
    viewModel: CallViewModel = hiltViewModel()
) {
    val state by viewModel.callState.collectAsState()
    var callDuration by remember { mutableStateOf(0) }
    
    // Call duration timer
    LaunchedEffect(state.isConnected) {
        if (state.isConnected) {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }
    
    // Pulsing animation for incoming call
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            // Avatar
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(if (isIncoming && !state.isConnected) scale else 1f)
                    .clip(CircleShape)
                    .background(ElectricBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    callerName.take(2).uppercase(),
                    color = White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Name
            Text(
                callerName,
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status
            Text(
                when {
                    state.isConnected -> formatDuration(callDuration)
                    isIncoming -> "Incoming ${if (callType == "VIDEO") "video" else "voice"} call..."
                    else -> "Calling..."
                },
                color = LightGrey,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Call controls
            if (state.isConnected) {
                // Connected call controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlButton(
                        icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (state.isMuted) "Unmute" else "Mute",
                        isActive = state.isMuted,
                        onClick = { viewModel.toggleMute() }
                    )
                    
                    if (callType == "VIDEO") {
                        CallControlButton(
                            icon = if (state.isVideoOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            label = if (state.isVideoOff) "Video On" else "Video Off",
                            isActive = state.isVideoOff,
                            onClick = { viewModel.toggleVideo() }
                        )
                    }
                    
                    CallControlButton(
                        icon = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = if (state.isSpeakerOn) "Speaker" else "Earpiece",
                        isActive = state.isSpeakerOn,
                        onClick = { viewModel.toggleSpeaker() }
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // End call button
                IconButton(
                    onClick = {
                        viewModel.endCall(callId)
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(ErrorRed, CircleShape)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (isIncoming) {
                // Incoming call controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                viewModel.declineCall(callId)
                                navController.popBackStack()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(ErrorRed, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = LightGrey)
                    }
                    
                    // Accept
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewModel.acceptCall(callId) },
                            modifier = Modifier
                                .size(72.dp)
                                .background(OnlineGreen, CircleShape)
                        ) {
                            Icon(
                                if (callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept",
                                tint = White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accept", color = LightGrey)
                    }
                }
            } else {
                // Outgoing call - just end button
                IconButton(
                    onClick = {
                        viewModel.endCall(callId)
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(ErrorRed, CircleShape)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isActive) White else DarkGrey,
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) DarkBlack else White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = LightGrey, fontSize = 12.sp)
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
