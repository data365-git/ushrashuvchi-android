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
import java.time.Instant
import java.util.UUID

@Serializable
data class RegisterRequest(val name: String? = null, val existingDeviceId: String? = null)

@Serializable
data class RegisterResponse(val deviceId: String, val token: String)

fun Route.authRoutes() {
    post("/devices/register") {
        val req = call.receive<RegisterRequest>()
        val deviceId = req.existingDeviceId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        val name = req.name ?: "Android device"

        transaction {
            val existing = Devices.select { Devices.id eq deviceId }.firstOrNull()
            if (existing == null) {
                Devices.insert {
                    it[id] = deviceId
                    it[Devices.name] = name
                    it[createdAt] = Instant.now()
                    it[lastSeenAt] = Instant.now()
                }
            } else {
                Devices.update({ Devices.id eq deviceId }) {
                    it[lastSeenAt] = Instant.now()
                }
            }
        }

        val token = DeviceAuth.createToken(deviceId.toString())
        call.respond(HttpStatusCode.OK, RegisterResponse(deviceId.toString(), token))
    }
}
