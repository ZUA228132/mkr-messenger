package com.pioneer.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.security.SecureRandom
import java.util.*

object JwtConfig {
    private val secret = generateSecret()
    private const val issuer = "pioneer-server"
    private const val audience = "pioneer-app"
    private const val validityMs = 7 * 24 * 60 * 60 * 1000L // 7 days
    
    private val algorithm = Algorithm.HMAC256(secret)
    
    private fun generateSecret(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    fun generateToken(userId: String, accessLevel: Int): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("accessLevel", accessLevel)
            .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
            .sign(algorithm)
    }
    
    fun configureJwt(config: JWTAuthenticationProvider.Config) {
        config.verifier(
            JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
        )
        config.validate { credential ->
            if (credential.payload.getClaim("userId").asString() != null) {
                JWTPrincipal(credential.payload)
            } else {
                null
            }
        }
    }
}

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            JwtConfig.configureJwt(this)
        }
    }
}
