package com.pioneer.messenger.ui.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String? = null,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    onStartChat: (String) -> Unit = {},
    onStartCall: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel()
) {
    // Используем MKR стиль профиля
    MKRProfileScreen(
        userId = userId,
        onBack = onBack,
        onEditProfile = onEditProfile,
        onStartChat = onStartChat,
        onStartCall = onStartCall,
        viewModel = viewModel
    )
}
