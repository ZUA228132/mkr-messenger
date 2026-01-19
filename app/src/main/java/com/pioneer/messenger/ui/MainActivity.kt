package com.pioneer.messenger.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.MessageDao
import com.pioneer.messenger.data.local.PreferencesManager
import com.pioneer.messenger.data.network.NetworkMonitor
import com.pioneer.messenger.data.security.StealthMode
import com.pioneer.messenger.service.MessageService
import com.pioneer.messenger.ui.navigation.PioneerNavHost
import com.pioneer.messenger.ui.security.FakeCalculatorScreen
import com.pioneer.messenger.ui.theme.PioneerTheme
import com.pioneer.messenger.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncomingCallData(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatar: String?,
    val isVideo: Boolean
)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var networkMonitor: NetworkMonitor
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var authManager: AuthManager
    
    @Inject
    lateinit var messageDao: MessageDao
    
    @Inject
    lateinit var stealthMode: StealthMode
    
    // State для входящего звонка - используем mutableStateOf для реактивности
    var pendingIncomingCall = mutableStateOf<IncomingCallData?>(null)
        private set
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Блокировка скриншотов и записи экрана
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        enableEdgeToEdge()
        
        // Проверяем intent на входящий звонок
        handleIntent(intent)
        
        // Очищаем старые зашифрованные сообщения (одноразово)
        val prefs = getSharedPreferences("pioneer_migration", MODE_PRIVATE)
        if (!prefs.getBoolean("messages_cleared_v2", false)) {
            lifecycleScope.launch {
                try {
                    messageDao.deleteAllMessages()
                    prefs.edit().putBoolean("messages_cleared_v2", true).apply()
                    android.util.Log.d("MainActivity", "Old encrypted messages cleared")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to clear messages: ${e.message}")
                }
            }
        }
        
        // Восстанавливаем сессию при запуске
        lifecycleScope.launch {
            val restored = authManager.restoreSession()
            android.util.Log.d("MainActivity", "Session restored: $restored")
            
            if (restored) {
                // Запускаем сервис синхронизации сообщений
                val isAuthenticated = authManager.isAuthenticated.first()
                if (isAuthenticated) {
                    MessageService.start(this@MainActivity)
                }
            }
        }
        
        setContent {
            val themeState = remember { ThemeState(preferencesManager) }
            val isOnline by networkMonitor.isOnline.collectAsState(initial = true)
            var showBluetoothDialog by remember { mutableStateOf(false) }
            var bluetoothModeEnabled by remember { mutableStateOf(false) }
            
            // Stealth Mode - показываем фейковый калькулятор
            var isStealthActive by remember { mutableStateOf(stealthMode.isStealthModeEnabled()) }
            var stealthUnlocked by remember { mutableStateOf(false) }
            val secretCode = remember { stealthMode.getSecretCode() ?: "" }
            
            LaunchedEffect(isOnline) {
                if (!isOnline && !bluetoothModeEnabled) {
                    showBluetoothDialog = true
                }
            }
            
            // Если Stealth Mode активен и не разблокирован - показываем калькулятор
            if (isStealthActive && !stealthUnlocked && secretCode.isNotEmpty()) {
                FakeCalculatorScreen(
                    secretCode = secretCode,
                    onSecretCodeEntered = {
                        stealthUnlocked = true
                    }
                )
            } else {
                PioneerTheme(themeState = themeState, darkTheme = true) {
                    // Применяем MKR тему поверх базовой темы - всегда тёмная
                    com.pioneer.messenger.ui.theme.MKRTheme(darkTheme = true) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box {
                                PioneerNavHost(themeState = themeState)
                            
                            if (bluetoothModeEnabled) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Bluetooth,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Режим Bluetooth",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                    
                    if (showBluetoothDialog) {
                        AlertDialog(
                            onDismissRequest = { showBluetoothDialog = false },
                            icon = {
                                Icon(
                                    Icons.Default.WifiOff,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                            },
                            title = {
                                Text(
                                    "Нет подключения к интернету",
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            },
                            text = {
                                Text(
                                    "Интернет-соединение потеряно. Хотите перейти в режим связи через Bluetooth?",
                                    textAlign = TextAlign.Center
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        bluetoothModeEnabled = true
                                        showBluetoothDialog = false
                                    }
                                ) {
                                    Icon(Icons.Default.Bluetooth, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Включить Bluetooth")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBluetoothDialog = false }) {
                                    Text("Позже")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        android.util.Log.d("MainActivity", "handleIntent: ${intent?.extras}")
        if (intent?.getStringExtra("navigate_to") == "call") {
            val callId = intent.getStringExtra("callId") ?: return
            val callerId = intent.getStringExtra("callerId") ?: ""
            val callerName = intent.getStringExtra("callerName") ?: "Пользователь"
            val callerAvatar = intent.getStringExtra("callerAvatar")
            val isVideo = intent.getBooleanExtra("isVideo", false)
            
            android.util.Log.d("MainActivity", "Setting pending call: $callId from $callerName")
            
            pendingIncomingCall.value = IncomingCallData(
                callId = callId,
                callerId = callerId,
                callerName = callerName,
                callerAvatar = callerAvatar,
                isVideo = isVideo
            )
        }
    }
    
    fun consumePendingCall(): IncomingCallData? {
        val call = pendingIncomingCall.value
        pendingIncomingCall.value = null
        return call
    }
}
