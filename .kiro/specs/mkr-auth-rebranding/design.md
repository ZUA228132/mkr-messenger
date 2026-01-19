# Design Document: MKR Auth & Rebranding

## Overview

Данный документ описывает дизайн обновления системы авторизации и ребрендинга мессенджера с LIMO на MKR. Изменения включают:

1. Упрощение регистрации до позывного + пароль (без email/телефона)
2. Обязательная установка PIN-кода сразу после регистрации
3. Переименование всех упоминаний LIMO в MKR
4. Обновление официального канала

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Registration Flow                         │
├─────────────────────────────────────────────────────────────┤
│  MKRAuthScreen → PIN Setup → ChatList                       │
│       │              │           │                          │
│       ▼              ▼           ▼                          │
│  AuthViewModel  LockViewModel  Navigation                   │
│       │              │                                      │
│       ▼              ▼                                      │
│  AuthManager    SharedPrefs (PIN storage)                   │
│       │                                                     │
│       ▼                                                     │
│  Backend API                                                │
└─────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. MKRAuthScreen (Updated)

Упрощённый экран авторизации с полями:
- Позывной (callsign)
- Имя (displayName) - только при регистрации
- Пароль
- Подтверждение пароля - только при регистрации

```kotlin
@Composable
fun MKRAuthScreen(
    onAuthSuccess: () -> Unit,  // Изменено: теперь ведёт на PIN setup
    viewModel: AuthViewModel
)
```

### 2. SetupPinScreen (Updated)

Экран установки PIN-кода:
- Ввод 4-6 значного PIN
- Подтверждение PIN
- Сохранение в SharedPreferences

```kotlin
@Composable
fun SetupPinScreen(
    onPinSet: (String) -> Unit,
    onSkip: () -> Unit,  // Будет скрыт при обязательной установке
    isRequired: Boolean = false  // Новый параметр
)
```

### 3. PioneerNavHost (Updated)

Обновлённая навигация:
- После успешной регистрации → SetupPin (обязательно)
- После успешного входа → ChatList (если PIN уже установлен)

### 4. Rebranding Changes

Файлы для переименования LIMO → MKR:
- `LimoTheme.kt` → `MKRTheme.kt` (или обновить содержимое)
- `LimoComponents.kt` → `MKRComponents.kt`
- `LimoMainScreen.kt` → `MKRMainScreen.kt`
- `limo-official-channel` → `mkr-official-channel`
- Все строковые ресурсы с "LIMO"

## Data Models

### AuthState

```kotlin
sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data object PinSetupRequired : AuthUiState()  // Новое состояние
    data class Banned(val reason: String?) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
```

### PIN Storage

```kotlin
// SharedPreferences keys
const val PREF_PIN_HASH = "pin_hash"
const val PREF_LOCK_ENABLED = "lock_enabled"
const val PREF_PIN_SET_DURING_REGISTRATION = "pin_set_during_registration"
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Callsign Character Validation
*For any* string input as callsign, the validation function SHALL accept it if and only if it contains only lowercase letters (a-z), digits (0-9), and underscores (_).
**Validates: Requirements 1.2**

### Property 2: Input Length Validation
*For any* callsign input, the validation SHALL reject inputs shorter than 3 characters. *For any* password input, the validation SHALL reject inputs shorter than 6 characters.
**Validates: Requirements 1.3, 1.4**

### Property 3: Password Matching
*For any* pair of password strings (password, confirmPassword), the form SHALL be valid if and only if password equals confirmPassword.
**Validates: Requirements 1.5**

### Property 4: PIN Format Validation
*For any* PIN input, the validation SHALL accept it if and only if it contains exactly 4-6 digits (0-9).
**Validates: Requirements 2.2**

### Property 5: PIN Confirmation Matching
*For any* pair of PIN strings (pin, confirmPin), the PIN setup SHALL succeed if and only if pin equals confirmPin.
**Validates: Requirements 2.4, 2.5**

### Property 6: PIN Storage Round-Trip
*For any* valid PIN that is saved, retrieving and verifying the PIN SHALL return true for the same PIN and false for any different PIN.
**Validates: Requirements 2.4**

## Error Handling

| Scenario | Error Message |
|----------|---------------|
| Callsign too short | "Позывной должен быть минимум 3 символа" |
| Invalid callsign chars | "Позывной может содержать только буквы, цифры и _" |
| Password too short | "Пароль должен быть минимум 6 символов" |
| Passwords don't match | "Пароли не совпадают" |
| Callsign taken | "Этот позывной уже занят" |
| Invalid credentials | "Неверный позывной или пароль" |
| PIN too short | "PIN должен быть 4-6 цифр" |
| PINs don't match | "PIN-коды не совпадают" |
| Network error | "Нет подключения к серверу" |

## Testing Strategy

### Property-Based Testing

Используем библиотеку **Kotest** с генераторами для property-based тестов:

```kotlin
// Пример структуры теста
class AuthValidationPropertyTest : StringSpec({
    "callsign validation accepts only valid characters" {
        // Property 1: Callsign Character Validation
        forAll(Arb.string()) { input ->
            val isValid = validateCallsign(input)
            val expectedValid = input.all { it.isLowerCase() || it.isDigit() || it == '_' }
            isValid == expectedValid
        }
    }
})
```

### Unit Tests

- Тесты валидации форм
- Тесты навигации после регистрации
- Тесты сохранения/проверки PIN

### Integration Tests

- Полный flow регистрации → PIN setup → ChatList
- Вход с существующим аккаунтом
- Обработка ошибок сети
