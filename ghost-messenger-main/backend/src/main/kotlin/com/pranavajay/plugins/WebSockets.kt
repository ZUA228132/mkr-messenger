package com.pranavajay.plugins

import com.pranavajay.utils.JwtConfig
import com.pranavajay.websocket.chatServer
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            
            if (token == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Token required"))
                return@webSocket
            }
            
            val userId = JwtConfig.getUserIdFromToken(token)
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
            
            chatServer.onConnect(this, token)
            
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        chatServer.handleMessage(userId, text)
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                // Client disconnected
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                chatServer.onDisconnect(userId)
            }
        }
    }
}
