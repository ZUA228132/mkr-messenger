package com.pioneer.routes

import com.pioneer.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.management.ManagementFactory

@Serializable
data class AdminStatsResponse(
    val totalUsers: Int,
    val onlineUsers: Int,
    val totalChats: Int,
    val todayMessages: Int,
    val totalMessages: Int,
    val totalChannels: Int,
    val todayCalls: Int,
    val usedMemory: Long,
    val cpuUsage: Double,
    val uptime: Long,
    val timestamp: Long
)

@Serializable
data class SystemInfoResponse(
    val osName: String,
    val osVersion: String,
    val javaVersion: String,
    val processors: Int,
    val totalMemory: Long,
    val freeMemory: Long,
    val maxMemory: Long,
    val usedMemory: Long,
    val threadCount: Int,
    val dbConnections: Int,
    val wsConnections: Int
)

@Serializable
data class ActivityResponse(
    val newUsersToday: Int,
    val activeUsersToday: Int,
    val messagesPerHour: Int,
    val peakOnline: Int,
    val directChats: Int,
    val groupChats: Int,
    val channelChats: Int,
    val totalCalls: Int,
    val successfulCalls: Int,
    val missedCalls: Int,
    val avgCallDuration: Int,
    val recentActivity: List<ActivityItem>
)

@Serializable
data class ActivityItem(
    val username: String,
    val action: String,
    val timestamp: Long,
    val isOnline: Boolean
)

@Serializable
data class ServerHealthResponse(
    val status: String,
    val backend: String,
    val database: String,
    val uptime: Long,
    val version: String,
    val timestamp: Long
)

