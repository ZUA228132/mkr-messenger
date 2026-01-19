package com.pioneer.messenger.ui.calls

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.ui.theme.PioneerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val CALL_NOTIFICATION_ID = 2002
        
        // Статические переменные для хранения данных звонка
        @Volatile
        private var currentCallId: String? = null
        
        fun hasActiveCall(): Boolean = currentCallId != null
        
        fun clearCall() {
            currentCallId = null
        }
    }
    
    @Inject
    lateinit var authManager: AuthManager

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var callId: String = ""
    private var callerId: String = ""
    private var callerName: String = ""
    private var callerAvatar: String? = null
    private var isVideo: Boolean = false
    
    // Состояние: показываем входящий звонок или активный звонок
    private var showActiveCall = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Блокировка скриншотов
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        android.util.Log.d("IncomingCallActivity", "onCreate, action=${intent.action}")

        setupWindowFlags()
        parseIntent(intent)
        
        // Сохраняем текущий callId
        currentCallId = callId
        
        // Сразу отменяем уведомление
        cancelNotification()
        
        // ВАЖНО: Восстанавливаем сессию авторизации
        kotlinx.coroutines.MainScope().launch {
            val restored = authManager.restoreSession()
            android.util.Log.d("IncomingCallActivity", "Session restored: $restored")
        }

        when (intent.action) {
            "ACCEPT_CALL" -> {
                android.util.Log.d("IncomingCallActivity", "ACCEPT_CALL action")
                stopRinging()
                showActiveCall.value = true
            }
            "REJECT_CALL" -> {
                android.util.Log.d("IncomingCallActivity", "REJECT_CALL action")
                stopRinging()
                clearCall()
                finish()
                return
            }
            else -> {
                // Обычный запуск - показываем входящий звонок
                startRinging()
            }
        }

        setContent {
            PioneerTheme {
                val isActiveCall by showActiveCall
                
                if (isActiveCall) {
                    android.util.Log.d("IncomingCallActivity", "Showing CallScreen")
                    CallScreen(
                        callId = callId,
                        isVideo = isVideo,
                        isIncoming = true,
                        callerName = callerName,
                        callerAvatarUrl = callerAvatar,
                        onBack = { 
                            android.util.Log.d("IncomingCallActivity", "CallScreen onBack")
                            stopRinging() // Явно останавливаем вибрацию
                            clearCall()
                            finish() 
                        }
                    )
                } else {
                    IncomingCallScreen(
                        callerName = callerName,
                        callerAvatar = callerAvatar,
                        isVideo = isVideo,
                        onAccept = {
                            android.util.Log.d("IncomingCallActivity", "User pressed Accept")
                            stopRinging()
                            cancelNotification()
                            showActiveCall.value = true
                        },
                        onReject = {
                            android.util.Log.d("IncomingCallActivity", "User pressed Reject")
                            stopRinging()
                            cancelNotification()
                            clearCall()
                            finish()
                        }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("IncomingCallActivity", "onNewIntent, action=${intent.action}")
        
        setIntent(intent)
        parseIntent(intent)
        cancelNotification()
        
        when (intent.action) {
            "ACCEPT_CALL" -> {
                stopRinging()
                showActiveCall.value = true
            }
            "REJECT_CALL" -> {
                stopRinging()
                clearCall()
                finish()
            }
        }
    }
    
    private fun parseIntent(intent: Intent) {
        callId = intent.getStringExtra("callId") ?: callId
        callerId = intent.getStringExtra("callerId") ?: callerId
        callerName = intent.getStringExtra("callerName") ?: callerName.ifEmpty { "Неизвестный" }
        callerAvatar = intent.getStringExtra("callerAvatar") ?: callerAvatar
        isVideo = intent.getBooleanExtra("isVideo", isVideo)
        
        android.util.Log.d("IncomingCallActivity", "Parsed: callId=$callId, callerName=$callerName, isVideo=$isVideo")
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                ringtone?.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun cancelNotification() {
        android.util.Log.d("IncomingCallActivity", "Cancelling notification $CALL_NOTIFICATION_ID")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("IncomingCallActivity", "onResume")
        // Убеждаемся что уведомление отменено
        cancelNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("IncomingCallActivity", "onDestroy")
        stopRinging()
        cancelNotification()
        clearCall()
    }
    
    override fun onBackPressed() {
        // Запрещаем закрытие кнопкой назад во время входящего звонка
        if (!showActiveCall.value) {
            android.util.Log.d("IncomingCallActivity", "Back pressed during incoming call - ignoring")
            return
        }
        super.onBackPressed()
    }
}


@Composable
fun IncomingCallScreen(
    callerName: String,
    callerAvatar: String?,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Пульсация аватара
    val avatarScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarScale"
    )
    
    // Волны вокруг аватара
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )
    
    // Прозрачность волн
    val waveAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha1"
    )
    
    val waveAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha2"
    )
    
    val waveAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha3"
    )
    
    // Анимация кнопки принятия
    val acceptButtonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "acceptScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E21),
                        Color(0xFF1A1F38),
                        Color(0xFF252B48)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp)
        ) {
            // Верхняя часть - тип звонка
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MKR",
                    color = Color(0xFF6C63FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isVideo) "Видеозвонок" else "Аудиозвонок",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Центральная часть - аватар с волнами
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                // Волны
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(wave1)
                        .clip(CircleShape)
                        .background(Color(0xFF6C63FF).copy(alpha = waveAlpha1))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(wave2)
                        .clip(CircleShape)
                        .background(Color(0xFF6C63FF).copy(alpha = waveAlpha2))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(wave3)
                        .clip(CircleShape)
                        .background(Color(0xFF6C63FF).copy(alpha = waveAlpha3))
                )
                
                // Аватар
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(avatarScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6C63FF),
                                    Color(0xFF5A52E0)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!callerAvatar.isNullOrEmpty()) {
                        val fullAvatarUrl = if (callerAvatar.startsWith("http")) {
                            callerAvatar
                        } else {
                            "${com.pioneer.messenger.data.network.ApiClient.BASE_URL}$callerAvatar"
                        }
                        AsyncImage(
                            model = fullAvatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = callerName.take(2).uppercase(),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Имя и статус
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Анимированные точки
                    val dotAlpha1 by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot1"
                    )
                    val dotAlpha2 by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot2"
                    )
                    val dotAlpha3 by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = 400),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot3"
                    )
                    
                    Text(
                        text = "Входящий звонок",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("•", color = Color.White.copy(alpha = dotAlpha1), fontSize = 16.sp)
                    Text("•", color = Color.White.copy(alpha = dotAlpha2), fontSize = 16.sp)
                    Text("•", color = Color.White.copy(alpha = dotAlpha3), fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Кнопка отклонения
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onReject,
                        shape = CircleShape,
                        color = Color(0xFFFF3B30),
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Отклонить",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Отклонить",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }

                // Кнопка принятия с анимацией
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onAccept,
                        shape = CircleShape,
                        color = Color(0xFF34C759),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(acceptButtonScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Принять",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Принять",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Подсказка свайпа
            Text(
                text = "Защищённый звонок",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp
            )
        }
    }
}
