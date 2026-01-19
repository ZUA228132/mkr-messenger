package com.pioneer.messenger.ui.security

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityCheckViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {
    
    private val _checkState = MutableStateFlow<SecurityCheckState>(SecurityCheckState.Idle)
    val checkState = _checkState.asStateFlow()
    
    private val _result = MutableStateFlow<SecurityManager.SecurityCheckResult?>(null)
    val result = _result.asStateFlow()

    fun performCheck() {
        viewModelScope.launch {
            _checkState.value = SecurityCheckState.Checking
            
            // Имитация проверки с задержкой для UX
            delay(500)
            _checkState.value = SecurityCheckState.CheckingRoot
            delay(400)
            _checkState.value = SecurityCheckState.CheckingDebugger
            delay(400)
            _checkState.value = SecurityCheckState.CheckingEmulator
            delay(400)
            _checkState.value = SecurityCheckState.CheckingApps
            delay(400)
            _checkState.value = SecurityCheckState.CheckingIntegrity
            delay(300)
            
            val checkResult = securityManager.performSecurityCheck()
            _result.value = checkResult
            _checkState.value = SecurityCheckState.Complete
        }
    }
    
    sealed class SecurityCheckState {
        object Idle : SecurityCheckState()
        object Checking : SecurityCheckState()
        object CheckingRoot : SecurityCheckState()
        object CheckingDebugger : SecurityCheckState()
        object CheckingEmulator : SecurityCheckState()
        object CheckingApps : SecurityCheckState()
        object CheckingIntegrity : SecurityCheckState()
        object Complete : SecurityCheckState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCheckScreen(
    onBack: () -> Unit,
    viewModel: SecurityCheckViewModel = hiltViewModel()
) {
    val checkState by viewModel.checkState.collectAsState()
    val result by viewModel.result.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.performCheck()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Проверка безопасности") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Анимированный индикатор
            SecurityStatusIndicator(
                state = checkState,
                result = result
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Статус проверки
            when (checkState) {
                is SecurityCheckViewModel.SecurityCheckState.Complete -> {
                    result?.let { SecurityResultCard(it) }
                }
                else -> {
                    CheckingStatusCard(checkState)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Кнопка повторной проверки
            if (checkState is SecurityCheckViewModel.SecurityCheckState.Complete) {
                OutlinedButton(
                    onClick = { viewModel.performCheck() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверить снова")
                }
            }
        }
    }
}

@Composable
private fun SecurityStatusIndicator(
    state: SecurityCheckViewModel.SecurityCheckState,
    result: SecurityManager.SecurityCheckResult?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    val isComplete = state is SecurityCheckViewModel.SecurityCheckState.Complete
    val isSecure = result?.isSecure == true
    
    val backgroundColor = when {
        !isComplete -> Color(0xFF2196F3)
        isSecure -> Color(0xFF4CAF50)
        else -> Color(0xFFFF5252)
    }
    
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.3f),
                        backgroundColor.copy(alpha = 0.1f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Внешнее кольцо
        if (!isComplete) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(140.dp)
                    .rotate(rotation),
                color = backgroundColor,
                strokeWidth = 4.dp
            )
        }
        
        // Внутренний круг
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = backgroundColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        !isComplete -> Icons.Outlined.Security
                        isSecure -> Icons.Default.CheckCircle
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = when {
            !isComplete -> "Сканирование..."
            isSecure -> "Устройство защищено"
            else -> "Обнаружены угрозы"
        },
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = backgroundColor
    )
}


@Composable
private fun CheckingStatusCard(state: SecurityCheckViewModel.SecurityCheckState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CheckItem("Проверка Root-доступа", state is SecurityCheckViewModel.SecurityCheckState.CheckingRoot)
            CheckItem("Проверка отладчика", state is SecurityCheckViewModel.SecurityCheckState.CheckingDebugger)
            CheckItem("Проверка эмулятора", state is SecurityCheckViewModel.SecurityCheckState.CheckingEmulator)
            CheckItem("Проверка приложений", state is SecurityCheckViewModel.SecurityCheckState.CheckingApps)
            CheckItem("Проверка целостности", state is SecurityCheckViewModel.SecurityCheckState.CheckingIntegrity)
        }
    }
}

@Composable
private fun CheckItem(title: String, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(title)
    }
}

@Composable
private fun SecurityResultCard(result: SecurityManager.SecurityCheckResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isSecure) 
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else 
                Color(0xFFFF5252).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Уровень риска
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Уровень риска", fontWeight = FontWeight.Medium)
                RiskLevelBadge(result.riskLevel)
            }
            
            if (result.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Обнаруженные проблемы:",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF5252)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                result.issues.forEach { issue ->
                    IssueItem(issue)
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Все проверки пройдены успешно",
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskLevelBadge(level: SecurityManager.RiskLevel) {
    val (color, text) = when (level) {
        SecurityManager.RiskLevel.SAFE -> Color(0xFF4CAF50) to "Безопасно"
        SecurityManager.RiskLevel.LOW -> Color(0xFF8BC34A) to "Низкий"
        SecurityManager.RiskLevel.MEDIUM -> Color(0xFFFFC107) to "Средний"
        SecurityManager.RiskLevel.HIGH -> Color(0xFFFF9800) to "Высокий"
        SecurityManager.RiskLevel.CRITICAL -> Color(0xFFFF5252) to "Критический"
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun IssueItem(issue: SecurityManager.SecurityIssue) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconColor = when (issue.severity) {
            SecurityManager.Severity.CRITICAL -> Color(0xFFFF5252)
            SecurityManager.Severity.HIGH -> Color(0xFFFF9800)
            SecurityManager.Severity.MEDIUM -> Color(0xFFFFC107)
            SecurityManager.Severity.LOW -> Color(0xFF8BC34A)
        }
        
        Icon(
            Icons.Default.Error,
            null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(issue.description, fontWeight = FontWeight.Medium)
            Text(
                "Уровень: ${issue.severity.name}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
