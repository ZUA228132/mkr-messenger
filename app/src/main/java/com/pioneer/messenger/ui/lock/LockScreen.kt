package com.pioneer.messenger.ui.lock

import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.security.AppLockManager
import com.pioneer.messenger.util.HapticFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private val DarkBlack = Color(0xFF0D0D0D)
private val DarkGrey = Color(0xFF1A1A2E)
private val ElectricBlue = Color(0xFF0066FF)
private val LightGrey = Color(0xFFB0B0B0)
private val ErrorRed = Color(0xFFFF4757)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLockManager: AppLockManager
) : ViewModel() {
    
    val isPinSet = appLockManager.isPinSet
    val isBiometricEnabled = appLockManager.isBiometricEnabled
    val isLocked = appLockManager.isLocked
    
    fun verifyPin(pin: String): Boolean {
        return appLockManager.verifyPin(pin)
    }
    
    fun setPin(pin: String): Boolean {
        return appLockManager.setPin(pin)
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        appLockManager.setBiometricEnabled(enabled)
    }
    
    fun clearPin() {
        appLockManager.clearPin()
    }
    
    fun isBiometricAvailable(): Boolean {
        return appLockManager.isBiometricAvailable()
    }
    
    fun showBiometricPrompt(
        activity: Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        appLockManager.showBiometricPrompt(activity, onSuccess, onError, onCancel)
    }
    
    fun unlock() {
        appLockManager.unlock()
    }
}

@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    onBiometricClick: () -> Unit = {},
    isBiometricAvailable: Boolean = true,
    viewModel: LockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val canUseBiometric = remember { viewModel.isBiometricAvailable() }
    
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    
    val shakeOffset by animateFloatAsState(
        targetValue = if (error) 10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        finishedListener = { error = false },
        label = "shake"
    )
    
    // Автоматический запуск биометрии
    LaunchedEffect(isBiometricEnabled, canUseBiometric) {
        if (isBiometricEnabled && canUseBiometric && activity != null) {
            viewModel.showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlock,
                onError = { },
                onCancel = { }
            )
        }
    }
    
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
            
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Pioneer Messenger",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Введите PIN для разблокировки",
                color = LightGrey,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                modifier = Modifier.offset(x = shakeOffset.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (index < pin.length) ElectricBlue else DarkGrey)
                            .border(1.dp, if (error) ErrorRed else ElectricBlue, CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
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
                                        HapticFeedback.pinInput(view)
                                        if (digit == "⌫") {
                                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        } else if (pin.length < 4) {
                                            pin += digit
                                            if (pin.length == 4) {
                                                if (viewModel.verifyPin(pin)) {
                                                    HapticFeedback.success(context)
                                                    onUnlock()
                                                } else {
                                                    HapticFeedback.pinError(context)
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
            
            if (canUseBiometric && isBiometricEnabled && activity != null) {
                TextButton(onClick = { 
                    HapticFeedback.lightClick(view)
                    viewModel.showBiometricPrompt(
                        activity = activity,
                        onSuccess = onUnlock,
                        onError = { },
                        onCancel = { }
                    )
                }) {
                    Icon(Icons.Default.Fingerprint, null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Использовать отпечаток", color = ElectricBlue)
                }
            }
        }
    }
}

@Composable
fun NumberButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DarkGrey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text == "⌫") {
            Icon(Icons.Default.Backspace, "Delete", tint = Color.White, modifier = Modifier.size(28.dp))
        } else {
            Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SetupPinScreen(
    onPinSet: (String) -> Unit,
    onSkip: () -> Unit,
    isRequired: Boolean = false,  // When true, hide skip button (for registration flow)
    viewModel: LockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var enableBiometric by remember { mutableStateOf(false) }
    
    val canUseBiometric = remember { viewModel.isBiometricAvailable() }
    
    Box(
        modifier = Modifier.fillMaxSize().background(DarkBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            Icon(Icons.Default.Lock, null, tint = ElectricBlue, modifier = Modifier.size(64.dp))
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                if (isConfirming) "Подтвердите PIN" else "Установите PIN",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                if (isConfirming) "Введите PIN ещё раз" else "Создайте 4-значный PIN",
                color = LightGrey,
                fontSize = 16.sp
            )
            
            // Show info message when PIN is required
            if (isRequired && !isConfirming) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "PIN обязателен для защиты приложения",
                    color = ElectricBlue.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
            
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = ErrorRed, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
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
                                        HapticFeedback.pinInput(view)
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
                                                        // Сохраняем PIN
                                                        if (viewModel.setPin(pin)) {
                                                            HapticFeedback.success(context)
                                                            if (enableBiometric) {
                                                                viewModel.setBiometricEnabled(true)
                                                            }
                                                            onPinSet(pin)
                                                        } else {
                                                            HapticFeedback.pinError(context)
                                                            error = "Ошибка сохранения PIN"
                                                        }
                                                    } else {
                                                        HapticFeedback.pinError(context)
                                                        error = "PIN не совпадает"
                                                        confirmPin = ""
                                                    }
                                                }
                                            } else if (!isConfirming && pin.length < 4) {
                                                pin += digit
                                                if (pin.length == 4) {
                                                    HapticFeedback.success(context)
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
            
            // Опция биометрии
            if (canUseBiometric && isConfirming) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { 
                        HapticFeedback.lightClick(view)
                        enableBiometric = !enableBiometric 
                    }
                ) {
                    Checkbox(
                        checked = enableBiometric,
                        onCheckedChange = { 
                            HapticFeedback.lightClick(view)
                            enableBiometric = it 
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ElectricBlue,
                            uncheckedColor = LightGrey
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Использовать отпечаток", color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Only show skip button when PIN is not required
            if (!isRequired) {
                TextButton(onClick = { 
                    HapticFeedback.lightClick(view)
                    onSkip() 
                }) {
                    Text("Пропустить", color = LightGrey)
                }
            }
        }
    }
}
