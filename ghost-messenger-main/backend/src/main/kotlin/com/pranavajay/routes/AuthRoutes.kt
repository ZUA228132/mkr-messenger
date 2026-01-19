package com.pranavajay.routes

import com.pranavajay.database.UserRepository
import com.pranavajay.models.*
import com.pranavajay.utils.JwtConfig
import com.pranavajay.utils.PasswordUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.authRoutes() {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            
            // Check if user exists
            val existingUser = UserRepository.findByEmail(request.email)
            if (existingUser != null) {
                call.respond(HttpStatusCode.Conflict, AuthResponse(
                    success = false,
                    message = "User with this email already exists"
                ))
                return@post
            }
            
            val userId = UUID.randomUUID().toString()
            val userEntity = UserEntity(
                id = userId,
                username = request.username,
                email = request.email,
                passwordHash = PasswordUtils.hashPassword(request.password),
                phone = request.phone
            )
            
            UserRepository.createUser(userEntity)
            
            val token = JwtConfig.generateToken(userId, request.email)
            val user = User(
                id = userId,
                username = request.username,
                email = request.email,
                phone = request.phone
            )
            
            call.respond(HttpStatusCode.Created, AuthResponse(
                success = true,
                token = token,
                user = user,
                message = "User registered successfully"
            ))
        }
        
        post("/login") {
            val request = call.receive<LoginRequest>()
            
            val userEntity = UserRepository.findByEmail(request.email)
            if (userEntity == null) {
                call.respond(HttpStatusCode.Unauthorized, AuthResponse(
                    success = false,
                    message = "Invalid email or password"
                ))
                return@post
            }
            
            if (!PasswordUtils.verifyPassword(request.password, userEntity.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, AuthResponse(
                    success = false,
                    message = "Invalid email or password"
                ))
                return@post
            }
            
            val token = JwtConfig.generateToken(userEntity.id, userEntity.email)
            val user = User(
                id = userEntity.id,
                username = userEntity.username,
                email = userEntity.email,
                phone = userEntity.phone,
                profileImage = userEntity.profileImage,
                bio = userEntity.bio,
                isOnline = true,
                lastSeen = userEntity.lastSeen
            )
            
            UserRepository.setOnlineStatus(userEntity.id, true)
            
            call.respond(AuthResponse(
                success = true,
                token = token,
                user = user,
                message = "Login successful"
            ))
        }
        
        post("/google") {
            val body = call.receive<Map<String, String>>()
            val idToken = body["idToken"] ?: run {
                call.respond(HttpStatusCode.BadRequest, AuthResponse(
                    success = false,
                    message = "ID token required"
                ))
                return@post
            }
            
            // TODO: Verify Google ID token and extract user info
            // For now, return placeholder
            call.respond(AuthResponse(
                success = false,
                message = "Google auth not configured"
            ))
        }
    }
    
    authenticate("auth-jwt") {
        route("/api/users") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val userEntity = UserRepository.findById(userId)
                if (userEntity == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    return@get
                }
                
                val user = User(
                    id = userEntity.id,
                    username = userEntity.username,
                    email = userEntity.email,
                    phone = userEntity.phone,
                    profileImage = userEntity.profileImage,
                    bio = userEntity.bio,
                    isOnline = userEntity.isOnline,
                    lastSeen = userEntity.lastSeen
                )
                
                call.respond(user)
            }
            
            put("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@put
                }
                
                val request = call.receive<UpdateProfileRequest>()
                val updates = org.bson.Document()
                
                request.username?.let { updates.append("username", it) }
                request.bio?.let { updates.append("bio", it) }
                request.profileImage?.let { updates.append("profileImage", it) }
                request.phone?.let { updates.append("phone", it) }
                
                UserRepository.updateUser(userId, updates)
                
                call.respond(mapOf("success" to true, "message" to "Profile updated"))
            }
            
            put("/fcm-token") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@put
                }
                
                val request = call.receive<UpdateFcmTokenRequest>()
                UserRepository.updateUser(userId, org.bson.Document("fcmToken", request.token))
                
                call.respond(mapOf("success" to true))
            }
            
            get("/search") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@get
                }
                
                val query = call.request.queryParameters["q"] ?: ""
                if (query.length < 2) {
                    call.respond(emptyList<User>())
                    return@get
                }
                
                val users = UserRepository.searchUsers(query)
                    .filter { it.id != userId }
                    .map { entity ->
                        User(
                            id = entity.id,
                            username = entity.username,
                            email = entity.email,
                            phone = entity.phone,
                            profileImage = entity.profileImage,
                            bio = entity.bio,
                            isOnline = entity.isOnline,
                            lastSeen = entity.lastSeen
                        )
                    }
                
                call.respond(users)
            }
        }
    }
}
