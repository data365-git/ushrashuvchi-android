package com.example.server

import com.example.server.auth.DeviceAuth
import com.example.server.auth.authRoutes
import com.example.server.auth.pairingRoutes
import com.example.server.auth.telegramOtpRoutes
import com.example.server.audio.audioRoutes
import com.example.server.dashboard.dashboardRoutes
import com.example.server.db.Db
import com.example.server.db.Meetings
import com.example.server.folders.folderRoutes
import com.example.server.meetings.meetingRoutes
import com.example.server.share.publicRoutes
import com.example.server.share.shareRoutes
import com.example.server.share.webViewerRoute
import com.example.server.transcribe.Transcription
import com.example.server.video.Transcoder
import com.example.server.video.videoRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val appLogger = org.slf4j.LoggerFactory.getLogger("App")

private fun startVideoRetentionWorker() {
    val volumeRoot = System.getenv("VOLUME_ROOT") ?: "/data"
    kotlin.concurrent.fixedRateTimer(
        name = "video-retention",
        daemon = true,
        initialDelay = TimeUnit.HOURS.toMillis(1),
        period = TimeUnit.HOURS.toMillis(6)
    ) {
        try {
            val now = Instant.now()
            val expired = transaction {
                Meetings
                    .select {
                        (Meetings.videoExpiresAt lessEq now) and
                        (Meetings.videoStatus eq "READY")
                    }
                    .map { Triple(it[Meetings.id].value, it[Meetings.deviceId].value, it[Meetings.videoObjectKey]) }
            }
            for ((meetingId, deviceId, _) in expired) {
                try {
                    val file = File("$volumeRoot/video/$deviceId/$meetingId.mp4")
                    if (file.exists()) file.delete()
                    transaction {
                        Meetings.update({ Meetings.id eq meetingId }) {
                            it[videoStatus] = "EXPIRED"
                        }
                    }
                } catch (e: Exception) {
                    appLogger.warn("Retention: failed to expire meeting $meetingId: ${e.message}")
                }
            }
            if (expired.isNotEmpty()) appLogger.info("Retention: expired ${expired.size} video(s)")
        } catch (e: Exception) {
            appLogger.error("Retention worker error: ${e.message}", e)
        }
    }
}

fun main() {
    try {
        Db.init()
        appLogger.info("Database initialized")
    } catch (e: Exception) {
        appLogger.error("Database not initialized: ${e.javaClass.simpleName}: ${e.message}", e)
    }

    val transcoder = Transcoder()
    val transcriber = Transcription()

    startVideoRetentionWorker()

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val publicBaseUrl = System.getenv("PUBLIC_BASE_URL")
        ?: "https://example.up.railway.app"  // override in Railway

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        DeviceAuth.configure(this)

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(CallLogging)
        install(PartialContent)
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader("X-Share-Password")
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                cause.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (cause.message ?: "unknown"))
                )
            }
        }

        routing {
            get("/") { call.respondText("Ushrashuvchi API · v0.1") }
            get("/health") { call.respond(mapOf("status" to "ok", "version" to "0.1.0")) }

            webViewerRoute()
            dashboardRoutes()

            route("/api/v1") {
                authRoutes()
                pairingRoutes()
                telegramOtpRoutes()
                meetingRoutes()
                audioRoutes()
                videoRoutes(transcoder, transcriber)
                folderRoutes()
                shareRoutes(publicBaseUrl)
                publicRoutes()
            }
        }
    }.start(wait = true)
}
