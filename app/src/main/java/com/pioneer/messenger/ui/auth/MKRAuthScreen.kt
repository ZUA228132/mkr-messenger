package com.pioneer.messenger.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pioneer.messenger.data.security.CallsignValidator
import com.pioneer.messenger.ui.theme.MKRColors
import kotlinx.coroutines.launch

/**
 * Упрощённый экран авторизации MKR
 * Регистрация/вход по позывному + пароль
 */
@Composable
fun MKRAuthScreen(
    onAuthSuccess: () -> Unit,
    onPinSetupRequired: () -> Unit = onAuthSuccess,  // Default to onAuthSuccess for backward compatibility
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    // Состояние
    var isLogin by remember { mutableStateOf(true) }
    var callsign by remember { mutableStateOf("") } // Позывной
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> onAuthSuccess()
            is AuthUiState.PinSetupRequired -> onPinSetupRequired()
            else -> { }
        }
    }
    
    // Экран бана
    if (uiState is AuthUiState.Banned) {
        com.pioneer.messenger.ui.banned.BannedAccountScreen(
            banReason = (uiState as AuthUiState.Banned).reason,
            onLogout = { }
        )
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F0F1A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Логотип
            Text(
                text = "MKR",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 8.sp
            )
            
            Text(
                text = if (isLogin) "Вход в систему" else "Регистрация",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Форма
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Позывной (или email для админов)
                    AuthTextField(
                        value = callsign,
                        onValueChange = { input ->
                            // Если содержит @, это email - разрешаем все символы email
                            callsign = if (input.contains("@") || isLogin) {
                                input.lowercase().filter { c -> c.isLetterOrDigit() || c in "@._-" }
                            } else {
                                input.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }
                            }
                        },
                        label = if (isLogin) "Позывной или Email" else "Позывной",
                        icon = Icons.Default.Badge,
                        placeholder = if (isLogin) "позывной или admin@email.com" else "ваш_позывной",
                        imeAction = ImeAction.Next,
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                    
                    // Имя (только при регистрации)
                    if (!isLogin) {
                        AuthTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = "Ваше имя",
                            icon = Icons.Default.Person,
                            placeholder = "Как вас называть?",
                            imeAction = ImeAction.Next,
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    }
                    
                    // Пароль
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Пароль",
                        icon = Icons.Default.Lock,
                        isPassword = !showPassword,
                        placeholder = if (isLogin) "Введите пароль" else "Минимум 6 символов",
                        imeAction = if (isLogin) ImeAction.Done else ImeAction.Next,
                        onDone = if (isLogin) { { focusManager.clearFocus() } } else null,
                        onNext = if (!isLogin) { { focusManager.moveFocus(FocusDirection.Down) } } else null,
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    )
                    
                    // Подтверждение пароля (только при регистрации)
                    if (!isLogin) {
                        AuthTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Подтвердите пароль",
                            icon = Icons.Default.LockReset,
                            isPassword = !showPassword,
                            placeholder = "Повторите пароль",
                            imeAction = ImeAction.Done,
                            onDone = { focusManager.clearFocus() }
                        )
                    }
                }
            }
            
            // Ошибка
            (uiState as? AuthUiState.Error)?.message?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MKRColors.Error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Валидация позывного (или email для админов)
            val isEmail = callsign.contains("@")
            val callsignError = when {
                callsign.isEmpty() -> null
                isEmail -> null // Email пропускается для админов
                !CallsignValidator.hasValidCharacters(callsign) -> "Позывной может содержать только буквы, цифры и _"
                !CallsignValidator.hasValidLength(callsign) -> "Позывной должен быть минимум 3 символа"
                else -> null
            }
            
            // Валидация пароля
            val passwordError = when {
                password.isEmpty() -> null
                password.length < 6 -> "Пароль должен быть минимум 6 символов"
                else -> null
            }
            
            // Валидация подтверждения пароля
            val confirmPasswordError = when {
                !isLogin && confirmPassword.isNotEmpty() && password != confirmPassword -> "Пароли не совпадают"
                else -> null
            }
            
            // Показываем ошибки валидации
            callsignError?.let { error ->
                Text(
                    text = error,
                    color = MKRColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            passwordError?.let { error ->
                Text(
                    text = error,
                    color = MKRColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            confirmPasswordError?.let { error ->
                Text(
                    text = error,
                    color = MKRColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Кнопка действия
            val isValid = if (isLogin) {
                // Для входа: позывной ИЛИ email (для админов)
                (CallsignValidator.isValidCallsign(callsign) || callsign.contains("@")) && password.length >= 6
            } else {
                // Для регистрации: только позывной
                CallsignValidator.isValidCallsign(callsign) && displayName.isNotBlank() && 
                password.length >= 6 && password == confirmPassword
            }
            
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (isLogin) {
                        viewModel.loginSimple(callsign, password)
                    } else {
                        viewModel.registerSimple(callsign, displayName, password)
                    }
                },
                enabled = isValid && uiState !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MKRColors.Primary)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (isLogin) "Войти" else "Создать аккаунт",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Переключение режима
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isLogin) "Нет аккаунта?" else "Уже есть аккаунт?",
                    color = Color.White.copy(alpha = 0.6f)
                )
                TextButton(onClick = { 
                    isLogin = !isLogin
                    // Сбрасываем поля
                    password = ""
                    confirmPassword = ""
                }) {
                    Text(
                        if (isLogin) "Создать" else "Войти",
                        color = MKRColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.3f)) },
        leadingIcon = { Icon(icon, null, tint = MKRColors.Primary) },
        trailingIcon = trailingIcon,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = MKRColors.Primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            cursorColor = MKRColors.Primary
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
