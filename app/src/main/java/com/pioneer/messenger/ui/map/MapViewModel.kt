package com.pioneer.messenger.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pioneer.messenger.data.auth.AuthManager
import com.pioneer.messenger.data.local.MapAreaEntity
import com.pioneer.messenger.data.local.MapDao
import com.pioneer.messenger.data.local.MapMarkerEntity
import com.pioneer.messenger.domain.model.AccessLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapDao: MapDao,
    private val authManager: AuthManager
) : ViewModel() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    val markers: Flow<List<MapMarkerUi>> = mapDao.getMarkersByAccess(AccessLevel.OWNER.level).map { entities ->
        entities.map { entity ->
            MapMarkerUi(
                id = entity.id,
                latitude = entity.latitude,
                longitude = entity.longitude,
                title = entity.title,
                description = entity.description,
                type = entity.type
            )
        }
    }
    
    val areas: Flow<List<MapAreaUi>> = mapDao.getAreasByAccess(AccessLevel.OWNER.level).map { entities ->
        entities.map { entity ->
            val points = try {
                json.decodeFromString<List<MapGeoPoint>>(entity.points)
            } catch (e: Exception) {
                emptyList()
            }
            
            MapAreaUi(
                id = entity.id,
                name = entity.name,
                points = points,
                fillColor = entity.fillColor,
                strokeColor = entity.strokeColor
            )
        }
    }
    
    fun addMarker(
        latitude: Double,
        longitude: Double,
        title: String,
        description: String,
        type: String
    ) {
        viewModelScope.launch {
            val currentUser = authManager.currentUser.first() ?: return@launch
            
            val marker = MapMarkerEntity(
                id = UUID.randomUUID().toString(),
                latitude = latitude,
                longitude = longitude,
                title = title,
                description = description,
                type = type,
                icon = null,
                color = when (type) {
                    "WARNING" -> "#FF0000"
                    "INFO" -> "#2196F3"
                    else -> "#4CAF50"
                },
                createdBy = currentUser.id,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                accessLevel = currentUser.accessLevel,
                visibleTo = null,
                metadata = "{}"
            )
            
            mapDao.insertMarker(marker)
        }
    }
    
    fun createArea(name: String, points: List<MapGeoPoint>) {
        viewModelScope.launch {
            val currentUser = authManager.currentUser.first() ?: return@launch
            
            val area = MapAreaEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                description = null,
                points = json.encodeToString(points),
                fillColor = "#FF000033",
                strokeColor = "#FF0000",
                createdBy = currentUser.id,
                createdAt = System.currentTimeMillis(),
                accessLevel = currentUser.accessLevel,
                visibleTo = null
            )
            
            mapDao.insertArea(area)
        }
    }
    
    fun updateMarker(markerId: String, title: String, description: String) {
        viewModelScope.launch {
            val marker = mapDao.getMarkersByAccess(AccessLevel.OWNER.level).first()
                .find { it.id == markerId } ?: return@launch
            
            val updated = marker.copy(
                title = title,
                description = description,
                updatedAt = System.currentTimeMillis()
            )
            mapDao.insertMarker(updated)
        }
    }
    
    fun deleteMarker(markerId: String) {
        viewModelScope.launch {
            val marker = mapDao.getMarkersByAccess(AccessLevel.OWNER.level).first()
                .find { it.id == markerId } ?: return@launch
            mapDao.deleteMarker(marker)
        }
    }
    
    fun updateArea(areaId: String, name: String) {
        viewModelScope.launch {
            val area = mapDao.getAreasByAccess(AccessLevel.OWNER.level).first()
                .find { it.id == areaId } ?: return@launch
            
            val updated = area.copy(name = name)
            mapDao.insertArea(updated)
        }
    }
    
    fun deleteArea(areaId: String) {
        viewModelScope.launch {
            val area = mapDao.getAreasByAccess(AccessLevel.OWNER.level).first()
                .find { it.id == areaId } ?: return@launch
            mapDao.deleteArea(area)
        }
    }
}
