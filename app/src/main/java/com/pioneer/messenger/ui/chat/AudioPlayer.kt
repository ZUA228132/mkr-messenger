package com.pioneer.messenger.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: String? = null
    
    private val _playingState = MutableStateFlow<PlayingState>(PlayingState.Idle)
    val playingState: StateFlow<PlayingState> = _playingState
    
    fun play(context: Context, messageId: String, audioData: ByteArray) {
        // Если уже играет это сообщение - пауза
        if (currentPlayingId == messageId && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }
        
        // Если играет другое - остановить
        stop()
        
        try {
            // Сохраняем во временный файл
            val tempFile = File(context.cacheDir, "temp_audio_$messageId.m4a")
            tempFile.writeBytes(audioData)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    _playingState.value = PlayingState.Idle
                    currentPlayingId = null
                    tempFile.delete()
                }
            }
            
            currentPlayingId = messageId
            _playingState.value = PlayingState.Playing(messageId)
            
        } catch (e: Exception) {
            e.printStackTrace()
            _playingState.value = PlayingState.Error(e.message ?: "Ошибка воспроизведения")
        }
    }
    
    fun playFromFile(messageId: String, file: File) {
        if (currentPlayingId == messageId && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }
        
        stop()
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    _playingState.value = PlayingState.Idle
                    currentPlayingId = null
                }
            }
            
            currentPlayingId = messageId
            _playingState.value = PlayingState.Playing(messageId)
            
        } catch (e: Exception) {
            e.printStackTrace()
            _playingState.value = PlayingState.Error(e.message ?: "Ошибка воспроизведения")
        }
    }
    
    fun pause() {
        mediaPlayer?.pause()
        currentPlayingId?.let {
            _playingState.value = PlayingState.Paused(it)
        }
    }
    
    fun resume() {
        mediaPlayer?.start()
        currentPlayingId?.let {
            _playingState.value = PlayingState.Playing(it)
        }
    }
    
    fun stop() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        currentPlayingId = null
        _playingState.value = PlayingState.Idle
    }
    
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
    
    fun release() {
        stop()
    }
}

sealed class PlayingState {
    object Idle : PlayingState()
    data class Playing(val messageId: String) : PlayingState()
    data class Paused(val messageId: String) : PlayingState()
    data class Error(val message: String) : PlayingState()
}