fun Route.adminRoutes() {
    route("/api/admin") {
        authenticate("auth-jwt") {
            // Admin stats endpoint
            get("/stats") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                // Only admins can access
                if (accessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@get
                }
                
                val stats = transaction {
                    val totalUsers = Users.selectAll().count().toInt()
                    val onlineUsers = ConnectionManager.getOnlineUserIds().size
                    val totalChats = Chats.selectAll().count().toInt()
                    
                    // Messages today
                    val todayStart = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                    val todayMessages = Messages.select { 
                        Messages.timestamp greater todayStart 
                    }.count().toInt()
                    
                    val totalMessages = Messages.selectAll().count().toInt()
                    
                    // Channels
                    val totalChannels = Channels.selectAll().count().toInt()
                    
                    // Calls today
                    val todayCalls = try {
                        CallHistory.select {
                            CallHistory.startedAt greater todayStart
                        }.count().toInt()
                    } catch (e: Exception) {
                        0
                    }
                    
                    // Memory
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    
                    // CPU (simplified)
                    val cpuUsage = ManagementFactory.getOperatingSystemMXBean().systemLoadAverage
                    
                    AdminStatsResponse(
                        totalUsers = totalUsers,
                        onlineUsers = onlineUsers,
                        totalChats = totalChats,
                        todayMessages = todayMessages,
                        totalMessages = totalMessages,
                        totalChannels = totalChannels,
                        todayCalls = todayCalls,
                        usedMemory = usedMemory,
                        cpuUsage = if (cpuUsage >= 0) cpuUsage * 10 else 0.0,
                        uptime = ManagementFactory.getRuntimeMXBean().uptime / 1000,
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                call.respond(stats)
            }
            
            // System info endpoint
            get("/system") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (accessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@get
                }
                
                val runtime = Runtime.getRuntime()
                val osBean = ManagementFactory.getOperatingSystemMXBean()
                
                val systemInfo = SystemInfoResponse(
                    osName = System.getProperty("os.name"),
                    osVersion = System.getProperty("os.version"),
                    javaVersion = System.getProperty("java.version"),
                    processors = runtime.availableProcessors(),
                    totalMemory = runtime.totalMemory(),
                    freeMemory = runtime.freeMemory(),
                    maxMemory = runtime.maxMemory(),
                    usedMemory = runtime.totalMemory() - runtime.freeMemory(),
                    threadCount = Thread.activeCount(),
                    dbConnections = 10, // TODO: get from HikariCP
                    wsConnections = ConnectionManager.getOnlineUserIds().size
                )
                
                call.respond(systemInfo)
            }
            
            // Activity endpoint
            get("/activity") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (accessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@get
                }
                
                val activity = transaction {
                    val todayStart = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                    
                    // New users today
                    val newUsersToday = Users.select {
                        Users.createdAt greater todayStart
                    }.count().toInt()
                    
                    // Active users today (sent message or logged in)
                    val activeUsersToday = Users.select {
                        Users.lastSeen greater todayStart
                    }.count().toInt()
                    
                    // Messages per hour
                    val todayMessages = Messages.select {
                        Messages.timestamp greater todayStart
                    }.count().toInt()
                    val messagesPerHour = todayMessages / 24
                    
                    // Peak online (current online as approximation)
                    val peakOnline = ConnectionManager.getOnlineUserIds().size
                    
                    // Chat types
                    val directChats = Chats.select { Chats.type eq "direct" }.count().toInt()
                    val groupChats = Chats.select { Chats.type eq "group" }.count().toInt()
                    val channelChats = Channels.selectAll().count().toInt()
                    
                    // Calls
                    val totalCalls = try {
                        CallHistory.selectAll().count().toInt()
                    } catch (e: Exception) {
                        0
                    }
                    
                    val successfulCalls = try {
                        CallHistory.select {
                            CallHistory.status eq "completed"
                        }.count().toInt()
                    } catch (e: Exception) {
                        0
                    }
                    
                    val missedCalls = try {
                        CallHistory.select {
                            CallHistory.status eq "missed"
                        }.count().toInt()
                    } catch (e: Exception) {
                        0
                    }
                    
                    val avgCallDuration = try {
                        val durations = CallHistory.select {
                            CallHistory.duration.isNotNull()
                        }.map { it[CallHistory.duration] ?: 0 }
                        if (durations.isNotEmpty()) durations.average().toInt() else 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    // Recent activity
                    val recentActivity = Users.selectAll()
                        .orderBy(Users.lastSeen to SortOrder.DESC)
                        .limit(10)
                        .map { row ->
                            ActivityItem(
                                username = row[Users.username],
                                action = "Последняя активность",
                                timestamp = row[Users.lastSeen] ?: 0,
                                isOnline = ConnectionManager.isOnline(row[Users.id])
                            )
                        }
                    
                    ActivityResponse(
                        newUsersToday = newUsersToday,
                        activeUsersToday = activeUsersToday,
                        messagesPerHour = messagesPerHour,
                        peakOnline = peakOnline,
                        directChats = directChats,
                        groupChats = groupChats,
                        channelChats = channelChats,
                        totalCalls = totalCalls,
                        successfulCalls = successfulCalls,
                        missedCalls = missedCalls,
                        avgCallDuration = avgCallDuration,
                        recentActivity = recentActivity
                    )
                }
                
                call.respond(activity)
            }
            
            // Server health check
            get("/health") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (accessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@get
                }
                
                val dbStatus = try {
                    transaction {
                        Users.selectAll().limit(1).count()
                    }
                    "online"
                } catch (e: Exception) {
                    "offline"
                }
                
                val health = ServerHealthResponse(
                    status = "ok",
                    backend = "online",
                    database = dbStatus,
                    uptime = ManagementFactory.getRuntimeMXBean().uptime / 1000,
                    version = "1.0.0",
                    timestamp = System.currentTimeMillis()
                )
                
                call.respond(health)
            }
            
            // Get server logs
            get("/logs") {
                val principal = call.principal<JWTPrincipal>()
                val accessLevel = principal?.payload?.getClaim("accessLevel")?.asInt() ?: 0
                
                if (accessLevel < 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Недостаточно прав"))
                    return@get
                }
                
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 100
                
                try {
                    val logFile = java.io.File("/opt/pioneer/logs/backend.log")
                    if (logFile.exists()) {
                        val allLines = logFile.readLines()
                        val lastLines = allLines.takeLast(lines)
                        call.respond(mapOf("logs" to lastLines))
                    } else {
                        call.respond(mapOf("logs" to emptyList<String>(), "error" to "Log file not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }
    }
    
    // Public health endpoint (no auth required)
    get("/api/health") {
        call.respondText(
            """{"status":"ok","timestamp":${System.currentTimeMillis()},"version":"1.0.0"}""",
            ContentType.Application.Json
        )
    }
}
