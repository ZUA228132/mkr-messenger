
<h1 align="center">👻 Ghost Messenger</h1>
<h3 align="center">Privacy-First • Open Source • Next Generation Messaging</h3>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0--beta-blue?style=for-the-badge" alt="Version"/>
  <img src="https://img.shields.io/badge/Release-22%20Feb%202027-green?style=for-the-badge" alt="Release"/>
  <img src="https://img.shields.io/badge/License-MIT-orange?style=for-the-badge" alt="License"/>
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=for-the-badge&logo=android" alt="Platform"/>
</p>

<p align="center">
  <a href="https://t.me/SpiralTechDivision">
    <img src="https://img.shields.io/badge/Telegram-Join%20Community-blue?style=for-the-badge&logo=telegram" alt="Telegram"/>
  </a>
  <a href="https://github.com/spiraltech/ghost-messenger">
    <img src="https://img.shields.io/badge/GitHub-Star%20Us-black?style=for-the-badge&logo=github" alt="GitHub"/>
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-Backend-purple?style=flat-square&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI-blue?style=flat-square&logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/Ktor-Server-orange?style=flat-square" alt="Ktor"/>
  <img src="https://img.shields.io/badge/MongoDB-Database-green?style=flat-square&logo=mongodb" alt="MongoDB"/>
  <img src="https://img.shields.io/badge/WebSocket-Real--time-red?style=flat-square" alt="WebSocket"/>
</p>

---

> ⚠️ **WORK IN PROGRESS** - This project is under active development. Features may change, and some functionality might not be fully implemented yet. We welcome contributions and feedback!

---

## 📖 Table of Contents

