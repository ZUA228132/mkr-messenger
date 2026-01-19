package com.pioneer.messenger.ui.auth

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    // Используем MKR стиль авторизации
    MKRAuthScreen(
        onAuthSuccess = onAuthSuccess,
        viewModel = viewModel
    )
}
