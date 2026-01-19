package com.pioneer.messenger.ui.map

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.hilt.navigation.compose.hiltViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import com.pioneer.messenger.R

// Типы военных меток с иконками
enum class MilitaryMarkerType(
    val title: String, 
    val emoji: String, 
    val colorValue: Long,
    val iconRes: Int? = null
) {
    FRIENDLY_POSITION("Своя позиция", "🟢", 0xFF4CAF50, R.drawable.ic_marker_friendly),
    ENEMY_POSITION("Позиция противника", "🔴", 0xFFE53935, R.drawable.ic_marker_enemy),
    OBSERVATION_POST("Наблюдательный пост", "👁", 0xFF2196F3, R.drawable.ic_marker_observation),
    CHECKPOINT("Блокпост", "🛡", 0xFF9C27B0, R.drawable.ic_marker_checkpoint),
    SUPPLY_POINT("Точка снабжения", "📦", 0xFFFF9800, R.drawable.ic_marker_supply),
    MEDICAL_POINT("Медпункт", "🏥", 0xFFE91E63, R.drawable.ic_marker_medical),
    COMMAND_POST("Командный пункт", "⭐", 0xFFFFEB3B, R.drawable.ic_marker_command),
    DANGER_ZONE("Опасная зона", "⚠️", 0xFFFF5722, R.drawable.ic_marker_danger),
    MINEFIELD("Минное поле", "💣", 0xFF795548, R.drawable.ic_marker_mine),
    ARTILLERY("Артиллерия", "🎯", 0xFF607D8B, R.drawable.ic_marker_artillery),
    DRONE_ZONE("Зона дронов", "🛸", 0xFF00BCD4, R.drawable.ic_marker_drone),
    EVACUATION("Эвакуация", "🚑", 0xFF8BC34A, R.drawable.ic_marker_evacuation),
    CUSTOM("Другое", "📍", 0xFF9E9E9E, null)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val markers by viewModel.markers.collectAsState(initial = emptyList())
    val areas by viewModel.areas.collectAsState(initial = emptyList())
    val context = LocalContext.current
    
    var showAddMarkerDialog by remember { mutableStateOf(false) }
    var showEditMarkerDialog by remember { mutableStateOf<MapMarkerUi?>(null) }
    var showMarkersList by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isDrawingArea by remember { mutableStateOf(false) }
    var areaPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showMarkersList = true }) {
                        Icon(Icons.Outlined.List, "Список меток")
                    }
                    IconButton(onClick = {
                        mapViewRef?.controller?.setCenter(GeoPoint(55.7558, 37.6173))
                    }) {
                        Icon(Icons.Default.MyLocation, "Центр")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = {
                        isDrawingArea = !isDrawingArea
                        if (!isDrawingArea) areaPoints = emptyList()
                    },
                    containerColor = if (isDrawingArea) 
                        MaterialTheme.colorScheme.tertiary 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        if (isDrawingArea) Icons.Default.Done else Icons.Default.Draw,
                        "Рисовать область"
                    )
                }
                
                FloatingActionButton(
                    onClick = { showAddMarkerDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.AddLocation, "Добавить метку", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapViewRef = this
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(55.7558, 37.6173))
                        
                        setOnTouchListener { _, event ->
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                val geoPoint = projection.fromPixels(
                                    event.x.toInt(), 
                                    event.y.toInt()
                                ) as GeoPoint
                                
                                if (isDrawingArea) {
                                    areaPoints = areaPoints + geoPoint
                                    updateAreaOverlay(this, areaPoints)
                                } else {
                                    selectedLocation = geoPoint
                                }
                            }
                            false
                        }
                    }
                },
                update = { mapView ->
                    mapView.overlays.removeAll { it is Marker }
                    
                    markers.forEach { marker ->
                        val markerType = try {
                            MilitaryMarkerType.valueOf(marker.type)
                        } catch (e: Exception) {
                            MilitaryMarkerType.CUSTOM
                        }
                        
                        val osmMarker = Marker(mapView).apply {
                            position = GeoPoint(marker.latitude, marker.longitude)
                            title = marker.title
                            snippet = marker.description
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            
                            // Создаём иконку с цветом типа метки
                            icon = createMarkerIcon(context, markerType)
                            
                            setOnMarkerClickListener { _, _ ->
                                showEditMarkerDialog = marker
                                true
                            }
                        }
                        mapView.overlays.add(osmMarker)
                    }
                    
                    mapView.overlays.removeAll { it is Polygon && it.title != "temp_area" }
                    
                    areas.forEach { area ->
                        val polygon = Polygon().apply {
                            points = area.points.map { GeoPoint(it.latitude, it.longitude) }
                            fillPaint.color = android.graphics.Color.parseColor(area.fillColor)
                            outlinePaint.color = android.graphics.Color.parseColor(area.strokeColor)
                            outlinePaint.strokeWidth = 3f
                            title = area.name
                        }
                        mapView.overlays.add(polygon)
                    }
                    
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Режим рисования области
            if (isDrawingArea && areaPoints.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Draw, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Точек: ${areaPoints.size}")
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(onClick = { areaPoints = emptyList() }) {
                            Text("Очистить")
                        }
                        TextButton(
                            onClick = {
                                if (areaPoints.size >= 3) {
                                    viewModel.createArea("Зона", areaPoints.map {
                                        MapGeoPoint(it.latitude, it.longitude)
                                    })
                                    areaPoints = emptyList()
                                    isDrawingArea = false
                                }
                            },
                            enabled = areaPoints.size >= 3
                        ) {
                            Text("Сохранить")
                        }
                    }
                }
            }
            
            // Координаты выбранной точки
            selectedLocation?.let { loc ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .padding(bottom = 80.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    
    if (showAddMarkerDialog) {
        AddMilitaryMarkerDialog(
            location = selectedLocation,
            onDismiss = {
                showAddMarkerDialog = false
                selectedLocation = null
            },
            onAdd = { title, description, type ->
                selectedLocation?.let { loc ->
                    viewModel.addMarker(loc.latitude, loc.longitude, title, description, type)
                }
                showAddMarkerDialog = false
                selectedLocation = null
            }
        )
    }
    
    showEditMarkerDialog?.let { marker ->
        EditMarkerDialog(
            marker = marker,
            onDismiss = { showEditMarkerDialog = null },
            onSave = { title, description ->
                viewModel.updateMarker(marker.id, title, description)
                showEditMarkerDialog = null
            },
            onDelete = {
                viewModel.deleteMarker(marker.id)
                showEditMarkerDialog = null
            }
        )
    }
    
    if (showMarkersList) {
        MarkersListSheet(
            markers = markers,
            onDismiss = { showMarkersList = false },
            onMarkerClick = { marker ->
                mapViewRef?.controller?.animateTo(GeoPoint(marker.latitude, marker.longitude))
                showMarkersList = false
            },
            onEditMarker = { marker ->
                showEditMarkerDialog = marker
                showMarkersList = false
            }
        )
    }
}

// Создание иконки маркера с цветом
private fun createMarkerIcon(context: Context, type: MilitaryMarkerType): BitmapDrawable {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Рисуем круг с цветом типа
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = type.colorValue.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f - 4, size / 2.5f, paint)
    
    // Белая обводка
    paint.apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f - 4, size / 2.5f, paint)
    
    // Рисуем эмодзи в центре
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(type.emoji, size / 2f, size / 2f + 4, textPaint)
    
    // Рисуем "хвостик" маркера
    val path = Path().apply {
        moveTo(size / 2f - 8, size / 2f + 10)
        lineTo(size / 2f, size.toFloat())
        lineTo(size / 2f + 8, size / 2f + 10)
        close()
    }
    paint.apply {
        color = type.colorValue.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, paint)
    
    return BitmapDrawable(context.resources, bitmap)
}

