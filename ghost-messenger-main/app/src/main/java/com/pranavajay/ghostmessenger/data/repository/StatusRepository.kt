package com.pranavajay.ghostmessenger.data.repository

import android.net.Uri
import com.pranavajay.ghostmessenger.data.local.SessionManager
import com.pranavajay.ghostmessenger.data.models.CreateStatusRequest
import com.pranavajay.ghostmessenger.data.models.Status
import com.pranavajay.ghostmessenger.data.models.StatusGroup
import com.pranavajay.ghostmessenger.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    
    suspend fun getMyStatuses(): Result<List<Status>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.getMyStatuses("Bearer $token")
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Failed to load statuses")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun getContactsStatuses(): Result<List<StatusGroup>> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.getContactsStatuses("Bearer $token")
            if (response.isSuccessful) {
                val statuses = response.body() ?: emptyList()
                // Group statuses by user
                val grouped = statuses.groupBy { it.userId }.map { (userId, userStatuses) ->
                    StatusGroup(
                        userId = userId,
                        username = userStatuses.firstOrNull()?.username ?: "Unknown",
                        profileImage = userStatuses.firstOrNull()?.profileImage,
                        statuses = userStatuses,
                        hasUnviewed = userStatuses.any { !it.viewedByMe }
                    )
                }
                Result.Success(grouped)
            } else {
                Result.Error("Failed to load statuses")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun createStatus(
        content: String?,
        mediaUrl: String?,
        mediaType: String,
        backgroundColor: String?
    ): Result<Status> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val request = CreateStatusRequest(
                content = content,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                backgroundColor = backgroundColor
            )
            val response = apiService.createStatus("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to create status")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun markStatusViewed(statusId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.viewStatus("Bearer $token", statusId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to mark status as viewed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun reactToStatus(statusId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.reactToStatus("Bearer $token", statusId, mapOf("emoji" to emoji))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to react to status")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun uploadMedia(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            // This would need proper file handling in a real app
            // For now, return a placeholder
            Result.Success("uploaded_media_url")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Upload failed")
        }
    }
    
    suspend fun deleteStatus(statusId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager.getToken() ?: return@withContext Result.Error("Not logged in")
            val response = apiService.deleteStatus("Bearer $token", statusId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to delete status")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
