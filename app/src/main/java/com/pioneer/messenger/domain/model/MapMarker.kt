package com.pioneer.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String? = null,
    val type: MarkerType,
    val icon: String? = null,
    val color: String = "#FF0000",
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val accessLevel: AccessLevel = AccessLevel.USER,
    val visibleTo: List<String>? = null,  // null = все с нужным уровнем доступа
    val attachments: List<Attachment> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class MarkerType {
    POINT,      // Точка
    WARNING,    // Предупреждение
    INFO,       // Информация
    TASK,       // Связано с задачей
    CUSTOM      // Пользовательский
}

@Serializable
data class MapArea(
    val id: String,
    val name: String,
    val description: String? = null,
    val points: List<GeoPoint>,
    val fillColor: String = "#FF000033",
    val strokeColor: String = "#FF0000",
    val createdBy: String,
    val createdAt: Long,
    val accessLevel: AccessLevel = AccessLevel.USER,
    val visibleTo: List<String>? = null
)

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class MapLayer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val markers: List<MapMarker> = emptyList(),
    val areas: List<MapArea> = emptyList(),
    val accessLevel: AccessLevel = AccessLevel.USER
)
