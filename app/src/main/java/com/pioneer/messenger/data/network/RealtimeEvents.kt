package com.pioneer.messenger.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Глобальный объект для real-time событий через WebSocket
 */
object RealtimeEvents {
    
    // Новое сообщение
    private val _newMessage = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newMessage: SharedFlow<String> = _newMessage.asSharedFlow()
    
    // Typing событие
    data class TypingEvent(
        val chatId: String,
        val userId: String,
        val userName: String
    )
    private val _typing = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val typing: SharedFlow<TypingEvent> = _typing.asSharedFlow()
    
    // Статус пользователя (онлайн/офлайн)
    data class UserStatusEvent(
        val userId: String,
        val isOnline: Boolean
    )
    private val _userStatus = MutableSharedFlow<UserStatusEvent>(extraBufferCapacity = 64)
    val userStatus: SharedFlow<UserStatusEvent> = _userStatus.asSharedFlow()
    
    // Методы для отправки событий
    fun tryEmitNewMessage(messageId: String) {
        _newMessage.tryEmit(messageId)
    }
    
    fun tryEmitTyping(chatId: String, userId: String, userName: String) {
        _typing.tryEmit(TypingEvent(chatId, userId, userName))
    }
    
    fun tryEmitUserStatus(userId: String, isOnline: Boolean) {
        _userStatus.tryEmit(UserStatusEvent(userId, isOnline))
    }
}