- [About](#-about-ghost-messenger)
- [Vision & Mission](#-vision--mission)
- [Key Features](#-key-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Development Timeline](#-development-timeline)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [Support](#-support)
- [License](#-license)

---

## 🌟 About Ghost Messenger

<p align="center">
  <img src="other/ic_ghost_logo.png" width="150" alt="Ghost Messenger Logo"/>
</p>

**Ghost Messenger** is a revolutionary, privacy-focused messaging application that puts user security and data protection at the forefront. Built from the ground up with modern technologies, Ghost Messenger offers a unique "Ghost Mode" feature that allows messages to disappear after a set time, ensuring your conversations remain truly private.

In an era where digital privacy is increasingly compromised, Ghost Messenger stands as a beacon of hope for users who value their personal data. Unlike mainstream messaging apps that harvest user data for advertising, Ghost Messenger is committed to a privacy-first approach with end-to-end encryption and zero data collection policies.

### Why Ghost Messenger?

| Problem | Our Solution |
|---------|--------------|
| Messages stored forever on servers | **Ghost Mode** - Messages auto-delete after set time |
| Companies selling your data | **Zero data collection** - We don't track you |
| Complex, bloated apps | **Clean, minimal UI** - Focus on what matters |
| Closed source, no transparency | **Open source** - Audit our code yourself |
| No control over your data | **Full user control** - Delete anything, anytime |

---

## 🎯 Vision & Mission

### Our Vision
To create a world where digital communication is truly private, secure, and under the complete control of users - not corporations.

### Our Mission
- **Democratize Privacy**: Make enterprise-grade security accessible to everyone
- **Empower Users**: Give complete control over personal data and conversations
- **Foster Trust**: Build transparent, open-source software that users can verify
- **Innovate Responsibly**: Push the boundaries of messaging technology while respecting user privacy

### Core Values
1. **Privacy First** - Every feature is designed with privacy as the primary consideration
2. **Transparency** - Open source code, clear policies, no hidden agendas
3. **User Empowerment** - You own your data, you control your experience
4. **Security by Design** - Not an afterthought, but the foundation
5. **Simplicity** - Powerful features wrapped in an intuitive interface

---

## ✨ Key Features

### 💬 Core Messaging

<table>
<tr>
<td width="50%">

**Real-time Communication**
- Instant message delivery via WebSocket
- Typing indicators show when contacts are composing
- Read receipts with blue double-tick
- Online/offline status indicators
- Message delivery confirmation

</td>
<td width="50%">

**Rich Media Support**
- Share images, videos, and documents
- Voice message recording and playback
- Location sharing with map preview
- Contact card sharing
- File attachments up to 100MB

</td>
</tr>
</table>

### 👻 Ghost Mode (Signature Feature)

<p align="center">
  <img src="assets/ghost_mode_demo.gif" width="300" alt="Ghost Mode Demo"/>
</p>

Ghost Mode is what sets us apart. Enable it on any conversation to make messages automatically disappear:

| Duration | Use Case |
|----------|----------|
| **5 minutes** | Quick, sensitive information |
| **1 hour** | Time-sensitive discussions |
| **24 hours** | Daily conversations |
| **7 days** | Extended but temporary chats |
| **Custom** | Set your own duration |

**Additional Ghost Mode Features:**
- 🔔 Screenshot detection alerts
- 🚫 Forward prevention option
- 👁️ View-once media
- 🔒 No server-side message storage

### 📞 Voice & Video Calls

| Feature | Description |
|---------|-------------|
| **HD Voice Calls** | Crystal clear audio with noise cancellation |
| **Video Calls** | Up to 1080p video quality |
| **Group Calls** | Up to 8 participants |
| **Call Recording** | Record important calls (with consent) |
| **Screen Sharing** | Share your screen during video calls |

### 🔍 Smart Search

- **Global Search**: Find messages, contacts, and media across all chats
- **In-Chat Search**: Search within specific conversations
- **Filter by Type**: Images, videos, documents, links
- **Date Range**: Find messages from specific time periods

### 📢 Channels & Groups

**Channels** - Broadcast to unlimited subscribers
- Public or private channels
- Admin-only posting option
- Subscriber analytics
- Scheduled posts

**Groups** - Collaborate with up to 200,000 members
- Multiple admin levels
- Polls and surveys
- Pinned messages
- Slow mode option

---

## 🏗️ Architecture

Ghost Messenger follows a clean, modular architecture designed for scalability, maintainability, and testability.

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           GHOST MESSENGER ECOSYSTEM                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        ANDROID APPLICATION                           │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                    PRESENTATION LAYER                          │  │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │  │    │
│  │  │  │ Screens │  │ViewModels│  │  State  │  │   UI    │          │  │    │
│  │  │  │(Compose)│  │  (MVVM) │  │  Flows  │  │ Events  │          │  │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                      DOMAIN LAYER                              │  │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │  │    │
│  │  │  │Use Cases│  │Repositories│ │ Models │  │Interfaces│          │  │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                       DATA LAYER                               │  │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │  │    │
│  │  │  │  Room   │  │ Retrofit│  │WebSocket│  │DataStore│          │  │    │
│  │  │  │   DB    │  │   API   │  │ Client  │  │  Prefs  │          │  │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                     │                                        │
│                                     ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         KTOR BACKEND SERVER                          │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                      API LAYER (REST)                          │  │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │  │    │
│  │  │  │  Auth   │  │ Message │  │  Call   │  │ Channel │          │  │    │
│  │  │  │ Routes  │  │ Routes  │  │ Routes  │  │ Routes  │          │  │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                   REAL-TIME LAYER (WebSocket)                  │  │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │  │    │
│  │  │  │  Chat   │  │ Typing  │  │ Online  │  │  Call   │          │  │    │
│  │  │  │ Server  │  │ Events  │  │ Status  │  │Signaling│          │  │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                     DATA LAYER                                 │  │    │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │  │    │
│  │  │  │ MongoDB │  │   S3    │  │  Redis  │  │   FCM   │          │  │    │
│  │  │  │Database │  │ Storage │  │  Cache  │  │  Push   │          │  │    │
│  │  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

### Android Application

| Technology | Purpose | Why We Chose It |
|------------|---------|-----------------|
| **Kotlin** | Primary Language | Modern, concise, null-safe, officially supported by Google |
| **Jetpack Compose** | UI Framework | Declarative UI, less boilerplate, better performance |
| **Hilt** | Dependency Injection | Official DI solution, compile-time verification |
| **Room** | Local Database | Type-safe SQLite abstraction, LiveData/Flow support |
| **Retrofit** | HTTP Client | Industry standard, type-safe, easy to use |
| **OkHttp** | WebSocket Client | Reliable, efficient, great interceptor support |
| **DataStore** | Preferences | Modern replacement for SharedPreferences |
| **Coil** | Image Loading | Kotlin-first, lightweight, Compose support |
| **Navigation Compose** | Navigation | Type-safe navigation, deep linking support |

### Backend Server

| Technology | Purpose | Why We Chose It |
|------------|---------|-----------------|
| **Kotlin** | Primary Language | Shared language with Android, coroutines support |
| **Ktor** | Web Framework | Lightweight, async, Kotlin-native |
| **MongoDB** | Database | Flexible schema, horizontal scaling, JSON-like documents |
| **JWT** | Authentication | Stateless, secure, industry standard |
| **WebSocket** | Real-time | Bi-directional, low latency communication |
| **AWS S3** | File Storage | Scalable, reliable, cost-effective |
| **Firebase FCM** | Push Notifications | Reliable delivery, cross-platform |

### Development Tools

| Tool | Purpose |
|------|---------|
| **Android Studio** | IDE |
| **IntelliJ IDEA** | Backend IDE |
| **Git** | Version Control |
| **GitHub Actions** | CI/CD |
| **Docker** | Containerization |
| **Postman** | API Testing |

---

## 🚀 Getting Started

### Prerequisites

Before you begin, ensure you have the following installed:

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17** or higher
- **MongoDB 6.0+** (local or Atlas)
- **Kotlin 1.9+**
- **Gradle 8.0+**

### Clone the Repository

```bash
git clone https://github.com/pranav291/ghost-messenger.git
cd ghost-messenger
```

### Backend Setup

1. **Navigate to backend directory**
   ```bash
   cd backend
   ```

2. **Configure environment variables**
   Create `src/main/resources/application.conf`:
   ```hocon
   ktor {
       deployment {
           port = 8080
       }
       application {
           modules = [ com.pranavajay.ApplicationKt.module ]
       }
   }
   
   mongodb {
       connectionString = "mongodb://localhost:27017"
       database = "ghost_messenger"
   }
   
   jwt {
       secret = "your-super-secret-key-min-32-chars"
       issuer = "ghost-messenger"
       audience = "ghost-users"
       realm = "Ghost Messenger"
   }
   
   aws {
       accessKey = "your-aws-access-key"
       secretKey = "your-aws-secret-key"
       region = "ap-south-1"
       bucket = "ghost-messenger-media"
   }
   ```

3. **Run the server**
   ```bash
   ./gradlew run
   ```
   Server starts at `http://localhost:8080`

4. **Verify installation**
   ```bash
   curl http://localhost:8080/health
   # Should return: OK
   ```

### Android Setup

1. **Open in Android Studio**
   - File → Open → Select the `app` folder

2. **Configure API URL**
   Update `app/src/main/java/.../di/AppModule.kt`:
   ```kotlin
   // For emulator
   private const val BASE_URL = "http://10.0.2.2:8080/"
   
   // For physical device (replace with your IP)
   // private const val BASE_URL = "http://192.168.1.100:8080/"
   ```

3. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use Android Studio's Run button (Shift+F10)

---

## 📡 API Documentation

### Base URL
```
Production: https://api.ghostmessenger.app/
Development: http://localhost:8080/
```

### Authentication

All authenticated endpoints require a Bearer token in the Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

### Endpoints Overview

<details>
<summary><b>🔐 Authentication</b></summary>

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Login with email/password |
| `POST` | `/api/auth/verify-otp` | Verify OTP code |
| `POST` | `/api/auth/google` | Google OAuth login |
| `POST` | `/api/auth/refresh` | Refresh access token |
| `POST` | `/api/auth/logout` | Logout and invalidate token |

</details>

<details>
<summary><b>💬 Chats & Messages</b></summary>

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/chats` | Get all user's chats |
| `POST` | `/api/chats` | Create new chat |
| `GET` | `/api/messages/{chatId}` | Get messages for chat |
| `POST` | `/api/messages` | Send message |
| `POST` | `/api/messages/forward` | Forward message |
| `DELETE` | `/api/messages/{id}` | Delete message |
| `PUT` | `/api/chats/{id}/ghost-mode` | Toggle ghost mode |
| `PUT` | `/api/chats/{id}/pin` | Pin/unpin chat |
| `PUT` | `/api/chats/{id}/archive` | Archive chat |

</details>

<details>
<summary><b>📞 Calls</b></summary>

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/calls/initiate` | Start a call |
| `POST` | `/api/calls/{id}/accept` | Accept incoming call |
| `POST` | `/api/calls/{id}/decline` | Decline call |
| `POST` | `/api/calls/{id}/end` | End ongoing call |
| `GET` | `/api/calls/history` | Get call history |

</details>

<details>
<summary><b>📢 Channels</b></summary>

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/channels` | Get subscribed channels |
| `POST` | `/api/channels` | Create channel |
| `POST` | `/api/channels/{id}/subscribe` | Subscribe to channel |
| `POST` | `/api/channels/{id}/unsubscribe` | Unsubscribe |
| `GET` | `/api/channels/search` | Search public channels |

</details>

<details>
<summary><b>📊 Status/Stories</b></summary>

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/status/me` | Get my statuses |
| `GET` | `/api/status` | Get contacts' statuses |
| `POST` | `/api/status` | Create status |
| `POST` | `/api/status/{id}/view` | Mark as viewed |
| `POST` | `/api/status/{id}/react` | React to status |
| `DELETE` | `/api/status/{id}` | Delete status |

</details>

### WebSocket Events

Connect to: `ws://localhost:8080/chat?token=<jwt-token>`

**Client → Server:**
```json
{ "type": "message", "data": { "chatId": "...", "content": "Hello!" } }
{ "type": "typing", "data": { "chatId": "...", "isTyping": true } }
{ "type": "read", "data": { "chatId": "...", "messageId": "..." } }
```

**Server → Client:**
```json
{ "type": "message", "data": { "id": "...", "content": "...", ... } }
{ "type": "typing", "data": { "userId": "...", "isTyping": true } }
{ "type": "online_status", "data": { "userId": "...", "isOnline": true } }
{ "type": "incoming_call", "data": { "callId": "...", "callerId": "..." } }
```

---

## 📅 Development Timeline

### The Journey So Far

```
2022 ─────────────────────────────────────────────────────────────────────────
     │
     ├── Q1: 💡 Concept & Research
     │        - Market research on messaging apps
     │        - Privacy concerns analysis
     │        - Initial concept development
     │        - Technology stack evaluation
     │
     ├── Q2: 📝 Planning & Design
     │        - UI/UX wireframes
     │        - Database schema design
     │        - API architecture planning
     │        - Security protocol research
     │
     ├── Q3: 🏗️ Foundation
     │        - Project setup
     │        - Basic authentication system
     │        - Database integration
     │        - Initial UI components
     │
     └── Q4: 💬 Core Messaging
              - Real-time messaging via WebSocket
              - Message persistence
              - Basic chat UI
              - User profiles

2023 ─────────────────────────────────────────────────────────────────────────
     │
     ├── Q1: 👻 Ghost Mode Development
     │        - Disappearing messages logic
     │        - Timer implementation
     │        - Screenshot detection research
     │        - Message expiry system
     │
     ├── Q2: 📞 Voice & Video Calls
     │        - WebRTC integration
     │        - Call signaling server
     │        - Audio/video streaming
     │        - Call UI implementation
     │
     ├── Q3: 📢 Groups & Channels
     │        - Group chat functionality
     │        - Channel broadcasting
     │        - Admin management
     │        - Member permissions
     │
     └── Q4: 🔐 Security Enhancements
              - End-to-end encryption
              - Biometric authentication
              - App lock feature
              - Security audit

2024 ─────────────────────────────────────────────────────────────────────────
     │
     ├── Q1: 📊 Status/Stories
     │        - 24-hour stories
     │        - Text/image/video status
     │        - Viewer analytics
     │        - Reactions system
     │
     ├── Q2: 🔍 Search & Discovery
     │        - Global search
     │        - Message search
     │        - User discovery
     │        - Channel discovery
     │
     ├── Q3: 🎨 UI/UX Overhaul
     │        - Material Design 3
     │        - Dark/Light themes
     │        - Animations
     │        - Accessibility improvements
     │
     └── Q4: 🧪 Testing & Optimization
              - Unit testing
              - Integration testing
              - Performance optimization
              - Bug fixes

2025 ─────────────────────────────────────────────────────────────────────────
     │
     ├── Q1: 📱 Beta Release Preparation
     │        - Feature freeze
     │        - Beta testing program
     │        - User feedback collection
     │        - Critical bug fixes
     │
     ├── Q2: 🌐 Internationalization
     │        - Multi-language support
     │        - RTL layout support
     │        - Regional compliance
     │        - Localized content
     │
     ├── Q3: ☁️ Cloud Infrastructure
     │        - AWS deployment
     │        - Auto-scaling setup
     │        - CDN integration
     │        - Backup systems
     │
     └── Q4: 🔔 Push Notifications
              - FCM integration
              - Notification channels
              - Silent notifications
              - Notification preferences

2026 ─────────────────────────────────────────────────────────────────────────
     │
     ├── Q1: 💾 Offline Mode
     │        - Local message caching
     │        - Offline message queue
     │        - Sync on reconnect
     │        - Conflict resolution
     │
     ├── Q2: 📤 Backup & Restore
     │        - Cloud backup
     │        - Local backup
     │        - Encrypted backups
     │        - Cross-device restore
     │
     ├── Q3: 🖥️ Multi-Device Support
     │        - Device linking
     │        - Session management
     │        - Sync across devices
     │        - Device authorization
     │
     └── Q4: 🎯 Final Polish
              - Performance tuning
              - UI refinements
              - Documentation
              - Release preparation

2027 ─────────────────────────────────────────────────────────────────────────
     │
     └── 📅 February 22, 2027: 🚀 OFFICIAL RELEASE v1.0.0
              - Public launch on Google Play Store
              - Press release
              - Marketing campaign
              - Community launch event
```

---

## 🗺️ Roadmap

### Completed ✅

| Phase | Features | Status |
|-------|----------|--------|
| **Phase 1** | Core Messaging, Authentication, Basic UI | ✅ Complete |
| **Phase 2** | Ghost Mode, Reactions, Reply/Forward | ✅ Complete |
| **Phase 3** | Voice/Video Calls, Search | ✅ Complete |
| **Phase 4** | Status/Stories, Channels, Groups | ✅ Complete |
| **Phase 5** | Security (Biometric, App Lock, E2E) | ✅ Complete |

### In Progress 🔄

| Phase | Features | Progress |
|-------|----------|----------|
| **Phase 6** | Settings, Notifications, Polish | 80% |

### Planned 📋

| Phase | Features | Target |
|-------|----------|--------|
| **Phase 7** | Push Notifications (FCM) | Q4 2025 |
| **Phase 8** | Offline Mode | Q1 2026 |
| **Phase 9** | Backup & Restore | Q2 2026 |
| **Phase 10** | Multi-Device Sync | Q3 2026 |
| **Phase 11** | iOS Version | 2027 |
| **Phase 12** | Desktop Apps | 2027 |
| **Phase 13** | Web Version | 2028 |

### Future Vision 🔮

**2027 Predictions:**
- 🤖 AI-powered smart replies
- 🌍 Real-time translation
- 🎮 In-chat mini games
- 💰 Integrated payments

**2028 Predictions:**
- 🥽 AR/VR messaging
- 🧠 Sentiment analysis
- 🔗 Blockchain verification
- 🌐 Decentralized architecture

---

## 🤝 Contributing

We love contributions! Ghost Messenger is an open-source project, and we welcome developers of all skill levels.

### How to Contribute

1. **Fork the Repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/ghost-messenger.git
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Make Your Changes**
   - Follow our coding standards
   - Write tests for new features
   - Update documentation

4. **Commit Your Changes**
   ```bash
   git commit -m "feat: add amazing feature"
   ```
   We follow [Conventional Commits](https://www.conventionalcommits.org/)

5. **Push and Create PR**
   ```bash
   git push origin feature/amazing-feature
   ```
   Then open a Pull Request on GitHub

### Contribution Guidelines

- **Code Style**: Follow Kotlin coding conventions
- **Documentation**: Update README and code comments
- **Testing**: Add unit tests for new features
- **Commits**: Use meaningful commit messages
- **PRs**: Keep pull requests focused and small

### Areas We Need Help

- 🐛 Bug fixes
- 📝 Documentation improvements
- 🌍 Translations
- 🧪 Testing
- 🎨 UI/UX improvements
- 🔒 Security audits

---

## 💬 Support

### Get Help

<p align="center">
  <a href="https://t.me/SpiralTechDivision">
    <img src="https://img.shields.io/badge/Telegram-Join%20Community-blue?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"/>
  </a>
</p>

**Join our Telegram community for:**
- 📢 Latest updates and announcements
- 💬 Discussion with other developers
- 🐛 Bug reports and feature requests
- 🤝 Collaboration opportunities
- 📚 Tutorials and guides

### Contact

| Channel | Link |
|---------|------|
| **Telegram Community** | [@SpiralTechDivision](https://t.me/SpiralTechDivision) |
| **GitHub Issues** | [Report a Bug](https://github.com/pranav291/ghost-messenger/issues) |
| **Email** | support@spiraltech.dev |

### FAQ

<details>
<summary><b>Is Ghost Messenger really private?</b></summary>

Yes! We use end-to-end encryption for all messages. Even we cannot read your messages. Ghost Mode ensures messages are deleted from our servers after the set time.
</details>

<details>
<summary><b>Is it free to use?</b></summary>

Ghost Messenger is completely free and open source. We don't have ads, and we don't sell your data. We may introduce optional premium features in the future to sustain development.
</details>

<details>
<summary><b>When will it be released?</b></summary>

The official release is planned for **February 22, 2027**. However, you can build and run the app from source right now!
</details>

<details>
<summary><b>Can I contribute?</b></summary>

Absolutely! We welcome contributions of all kinds. Check out our Contributing section above.
</details>

---

## 📄 License

```
MIT License

Copyright (c) 2022-2027 Spiral Tech Division

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 👨‍💻 Developer

<h3 align="center">Pranav Ajay</h3>
<p align="center">Founder & Lead Developer</p>

<p align="center">
  <a href="https://t.me/SpiralTechDivision">
    <img src="https://img.shields.io/badge/Telegram-Contact-blue?style=flat-square&logo=telegram" alt="Telegram"/>
  </a>
  <a href="https://github.com/pranavajay">
    <img src="https://img.shields.io/badge/GitHub-Follow-black?style=flat-square&logo=github" alt="GitHub"/>
  </a>
</p>

---


<h3 align="center">Spiral Tech Division</h3>
<p align="center">Building the future of private communication</p>

<p align="center">
  <a href="https://t.me/SpiralTechDivision">
    <img src="https://img.shields.io/badge/Join%20Us-Telegram-blue?style=for-the-badge&logo=telegram" alt="Join Telegram"/>
  </a>
</p>

---

<p align="center">
  <sub>Made with ❤️ in India</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/🚀%20Release-22%20February%202027-success?style=for-the-badge" alt="Release Date"/>
</p>

<p align="center">
  <sub>© 2022-2027 Spiral Tech Division. All rights reserved.</sub>
</p>

---

<p align="center">
  <b>⭐ Star this repo if you find it useful! ⭐</b>
</p>
