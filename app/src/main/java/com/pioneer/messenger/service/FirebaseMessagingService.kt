package com.pioneer.messenger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pioneer.messenger.R
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.ui.MainActivity
import com.pioneer.messenger.ui.calls.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PioneerFirebaseService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_MESSAGES = "pioneer_messages"
        const val CHANNEL_CALLS = "pioneer_calls"
        const val NOTIFICATION_ID_MESSAGE = 1
        const val NOTIFICATION_ID_CALL = 2
        const val PREFS_NAME = "fcm_prefs"
        const val KEY_FCM_TOKEN = "fcm_token"
        
        /**
         * Отправить токен на сервер если авторизован
         */
        fun sendTokenToServerIfAuth(context: android.content.Context, token: String? = null) {
            android.util.Log.d("FCM", "sendTokenToServerIfAuth called, hasAuth=${ApiClient.hasAuthToken()}")
            
            if (!ApiClient.hasAuthToken()) {
                android.util.Log.d("FCM", "Not authenticated, skipping")
                return
            }
            
            // Если токен не передан - получаем из Firebase напрямую
            if (token != null) {
                sendTokenInternal(context, token)
            } else {
                // Сначала пробуем из SharedPreferences
                val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val savedToken = prefs.getString(KEY_FCM_TOKEN, null)
                
                if (savedToken != null) {
                    android.util.Log.d("FCM", "Using saved token: ${savedToken.take(20)}...")
                    sendTokenInternal(context, savedToken)
                } else {
                    // Получаем токен напрямую из Firebase
                    android.util.Log.d("FCM", "No saved token, fetching from Firebase...")
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { fcmToken ->
                            android.util.Log.d("FCM", "Got token from Firebase: ${fcmToken.take(20)}...")
                            // Сохраняем
                            prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply()
                            sendTokenInternal(context, fcmToken)
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FCM", "Failed to get token from Firebase: ${e.message}")
                        }
                }
            }
        }
        
        private fun sendTokenInternal(context: android.content.Context, fcmToken: String) {
            android.util.Log.d("FCM", "sendTokenInternal: ${fcmToken.take(20)}...")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown"
                    val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    
                    android.util.Log.d("FCM", "Sending to server: device=$deviceName")
                    
                    val result = ApiClient.updateFcmToken(fcmToken, deviceId, deviceName)
                    result.fold(
                        onSuccess = { 
                            android.util.Log.d("FCM", "✓ Token sent to server successfully!") 
                        },
                        onFailure = { 
                            android.util.Log.e("FCM", "✗ Failed to send token: ${it.message}") 
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("FCM", "✗ Error sending token: ${e.message}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Канал для сообщений - с звуком
            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableLights(true)
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // Канал для звонков - максимальный приоритет
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Входящие звонки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих звонках"
                enableLights(true)
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("FCM", "New FCM token: ${token.take(20)}...")
        
        // Сохраняем токен локально
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        android.util.Log.d("FCM", "Token saved locally")
        
        // Отправляем на сервер если авторизованы
        sendTokenToServerIfAuth(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: return
        
        android.util.Log.d("FCM", "Message received: type=$type, data=$data")

        when (type) {
            "incoming_call" -> handleIncomingCall(data)
            "message" -> handleNewMessage(data)
            "call_ended" -> handleCallEnded(data)
            "call_rejected" -> handleCallRejected(data)
        }
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val callerId = data["callerId"] ?: return
        val callerName = data["callerName"] ?: "Неизвестный"
        val callerAvatar = data["callerAvatar"]
        val isVideo = data["isVideo"]?.toBoolean() ?: false
        val sdp = data["sdp"] ?: ""
        
        android.util.Log.d("FCM", "Incoming call from $callerName (callId: $callId, isVideo: $isVideo)")

        // Вибрация
        vibrate()

        // Запускаем IncomingCallActivity с правильными флагами
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("callId", callId)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("isVideo", isVideo)
            putExtra("sdp", sdp)
        }
        startActivity(intent)

        // Показываем уведомление для lock screen
        showIncomingCallNotification(callId, callerId, callerName, callerAvatar, isVideo)
    }

    private fun showIncomingCallNotification(
        callId: String, 
        callerId: String,
        callerName: String, 
        callerAvatar: String?,
        isVideo: Boolean
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent для принятия звонка - передаём ВСЕ данные
        val acceptIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "ACCEPT_CALL"
            putExtra("callId", callId)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("isVideo", isVideo)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent для отклонения звонка
        val rejectIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "REJECT_CALL"
            putExtra("callId", callId)
            putExtra("callerId", callerId)
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            this, 1, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full screen intent
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("isVideo", isVideo)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 2, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "Видеозвонок" else "Аудиозвонок"

        val notification = NotificationCompat.Builder(this, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callType)
            .setContentText("Входящий звонок от $callerName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отклонить", rejectPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Ответить", acceptPendingIntent)
            .build()

        // Используем тот же ID что и в IncomingCallActivity
        notificationManager.notify(IncomingCallActivity.CALL_NOTIFICATION_ID, notification)
    }

    private fun handleNewMessage(data: Map<String, String>) {
        val chatId = data["chatId"] ?: return
        val senderName = data["senderName"] ?: "Новое сообщение"
        val messageText = data["messageText"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatId", chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun handleCallEnded(data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(IncomingCallActivity.CALL_NOTIFICATION_ID)
        stopVibration()
    }

    private fun handleCallRejected(data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(IncomingCallActivity.CALL_NOTIFICATION_ID)
        stopVibration()
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
        }
    }
}
