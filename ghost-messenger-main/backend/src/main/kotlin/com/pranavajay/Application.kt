package com.pranavajay

import com.pranavajay.database.DatabaseFactory
import com.pranavajay.plugins.*
import com.pranavajay.utils.JwtConfig
import com.pranavajay.utils.S3Utils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize utilities
    JwtConfig.init(environment)
    S3Utils.init(environment)
    
    // Initialize database
    try {
        DatabaseFactory.init(environment)
    } catch (e: Exception) {
        log.warn("MongoDB not connected: ${e.message}. Running in demo mode.")
    }
    
    // CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }
    
    configureSerialization()
    configureSecurity()
    configureWebSockets()
    configureRouting()
}
