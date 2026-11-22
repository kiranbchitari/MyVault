package com.myvault.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** A Drive request failed. [retryable] failures are worth trying again later, unchanged. */
class DriveException(message: String, val retryable: Boolean, val authExpired: Boolean = false) : IOException(message)

@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    /** Drive reports sizes as decimal strings. */
    val size: String? = null,
    val createdTime: String? = null,
    val md5Checksum: String? = null,
) {
    val sizeBytes: Long get() = size?.toLongOrNull() ?: 0
}

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

/**
 * Minimal Google Drive v3 client over HTTPS, covering exactly what encrypted backups need.
 *
 * It is written for the `drive.file` scope: the app can only see files it created itself. The
 * only thing it ever uploads is an already-encrypted backup archive.
 */
class DriveClient(
    private val token: suspend () -> String,
    private val baseUrl: String = "https://www.googleapis.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the id of the app's backup folder, creating it on first use. */
    suspend fun ensureFolder(): String {
        val query = "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val existing = json.decodeFromString(
            DriveFileList.serializer(),
            request("GET", "$baseUrl/drive/v3/files?q=${encode(query)}&spaces=drive&fields=${encode("files(id,name)")}"),
        ).files.firstOrNull()
        if (existing != null) return existing.id
        val body = """{"name":"$FOLDER_NAME","mimeType":"$FOLDER_MIME"}"""
        return json.decodeFromString(DriveFile.serializer(), request("POST", "$baseUrl/drive/v3/files?fields=id", body)).id
    }

    /**
     * Uploads [file] with a resumable session, then asks Drive for the stored file's size and MD5
     * and compares them with what was sent. A mismatching upload is deleted and reported as failed.
     */
    suspend fun upload(folderId: String, name: String, file: File, onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }): DriveFile =
        withContext(Dispatchers.IO) {
            val total = file.length()
            val metadata = """{"name":"${name.replace("\"", "")}","parents":["$folderId"],"mimeType":"${BackupFormat.MIME_TYPE}"}"""
            val start = open("POST", "$baseUrl/upload/drive/v3/files?uploadType=resumable&fields=${encode(FILE_FIELDS)}")
            val sessionUrl = try {
                start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                start.setRequestProperty("X-Upload-Content-Type", BackupFormat.MIME_TYPE)
                start.setRequestProperty("X-Upload-Content-Length", total.toString())
                start.doOutput = true
                start.outputStream.use { it.write(metadata.toByteArray()) }
                check(start)
                start.getHeaderField("Location") ?: throw DriveException("Drive did not start the upload", retryable = true)
            } finally {
                start.disconnect()
            }

            val digest = MessageDigest.getInstance("MD5")
            val put = open("PUT", sessionUrl)
            val uploaded = try {
                put.setRequestProperty("Content-Type", BackupFormat.MIME_TYPE)
                put.doOutput = true
                put.setFixedLengthStreamingMode(total)
                put.outputStream.use { out ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var sent = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            sent += read
                            onProgress(sent, total)
                        }
                    }
                }
                check(put)
                json.decodeFromString(DriveFile.serializer(), put.inputStream.bufferedReader().use { it.readText() })
            } finally {
                put.disconnect()
            }

            val stored = json.decodeFromString(
                DriveFile.serializer(),
                request("GET", "$baseUrl/drive/v3/files/${uploaded.id}?fields=${encode(FILE_FIELDS)}"),
            )
            val localMd5 = digest.digest().joinToString("") { "%02x".format(it) }
            if (stored.sizeBytes != total || !stored.md5Checksum.equals(localMd5, ignoreCase = true)) {
                runCatching { delete(stored.id) }
                throw DriveException("The uploaded backup did not match the local file", retryable = true)
            }
            stored
        }

    /** Backups in the folder, newest first. */
    suspend fun list(folderId: String): List<DriveFile> {
        val query = "'$folderId' in parents and trashed = false"
        val url = "$baseUrl/drive/v3/files?q=${encode(query)}&orderBy=${encode("createdTime desc")}&pageSize=100&spaces=drive" +
            "&fields=${encode("files($FILE_FIELDS)")}"
        return json.decodeFromString(DriveFileList.serializer(), request("GET", url)).files
    }

    suspend fun download(fileId: String, into: File, onProgress: (received: Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        val connection = open("GET", "$baseUrl/drive/v3/files/$fileId?alt=media")
        try {
            check(connection)
            connection.inputStream.use { input ->
                into.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        received += read
                        onProgress(received)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun delete(fileId: String) {
        request("DELETE", "$baseUrl/drive/v3/files/$fileId")
    }

    private suspend fun request(method: String, url: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val connection = open(method, url)
        try {
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            check(connection)
            if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) "" else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun open(method: String, url: String): HttpURLConnection {
        val bearer = token()
        return try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Accept", "application/json")
            }
        } catch (e: IOException) {
            throw DriveException("Could not reach Google Drive", retryable = true)
        }
    }

    private fun check(connection: HttpURLConnection) {
        val code = try {
            connection.responseCode
        } catch (e: IOException) {
            throw DriveException("Could not reach Google Drive", retryable = true)
        }
        when {
            code in 200..299 -> return
            code == 401 -> throw DriveException("Google Drive access has expired", retryable = false, authExpired = true)
            code == 403 || code == 429 || code >= 500 -> throw DriveException("Google Drive is busy or unavailable ($code)", retryable = true)
            code == 404 -> throw DriveException("The backup was not found in Google Drive", retryable = false)
            else -> throw DriveException("Google Drive refused the request ($code)", retryable = false)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val FOLDER_NAME = "MyVault Backups"
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val FILE_FIELDS = "id,name,size,createdTime,md5Checksum"
    }
}
