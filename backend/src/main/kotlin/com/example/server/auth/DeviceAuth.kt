package com.example.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

object DeviceAuth {
    private val secret: String by lazy {
        System.getenv("JWT_SECRET") ?: "dev-secret-change-me-please-32chars-min"
    }
    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(secret) }
    private const val ISSUER = "ushrashuvchi-backend"
    private const val AUDIENCE = "ushrashuvchi-app"

    fun createToken(deviceId: String): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("deviceId", deviceId)
            .withIssuedAt(Date())
            .sign(algorithm)

    fun configure(app: Application) {
        app.install(Authentication) {
            jwt("device") {
                verifier(
                    JWT.require(algorithm)
                        .withIssuer(ISSUER)
                        .withAudience(AUDIENCE)
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("deviceId").asString() != null) {
                        JWTPrincipal(credential.payload)
                    } else null
                }
            }
        }
    }

    fun JWTPrincipal.deviceId(): String =
        this.payload.getClaim("deviceId").asString()
}
