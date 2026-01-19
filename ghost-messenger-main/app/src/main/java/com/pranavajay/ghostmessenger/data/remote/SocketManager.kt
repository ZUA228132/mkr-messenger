package com.pranavajay.ghostmessenger.data.remote

import com.google.gson.Gson
import com.pranavajay.ghostmessenger.data.models.Message
import com.pranavajay.ghostmessenger.data.models.MessageType
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor() {
    
    private var socket: Socket? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _messageFlow = MutableSharedFlow<Message>()
    val messageFlow = _messageFlow.asSharedFlow()
    
    private val _typingFlow = MutableSharedFlow<Pair<String, Boolean>>()
    val typingFlow = _typingFlow.asSharedFlow()
    
    private val _onlineStatusFlow = MutableSharedFlow<Pair<String, Boolean>>()
    val onlineStatusFlow = _onlineStatusFlow.asSharedFlow()
    
    fun connect(token: String, baseUrl: String) {
        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = true
            }
            
            socket = IO.socket(baseUrl, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                println("Socket connected")
            }
            
            socket?.on("message") { args ->
                val data = args[0] as JSONObject
                val message = parseMessage(data)
                scope.launch {
                    _messageFlow.emit(message)
                }
            }
            
            socket?.on("typing") { args ->
                val data = args[0] as JSONObject
                val userId = data.getString("userId")
                val isTyping = data.getBoolean("isTyping")
                scope.launch {
                    _typingFlow.emit(Pair(userId, isTyping))
                }
            }
            
            socket?.on("online_status") { args ->
                val data = args[0] as JSONObject
                val userId = data.getString("userId")
                val isOnline = data.getBoolean("isOnline")
                scope.launch {
                    _onlineStatusFlow.emit(Pair(userId, isOnline))
                }
            }
            
            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun parseMessage(data: JSONObject): Message {
        return Message(
            id = data.getString("id"),
            chatId = data.getString("chatId"),
            senderId = data.getString("senderId"),
            receiverId = data.getString("receiverId"),
            content = data.getString("content"),
            type = try { MessageType.valueOf(data.optString("type", "TEXT")) } catch (e: Exception) { MessageType.TEXT },
            mediaUrl = data.optString("mediaUrl", null),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
    }
    
    fun sendMessage(message: Message) {
        val json = JSONObject().apply {
            put("type", "message")
            put("data", JSONObject().apply {
                put("chatId", message.chatId)
                put("receiverId", message.receiverId)
                put("content", message.content)
                put("messageType", message.type.name)
                put("mediaUrl", message.mediaUrl)
            }.toString())
        }
        socket?.emit("message", json)
    }
    
    fun sendTyping(chatId: String, isTyping: Boolean) {
        val json = JSONObject().apply {
            put("type", "typing")
            put("data", JSONObject().apply {
                put("chatId", chatId)
                put("isTyping", isTyping)
            }.toString())
        }
        socket?.emit("message", json)
    }
    
    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
