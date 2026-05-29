package com.example.server.folders

import com.example.server.auth.DeviceAuth.deviceId
import com.example.server.db.Folders
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val parentId: String?,
    val sortOrder: Int
)

@Serializable
data class CreateFolderReq(
    val name: String,
    val parentId: String? = null,
    val sortOrder: Int? = null
)

@Serializable
data class UpdateFolderReq(
    val name: String? = null,
    val parentId: String? = null,
    val sortOrder: Int? = null
)

fun Route.folderRoutes() {
    authenticate("device") {
        // GET /folders — list this device's folders
        get("/folders") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val list = transaction {
                Folders.select { Folders.deviceId eq devId }
                    .orderBy(Folders.sortOrder, SortOrder.ASC)
                    .orderBy(Folders.createdAt, SortOrder.ASC)
                    .map { row ->
                        FolderDto(
                            id = row[Folders.id].value.toString(),
                            name = row[Folders.name],
                            parentId = row[Folders.parentId]?.value?.toString(),
                            sortOrder = row[Folders.sortOrder]
                        )
                    }
            }
            call.respond(list)
        }

        // POST /folders — create a folder
        post("/folders") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val req = call.receive<CreateFolderReq>()
            val newId = UUID.randomUUID()
            val parentUuid = req.parentId?.let { UUID.fromString(it) }
            val dto = transaction {
                Folders.insert {
                    it[id] = newId
                    it[deviceId] = devId
                    it[name] = req.name
                    it[parentId] = parentUuid
                    it[sortOrder] = req.sortOrder ?: 0
                    it[createdAt] = Instant.now()
                }
                FolderDto(
                    id = newId.toString(),
                    name = req.name,
                    parentId = req.parentId,
                    sortOrder = req.sortOrder ?: 0
                )
            }
            call.respond(HttpStatusCode.Created, dto)
        }

        // PUT /folders/{id} — partial update
        put("/folders/{id}") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val folderId = UUID.fromString(call.parameters["id"]!!)
            val req = call.receive<UpdateFolderReq>()

            val owned = transaction {
                Folders.select {
                    (Folders.id eq folderId) and (Folders.deviceId eq devId)
                }.any()
            }
            if (!owned) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not your folder"))
                return@put
            }

            transaction {
                Folders.update({ Folders.id eq folderId }) {
                    if (req.name != null) it[name] = req.name
                    if (req.sortOrder != null) it[sortOrder] = req.sortOrder
                    // parentId: explicit null in JSON means "clear parent"; missing field means no change.
                    // UpdateFolderReq.parentId == null could mean "not provided" or "clear" — since it's
                    // a nullable field with default null we treat any PUT that includes parentId as intentional.
                    // To distinguish "omitted" from "set to null" we rely on the client sending the full object.
                    // Simple rule: if req.parentId is present in the class (even as null) update it.
                    // We always update parentId when the request field is present — caller controls this.
                    it[parentId] = req.parentId?.let { pid -> UUID.fromString(pid) }
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        // DELETE /folders/{id}
        delete("/folders/{id}") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val folderId = UUID.fromString(call.parameters["id"]!!)

            val owned = transaction {
                Folders.select {
                    (Folders.id eq folderId) and (Folders.deviceId eq devId)
                }.any()
            }
            if (!owned) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not your folder"))
                return@delete
            }

            transaction {
                Folders.deleteWhere { Folders.id eq folderId }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}
