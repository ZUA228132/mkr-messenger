# Requirements Document

## Introduction

Данный документ описывает требования к обновлению системы авторизации мессенджера и ребрендингу с LIMO на MKR. Основные изменения включают: упрощение регистрации до позывного+пароль, обязательную установку PIN-кода при регистрации, переименование всех упоминаний LIMO в MKR, и обновление официального канала MKR с информацией об изменениях.

## Glossary

- **MKR**: Новое название мессенджера (ранее LIMO)
- **Позывной (Callsign)**: Уникальный идентификатор пользователя для входа в систему
- **PIN-код**: 4-6 значный код для блокировки/разблокировки приложения
- **Официальный канал MKR**: Системный канал для публикации обновлений и новостей
- **Система авторизации**: Компонент, отвечающий за регистрацию и вход пользователей

## Requirements

### Requirement 1

**User Story:** As a new user, I want to register using only a callsign and password, so that I can quickly create an account without providing personal information like email or phone.

#### Acceptance Criteria

1. WHEN a user opens the registration form THEN the System SHALL display only callsign, display name, password, and confirm password fields
2. WHEN a user enters a callsign THEN the System SHALL validate that the callsign contains only lowercase letters, digits, and underscores
3. WHEN a user enters a callsign shorter than 3 characters THEN the System SHALL prevent form submission and display a validation message
4. WHEN a user enters a password shorter than 6 characters THEN the System SHALL prevent form submission and display a validation message
5. WHEN a user enters non-matching passwords THEN the System SHALL display "Пароли не совпадают" error message
6. WHEN a user submits valid registration data THEN the System SHALL create the account and proceed to PIN setup

### Requirement 2

**User Story:** As a new user, I want to set up a PIN code immediately after registration, so that my application is protected from unauthorized access from the start.

#### Acceptance Criteria

1. WHEN registration completes successfully THEN the System SHALL navigate to the PIN setup screen
2. WHEN the PIN setup screen displays THEN the System SHALL require a 4-6 digit PIN code
3. WHEN a user enters a PIN THEN the System SHALL require PIN confirmation by re-entering
4. WHEN PINs match THEN the System SHALL save the PIN and enable app lock
5. WHEN PINs do not match THEN the System SHALL display an error and clear the confirmation field
6. WHEN PIN is set successfully THEN the System SHALL navigate to the main chat list

### Requirement 3

**User Story:** As a returning user, I want to log in using my callsign and password, so that I can access my account quickly.

#### Acceptance Criteria

1. WHEN a user opens the login form THEN the System SHALL display callsign and password fields
2. WHEN a user enters valid credentials THEN the System SHALL authenticate and navigate to the main screen
3. WHEN a user enters invalid credentials THEN the System SHALL display an appropriate error message
4. WHEN a user's account is banned THEN the System SHALL display the banned account screen with the ban reason

### Requirement 4

**User Story:** As a user, I want all references to LIMO renamed to MKR throughout the application, so that the branding is consistent.

#### Acceptance Criteria

1. WHEN the application displays any text containing "LIMO" or "Limo" THEN the System SHALL display "MKR" instead
2. WHEN the official channel is referenced THEN the System SHALL use "mkr-official-channel" as the channel ID
3. WHEN the application displays the app name THEN the System SHALL display "MKR"
4. WHEN theme or component names reference "Limo" THEN the System SHALL use "MKR" naming convention

### Requirement 5

**User Story:** As a user, I want to receive updates about application changes in the official MKR channel, so that I stay informed about new features and fixes.

#### Acceptance Criteria

1. WHEN a development update is made THEN the System SHALL support posting to the official MKR channel
2. WHEN the official channel displays THEN the System SHALL show it as pinned at the top of the channel list
3. WHEN the official channel is accessed THEN the System SHALL display the channel name as "MKR Official"
