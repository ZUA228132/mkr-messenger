package com.pioneer.messenger

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PioneerApp : Application() {
    
    companion object {
        private const val TAG = "PioneerApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Получаем и отправляем FCM токен при КАЖДОМ старте
        initFirebaseMessaging()
    }
    
    private fun initFirebaseMessaging() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "FCM: Fetching token failed", task.exception)
                return@addOnCompleteListener
            }
            
            val token = task.result
            Log.d(TAG, "FCM: Token at start: ${token.take(30)}...")
            
            // Сохраняем токен локально
            val prefs = getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()
            Log.d(TAG, "FCM: Token saved locally")
            
            // Отправляем на сервер если авторизованы
            com.pioneer.messenger.service.PioneerFirebaseService.sendTokenToServerIfAuth(this, token)
        }
    }
}
