package com.pranavajay.ghostmessenger.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pranavajay.ghostmessenger.presentation.auth.LoginScreen
import com.pranavajay.ghostmessenger.presentation.auth.RegisterScreen
import com.pranavajay.ghostmessenger.presentation.call.CallScreen
import com.pranavajay.ghostmessenger.presentation.channel.ChannelListScreen
import com.pranavajay.ghostmessenger.presentation.chat.ChatDetailScreen
import com.pranavajay.ghostmessenger.presentation.chat.ChatListScreen
import com.pranavajay.ghostmessenger.presentation.group.GroupListScreen
import com.pranavajay.ghostmessenger.presentation.profile.ProfileScreen
import com.pranavajay.ghostmessenger.presentation.search.SearchScreen
import com.pranavajay.ghostmessenger.presentation.settings.SettingsScreen
import com.pranavajay.ghostmessenger.presentation.status.StatusScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ChatList : Screen("chat_list")
    object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: String) = "chat_detail/$chatId"
    }
    object GroupList : Screen("group_list")
    object Status : Screen("status")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Search : Screen("search")
    object Channels : Screen("channels")
    object Call : Screen("call/{callId}/{callType}/{callerName}/{isIncoming}") {
        fun createRoute(callId: String, callType: String, callerName: String, isIncoming: Boolean) = 
            "call/$callId/$callType/$callerName/$isIncoming"
    }
}

@Composable
fun NavGraph(
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme
            )
        }

        composable(Screen.ChatDetail.route) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatDetailScreen(navController = navController, chatId = chatId)
        }

        composable(Screen.GroupList.route) {
            GroupListScreen(navController = navController)
        }

        composable(Screen.Status.route) {
            StatusScreen(navController = navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }
        
        composable(Screen.Channels.route) {
            ChannelListScreen(navController = navController)
        }
        
        composable(Screen.Call.route) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val callType = backStackEntry.arguments?.getString("callType") ?: "VOICE"
            val callerName = backStackEntry.arguments?.getString("callerName") ?: "Unknown"
            val isIncoming = backStackEntry.arguments?.getString("isIncoming")?.toBoolean() ?: false
            CallScreen(
                navController = navController,
                callId = callId,
                callType = callType,
                callerName = callerName,
                isIncoming = isIncoming
            )
        }
    }
}
