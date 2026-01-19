package com.pranavajay.ghostmessenger.data.repository

import com.pranavajay.ghostmessenger.data.local.MessageDao
import com.pranavajay.ghostmessenger.data.local.SessionManager
import com.pranavajay.ghostmessenger.data.models.*
import com.pranavajay.ghostmessenger.data.remote.ApiService
import com.pranavajay.ghostmessenger.data.remote.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService,
    private val socketManager: SocketManager,
    private val messageDao: MessageDao,
    private val sessionManager: SessionManager
) {
    val incomingMessages: Flow<Message> = socketManager.messageFlow
    val typingStatus: Flow<Pair<String, Boolean>> = socketManager.typingFlow
    val onlineStatus: Flow<Pair<String, Boolean>> = socketManager.onlineStatusFlow
    
    suspend fun getChats(): Result<List<Chat>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.getChats("Bearer $token")
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Failed to load chats")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun getMessages(chatId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.getMessages("Bearer $token", chatId)
            if (response.isSuccessful) {
                val messages = response.body() ?: emptyList()
                // Cache messages locally
                messageDao.insertMessages(messages)
                Result.Success(messages)
            } else {
                Result.Error("Failed to load messages")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    fun getLocalMessages(chatId: String): Flow<List<Message>> = messageDao.getMessagesByChatId(chatId)
    
    suspend fun sendMessage(
        chatId: String, 
        receiverId: String, 
        content: String, 
        replyToId: String? = null,
        type: String = "TEXT", 
        mediaUrl: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val request = SendMessageRequest(chatId, receiverId, content, type, mediaUrl, replyToId)
            val response = apiService.sendMessage("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                val message = response.body()!!
                messageDao.insertMessage(message)
                Result.Success(message)
            } else {
                Result.Error("Failed to send message")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun connectSocket() {
        val token = sessionManager.getToken() ?: return
        socketManager.connect(token, "http://10.0.2.2:8080")
    }
    
    fun disconnectSocket() {
        socketManager.disconnect()
    }
    
    fun sendTyping(chatId: String, isTyping: Boolean) {
        socketManager.sendTyping(chatId, isTyping)
    }
    
    // Reactions
    suspend fun addReaction(messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.addReaction("Bearer $token", AddReactionRequest(messageId, emoji))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to add reaction")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun removeReaction(messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.removeReaction("Bearer $token", RemoveReactionRequest(messageId, emoji))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to remove reaction")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    // Calls
    suspend fun initiateCall(receiverId: String, callType: String): Result<CallResponse> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.initiateCall("Bearer $token", InitiateCallRequest(receiverId, callType))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to initiate call")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun acceptCall(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.acceptCall("Bearer $token", callId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to accept call")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun declineCall(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.declineCall("Bearer $token", callId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to decline call")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun endCall(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.endCall("Bearer $token", callId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to end call")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun getCallHistory(): Result<List<CallEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.getCallHistory("Bearer $token")
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Failed to get call history")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    // Ghost Mode / Disappearing Messages
    suspend fun setGhostMode(chatId: String, duration: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.setGhostMode("Bearer $token", chatId, GhostModeRequest(duration != null))
            if (response.isSuccessful) {
                if (duration != null) {
                    apiService.setDisappearingMessages("Bearer $token", chatId, DisappearingSettingsRequest(duration))
                }
                Result.Success(Unit)
            } else {
                Result.Error("Failed to set ghost mode")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    // Pin/Archive Chat
    suspend fun pinChat(chatId: String, pinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.pinChat("Bearer $token", chatId, PinChatRequest(pinned))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to pin chat")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun archiveChat(chatId: String, archived: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.archiveChat("Bearer $token", chatId, ArchiveChatRequest(archived))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to archive chat")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    // Forward/Delete Messages
    suspend fun forwardMessage(messageId: String, toChatIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.forwardMessage("Bearer $token", ForwardMessageRequest(messageId, toChatIds))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to forward message")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.deleteMessage("Bearer $token", messageId, forEveryone)
            if (response.isSuccessful) {
                messageDao.deleteMessage(messageId)
                Result.Success(Unit)
            } else {
                Result.Error("Failed to delete message")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    // Search
    suspend fun searchMessages(query: String, chatId: String? = null): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.searchMessages("Bearer $token", query, chatId)
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Failed to search messages")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun globalSearch(query: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.globalSearch("Bearer $token", query)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to search")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
