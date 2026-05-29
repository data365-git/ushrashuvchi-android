package com.example.server.auth

import com.example.server.auth.DeviceAuth.deviceId
import com.example.server.db.PairingCodes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class PairStartResponse(val code: String, val expiresInSeconds: Int)

@Serializable
data class PairClaimRequest(val code: String)

@Serializable
data class PairClaimResponse(val token: String)

fun Route.pairingRoutes() {
    // POST /auth/pair/start — authenticated device generates a 6-digit pairing code
    authenticate("device") {
        post("/auth/pair/start") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())

            val code = (100000..999999).random().toString()
            val now = Instant.now()
            val expiresAt = now.plusSeconds(600)

            transaction {
                PairingCodes.insert {
                    it[id] = UUID.randomUUID()
                    it[PairingCodes.code] = code
                    it[deviceId] = devId
                    it[PairingCodes.expiresAt] = expiresAt
                    it[claimedAt] = null
                    it[createdAt] = now
                }
            }

            call.respond(PairStartResponse(code = code, expiresInSeconds = 600))
        }
    }

    // POST /auth/pair/claim — unauthenticated; browser submits code to get a token
    post("/auth/pair/claim") {
        val req = call.receive<PairClaimRequest>()
        val now = Instant.now()

        val row = transaction {
            PairingCodes.select {
                (PairingCodes.code eq req.code) and
                (PairingCodes.claimedAt.isNull()) and
                (PairingCodes.expiresAt greater now)
            }.firstOrNull()
        }

        if (row == null) {
            call.respond(HttpStatusCode.Gone, mapOf("error" to "code_invalid_or_expired"))
            return@post
        }

        val codeId = row[PairingCodes.id].value
        val devId = row[PairingCodes.deviceId].value

        transaction {
            PairingCodes.update({ PairingCodes.id eq codeId }) {
                it[claimedAt] = Instant.now()
            }
        }

        val token = DeviceAuth.createToken(devId.toString())
        call.respond(PairClaimResponse(token = token))
    }
}
