package com.example.server.auth

import com.example.server.db.Devices
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

// TODO: When a proper Users table is added, unify device-keyed accounts with user accounts.
//       For now, each phone number maps to a deterministic device UUID (UUID3 of the phone string)
//       so the same phone always resolves to the same deviceId.

@Serializable
data class TelegramOtpRequestBody(val phone: String)

@Serializable
data class TelegramOtpVerifyBody(val phone: String, val code: String)

private fun gatewayToken(): String? =
    System.getenv("TELEGRAM_GATEWAY_TOKEN")?.takeIf { it.isNotBlank() }

private val httpClient: HttpClient by lazy { HttpClient.newHttpClient() }

/** POST to Telegram Gateway API and return the raw response body string. */
private fun telegramPost(token: String, path: String, jsonBody: String): Pair<Int, String> {
    val req = HttpRequest.newBuilder()
        .uri(URI.create("https://gateway.telegram.org/api/$path"))
        .header("Authorization", "Bearer $token")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build()
    val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
    return resp.statusCode() to resp.body()
}

/** Stable deviceId for a phone number — lets the same phone always get the same token. */
private fun deviceIdForPhone(phone: String): UUID =
    UUID.nameUUIDFromBytes("phone:$phone".toByteArray(Charsets.UTF_8))

fun Route.telegramOtpRoutes() {
    // POST /auth/telegram/request — send OTP via Telegram Gateway
    post("/auth/telegram/request") {
        val token = gatewayToken() ?: run {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "otp_not_configured"))
            return@post
        }
        val req = call.receive<TelegramOtpRequestBody>()
        val phone = req.phone.trim()
        if (phone.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phone_required"))
            return@post
        }

        val body = """{"phone_number":"$phone","code_length":6}"""
        val (status, _) = telegramPost(token, "sendVerificationMessage", body)

        if (status in 200..299) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "sent"))
        } else {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to "telegram_gateway_error", "upstream_status" to status.toString()))
        }
    }

    // POST /auth/telegram/verify — check OTP, mint device JWT on success
    post("/auth/telegram/verify") {
        val token = gatewayToken() ?: run {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "otp_not_configured"))
            return@post
        }
        val req = call.receive<TelegramOtpVerifyBody>()
        val phone = req.phone.trim()
        val code = req.code.trim()
        if (phone.isBlank() || code.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phone_and_code_required"))
            return@post
        }

        val body = """{"phone_number":"$phone","code":"$code"}"""
        val (status, responseBody) = telegramPost(token, "checkVerificationStatus", body)

        // Telegram Gateway returns {"ok":true,"result":{"status":"code_valid",...}} on success
        if (status !in 200..299 || !responseBody.contains("\"code_valid\"")) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "code_invalid"))
            return@post
        }

        val devId = deviceIdForPhone(phone)
        transaction {
            val existing = Devices.select { Devices.id eq devId }.firstOrNull()
            if (existing == null) {
                Devices.insert {
                    it[id] = devId
                    it[name] = "Telegram:$phone"
                    it[createdAt] = Instant.now()
                    it[lastSeenAt] = Instant.now()
                }
            } else {
                Devices.update({ Devices.id eq devId }) {
                    it[lastSeenAt] = Instant.now()
                }
            }
        }

        val jwtToken = DeviceAuth.createToken(devId.toString())
        call.respond(mapOf("token" to jwtToken))
    }
}
