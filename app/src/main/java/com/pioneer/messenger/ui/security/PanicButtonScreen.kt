package com.pioneer.messenger.ui.security

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicButtonScreen(
    onBack: () -> Unit,
    onWipeComplete: () -> Unit,
    viewModel: PanicButtonViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    var isPressed by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isWiping by remember { mutableStateOf(false) }
    var wipeComplete by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    // Анимация пульсации
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // Прогресс удержания - 3 секунды
    LaunchedEffect(isPressed) {
        if (isPressed && !isWiping) {
            holdProgress = 0f
            val startTime = System.currentTimeMillis()
            val holdDuration = 3000L // 3 секунды
            
            while (isPressed && holdProgress < 1f) {
                delay(16) // ~60fps
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / holdDuration).coerceIn(0f, 1f)
                
                // Вибрация на каждые 25%
                val progressPercent = (holdProgress * 100).toInt()
                if (progressPercent == 25 || progressPercent == 50 || progressPercent == 75) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            
            if (holdProgress >= 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showConfirmDialog = true
                isPressed = false
            }
        } else if (!isPressed) {
            holdProgress = 0f
        }
    }
    
    // Процесс удаления
    LaunchedEffect(isWiping) {
        if (isWiping) {
            delay(1000)
            val success = viewModel.performPanicWipe()
            wipeComplete = true
            delay(1500)
            if (success) {
                onWipeComplete()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экстренное удаление", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = Color(0xFF1A1A2E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верхняя часть - предупреждение
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(56.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "PANIC BUTTON",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Удерживайте кнопку 3 секунды\nдля полного удаления всех данных",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            
            // Центр - кнопка
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f)
            ) {
                // Фоновый круг с прогрессом
                if (holdProgress > 0f && !isWiping) {
                    CircularProgressIndicator(
                        progress = holdProgress,
                        modifier = Modifier.size(200.dp),
                        color = Color(0xFFFF5252),
                        strokeWidth = 8.dp,
                        trackColor = Color(0xFF333333)
                    )
                }
                
                // Основная кнопка
                Box(
                    modifier = Modifier
                        .size(if (isWiping) 160.dp else 160.dp)
                        .scale(if (isPressed) 0.92f else if (!isWiping) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isWiping) {
                                    if (wipeComplete) listOf(Color(0xFF66BB6A), Color(0xFF4CAF50))
                                    else listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                                } else {
                                    listOf(Color(0xFFFF7043), Color(0xFFFF5252), Color(0xFFD32F2F))
                                }
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (!isWiping) {
                                        isPressed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        tryAwaitRelease()
                                        isPressed = false
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isWiping) {
                        if (wipeComplete) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "УДАЛИТЬ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
            
            // Нижняя часть - статус и информация
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isWiping) {
                    Text(
                        if (wipeComplete) "✓ Все данные удалены" else "Удаление данных...",
                        color = if (wipeComplete) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        if (isPressed) "Удерживайте... ${(holdProgress * 3).toInt() + 1}с" 
                        else "Нажмите и удерживайте",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Что будет удалено
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2D2D44)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Будут удалены:",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "• Все сообщения и чаты",
                            "• Ключи шифрования",
                            "• Медиафайлы",
                            "• Настройки и профиль"
                        ).forEach { item ->
                            Text(
                                item,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Диалог подтверждения
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text("Подтвердите удаление", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "ВСЕ ДАННЫЕ будут безвозвратно удалены.\n\nЭто действие НЕОБРАТИМО!",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isWiping = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("УДАЛИТЬ ВСЁ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Отмена")
                }
            },
            containerColor = Color(0xFF2D2D44),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }
}
