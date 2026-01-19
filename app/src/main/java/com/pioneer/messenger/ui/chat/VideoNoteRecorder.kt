package com.pioneer.messenger.ui.chat

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executor

@Composable
fun VideoNoteRecorderDialog(
    onDismiss: () -> Unit,
    onVideoRecorded: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var useFrontCamera by remember { mutableStateOf(true) }
    
    // Таймер записи
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording && recordingDuration < 60) {
                delay(1000)
                recordingDuration++
            }
            if (recordingDuration >= 60) {
                // Автостоп после 60 секунд
                recording?.stop()
            }
        }
    }
    
    Dialog(
        onDismissRequest = {
            recording?.stop()
            cameraProvider?.unbindAll()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            // Круглое превью камеры
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isRecording) 4.dp else 2.dp,
                        color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                CameraPreview(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    useFrontCamera = useFrontCamera,
                    onCameraReady = { provider, capture ->
                        cameraProvider = provider
                        videoCapture = capture
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Таймер сверху
            if (isRecording) {
                val infiniteTransition = rememberInfiniteTransition(label = "rec")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                    label = "alpha"
                )
                
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        String.format("%d:%02d", recordingDuration / 60, recordingDuration % 60),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Кнопки управления
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Закрыть
                IconButton(
                    onClick = {
                        recording?.stop()
                        cameraProvider?.unbindAll()
                        onDismiss()
                    }
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Кнопка записи
                IconButton(
                    onClick = {
                        if (isRecording) {
                            // Остановить и отправить
                            recording?.stop()
                        } else {
                            // Начать запись
                            videoCapture?.let { capture ->
                                val outputFile = File(
                                    context.cacheDir,
                                    "video_note_${System.currentTimeMillis()}.mp4"
                                )
                                
                                val outputOptions = FileOutputOptions.Builder(outputFile).build()
                                
                                recording = capture.output
                                    .prepareRecording(context, outputOptions)
                                    .withAudioEnabled()
                                    .start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                if (!event.hasError()) {
                                                    onVideoRecorded(outputFile)
                                                    cameraProvider?.unbindAll()
                                                    onDismiss()
                                                } else {
                                                    Log.e("VideoNote", "Error: ${event.error}")
                                                    outputFile.delete()
                                                }
                                            }
                                            is VideoRecordEvent.Start -> {
                                                isRecording = true
                                            }
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color.Red else Color.White)
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        null,
                        tint = if (isRecording) Color.White else Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                // Переключить камеру
                IconButton(
                    onClick = {
                        if (!isRecording) {
                            useFrontCamera = !useFrontCamera
                        }
                    },
                    enabled = !isRecording
                ) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        null,
                        tint = if (isRecording) Color.Gray else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Подсказка
            if (!isRecording) {
                Text(
                    "Нажмите для записи (до 60 сек)",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}


@Composable
private fun CameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    useFrontCamera: Boolean,
    onCameraReady: (ProcessCameraProvider, VideoCapture<Recorder>) -> Unit,
    modifier: Modifier = Modifier
) {
    val previewView = remember { PreviewView(context) }
    
    LaunchedEffect(useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            
            val videoCapture = VideoCapture.withOutput(recorder)
            
            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                
                onCameraReady(cameraProvider, videoCapture)
                
            } catch (e: Exception) {
                Log.e("CameraPreview", "Use case binding failed", e)
            }
            
        }, ContextCompat.getMainExecutor(context))
    }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
