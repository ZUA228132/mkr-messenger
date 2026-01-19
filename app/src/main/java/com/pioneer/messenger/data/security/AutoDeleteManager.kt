package com.pioneer.messenger.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер автоматического удаления сообщений
 * Упрощённая версия без WorkManager
 */
@Singleton
class AutoDeleteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Интервалы автоудаления в миллисекундах
        const val DELETE_1_DAY = 24 * 60 * 60 * 1000L
        const val DELETE_3_DAYS = 3 * DELETE_1_DAY
        const val DELETE_7_DAYS = 7 * DELETE_1_DAY
        const val DELETE_30_DAYS = 30 * DELETE_1_DAY
        
        // Специальные режимы
        const val DELETE_AFTER_READ = -1L
        const val DELETE_ON_EXIT = -2L
        const val DELETE_NEVER = 0L
    }
    
    // Хранилище запланированных удалений
    private val scheduledDeletions = mutableMapOf<String, Long>()
    
    /**
     * Запланировать автоудаление сообщения
     */
    fun scheduleMessageDeletion(
        messageId: String,
        chatId: String,
        deleteAfterMs: Long
    ) {
        if (deleteAfterMs <= 0) return
        val deleteAt = System.currentTimeMillis() + deleteAfterMs
        scheduledDeletions["msg_$messageId"] = deleteAt
    }
    
    /**
     * Запланировать удаление всех сообщений чата
     */
    fun scheduleChatDeletion(chatId: String, deleteAfterMs: Long) {
        if (deleteAfterMs <= 0) return
        val deleteAt = System.currentTimeMillis() + deleteAfterMs
        scheduledDeletions["chat_$chatId"] = deleteAt
    }
    
    /**
     * Отменить запланированное удаление сообщения
     */
    fun cancelMessageDeletion(messageId: String) {
        scheduledDeletions.remove("msg_$messageId")
    }
    
    /**
     * Отменить запланированное удаление чата
     */
    fun cancelChatDeletion(chatId: String) {
        scheduledDeletions.remove("chat_$chatId")
    }
    
    /**
     * Отменить все запланированные удаления
     */
    fun cancelAll() {
        scheduledDeletions.clear()
    }
    
    /**
     * Проверить и выполнить запланированные удаления
     */
    fun checkAndExecuteDeletions(): List<String> {
        val now = System.currentTimeMillis()
        val toDelete = scheduledDeletions.filter { it.value <= now }
        
        toDelete.keys.forEach { key ->
            scheduledDeletions.remove(key)
        }
        
        return toDelete.keys.toList()
    }
    
    /**
     * Очистка старого кэша
     */
    fun cleanupCache() {
        val cacheDir = context.cacheDir
        val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 дней
        val now = System.currentTimeMillis()
        
        cacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAge) {
                file.deleteRecursively()
            }
        }
    }
    
    /**
     * Получить время до удаления в читаемом формате
     */
    fun formatDeleteTime(deleteAfterMs: Long): String {
        return when (deleteAfterMs) {
            DELETE_AFTER_READ -> "После прочтения"
            DELETE_ON_EXIT -> "При выходе"
            DELETE_NEVER -> "Никогда"
            DELETE_1_DAY -> "1 день"
            DELETE_3_DAYS -> "3 дня"
            DELETE_7_DAYS -> "7 дней"
            DELETE_30_DAYS -> "30 дней"
            else -> {
                val days = deleteAfterMs / DELETE_1_DAY
                if (days > 0) "$days дн." else "${deleteAfterMs / 1000 / 60} мин."
            }
        }
    }
}
