package com.pioneer

import com.pioneer.plugins.*
import com.pioneer.service.FcmService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureCORS()
    configureSecurity()
    configureSerialization()
    configureWebSockets()
    configureRouting()
    configureDatabase()
    configureFcm()
}

fun Application.configureCORS() {
    install(io.ktor.server.plugins.cors.routing.CORS) {
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowCredentials = true
    }
}

fun Application.configureFcm() {
    // Ищем service account файл в нескольких местах
    val possiblePaths = listOf(
        "firebase-service-account.json",
        "/opt/pioneer/firebase-service-account.json",
        System.getenv("FCM_SERVICE_ACCOUNT_PATH") ?: ""
    ).filter { it.isNotEmpty() }
    
    val found = possiblePaths.firstOrNull { File(it).exists() }
    
    if (found != null) {
        if (FcmService.initFromFile(found)) {
            println("FCM: Initialized from $found")
        }
    } else {
        // Пробуем из переменных окружения
        FcmService.initFromEnv()
    }
}
