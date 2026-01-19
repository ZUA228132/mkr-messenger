package com.pranavajay.ghostmessenger.data.repository

import com.pranavajay.ghostmessenger.data.local.SessionManager
import com.pranavajay.ghostmessenger.data.models.AuthResponse
import com.pranavajay.ghostmessenger.data.models.LoginRequest
import com.pranavajay.ghostmessenger.data.models.RegisterRequest
import com.pranavajay.ghostmessenger.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body()?.success == true) {
                val authResponse = response.body()!!
                authResponse.token?.let { token ->
                    authResponse.user?.let { user ->
                        sessionManager.saveSession(
                            token = token,
                            userId = user.id,
                            userName = user.username,
                            email = user.email,
                            image = user.profileImage
                        )
                    }
                }
                Result.Success(authResponse)
            } else {
                Result.Error(response.body()?.message ?: "Login failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun register(username: String, email: String, password: String, phone: String? = null): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.register(RegisterRequest(username, email, password, phone))
            if (response.isSuccessful && response.body()?.success == true) {
                val authResponse = response.body()!!
                authResponse.token?.let { token ->
                    authResponse.user?.let { user ->
                        sessionManager.saveSession(
                            token = token,
                            userId = user.id,
                            userName = user.username,
                            email = user.email,
                            image = user.profileImage
                        )
                    }
                }
                Result.Success(authResponse)
            } else {
                Result.Error(response.body()?.message ?: "Registration failed")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun logout() {
        sessionManager.clearSession()
    }
    
    suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
    
    suspend fun getToken(): String? = sessionManager.getToken()
}
