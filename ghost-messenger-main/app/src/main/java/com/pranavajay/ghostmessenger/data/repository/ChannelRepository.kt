package com.pranavajay.ghostmessenger.data.repository

import com.pranavajay.ghostmessenger.data.local.SessionManager
import com.pranavajay.ghostmessenger.data.models.Channel
import com.pranavajay.ghostmessenger.data.models.CreateChannelRequest
import com.pranavajay.ghostmessenger.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    
    suspend fun getCurrentUserId(): String {
        return sessionManager.getUserId() ?: ""
    }
    
    suspend fun getChannels(): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.getChannels("Bearer $token")
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Failed to load channels")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun createChannel(name: String, description: String?, isPublic: Boolean): Result<Channel> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val request = CreateChannelRequest(name, description, isPublic)
            val response = apiService.createChannel("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to create channel")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun subscribeChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.subscribeChannel("Bearer $token", channelId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to subscribe")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun unsubscribeChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.unsubscribeChannel("Bearer $token", channelId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to unsubscribe")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun searchChannels(query: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.searchChannels("Bearer $token", query)
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Failed to search channels")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
