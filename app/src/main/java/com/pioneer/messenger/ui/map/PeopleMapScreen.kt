package com.pioneer.messenger.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pioneer.messenger.data.network.ApiClient
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleMapScreen(
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: PeopleMapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            viewModel.startLocationUpdates(context)
        }
    }
    
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedUser by remember { mutableStateOf<NearbyUser?>(null) }
    
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        if (hasLocationPermission) {
            viewModel.startLocationUpdates(context)
        }
    }
    
    // Обновляем маркеры при изменении списка пользователей
    LaunchedEffect(uiState.nearbyUsers, mapViewRef) {
        mapViewRef?.let { mapView ->
            updateUserMarkers(context, mapView, uiState.nearbyUsers) { user ->
                selectedUser = user
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Карта", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${uiState.nearbyUsers.size} рядом",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Ghost Mode toggle
                    IconButton(onClick = { viewModel.toggleGhostMode() }) {
                        Icon(
                            if (uiState.ghostMode) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                            "Режим невидимки",
                            tint = if (uiState.ghostMode) MKRColors.Primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(onClick = { viewModel.refreshNearbyUsers() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasLocationPermission) {
                // Экран запроса разрешения
                LocationPermissionRequest(
                    onRequestPermission = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            } else {
                // Карта
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapViewRef = this
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            
                            // Центрируем на текущей позиции или Москве
                            val initialLocation = uiState.currentLocation ?: GeoPoint(55.7558, 37.6173)
                            controller.setCenter(initialLocation)
                        }
                    },
                    update = { mapView ->
                        uiState.currentLocation?.let { location ->
                            if (mapView.mapCenter.latitude == 55.7558) {
                                mapView.controller.animateTo(location)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Панель Ghost Mode
                if (uiState.ghostMode) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.8f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                null,
                                tint = MKRColors.Primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Режим невидимки включён",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                // Кнопка центрирования
                FloatingActionButton(
                    onClick = {
                        uiState.currentLocation?.let { location ->
                            mapViewRef?.controller?.animateTo(location)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MKRColors.Primary
                ) {
                    Icon(Icons.Default.MyLocation, "Моё местоположение", tint = Color.White)
                }
                
                // Загрузка
                if (uiState.isLoading) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MKRColors.Primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Поиск пользователей...")
                        }
                    }
                }
                
                // Ошибка
                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
    
    // Диалог пользователя
    selectedUser?.let { user ->
        UserProfileDialog(
            user = user,
            onDismiss = { selectedUser = null },
            onStartChat = {
                scope.launch {
                    try {
                        val result = ApiClient.createChat(
                            type = "direct",
                            name = "",
                            participantIds = listOf(user.userId)
                        )
                        result.onSuccess { chat ->
                            selectedUser = null
                            onNavigateToChat(chat.id)
                        }
                    } catch (e: Exception) {
                        // Пробуем открыть напрямую
                        selectedUser = null
                        onNavigateToChat(user.userId)
                    }
                }
            }
        )
    }
}

@Composable
private fun LocationPermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.LocationOn,
            null,
            modifier = Modifier.size(80.dp),
            tint = MKRColors.Primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Карта пользователей",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Найди людей рядом с тобой.\nВидишь всех, кто не в ghost-mode.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MKRColors.Primary)
        ) {
            Icon(Icons.Default.LocationOn, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Разрешить доступ к геолокации")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Ты можешь включить режим невидимки,\nчтобы другие не видели тебя на карте",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UserProfileDialog(
    user: NearbyUser,
    onDismiss: () -> Unit,
    onStartChat: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Аватар
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MKRColors.Primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (user.avatarUrl != null) {
                            // TODO: AsyncImage
                            Text(
                                user.displayName.take(2).uppercase(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                user.displayName.take(2).uppercase(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    user.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "@${user.username}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Был(а) ${user.lastSeen}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartChat,
                colors = ButtonDefaults.buttonColors(containerColor = MKRColors.Primary)
            ) {
                Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Написать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

private suspend fun updateUserMarkers(
    context: Context,
    mapView: MapView,
    users: List<NearbyUser>,
    onUserClick: (NearbyUser) -> Unit
) {
    // Удаляем старые маркеры пользователей
    mapView.overlays.removeAll { it is Marker && it.id?.startsWith("user_") == true }
    
    users.forEach { user ->
        val marker = Marker(mapView).apply {
            id = "user_${user.userId}"
            position = GeoPoint(user.latitude, user.longitude)
            title = user.displayName
            snippet = "@${user.username}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Создаём иконку аватара
            icon = createUserMarkerIcon(context, user)
            
            setOnMarkerClickListener { _, _ ->
                onUserClick(user)
                true
            }
        }
        mapView.overlays.add(marker)
    }
    
    mapView.invalidate()
}

private fun createUserMarkerIcon(context: Context, user: NearbyUser): BitmapDrawable {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Рисуем круглый фон
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f - 4, size / 2.5f, bgPaint)
    
    // Цветная обводка
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#00D9FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(size / 2f, size / 2f - 4, size / 2.5f, strokePaint)
    
    // Инициалы
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#00D9FF")
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val initials = user.displayName.take(2).uppercase()
    canvas.drawText(initials, size / 2f, size / 2f + 2, textPaint)
    
    // Хвостик маркера
    val path = Path().apply {
        moveTo(size / 2f - 8, size / 2f + 14)
        lineTo(size / 2f, size.toFloat())
        lineTo(size / 2f + 8, size / 2f + 14)
        close()
    }
    bgPaint.color = android.graphics.Color.WHITE
    canvas.drawPath(path, bgPaint)
    
    return BitmapDrawable(context.resources, bitmap)
}
