package com.pioneer.messenger.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pioneer.messenger.ui.admin.AdminScreen
import com.pioneer.messenger.ui.auth.MKRAuthScreen
import com.pioneer.messenger.ui.auth.AuthViewModel
import com.pioneer.messenger.ui.auth.AuthUiState
import com.pioneer.messenger.ui.banned.BannedAccountScreen
import com.pioneer.messenger.ui.calls.CallScreen
import com.pioneer.messenger.ui.chat.ChatListScreen
import com.pioneer.messenger.ui.chat.ChatScreen
import com.pioneer.messenger.ui.finance.FinanceScreen
import com.pioneer.messenger.ui.lock.LockScreen
import com.pioneer.messenger.ui.lock.SetupPinScreen
import com.pioneer.messenger.ui.map.MapScreen
import com.pioneer.messenger.ui.permissions.PermissionsScreen
import com.pioneer.messenger.ui.profile.EditProfileScreen
import com.pioneer.messenger.ui.profile.ProfileScreen
import com.pioneer.messenger.ui.search.SearchScreen
import com.pioneer.messenger.ui.settings.SecuritySettingsScreen
import com.pioneer.messenger.ui.settings.SettingsScreen
import com.pioneer.messenger.ui.tasks.TasksScreen
import com.pioneer.messenger.ui.theme.MKRColors
import com.pioneer.messenger.ui.theme.ThemeState
import com.pioneer.messenger.ui.channel.ChannelListScreen
import com.pioneer.messenger.ui.channel.ChannelScreen
import com.pioneer.messenger.ui.channel.CreateChannelScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Permissions : Screen("permissions")
    data object ChatList : Screen("chats")
    data object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    data object Call : Screen("call/{callId}?isVideo={isVideo}&isIncoming={isIncoming}&callerName={callerName}&isSecretChat={isSecretChat}") {
        fun createRoute(
            callId: String,
            isVideo: Boolean = false,
            isIncoming: Boolean = false,
            callerName: String = "",
            isSecretChat: Boolean = false
        ) = "call/$callId?isVideo=$isVideo&isIncoming=$isIncoming&callerName=$callerName&isSecretChat=$isSecretChat"
    }
    data object Tasks : Screen("tasks")
    data object Finance : Screen("finance")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data object EditProfile : Screen("edit_profile")
    data object Admin : Screen("admin")
    data object Search : Screen("search")
    data object Lock : Screen("lock")
    data object SetupPin : Screen("setup_pin")
    data object SetupPinRequired : Screen("setup_pin_required")  // New route for required PIN setup after registration
    data object Security : Screen("security")
    data object LinkedDevices : Screen("linked_devices")
    data object Channels : Screen("channels")
    data object Channel : Screen("channel/{channelId}") {
        fun createRoute(channelId: String) = "channel/$channelId"
    }
    data object CreateChannel : Screen("create_channel")
    // Убраны: Reels, CreateReel, CreateStory, Status, StoryViewer
    data object Banned : Screen("banned?reason={reason}") {
        fun createRoute(reason: String?) = "banned?reason=${reason ?: ""}"
    }
    data object PeopleMap : Screen("people_map")
    data object Privacy : Screen("privacy")
    // Добавлено: Panic Button
    data object PanicButton : Screen("panic_button")
    // Добавлено: Security Check
    data object SecurityCheck : Screen("security_check")
    // Добавлено: Stealth Mode
    data object StealthMode : Screen("stealth_mode")
}

