package com.pranavajay.plugins

import com.pranavajay.routes.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Ghost Messenger API - by Pranav Ajay")
        }
        
        get("/health") {
            call.respondText("OK")
        }
        
        authRoutes()
        messageRoutes()
        uploadRoutes()
        groupRoutes()
        statusRoutes()
        callRoutes()
        reactionRoutes()
        searchRoutes()
        channelRoutes()
    }
}
