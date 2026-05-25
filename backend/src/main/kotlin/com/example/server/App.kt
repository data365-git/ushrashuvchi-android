package com.example.server

import com.example.server.auth.DeviceAuth
import com.example.server.auth.authRoutes
import com.example.server.audio.audioRoutes
import com.example.server.db.Db
import com.example.server.meetings.meetingRoutes
import com.example.server.share.publicRoutes
import com.example.server.share.shareRoutes
import com.example.server.share.webViewerRoute
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
import kotlinx.serialization.json.Json

private val appLogger = org.slf4j.LoggerFactory.getLogger("App")

fun main() {
    try {
        Db.init()
        appLogger.info("Database initialized")
    } catch (e: Exception) {
        appLogger.error("Database not initialized: ${e.javaClass.simpleName}: ${e.message}", e)
    }

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

            route("/api/v1") {
                authRoutes()
                meetingRoutes()
                audioRoutes()
                shareRoutes(publicBaseUrl)
                publicRoutes()
            }
        }
    }.start(wait = true)
}
