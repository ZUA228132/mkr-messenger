package com.pioneer.messenger.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.pioneer.messenger.data.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

data class NearbyUser(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val lastSeen: String
)

data class PeopleMapUiState(
    val isLoading: Boolean = false,
    val currentLocation: GeoPoint? = null,
    val nearbyUsers: List<NearbyUser> = emptyList(),
    val ghostMode: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PeopleMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PeopleMapUiState())
    val uiState: StateFlow<PeopleMapUiState> = _uiState.asStateFlow()
    
    init {
        loadPrivacySettings()
    }
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(ctx: Context) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                _uiState.value = _uiState.value.copy(
                    currentLocation = geoPoint,
                    isLoading = false
                )
                updateLocationOnServer(location.latitude, location.longitude)
                refreshNearbyUsers()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Не удалось получить местоположение"
                )
            }
        }.addOnFailureListener { e: Exception ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Ошибка геолокации: ${e.message}"
            )
        }
    }
    
    private fun updateLocationOnServer(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                ApiClient.updateLocation(latitude, longitude)
            } catch (e: Exception) {
                android.util.Log.e("PeopleMapViewModel", "Failed to update location: ${e.message}")
            }
        }
    }
    
    fun refreshNearbyUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = ApiClient.getNearbyUsers()
                result.fold(
                    onSuccess = { users ->
                        _uiState.value = _uiState.value.copy(
                            nearbyUsers = users.map { 
                                NearbyUser(it.userId, it.username, it.displayName, it.avatarUrl, it.latitude, it.longitude, it.lastSeen)
                            },
                            isLoading = false
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Ошибка: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Ошибка: ${e.message}")
            }
        }
    }
    
    fun toggleGhostMode() {
        val newGhostMode = !_uiState.value.ghostMode
        _uiState.value = _uiState.value.copy(ghostMode = newGhostMode)
        
        viewModelScope.launch {
            try {
                val result = ApiClient.getPrivacySettings()
                result.onSuccess { settings ->
                    ApiClient.updatePrivacySettings(
                        settings.whoCanCall, settings.whoCanSeeAvatar, settings.whoCanMessage, settings.whoCanFindMe, newGhostMode
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(ghostMode = !newGhostMode)
            }
        }
    }
    
    private fun loadPrivacySettings() {
        viewModelScope.launch {
            try {
                val result = ApiClient.getPrivacySettings()
                result.onSuccess { settings ->
                    _uiState.value = _uiState.value.copy(ghostMode = settings.ghostMode)
                }
            } catch (e: Exception) { }
        }
    }
}
