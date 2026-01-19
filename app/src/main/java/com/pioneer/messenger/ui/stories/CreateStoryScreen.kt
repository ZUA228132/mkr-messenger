package com.pioneer.messenger.ui.stories

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryScreen(
    onBack: () -> Unit,
    onStoryCreated: () -> Unit,
    onNavigateToReels: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var storyText by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }
    var isReelsMode by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        capturedImageUri = uri
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (capturedImageUri != null) {
            // Превью захваченного изображения
            StoryPreviewScreen(
                imageUri = capturedImageUri!!,
                text = storyText,
                onTextChange = { storyText = it },
                onBack = { capturedImageUri = null },
                isUploading = isUploading,
                uploadError = uploadError,
                onPublish = {
                    scope.launch {
                        isUploading = true
                        uploadError = null
                        try {
                            // Загружаем изображение
                            val inputStream = context.contentResolver.openInputStream(capturedImageUri!!)
                            val bytes = inputStream?.readBytes() ?: throw Exception("Не удалось прочитать файл")
                            inputStream.close()
                            
                            val uploadResult = ApiClient.uploadFile(bytes, "story_${System.currentTimeMillis()}.jpg", "image/jpeg")
                            uploadResult.fold(
                                onSuccess = { uploadResponse ->
                                    // Создаём историю
                                    val storyResult = ApiClient.createStory(
                                        mediaUrl = uploadResponse.url,
                                        mediaType = "image",
                                        text = storyText.ifEmpty { null },
                                        duration = 5
                                    )
                                    storyResult.fold(
                                        onSuccess = {
                                            isUploading = false
                                            onStoryCreated()
                                        },
                                        onFailure = { e ->
                                            uploadError = e.message ?: "Ошибка создания истории"
                                            isUploading = false
                                        }
                                    )
                                },
                                onFailure = { e ->
                                    uploadError = e.message ?: "Ошибка загрузки"
                                    isUploading = false
                                }
                            )
                        } catch (e: Exception) {
                            uploadError = e.message ?: "Ошибка"
                            isUploading = false
                        }
                    }
                }
            )
        } else if (hasCameraPermission) {
            // Камера
            CameraPreview(
                isFrontCamera = isFrontCamera,
                isFlashOn = isFlashOn,
                onImageCaptured = { uri -> capturedImageUri = uri }
            )
            
            // Верхняя панель
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.Close, "Закрыть", tint = Color.White)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(
                        onClick = { isFlashOn = !isFlashOn },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            "Вспышка",
                            tint = if (isFlashOn) MKRColors.Primary else Color.White
                        )
                    }
                    
                    IconButton(
                        onClick = { /* Настройки */ },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Filled.Settings, "Настройки", tint = Color.White)
                    }
                }
            }
            
            // Нижняя панель
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Режимы съёмки
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    StoryModeChip(
                        text = "ИСТОРИЯ",
                        selected = !isReelsMode,
                        onClick = { isReelsMode = false }
                    )
                    StoryModeChip(
                        text = "REELS",
                        selected = isReelsMode,
                        onClick = { 
                            isReelsMode = true
                            onNavigateToReels()
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Галерея
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, "Галерея", tint = Color.White)
                    }
                    
                    // Кнопка съёмки
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(
                                width = 4.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(MKRColors.Primary, MKRColors.Secondary)
                                ),
                                shape = CircleShape
                            )
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                // Захват фото - будет реализовано через ImageCapture
                            }
                    )
                    
                    // Переключение камеры
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Filled.Cameraswitch, "Переключить камеру", tint = Color.White)
                    }
                }
            }
        } else {
            // Нет разрешения
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    null,
                    tint = MKRColors.Primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Нужен доступ к камере",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = MKRColors.Primary)
                ) {
                    Text("Разрешить")
                }
            }
        }
    }
}

@Composable
fun StoryModeChip(text: String, selected: Boolean, onClick: () -> Unit = {}) {
    Text(
        text = text,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    isFlashOn: Boolean,
    onImageCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun StoryPreviewScreen(
    imageUri: Uri,
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    isUploading: Boolean = false,
    uploadError: String? = null
) {
    var showTextInput by remember { mutableStateOf(false) }
    var textOffsetX by remember { mutableStateOf(0f) }
    var textOffsetY by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Изображение
        coil.compose.AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        
        // Текст на истории - перемещаемый
        if (text.isNotEmpty()) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(textOffsetX.roundToInt(), textOffsetY.roundToInt()) }
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            textOffsetX += dragAmount.x
                            textOffsetY += dragAmount.y
                        }
                    }
            )
        }
        
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                enabled = !isUploading,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.ArrowBack, "Назад", tint = Color.White)
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = { showTextInput = true },
                    enabled = !isUploading,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.TextFields, "Текст", tint = Color.White)
                }
                
                IconButton(
                    onClick = { /* Стикеры */ },
                    enabled = !isUploading,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.EmojiEmotions, "Стикеры", tint = Color.White)
                }
            }
        }
        
        // Ошибка загрузки
        uploadError?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp),
                containerColor = Color.Red.copy(alpha = 0.9f)
            ) {
                Text(error, color = Color.White)
            }
        }
        
        // Кнопка публикации
        Button(
            onClick = onPublish,
            enabled = !isUploading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.8f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MKRColors.Primary
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Публикация...", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Filled.Send, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Опубликовать историю", fontWeight = FontWeight.SemiBold)
            }
        }
        
        // Диалог ввода текста
        if (showTextInput) {
            AlertDialog(
                onDismissRequest = { showTextInput = false },
                title = { Text("Добавить текст") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = { Text("Введите текст...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showTextInput = false }) {
                        Text("Готово")
                    }
                }
            )
        }
    }
}
