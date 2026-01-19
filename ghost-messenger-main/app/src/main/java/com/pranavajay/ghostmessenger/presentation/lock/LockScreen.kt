package com.pranavajay.ghostmessenger.presentation.lock

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranavajay.ghostmessenger.R
import com.pranavajay.ghostmessenger.ui.theme.*

@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    onBiometricClick: () -> Unit,
    isBiometricAvailable: Boolean = true
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    
    // Shake animation for error
    val shakeOffset by animateFloatAsState(
        targetValue = if (error) 10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        finishedListener = { error = false },
        label = "shake"
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
            
            // Ghost Logo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ghost),
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(60.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Ghost Messenger",
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Enter PIN to unlock",
                color = LightGrey,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // PIN dots
            Row(
                modifier = Modifier.offset(x = shakeOffset.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) ElectricBlue else DarkGrey
                            )
                            .border(1.dp, if (error) ErrorRed else ElectricBlue, CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Number pad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                ).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        row.forEach { digit ->
                            if (digit.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else {
                                NumberButton(
                                    text = digit,
                                    onClick = {
                                        if (digit == "⌫") {
                                            if (pin.isNotEmpty()) {
                                                pin = pin.dropLast(1)
                                            }
                                        } else if (pin.length < 4) {
                                            pin += digit
                                            if (pin.length == 4) {
                                                // Verify PIN
                                                if (pin == "1234") { // Replace with actual verification
                                                    onUnlock()
                                                } else {
                                                    error = true
                                                    pin = ""
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Biometric button
            if (isBiometricAvailable) {
                TextButton(onClick = onBiometricClick) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Fingerprint", color = ElectricBlue)
                }
            }
        }
    }
}

@Composable
fun NumberButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DarkGrey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text == "⌫") {
            Icon(
                Icons.Default.Backspace,
                contentDescription = "Delete",
                tint = White,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text(
                text,
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SetupPinScreen(
    onPinSet: (String) -> Unit,
    onSkip: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
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
            
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                if (isConfirming) "Confirm PIN" else "Set PIN",
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                if (isConfirming) "Re-enter your PIN" else "Create a 4-digit PIN",
                color = LightGrey,
                fontSize = 16.sp
            )
            
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = ErrorRed, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val currentPin = if (isConfirming) confirmPin else pin
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (index < currentPin.length) ElectricBlue else DarkGrey)
                            .border(1.dp, ElectricBlue, CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Number pad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { digit ->
                            if (digit.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else {
                                NumberButton(
                                    text = digit,
                                    onClick = {
                                        if (digit == "⌫") {
                                            if (isConfirming && confirmPin.isNotEmpty()) {
                                                confirmPin = confirmPin.dropLast(1)
                                            } else if (!isConfirming && pin.isNotEmpty()) {
                                                pin = pin.dropLast(1)
                                            }
                                        } else {
                                            if (isConfirming && confirmPin.length < 4) {
                                                confirmPin += digit
                                                if (confirmPin.length == 4) {
                                                    if (confirmPin == pin) {
                                                        onPinSet(pin)
                                                    } else {
                                                        error = "PINs don't match"
                                                        confirmPin = ""
                                                    }
                                                }
                                            } else if (!isConfirming && pin.length < 4) {
                                                pin += digit
                                                if (pin.length == 4) {
                                                    isConfirming = true
                                                    error = null
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            TextButton(onClick = onSkip) {
                Text("Skip for now", color = LightGrey)
            }
        }
    }
}
