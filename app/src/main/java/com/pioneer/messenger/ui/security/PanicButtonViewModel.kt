package com.pioneer.messenger.ui.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.security.SecureDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PanicButtonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureDataManager: SecureDataManager
) : ViewModel() {
    
    /**
     * Выполнить экстренное удаление всех данных
     */
    suspend fun performPanicWipe(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Удаляем все данные через SecureDataManager
                val wipeSuccess = secureDataManager.panicWipe()
                
                // 2. Очищаем Room базу данных
                clearDatabase()
                
                // 3. Отзываем токены на сервере (если есть связь)
                revokeServerTokens()
                
                // 4. Очищаем WebView данные
                clearWebViewData()
                
                wipeSuccess
            } catch (e: Exception) {
                android.util.Log.e("PanicWipe", "Error during panic wipe: ${e.message}")
                false
            }
        }
    }
    
    private fun clearDatabase() {
        try {
            // Удаляем файлы базы данных
            context.databaseList().forEach { dbName ->
                context.deleteDatabase(dbName)
            }
        } catch (e: Exception) {
            android.util.Log.e("PanicWipe", "Database clear error: ${e.message}")
        }
    }
    
    private suspend fun revokeServerTokens() {
        try {
            // Отправляем запрос на сервер для отзыва всех токенов
            // Это предотвратит доступ с других устройств
            com.pioneer.messenger.data.network.ApiClient.revokeAllTokens()
        } catch (e: Exception) {
            // Игнорируем ошибки сети - главное удалить локальные данные
            android.util.Log.w("PanicWipe", "Could not revoke server tokens: ${e.message}")
        }
    }
    
    private fun clearWebViewData() {
        try {
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        } catch (e: Exception) {
            android.util.Log.e("PanicWipe", "WebView clear error: ${e.message}")
        }
    }
}
