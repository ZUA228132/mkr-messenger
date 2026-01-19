# Requirements Document

## Introduction

Интеграция Flutter iOS приложения MKR Messenger с существующим Kotlin/Ktor бэкендом. Flutter версия должна использовать те же API endpoints и WebSocket протокол, что и Android версия, обеспечивая полную совместимость и синхронизацию данных между платформами.

## Glossary

- **MKR Backend**: Ktor сервер на порту 8080, предоставляющий REST API и WebSocket для мессенджера
- **JWT Token**: JSON Web Token для аутентификации запросов к API
- **WebSocket**: Протокол для real-time коммуникации (сообщения, typing, звонки)
- **Flutter Client**: iOS приложение на Flutter, которое должно подключиться к бэкенду
- **API Base URL**: Базовый URL сервера (например, https://kluboksrm.ru или http://localhost:8080)

## Requirements

### Requirement 1: Конфигурация API

**User Story:** As a developer, I want to configure API endpoints in Flutter app, so that the app can connect to the MKR backend.

#### Acceptance Criteria

1. THE Flutter Client SHALL store API base URL in a configuration file
2. THE Flutter Client SHALL support switching between development and production environments
3. WHEN the app starts THEN THE Flutter Client SHALL validate API connectivity

### Requirement 2: Аутентификация по Email

**User Story:** As a user, I want to register and login using email, so that I can access the messenger.

#### Acceptance Criteria

1. WHEN a user submits registration form with email, password, username, displayName THEN THE Flutter Client SHALL send POST request to /api/auth/register/email
2. WHEN registration succeeds THEN THE Flutter Client SHALL display verification code input screen
3. WHEN a user enters verification code THEN THE Flutter Client SHALL send POST request to /api/auth/verify/email
4. WHEN verification succeeds THEN THE Flutter Client SHALL store JWT token securely and navigate to main screen
5. WHEN a user submits login form THEN THE Flutter Client SHALL send POST request to /api/auth/login/email
6. WHEN login succeeds THEN THE Flutter Client SHALL store JWT token and userId securely
7. IF login fails with lockout error THEN THE Flutter Client SHALL display remaining lockout time

### Requirement 3: Управление токеном

**User Story:** As a user, I want my session to persist, so that I don't have to login every time.

#### Acceptance Criteria

1. THE Flutter Client SHALL store JWT token in secure storage (flutter_secure_storage)
2. THE Flutter Client SHALL include Authorization header with Bearer token in all authenticated requests
3. WHEN token expires or becomes invalid THEN THE Flutter Client SHALL redirect to login screen
4. WHEN user logs out THEN THE Flutter Client SHALL clear all stored credentials

### Requirement 4: Список чатов

**User Story:** As a user, I want to see my chats, so that I can continue conversations.

#### Acceptance Criteria

1. WHEN user opens chats tab THEN THE Flutter Client SHALL send GET request to /api/chats
2. THE Flutter Client SHALL display chat list with name, last message preview, and unread count
3. WHEN a user taps on a chat THEN THE Flutter Client SHALL navigate to chat screen with chatId
4. WHEN a user wants to start new chat THEN THE Flutter Client SHALL send POST request to /api/chats with participantIds

### Requirement 5: Сообщения

**User Story:** As a user, I want to send and receive messages, so that I can communicate with others.

#### Acceptance Criteria

1. WHEN user opens a chat THEN THE Flutter Client SHALL send GET request to /api/messages/{chatId}
2. THE Flutter Client SHALL display messages with sender info, content, and timestamp
3. WHEN user sends a message THEN THE Flutter Client SHALL send POST request to /api/messages
4. WHEN new message arrives via WebSocket THEN THE Flutter Client SHALL add it to the chat view immediately

### Requirement 6: WebSocket подключение

**User Story:** As a user, I want to receive messages in real-time, so that I have instant communication.

#### Acceptance Criteria

1. WHEN user authenticates successfully THEN THE Flutter Client SHALL establish WebSocket connection to /ws/{userId}
2. THE Flutter Client SHALL handle WebSocket message types: new_message, typing, user_online, user_offline
3. WHEN user types in chat THEN THE Flutter Client SHALL send typing event via WebSocket
4. IF WebSocket disconnects THEN THE Flutter Client SHALL attempt automatic reconnection with exponential backoff
5. WHEN app goes to background THEN THE Flutter Client SHALL maintain WebSocket connection for notifications

### Requirement 7: Профиль пользователя

**User Story:** As a user, I want to view and edit my profile, so that others can identify me.

#### Acceptance Criteria

1. WHEN user opens settings THEN THE Flutter Client SHALL send GET request to /api/users/{userId}
2. WHEN user updates profile THEN THE Flutter Client SHALL send POST request to /api/users/me
3. WHEN user updates avatar THEN THE Flutter Client SHALL send POST request to /api/users/me/avatar

### Requirement 8: Поиск пользователей

**User Story:** As a user, I want to search for other users, so that I can start new conversations.

#### Acceptance Criteria

1. WHEN user enters search query THEN THE Flutter Client SHALL send GET request to /api/users/search?q={query}
2. THE Flutter Client SHALL display search results with username, displayName, and online status
3. WHEN user selects a search result THEN THE Flutter Client SHALL create or open existing direct chat

### Requirement 9: Push уведомления

**User Story:** As a user, I want to receive push notifications, so that I know about new messages when app is closed.

#### Acceptance Criteria

1. WHEN user grants notification permission THEN THE Flutter Client SHALL obtain FCM/APNs token
2. THE Flutter Client SHALL send POST request to /api/users/fcm-token with device token
3. WHEN push notification arrives THEN THE Flutter Client SHALL display it with sender name and message preview

### Requirement 10: Обработка ошибок

**User Story:** As a user, I want clear error messages, so that I understand what went wrong.

#### Acceptance Criteria

1. WHEN API returns error response THEN THE Flutter Client SHALL parse error message and display it to user
2. WHEN network is unavailable THEN THE Flutter Client SHALL display offline indicator
3. WHEN request times out THEN THE Flutter Client SHALL offer retry option
4. THE Flutter Client SHALL log all API errors for debugging