private fun updateAreaOverlay(mapView: MapView, points: List<GeoPoint>) {
    mapView.overlays.removeAll { it is Polygon && it.title == "temp_area" }
    
    if (points.size >= 2) {
        val polygon = Polygon().apply {
            this.points = points
            fillPaint.color = android.graphics.Color.argb(50, 255, 100, 100)
            outlinePaint.color = android.graphics.Color.parseColor("#FF6B6B")
            outlinePaint.strokeWidth = 4f
            title = "temp_area"
        }
        mapView.overlays.add(polygon)
    }
    
    mapView.invalidate()
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMilitaryMarkerDialog(
    location: GeoPoint?,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MilitaryMarkerType.CUSTOM) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая метка", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (location != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Place, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Нажмите на карту для выбора места",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Text("Тип метки", style = MaterialTheme.typography.labelMedium)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MilitaryMarkerType.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { type ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { 
                                            selectedType = type
                                            if (title.isBlank()) title = type.title
                                        },
                                    color = if (selectedType == type)
                                        Color(type.colorValue).copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(type.emoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(type.title, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание / Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("Количество, вооружение, время...") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, description, selectedType.name) },
                enabled = title.isNotBlank() && location != null
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun EditMarkerDialog(
    marker: MapMarkerUi,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(marker.title) }
    var description by remember { mutableStateOf(marker.description ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить метку?") },
            text = { Text("Метка \"${marker.title}\" будет удалена.") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Редактировать", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val markerType = try {
                            MilitaryMarkerType.valueOf(marker.type)
                        } catch (e: Exception) {
                            MilitaryMarkerType.CUSTOM
                        }
                        Text(markerType.emoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${String.format("%.4f", marker.latitude)}, ${String.format("%.4f", marker.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(title, description) },
                    enabled = title.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkersListSheet(
    markers: List<MapMarkerUi>,
    onDismiss: () -> Unit,
    onMarkerClick: (MapMarkerUi) -> Unit,
    onEditMarker: (MapMarkerUi) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Метки на карте",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (markers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.LocationOff, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Нет меток", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(markers) { marker ->
                        val markerType = try {
                            MilitaryMarkerType.valueOf(marker.type)
                        } catch (e: Exception) {
                            MilitaryMarkerType.CUSTOM
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMarkerClick(marker) },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(markerType.colorValue).copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Иконка с цветом
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = Color(markerType.colorValue)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(markerType.emoji, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        marker.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    marker.description?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                IconButton(onClick = { onEditMarker(marker) }) {
                                    Icon(Icons.Default.Edit, null)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

data class MapMarkerUi(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String?,
    val type: String
)

data class MapAreaUi(
    val id: String,
    val name: String,
    val points: List<MapGeoPoint>,
    val fillColor: String,
    val strokeColor: String
)

data class MapGeoPoint(
    val latitude: Double,
    val longitude: Double
)
