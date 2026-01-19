package com.pioneer.plugins

import com.pioneer.routes.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        // Статические файлы (загруженные файлы)
        static("/uploads") {
            files("/root/uploads")
            
            static("avatars") {
                files("/root/uploads/avatars")
            }
            static("images") {
                files("/root/uploads/images")
            }
            static("videos") {
                files("/root/uploads/videos")
            }
            static("voice") {
                files("/root/uploads/voice")
            }
            static("files") {
                files("/root/uploads/files")
            }
            static("video_notes") {
                files("/root/uploads/video_notes")
            }
        }
        
        authRoutes()
        webSessionRoutes() // Web QR login
        adminRoutes() // Admin panel
        userRoutes()
        fcmTokenRoute() // FCM токен
        chatRoutes()
        messageRoutes()
        taskRoutes()
        financeRoutes()
        mapRoutes()
        fileRoutes() // Загрузка файлов
        liveKitRoutes() // LiveKit звонки
        callRoutes() // WebRTC сигнализация (legacy)
        channelRoutes() // Каналы
        reactionRoutes() // Реакции на сообщения
        locationRoutes() // Геолокация и приватность
        // Убраны: reelsRoutes(), storiesRoutes() - не нужны для защищённого мессенджера
    }
}
