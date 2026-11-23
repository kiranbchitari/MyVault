package com.myvault.app.backup

import com.myvault.app.data.db.BackupMetadataDao
import com.myvault.app.data.db.BackupMetadataEntity
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest

/** A local HTTP server that behaves like the parts of Google Drive v3 the app uses. */
class FakeDrive : AutoCloseable {
    class Stored(val id: String, val name: String, val parent: String?, val folder: Boolean, val bytes: ByteArray, val created: Int)

    val files = LinkedHashMap<String, Stored>()
    val authorizationHeaders = mutableListOf<String?>()
    val requests = mutableListOf<String>()

    /** Status codes to return for upcoming requests instead of handling them. */
    val failures = ArrayDeque<Int>()
    var corruptUploads = false

    /** Caps a listing page below what the client asks for, to exercise paging. */
    var maxPageSize = Int.MAX_VALUE

    /** Puts a backup straight into [parent], as if it had been uploaded earlier. */
    fun seed(parent: String, name: String): Stored =
        Stored("file-${++counter}", name, parent, false, ByteArray(1), counter).also { files[it.id] = it }

    /** Moves a file or folder into [parent], as the user can in the Drive app. */
    fun move(id: String, parent: String) {
        val old = files.getValue(id)
        files[id] = Stored(old.id, old.name, parent, old.folder, old.bytes, old.created)
    }

    private val sessions = HashMap<String, Pair<String, String?>>()
    private var counter = 0
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (e: Exception) {
                respond(exchange, 500, """{"error":"${e.message}"}""")
            }
        }
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val query = parseQuery(exchange.requestURI.rawQuery)
        val body = exchange.requestBody.readBytes()
        authorizationHeaders += exchange.requestHeaders.getFirst("Authorization")
        requests += "${exchange.requestMethod} $path"
        failures.removeFirstOrNull()?.let { return respond(exchange, it, """{"error":"injected"}""") }

        when {
            path == "/upload/drive/v3/files" && exchange.requestMethod == "POST" -> {
                val meta = Json.parseToJsonElement(String(body)).jsonObject
                val session = "session-${++counter}"
                sessions[session] = meta.getValue("name").jsonPrimitive.content to meta["parents"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                exchange.responseHeaders.add("Location", "$baseUrl/upload/drive/v3/files?upload_id=$session")
                respond(exchange, 200, "")
            }
            path == "/upload/drive/v3/files" && exchange.requestMethod == "PUT" -> {
                val (name, parent) = sessions.remove(query["upload_id"]) ?: return respond(exchange, 404, "{}")
                val stored = if (corruptUploads) body.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() } else body
                val file = Stored("file-${++counter}", name, parent, false, stored, counter)
                files[file.id] = file
                respond(exchange, 200, json(file))
            }
            path == "/drive/v3/files" && exchange.requestMethod == "POST" -> {
                val meta = Json.parseToJsonElement(String(body)).jsonObject
                val parent = meta["parents"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                val folder = Stored("folder-${++counter}", meta.getValue("name").jsonPrimitive.content, parent, true, ByteArray(0), counter)
                files[folder.id] = folder
                respond(exchange, 200, json(folder))
            }
            path == "/drive/v3/files" && exchange.requestMethod == "GET" -> {
                val q = query["q"].orEmpty()
                val parent = Regex("'([^']*)' in parents").find(q)?.groupValues?.get(1)
                val matches = if (q.contains("mimeType = 'application/vnd.google-apps.folder'")) {
                    val name = Regex("name = '([^']*)'").find(q)!!.groupValues[1]
                    files.values.filter { it.folder && it.name == name && (parent == null || it.parent == parent) }.sortedBy { it.created }
                } else {
                    files.values.filter { it.parent == parent }.sortedByDescending { it.created }
                }
                // Page tokens are offsets into the result.
                val from = query["pageToken"]?.toInt() ?: 0
                val size = minOf(query["pageSize"]?.toInt() ?: 100, maxPageSize)
                val page = matches.drop(from).take(size)
                val next = if (from + size < matches.size) ""","nextPageToken":"${from + size}"""" else ""
                respond(exchange, 200, """{"files":[${page.joinToString(",", transform = ::json)}]$next}""")
            }
            path.startsWith("/drive/v3/files/") -> {
                val file = files[path.removePrefix("/drive/v3/files/")] ?: return respond(exchange, 404, "{}")
                when {
                    exchange.requestMethod == "DELETE" -> {
                        files.remove(file.id)
                        exchange.sendResponseHeaders(204, -1)
                        exchange.close()
                    }
                    query["alt"] == "media" -> {
                        exchange.sendResponseHeaders(200, file.bytes.size.toLong())
                        exchange.responseBody.use { it.write(file.bytes) }
                    }
                    else -> respond(exchange, 200, json(file))
                }
            }
            else -> respond(exchange, 404, "{}")
        }
    }

    private fun json(file: Stored): String {
        val md5 = MessageDigest.getInstance("MD5").digest(file.bytes).joinToString("") { "%02x".format(it) }
        val name = Json.encodeToString(String.serializer(), file.name)
        return """{"id":"${file.id}","name":$name,"size":"${file.bytes.size}","createdTime":"2026-11-21T10:00:${"%02d".format(file.created)}Z","md5Checksum":"$md5","kind":"drive#file"}"""
    }

    private fun respond(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { it.contains('=') }.associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }

    override fun close() = server.stop(0)
}

class FakeBackupMetadataDao : BackupMetadataDao {
    val rows = MutableStateFlow<List<BackupMetadataEntity>>(emptyList())
    private var nextId = 1L

    override fun observe(): Flow<List<BackupMetadataEntity>> = rows
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun awaitingUpload() = rows.value
        .filter { it.destination == BackupDestination.GOOGLE_DRIVE && it.status != BackupStatus.COMPLETED && it.localPath != null }
        .sortedBy { it.createdAt }
    override suspend fun insert(entity: BackupMetadataEntity): Long {
        val id = nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: BackupMetadataEntity) {
        rows.value = rows.value.map { if (it.id == entity.id) entity else it }
    }
    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}
