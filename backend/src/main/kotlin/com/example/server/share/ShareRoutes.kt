package com.example.server.share

import com.example.server.auth.DeviceAuth.deviceId
import com.example.server.db.Meetings
import com.example.server.db.ShareTokens
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import at.favre.lib.crypto.bcrypt.BCrypt

@Serializable
data class CreateShareRequest(
    val password: String? = null,
    val expiresInDays: Int? = null
)

@Serializable
data class CreateShareResponse(
    val token: String,
    val url: String
)

private fun generateToken(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun Route.shareRoutes(publicBaseUrl: String) {
    authenticate("device") {
        post("/meetings/{id}/share") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val meetingUuid = UUID.fromString(call.parameters["id"]!!)
            val req = call.receive<CreateShareRequest>()

            // Verify ownership
            val ownerOk = transaction {
                Meetings.select { (Meetings.id eq meetingUuid) and (Meetings.deviceId eq devId) }.any()
            }
            if (!ownerOk) {
                call.respond(HttpStatusCode.Forbidden); return@post
            }

            val token = generateToken()
            val passwordHash = req.password?.takeIf { it.isNotBlank() }?.let {
                BCrypt.withDefaults().hashToString(10, it.toCharArray())
            }
            val expiresAt = req.expiresInDays?.let {
                Instant.now().plusSeconds(it.toLong() * 86400)
            }

            transaction {
                ShareTokens.insert {
                    it[ShareTokens.token] = token
                    it[meetingId] = meetingUuid
                    it[ShareTokens.passwordHash] = passwordHash
                    it[ShareTokens.expiresAt] = expiresAt
                    it[createdAt] = Instant.now()
                }
            }

            val url = "$publicBaseUrl/s/$token"
            call.respond(HttpStatusCode.OK, CreateShareResponse(token, url))
        }

        delete("/meetings/{id}/share/{token}") {
            val tok = call.parameters["token"]!!
            transaction {
                ShareTokens.update({ ShareTokens.token eq tok }) {
                    it[revokedAt] = Instant.now()
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}