@Composable
fun PioneerNavHost(themeState: ThemeState) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState(initial = false)
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var isAppLocked by remember { mutableStateOf(false) }
    var isPinSet by remember { mutableStateOf(false) }
    var permissionsChecked by remember { mutableStateOf(false) }
    
    // Проверяем состояние блокировки при запуске
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pioneer_security", android.content.Context.MODE_PRIVATE)
        isPinSet = prefs.contains("pin_hash")
        val lockEnabled = prefs.getBoolean("lock_enabled", false)
        isAppLocked = isPinSet && lockEnabled
        
        // Проверяем были ли разрешения уже запрошены
        val permPrefs = context.getSharedPreferences("pioneer_permissions", android.content.Context.MODE_PRIVATE)
        permissionsChecked = permPrefs.getBoolean("permissions_requested", false)
    }
    
    // Проверяем входящий звонок
    val activity = context as? com.pioneer.messenger.ui.MainActivity
    val pendingCall by activity?.pendingIncomingCall ?: remember { mutableStateOf(null) }
    
    LaunchedEffect(pendingCall, isAuthenticated) {
        if (isAuthenticated && pendingCall != null) {
            android.util.Log.d("PioneerNavHost", "Navigating to call: ${pendingCall?.callId}")
            val call = activity?.consumePendingCall()
            if (call != null) {
                navController.navigate(
                    Screen.Call.createRoute(
                        callId = call.callId,
                        isVideo = call.isVideo,
                        isIncoming = true,
                        callerName = call.callerName
                    )
                )
            }
        }
    }
    
    // Проверяем, нужно ли показать экран разрешений
    fun arePermissionsGranted(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    val permissionsGranted = arePermissionsGranted()
    
    // Показываем экран разрешений если они не даны И не были запрошены ранее
    val showPermissions = isAuthenticated && !permissionsGranted && !permissionsChecked
    
    val startDestination = when {
        !isAuthenticated -> Screen.Auth.route
        showPermissions -> Screen.Permissions.route
        isAppLocked -> Screen.Lock.route
        else -> Screen.ChatList.route
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            MKRAuthScreen(
                onAuthSuccess = {
                    // После входа (login) - проверяем разрешения и идём на ChatList
                    if (!arePermissionsGranted()) {
                        navController.navigate(Screen.Permissions.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.ChatList.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                },
                onPinSetupRequired = {
                    // После регистрации - обязательная установка PIN
                    navController.navigate(Screen.SetupPinRequired.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onAllPermissionsGranted = {
                    // Сохраняем что разрешения были запрошены
                    context.getSharedPreferences("pioneer_permissions", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("permissions_requested", true)
                        .apply()
                    
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onChatClick = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                },
                onChannelClick = { channelId ->
                    // Каналы открываем через ChannelScreen
                    navController.navigate(Screen.Channel.createRoute(channelId))
                },
                onNavigateToTasks = {
                    navController.navigate(Screen.Tasks.route)
                },
                onNavigateToFinance = {
                    navController.navigate(Screen.Finance.route)
                },
                onNavigateToMap = {
                    navController.navigate(Screen.PeopleMap.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToChannels = {
                    navController.navigate(Screen.Channels.route)
                },
                onNavigateToReels = {
                    // Убрано - Reels не нужны для защищённого мессенджера
                },
                onNavigateToCreateStory = {
                    // Убрано - Stories не нужны для защищённого мессенджера
                },
                onNavigateToStatus = {
                    // Убрано - Status не нужен для защищённого мессенджера
                },
                onLockApp = {
                    navController.navigate(Screen.Lock.route)
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            var chatName by remember { mutableStateOf("Пользователь") }
            var otherUserId by remember { mutableStateOf(chatId) }
            
            ChatScreen(
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onProfileClick = { oderId ->
                    // Открываем профиль пользователя
                    navController.navigate("profile/$oderId")
                },
                onCallClick = { isVideo, isSecretChat ->
                    navController.navigate(
                        Screen.Call.createRoute(
                            callId = otherUserId,
                            isVideo = isVideo,
                            callerName = chatName,
                            isSecretChat = isSecretChat
                        )
                    )
                },
                onChatInfoLoaded = { name, oderId ->
                    chatName = name
                    otherUserId = oderId
                }
            )
        }
        
        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.BoolType; defaultValue = false },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false },
                navArgument("callerName") { type = NavType.StringType; defaultValue = "" },
                navArgument("isSecretChat") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            val callerName = backStackEntry.arguments?.getString("callerName") ?: "Пользователь"
            val isSecretChat = backStackEntry.arguments?.getBoolean("isSecretChat") ?: false
            
            CallScreen(
                callId = callId,
                isVideo = isVideo,
                isIncoming = isIncoming,
                callerName = callerName,
                isSecretChat = isSecretChat,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Tasks.route) {
            TasksScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Finance.route) {
            FinanceScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Map.route) {
            MapScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToAdmin = {
                    navController.navigate(Screen.Admin.route)
                },
                onNavigateToSetupPin = {
                    navController.navigate(Screen.SetupPin.route)
                },
                onNavigateToSecurity = {
                    navController.navigate(Screen.Security.route)
                },
                onNavigateToLinkedDevices = {
                    navController.navigate(Screen.LinkedDevices.route)
                },
                onNavigateToPrivacy = {
                    navController.navigate(Screen.Privacy.route)
                },
                themeState = themeState
            )
        }
        
        composable(Screen.Profile.route) {
            com.pioneer.messenger.ui.profile.MKRProfileScreen(
                onBack = { navController.popBackStack() },
                onEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Admin.route) {
            AdminScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Search.route) {
            var isCreatingChat by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            
            SearchScreen(
                onBack = { navController.popBackStack() },
                onStartChat = { userId ->
                    // Создаём чат с пользователем и открываем его
                    if (!isCreatingChat) {
                        isCreatingChat = true
                        scope.launch {
                            try {
                                val result = com.pioneer.messenger.data.network.ApiClient.createChat(
                                    type = "direct",
                                    name = "",
                                    participantIds = listOf(userId)
                                )
                                result.fold(
                                    onSuccess = { chat ->
                                        navController.navigate(Screen.Chat.createRoute(chat.id)) {
                                            popUpTo(Screen.Search.route) { inclusive = true }
                                        }
                                    },
                                    onFailure = {
                                        // Если ошибка - всё равно пробуем открыть
                                        navController.navigate(Screen.Chat.createRoute(userId)) {
                                            popUpTo(Screen.Search.route) { inclusive = true }
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                navController.navigate(Screen.Chat.createRoute(userId)) {
                                    popUpTo(Screen.Search.route) { inclusive = true }
                                }
                            } finally {
                                isCreatingChat = false
                            }
                        }
                    }
                },
                onUserClick = { userId ->
                    // Открываем профиль пользователя
                    navController.navigate("profile/$userId")
                }
            )
        }
        
        // Профиль другого пользователя
        composable(
            route = "profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            var isCreatingChat by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            
            ProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onEditProfile = { },
                onStartChat = { id ->
                    // Создаём чат с пользователем и открываем его
                    if (!isCreatingChat) {
                        isCreatingChat = true
                        scope.launch {
                            try {
                                val result = com.pioneer.messenger.data.network.ApiClient.createChat(
                                    type = "direct",
                                    name = "",
                                    participantIds = listOf(id)
                                )
                                result.fold(
                                    onSuccess = { chat ->
                                        navController.navigate(Screen.Chat.createRoute(chat.id)) {
                                            popUpTo("profile/$id") { inclusive = true }
                                        }
                                    },
                                    onFailure = {
                                        // Если ошибка - всё равно пробуем открыть
                                        navController.navigate(Screen.Chat.createRoute(id)) {
                                            popUpTo("profile/$id") { inclusive = true }
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                navController.navigate(Screen.Chat.createRoute(id)) {
                                    popUpTo("profile/$id") { inclusive = true }
                                }
                            } finally {
                                isCreatingChat = false
                            }
                        }
                    }
                },
                onStartCall = { id, isVideo ->
                    // Начинаем звонок (только аудио)
                    navController.navigate(
                        Screen.Call.createRoute(
                            callId = id,
                            isVideo = false,
                            callerName = "Пользователь"
                        )
                    )
                }
            )
        }
        
        composable(Screen.Lock.route) {
            LockScreen(
                onUnlock = {
                    isAppLocked = false
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                },
                onBiometricClick = {
                    // TODO: биометрическая аутентификация
                }
            )
        }
        
        composable(Screen.SetupPin.route) {
            SetupPinScreen(
                onPinSet = { pin ->
                    navController.popBackStack()
                },
                onSkip = {
                    navController.popBackStack()
                }
            )
        }
        
        // Required PIN setup after registration - no skip option
        composable(Screen.SetupPinRequired.route) {
            SetupPinScreen(
                onPinSet = { pin ->
                    // After PIN is set, check permissions then go to ChatList
                    if (!arePermissionsGranted()) {
                        navController.navigate(Screen.Permissions.route) {
                            popUpTo(Screen.SetupPinRequired.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.ChatList.route) {
                            popUpTo(Screen.SetupPinRequired.route) { inclusive = true }
                        }
                    }
                },
                onSkip = {
                    // This won't be called since isRequired=true hides the skip button
                    // But we need to provide it for the function signature
                },
                isRequired = true
            )
        }
        
        composable(Screen.Security.route) {
            SecuritySettingsScreen(
                onBack = { navController.popBackStack() },
                onSetupPin = {
                    navController.navigate(Screen.SetupPin.route)
                },
                onNavigateToPanicButton = {
                    navController.navigate(Screen.PanicButton.route)
                },
                onNavigateToSecurityCheck = {
                    navController.navigate(Screen.SecurityCheck.route)
                },
                onNavigateToStealthMode = {
                    navController.navigate(Screen.StealthMode.route)
                }
            )
        }
        
        // Panic Button - экстренное удаление данных
        composable(Screen.PanicButton.route) {
            com.pioneer.messenger.ui.security.PanicButtonScreen(
                onBack = { navController.popBackStack() },
                onWipeComplete = {
                    // После удаления данных - выход на экран авторизации
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.LinkedDevices.route) {
            com.pioneer.messenger.ui.settings.LinkedDevicesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // Каналы
        composable(Screen.Channels.route) {
            ChannelListScreen(
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Channel.createRoute(channelId))
                },
                onBack = { navController.popBackStack() },
                onCreateChannel = {
                    navController.navigate(Screen.CreateChannel.route)
                }
            )
        }
        
        composable(
            route = Screen.Channel.route,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
            ChannelScreen(
                channelId = channelId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.CreateChannel.route) {
            CreateChannelScreen(
                onBack = { navController.popBackStack() },
                onChannelCreated = { channelId ->
                    navController.navigate(Screen.Channel.createRoute(channelId)) {
                        popUpTo(Screen.CreateChannel.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Убраны: Reels, CreateReel, CreateStory, Status, StoryViewer
        // Эти функции не нужны для защищённого мессенджера
        
        // Экран заблокированного аккаунта
        composable(
            route = Screen.Banned.route,
            arguments = listOf(navArgument("reason") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val reason = backStackEntry.arguments?.getString("reason")
            BannedAccountScreen(
                banReason = reason,
                onLogout = {
                    // Выход из аккаунта
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Карта пользователей
        composable(Screen.PeopleMap.route) {
            com.pioneer.messenger.ui.map.PeopleMapScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                }
            )
        }
        
        // Настройки приватности
        composable(Screen.Privacy.route) {
            com.pioneer.messenger.ui.settings.PrivacySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // Проверка безопасности устройства
        composable(Screen.SecurityCheck.route) {
            com.pioneer.messenger.ui.security.SecurityCheckScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // Скрытый режим (Stealth Mode)
        composable(Screen.StealthMode.route) {
            com.pioneer.messenger.ui.security.StealthModeScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
