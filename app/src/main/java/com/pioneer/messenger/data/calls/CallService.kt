package com.pioneer.messenger.data.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pioneer.messenger.R
import com.pioneer.messenger.data.crypto.CryptoManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Сервис аудиозвонков с шифрованием
 */
@AndroidEntryPoint
class CallService : Service() {
    
    @Inject lateinit var cryptoManager: CryptoManager
    
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false
    
    private var currentCallKey: ByteArray? = null
    
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState
    
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pioneer_calls"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): CallService = this@CallService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Звонки Pioneer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о звонках"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createCallNotification(callerName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Звонок Pioneer")
            .setContentText("Разговор с $callerName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Начать исходящий звонок
     */
    fun startCall(userId: String, sessionKey: ByteArray) {
        currentCallKey = sessionKey
        _callState.value = CallState.Calling(userId)
        
        startForeground(NOTIFICATION_ID, createCallNotification(userId))
        
        // Инициализация аудио
        initAudio()
    }
    
    /**
     * Принять входящий звонок
     */
    fun acceptCall(callerId: String, sessionKey: ByteArray) {
        currentCallKey = sessionKey
        _callState.value = CallState.InCall(callerId)
        
        startForeground(NOTIFICATION_ID, createCallNotification(callerId))
        
        initAudio()
        startAudioStreaming()
    }
    
    /**
     * Звонок принят другой стороной
     */
    fun onCallAccepted() {
        val currentState = _callState.value
        if (currentState is CallState.Calling) {
            _callState.value = CallState.InCall(currentState.userId)
            startAudioStreaming()
        }
    }
    
    private fun initAudio() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            bufferSize * 2
        )
        
        val playBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(playBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
    
    private fun startAudioStreaming() {
        isRecording = true
        isPlaying = true
        
        audioRecord?.startRecording()
        audioTrack?.play()
        
        // Захват и отправка аудио
        scope.launch {
            captureAndSendAudio()
        }
    }
    
    private suspend fun captureAndSendAudio() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        val buffer = ByteArray(bufferSize)
        
        while (isRecording) {
            val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            
            if (bytesRead > 0) {
                // Шифруем аудио данные
                val key = currentCallKey ?: continue
                val encrypted = cryptoManager.encrypt(buffer.copyOf(bytesRead), key)
                
                // Отправляем через WebRTC или Bluetooth
                sendEncryptedAudio(encrypted.ciphertext, encrypted.iv)
            }
            
            delay(20) // ~50 пакетов в секунду
        }
    }
    
    private fun sendEncryptedAudio(ciphertext: String, iv: String) {
        // Здесь отправка через WebRTC DataChannel или Bluetooth
        // В реальной реализации интегрируется с сетевым слоем
    }
    
    /**
     * Получение и воспроизведение зашифрованного аудио
     */
    fun onEncryptedAudioReceived(ciphertext: String, iv: String) {
        scope.launch {
            try {
                val key = currentCallKey ?: return@launch
                val encrypted = com.pioneer.messenger.data.crypto.EncryptedData(ciphertext, iv)
                val decrypted = cryptoManager.decrypt(encrypted, key)
                
                // Воспроизводим
                audioTrack?.write(decrypted, 0, decrypted.size)
            } catch (e: Exception) {
                // Ошибка расшифровки
            }
        }
    }
    
    /**
     * Завершить звонок
     */
    fun endCall() {
        isRecording = false
        isPlaying = false
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        currentCallKey = null
        _callState.value = CallState.Idle
        
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    
    /**
     * Включить/выключить микрофон
     */
    fun toggleMute(): Boolean {
        isRecording = !isRecording
        if (isRecording) {
            audioRecord?.startRecording()
        } else {
            audioRecord?.stop()
        }
        return !isRecording
    }
    
    /**
     * Включить/выключить динамик
     */
    fun toggleSpeaker(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val isSpeakerOn = !audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        return isSpeakerOn
    }
    
    override fun onDestroy() {
        super.onDestroy()
        endCall()
        scope.cancel()
    }
}

sealed class CallState {
    data object Idle : CallState()
    data class Incoming(val callerId: String, val callerName: String) : CallState()
    data class Calling(val userId: String) : CallState()
    data class InCall(val userId: String) : CallState()
    data object Ended : CallState()
}
