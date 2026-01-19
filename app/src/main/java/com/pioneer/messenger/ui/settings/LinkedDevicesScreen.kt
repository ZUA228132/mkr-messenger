package com.pioneer.messenger.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.util.HapticFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class WebSession(
    val sessionId: String,
    val deviceInfo: String?,
    val authorizedAt: Long
)

// Use ApiClient.WebSession instead of local WebSession
typealias WebSessionData = com.pioneer.messenger.data.network.ApiClient.WebSession

@Serializable
data class QrCodeData(
    val type: String,
    val code: String,
    val server: String
)

@HiltViewModel
class LinkedDevicesViewModel @Inject constructor() : ViewModel() {
    
    private val _sessions = MutableStateFlow<List<WebSessionData>>(emptyList())
    val sessions = _sessions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult = _authResult.asStateFlow()
    
    init {
        loadSessions()
    }
    
    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getWebSessions()
                _sessions.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoading.value = false
        }
    }
    
    fun authorizeSession(code: String, deviceInfo: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = ApiClient.authorizeWebSession(code, deviceInfo ?: "Pioneer Web")
                _authResult.value = if (success) AuthResult.Success else AuthResult.Error("Не удалось авторизовать")
                if (success) loadSessions()
            } catch (e: Exception) {
                _authResult.value = AuthResult.Error(e.message ?: "Ошибка")
            }
            _isLoading.value = false
        }
    }
    
    fun terminateSession(sessionId: String) {
        viewModelScope.launch {
            try {
                ApiClient.terminateWebSession(sessionId)
                loadSessions()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun clearAuthResult() {
        _authResult.value = null
    }
    
    sealed class AuthResult {
        object Success : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(
    onBack: () -> Unit,
    viewModel: LinkedDevicesViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current
    
    val sessions by viewModel.sessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val authResult by viewModel.authResult.collectAsState()
    
    var showScanner by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    
    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) showScanner = true
    }
    
    // Handle auth result
    LaunchedEffect(authResult) {
        when (authResult) {
            is LinkedDevicesViewModel.AuthResult.Success -> {
                HapticFeedback.success(context)
                showScanner = false
                showManualInput = false
                manualCode = ""
            }
            is LinkedDevicesViewModel.AuthResult.Error -> {
                HapticFeedback.error(context)
            }
            null -> {}
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Связанные устройства", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        HapticFeedback.lightClick(view)
                        onBack() 
                    }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    HapticFeedback.mediumClick(view)
                    if (hasCameraPermission) {
                        showScanner = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                icon = { Icon(Icons.Default.QrCodeScanner, null) },
                text = { Text("Сканировать QR") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Используйте Pioneer Web для доступа к сообщениям с компьютера. Откройте web.kluboksrm.ru и отсканируйте QR-код.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Manual code input button
            TextButton(
                onClick = { 
                    HapticFeedback.lightClick(view)
                    showManualInput = true 
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Outlined.Keyboard, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ввести код вручную")
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Active sessions
            Text(
                "Активные сессии",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            if (isLoading && sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.DevicesOther,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Нет активных сессий",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(sessions) { session ->
                        SessionItem(
                            session = session,
                            onTerminate = {
                                HapticFeedback.mediumClick(view)
                                viewModel.terminateSession(session.sessionId)
                            }
                        )
                    }
                }
            }
        }
    }
    
    // QR Scanner dialog
    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onCodeScanned = { code ->
                viewModel.authorizeSession(code)
            }
        )
    }
    
    // Manual input dialog
    if (showManualInput) {
        AlertDialog(
            onDismissRequest = { 
                showManualInput = false
                manualCode = ""
                viewModel.clearAuthResult()
            },
            title = { Text("Введите код", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "Введите 6-значный код, отображаемый на экране Pioneer Web",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) manualCode = it },
                        label = { Text("Код") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    AnimatedVisibility(visible = authResult is LinkedDevicesViewModel.AuthResult.Error) {
                        Text(
                            (authResult as? LinkedDevicesViewModel.AuthResult.Error)?.message ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualCode.length == 6) {
                            viewModel.authorizeSession(manualCode)
                        }
                    },
                    enabled = manualCode.length == 6 && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Подключить")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showManualInput = false
                    manualCode = ""
                    viewModel.clearAuthResult()
                }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Success snackbar
    if (authResult is LinkedDevicesViewModel.AuthResult.Success) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearAuthResult()
        }
    }
}


@Composable
private fun SessionItem(
    session: WebSessionData,
    onTerminate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Computer,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.deviceInfo ?: "Pioneer Web",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Подключено ${formatTime(session.authorizedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onTerminate) {
                Icon(
                    Icons.Default.Close,
                    "Отключить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "только что"
        diff < 3600_000 -> "${diff / 60_000} мин. назад"
        diff < 86400_000 -> "${diff / 3600_000} ч. назад"
        else -> "${diff / 86400_000} дн. назад"
    }
}

@Composable
private fun QrScannerDialog(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scannedCode by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { 
            Text("Сканируйте QR-код", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) 
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                
                                val scanner = BarcodeScanning.getClient()
                                
                                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    @androidx.camera.core.ExperimentalGetImage
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && scannedCode == null) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    if (barcode.valueType == Barcode.TYPE_TEXT) {
                                                        val rawValue = barcode.rawValue ?: continue
                                                        try {
                                                            val qrData = Json.decodeFromString<QrCodeData>(rawValue)
                                                            if (qrData.type == "pioneer_web_login") {
                                                                scannedCode = qrData.code
                                                                onCodeScanned(qrData.code)
                                                            }
                                                        } catch (e: Exception) {
                                                            // Not our QR code
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                                
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Scanning overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Corner markers
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Top-left
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopStart)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 8.dp))
                            )
                            // Top-right
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 8.dp))
                            )
                            // Bottom-left
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomStart)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 8.dp))
                            )
                            // Bottom-right
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomEnd = 8.dp))
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Наведите камеру на QR-код на экране Pioneer Web",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
