package com.pioneer.messenger.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.PreferencesManager
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authManager: AuthManager
) : ViewModel() {
    
    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()
    
    private val _phone = MutableStateFlow("")
    val phone = _phone.asStateFlow()
    
    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    
    private val _bio = MutableStateFlow("")
    val bio = _bio.asStateFlow()
    
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()
    
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl = _avatarUrl.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    
    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar = _isUploadingAvatar.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    init {
        loadProfile()
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            val currentUser = authManager.currentUser.first()
            _name.value = preferencesManager.userName.first().ifEmpty { currentUser?.displayName ?: "Пользователь" }
            _phone.value = preferencesManager.userPhone.first()
            _email.value = preferencesManager.userEmail.first()
            _bio.value = preferencesManager.userBio.first()
            _username.value = currentUser?.username ?: preferencesManager.userUsername.first()
            _avatarUrl.value = preferencesManager.userAvatarUrl.first()
            
            // Загружаем актуальные данные с сервера
            currentUser?.id?.let { userId ->
                try {
                    val result = ApiClient.getUser(userId)
                    result.onSuccess { user ->
                        _avatarUrl.value = user.avatarUrl
                        _name.value = user.displayName.ifEmpty { _name.value }
                        if (!user.avatarUrl.isNullOrEmpty()) {
                            preferencesManager.saveUserAvatarUrl(user.avatarUrl)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EditProfileVM", "Failed to load user: ${e.message}")
                }
            }
        }
    }
    
    fun updateName(value: String) { _name.value = value }
    fun updatePhone(value: String) { _phone.value = value }
    fun updateEmail(value: String) { _email.value = value }
    fun updateBio(value: String) { _bio.value = value }
    fun updateUsername(value: String) { _username.value = value }
    
    fun uploadAvatar(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                inputStream.close()
                
                android.util.Log.d("EditProfileVM", "Uploading avatar: ${bytes.size} bytes")
                
                val result = ApiClient.uploadAvatar(bytes)
                result.fold(
                    onSuccess = { response ->
                        android.util.Log.d("EditProfileVM", "Avatar uploaded: ${response.url}")
                        
                        // Сохраняем URL локально
                        _avatarUrl.value = response.url
                        preferencesManager.saveUserAvatarUrl(response.url)
                        
                        // Обновляем профиль на сервере с новым аватаром
                        val updateResult = ApiClient.updateProfile(
                            displayName = _name.value,
                            bio = _bio.value.ifEmpty { null },
                            avatarUrl = response.url
                        )
                        
                        updateResult.fold(
                            onSuccess = { user ->
                                android.util.Log.d("EditProfileVM", "Profile updated with avatar: ${user.avatarUrl}")
                            },
                            onFailure = { e ->
                                android.util.Log.e("EditProfileVM", "Failed to update profile: ${e.message}")
                            }
                        )
                    },
                    onFailure = { e ->
                        android.util.Log.e("EditProfileVM", "Failed to upload avatar: ${e.message}")
                        _error.value = "Ошибка загрузки: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("EditProfileVM", "Error uploading avatar: ${e.message}")
                _error.value = "Ошибка: ${e.message}"
            } finally {
                _isUploadingAvatar.value = false
            }
        }
    }
    
    fun saveProfile(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            
            try {
                val result = ApiClient.updateProfile(
                    displayName = _name.value,
                    bio = _bio.value.ifEmpty { null },
                    phone = _phone.value.ifEmpty { null },
                    email = _email.value.ifEmpty { null }
                )
                
                result.fold(
                    onSuccess = {
                        preferencesManager.saveProfile(
                            name = _name.value,
                            phone = _phone.value,
                            email = _email.value,
                            bio = _bio.value,
                            username = _username.value
                        )
                        onSuccess()
                    },
                    onFailure = { e ->
                        preferencesManager.saveProfile(
                            name = _name.value,
                            phone = _phone.value,
                            email = _email.value,
                            bio = _bio.value,
                            username = _username.value
                        )
                        onSuccess()
                    }
                )
            } catch (e: Exception) {
                preferencesManager.saveProfile(
                    name = _name.value,
                    phone = _phone.value,
                    email = _email.value,
                    bio = _bio.value,
                    username = _username.value
                )
                onSuccess()
            } finally {
                _isSaving.value = false
            }
        }
    }
}

// OkHttpClient для Coil с поддержкой самоподписанных сертификатов
private val trustAllOkHttpClient: OkHttpClient by lazy {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, SecureRandom())
    
    OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val name by viewModel.name.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val email by viewModel.email.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val username by viewModel.username.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isUploadingAvatar by viewModel.isUploadingAvatar.collectAsState()
    
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .okHttpClient(trustAllOkHttpClient)
            .crossfade(true)
            .build()
    }
    
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(it, context) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Закрыть")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { viewModel.saveProfile(onSave) }) {
                            Text("Сохранить")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Аватар
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    // Аватар с загрузкой изображения
                    val fullAvatarUrl = remember(avatarUrl) {
                        when {
                            avatarUrl.isNullOrEmpty() -> null
                            avatarUrl!!.startsWith("http") -> avatarUrl
                            avatarUrl!!.startsWith("/") -> "${ApiClient.BASE_URL}$avatarUrl"
                            else -> "${ApiClient.BASE_URL}/uploads/$avatarUrl"
                        }
                    }
                    
                    if (!fullAvatarUrl.isNullOrEmpty()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fullAvatarUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Аватар",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            loading = {
                                AvatarPlaceholder(name)
                            },
                            error = {
                                AvatarPlaceholder(name)
                            }
                        )
                    } else {
                        AvatarPlaceholder(name)
                    }
                    
                    // Кнопка смены аватара
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .clickable { imagePicker.launch("image/*") },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isUploadingAvatar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Основная информация
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Основная информация",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Имя") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = username,
                        onValueChange = { viewModel.updateUsername(it) },
                        label = { Text("Имя пользователя") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                        placeholder = { Text("@username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { viewModel.updatePhone(it) },
                        label = { Text("Телефон") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = email,
                        onValueChange = { viewModel.updateEmail(it) },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // О себе
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "О себе",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { viewModel.updateBio(it) },
                        label = { Text("Расскажите о себе") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun AvatarPlaceholder(name: String) {
    Surface(
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(2).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
        }
    }
}
