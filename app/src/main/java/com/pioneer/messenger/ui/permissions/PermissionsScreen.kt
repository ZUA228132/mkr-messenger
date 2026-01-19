package com.pioneer.messenger.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SecurePermission(
    val permission: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val securityNote: String,
    val isRequired: Boolean = true
)

@Composable
fun PermissionsScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Список разрешений
    val permissions = remember {
        buildList {
            // Уведомления (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(SecurePermission(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    icon = Icons.Outlined.Notifications,
                    title = "Уведомления",
                    description = "Мгновенные оповещения о сообщениях",
                    securityNote = "Уведомления зашифрованы и не содержат текст сообщений"
                ))
            }
            
            add(SecurePermission(
                permission = Manifest.permission.CAMERA,
                icon = Icons.Outlined.CameraAlt,
                title = "Камера",
                description = "Видеозвонки и отправка фото",
                securityNote = "Камера используется только когда вы этого хотите"
            ))
            
            add(SecurePermission(
                permission = Manifest.permission.RECORD_AUDIO,
                icon = Icons.Outlined.Mic,
                title = "Микрофон",
                description = "Голосовые сообщения и звонки",
                securityNote = "Аудио шифруется end-to-end перед отправкой"
            ))
            
            add(SecurePermission(
                permission = Manifest.permission.READ_CONTACTS,
                icon = Icons.Outlined.Contacts,
                title = "Контакты",
                description = "Поиск друзей в MKR",
                securityNote = "Контакты хешируются и не хранятся на сервере",
                isRequired = false
            ))
        }
    }
    
    var permissionStates by remember {
        mutableStateOf(permissions.associate { perm ->
            perm.permission to (ContextCompat.checkSelfPermission(context, perm.permission) 
                == PackageManager.PERMISSION_GRANTED)
        })
    }
    
    var currentStep by remember { mutableStateOf(0) } // 0 = intro, 1+ = permissions
    var showingPermissionIndex by remember { mutableStateOf(-1) }
    
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStates = permissionStates + results.mapKeys { it.key }
        
        // Проверяем все обязательные разрешения
        val allRequiredGranted = permissions
            .filter { it.isRequired }
            .all { permissionStates[it.permission] == true || results[it.permission] == true }
        
        if (allRequiredGranted) {
            scope.launch {
                delay(500)
                onAllPermissionsGranted()
            }
        }
    }
    
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (showingPermissionIndex >= 0 && showingPermissionIndex < permissions.size) {
            val perm = permissions[showingPermissionIndex]
            permissionStates = permissionStates + (perm.permission to isGranted)
        }
    }
    
    // Проверяем при старте
    LaunchedEffect(Unit) {
        val allRequired = permissions
            .filter { it.isRequired }
            .all { 
                ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED 
            }
        
        if (allRequired) {
            onAllPermissionsGranted()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        // Фоновые элементы
        SecurityBackground()
        
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn(tween(300)) + slideInHorizontally { it / 2 } togetherWith
                fadeOut(tween(300)) + slideOutHorizontally { -it / 2 }
            },
            label = "step"
        ) { step ->
            when (step) {
                0 -> IntroScreen(
                    onContinue = { currentStep = 1 }
                )
                else -> PermissionsListScreen(
                    permissions = permissions,
                    permissionStates = permissionStates,
                    onRequestPermission = { index ->
                        showingPermissionIndex = index
                        singlePermissionLauncher.launch(permissions[index].permission)
                    },
                    onRequestAll = {
                        val notGranted = permissions
                            .filter { permissionStates[it.permission] != true }
                            .map { it.permission }
                            .toTypedArray()
                        
                        if (notGranted.isNotEmpty()) {
                            multiplePermissionLauncher.launch(notGranted)
                        } else {
                            onAllPermissionsGranted()
                        }
                    },
                    onContinue = {
                        val allRequiredGranted = permissions
                            .filter { it.isRequired }
                            .all { permissionStates[it.permission] == true }
                        
                        if (allRequiredGranted) {
                            onAllPermissionsGranted()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SecurityBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Градиентные круги
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .rotate(rotation)
                .blur(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MKRColors.Primary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .rotate(-rotation)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00D9FF).copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun IntroScreen(
    onContinue: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "intro")
    
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.3f))
        
        // Щит безопасности
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(shieldScale)
        ) {
            // Свечение
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .alpha(glowAlpha)
                    .blur(40.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MKRColors.Primary,
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            
            // Основной щит
            Surface(
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                color = Color(0xFF1A1A2E),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            MKRColors.Primary,
                            Color(0xFF00D9FF)
                        )
                    )
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(70.dp),
                        tint = MKRColors.Primary
                    )
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .offset(y = 5.dp),
                        tint = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Ваша безопасность —\nнаш приоритет",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "MKR использует сквозное шифрование для защиты ваших данных. Для полноценной работы приложению нужны некоторые разрешения.",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Фичи безопасности
        SecurityFeaturesList()
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MKRColors.Primary
            )
        ) {
            Text(
                "Настроить разрешения",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SecurityFeaturesList() {
    val features = listOf(
        "🔐" to "End-to-end шифрование",
        "🛡️" to "Защита от скриншотов",
        "⏱️" to "Исчезающие сообщения",
        "🚫" to "Никакой рекламы и трекинга"
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        features.forEach { (emoji, text) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(emoji, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionsListScreen(
    permissions: List<SecurePermission>,
    permissionStates: Map<String, Boolean>,
    onRequestPermission: (Int) -> Unit,
    onRequestAll: () -> Unit,
    onContinue: () -> Unit
) {
    val grantedCount = permissions.count { permissionStates[it.permission] == true }
    val requiredCount = permissions.count { it.isRequired }
    val requiredGranted = permissions.filter { it.isRequired }.all { permissionStates[it.permission] == true }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Заголовок
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MKRColors.Primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Разрешения",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "$grantedCount из ${permissions.size} разрешено",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Прогресс
        LinearProgressIndicator(
            progress = grantedCount.toFloat() / permissions.size,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MKRColors.Primary,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Список разрешений
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            permissions.forEachIndexed { index, permission ->
                val isGranted = permissionStates[permission.permission] == true
                
                PermissionCard(
                    permission = permission,
                    isGranted = isGranted,
                    onClick = { 
                        if (!isGranted) {
                            onRequestPermission(index)
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопки
        if (!requiredGranted) {
            Button(
                onClick = onRequestAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MKRColors.Primary
                )
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Разрешить все",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        } else {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00C853)
                )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Продолжить",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Пропустить необязательные
        if (requiredGranted && grantedCount < permissions.size) {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Пропустить необязательные",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(
    permission: SecurePermission,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isGranted -> Color(0xFF00C853)
        permission.isRequired -> MKRColors.Primary
        else -> Color.White.copy(alpha = 0.1f)
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !isGranted, onClick = onClick),
        color = if (isGranted) 
            Color(0xFF00C853).copy(alpha = 0.1f) 
        else 
            Color.White.copy(alpha = 0.03f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isGranted) Color(0xFF00C853).copy(alpha = 0.2f)
                        else MKRColors.Primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    permission.icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF00C853) else MKRColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        permission.title,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    
                    if (!permission.isRequired) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "опционально",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    permission.description,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Заметка о безопасности
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF00D9FF).copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        permission.securityNote,
                        fontSize = 11.sp,
                        color = Color(0xFF00D9FF).copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Статус
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Разрешено",
                    tint = Color(0xFF00C853),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Разрешить",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
