package com.pioneer.messenger.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.PreferencesManager
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val bio: String = "",
    val status: String = "Онлайн",
    val role: String? = null,
    val avatarUrl: String? = null,
    val isOnline: Boolean = true,
    val accessLevel: Int = 0,
    val isVerified: Boolean = false
)

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val preferencesManager: PreferencesManager,
    private val userDao: com.pioneer.messenger.data.local.UserDao
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    fun loadProfile(userId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Убеждаемся что токен установлен
                authManager.restoreSession()
                
                val currentUser = authManager.currentUser.first()
                
                val profile = if (userId == null || userId == currentUser?.id) {
                    // Свой профиль - загружаем из AuthManager и PreferencesManager
                    val savedName = preferencesManager.userName.first()
                    val savedEmail = preferencesManager.userEmail.first()
                    val savedBio = preferencesManager.userBio.first()
                    val savedAvatarUrl = preferencesManager.userAvatarUrl.first()
                    
                    // Также пробуем загрузить актуальные данные с сервера
                    val serverUser = currentUser?.id?.let { 
                        try {
                            ApiClient.getUser(it).getOrNull()
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileViewModel", "Failed to load user from server: ${e.message}")
                            null
                        }
                    }
                    
                    UserProfile(
                        id = currentUser?.id ?: "",
                        name = serverUser?.displayName ?: savedName.ifEmpty { currentUser?.displayName ?: "Пользователь" },
                        username = serverUser?.username ?: currentUser?.username ?: "",
                        email = savedEmail,
                        bio = serverUser?.bio ?: savedBio,
                        status = "Онлайн",
                        role = when (currentUser?.accessLevel) {
                            10 -> "Администратор"
                            5 -> "Модератор"
                            else -> null
                        },
                        avatarUrl = serverUser?.avatarUrl ?: savedAvatarUrl,
                        accessLevel = currentUser?.accessLevel ?: 0,
                        isOnline = true,
                        isVerified = serverUser?.isVerified ?: false
                    )
                } else {
                    // Чужой профиль - сначала пробуем с сервера
                    var serverUser: ApiClient.UserResponse? = null
                    try {
                        serverUser = ApiClient.getUser(userId).getOrNull()
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Failed to load other user: ${e.message}")
                    }
                    
                    // Если с сервера не получилось - пробуем из локальной БД
                    if (serverUser == null) {
                        val localUser = userDao.getUserById(userId)
                        if (localUser != null) {
                            UserProfile(
                                id = localUser.id,
                                name = localUser.displayName,
                                username = localUser.username,
                                email = "",
                                bio = "",
                                status = if (localUser.isOnline) "Онлайн" else "Был(а) недавно",
                                avatarUrl = localUser.avatarUrl,
                                isOnline = localUser.isOnline,
                                accessLevel = localUser.accessLevel,
                                role = when (localUser.accessLevel) {
                                    10 -> "Администратор"
                                    5 -> "Модератор"
                                    else -> null
                                },
                                isVerified = false
                            )
                        } else {
                            // Совсем нет данных
                            UserProfile(
                                id = userId,
                                name = "Пользователь",
                                username = "",
                                status = "Был(а) недавно"
                            )
                        }
                    } else {
                        // Сохраняем в локальную БД для кэша
                        userDao.insertUser(com.pioneer.messenger.data.local.UserEntity(
                            id = serverUser.id,
                            username = serverUser.username,
                            displayName = serverUser.displayName,
                            publicKey = serverUser.publicKey,
                            avatarUrl = serverUser.avatarUrl,
                            accessLevel = serverUser.accessLevel,
                            isOnline = serverUser.isOnline,
                            lastSeen = System.currentTimeMillis()
                        ))
                        
                        UserProfile(
                            id = userId,
                            name = serverUser.displayName,
                            username = serverUser.username,
                            email = "",
                            bio = serverUser.bio ?: "",
                            status = if (serverUser.isOnline) "Онлайн" else "Был(а) недавно",
                            avatarUrl = serverUser.avatarUrl,
                            isOnline = serverUser.isOnline,
                            accessLevel = serverUser.accessLevel,
                            role = when (serverUser.accessLevel) {
                                10 -> "Администратор"
                                5 -> "Модератор"
                                else -> null
                            },
                            isVerified = serverUser.isVerified
                        )
                    }
                }
                
                _uiState.value = ProfileUiState(profile = profile)
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error loading profile: ${e.message}")
                _uiState.value = ProfileUiState(error = e.message)
            }
        }
    }
    
    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true)
            
            try {
                // Читаем байты изображения
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Не удалось прочитать файл")
                inputStream.close()
                
                // Загружаем на сервер
                val result = ApiClient.uploadAvatar(bytes)
                
                result.onSuccess { response ->
                    // Сохраняем URL локально
                    preferencesManager.saveUserAvatarUrl(response.url)
                    
                    // Обновляем UI
                    _uiState.value = _uiState.value.copy(
                        isUploadingAvatar = false,
                        profile = _uiState.value.profile?.copy(avatarUrl = response.url)
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingAvatar = false,
                        error = "Ошибка загрузки: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    error = "Ошибка: ${e.message}"
                )
            }
        }
    }
    
    fun startChat() {
        // TODO: Начать чат
    }
    
    fun startCall() {
        // TODO: Начать звонок
    }
    
    fun logout() {
        viewModelScope.launch {
            authManager.logout()
        }
    }
}
