/*
 * partstrack - a small inventory for electronic parts
 * Copyright (C) 2026 Kayla Theil (accounts@kaylatheil.de)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.kaylatheil.partstrack.data.api

import de.kaylatheil.partstrack.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import okio.Buffer
import okio.BufferedSink

/**
 * [PartstrackApi] over HTTP with OkHttp, for the server at [serverUrl] (the
 * web interface's address, e.g. `http://parts.lan:8080/`; it may have a path
 * when the server is behind a reverse proxy). [token] is read for every
 * request, so a changed token applies at once; it is sent only where the
 * server needs it (changes and the token check), and null leaves it out.
 */
class HttpPartstrackApi(
    serverUrl: HttpUrl,
    private val client: OkHttpClient,
    private val token: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PartstrackApi {

    private val base: HttpUrl = serverUrl.newBuilder().query(null).fragment(null).build().let {
        if (it.encodedPath.endsWith("/")) it else it.newBuilder().encodedPath(it.encodedPath + "/").build()
    }

    // Adding a link waits while the server downloads its file, which may take
    // longer than any other answer (the server gives up after 30 s without data).
    private val slowClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(SLOW_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES).build()
    }

    // -- reading --------------------------------------------------------------

    override suspend fun serverInfo(): ServerInfo = get(url("api"), ServerInfo.serializer())

    override suspend fun checkToken(): TokenInfo = get(url("api/token"), TokenInfo.serializer(), authorized = true)

    override suspend fun parts(
        query: String,
        tag: String?,
        location: String?,
        category: String?,
        lowOnly: Boolean,
    ): List<Part> {
        val url = url("api/parts").newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            if (!tag.isNullOrEmpty()) addQueryParameter("tag", tag)
            if (!location.isNullOrEmpty()) addQueryParameter("location", exact(location))
            if (!category.isNullOrEmpty()) addQueryParameter("category", category)
            if (lowOnly) addQueryParameter("low", "1")
        }.build()
        return get(url, ListSerializer(Part.serializer()))
    }

    override suspend fun part(id: Int): Part = get(url("api/parts/$id"), Part.serializer())

    override suspend fun locations(): List<Location> = get(url("api/locations"), ListSerializer(Location.serializer()))

    override suspend fun location(id: Int): Location = get(url("api/locations/$id"), Location.serializer())

    override suspend fun tags(): List<TagCount> = get(url("api/tags"), ListSerializer(TagCount.serializer()))

    override suspend fun categories(): List<String> = get(url("api/categories"), ListSerializer(String.serializer()))

    override suspend fun history(partId: Int?, location: String?, limit: Int?): List<StockChange> {
        val url = url("api/history").newBuilder().apply {
            if (partId != null) addQueryParameter("part", partId.toString())
            if (!location.isNullOrEmpty()) addQueryParameter("location", exact(location))
            if (limit != null) addQueryParameter("limit", limit.toString())
        }.build()
        return get(url, ListSerializer(StockChange.serializer()))
    }

    // -- changes --------------------------------------------------------------

    override suspend fun addPart(fields: PartFields, stock: List<StockAmount>): Part {
        require(!fields.name.isNullOrBlank()) { "a new part needs a name" }
        return send("POST", url("api/parts"), partBody(fields, stock), Part.serializer())
    }

    override suspend fun updatePart(id: Int, fields: PartFields): Part =
        send("PATCH", url("api/parts/$id"), partBody(fields, null), Part.serializer())

    override suspend fun deletePart(id: Int) = sendWithoutAnswer("DELETE", url("api/parts/$id"))

    override suspend fun setStock(partId: Int, location: String, quantity: Int, note: String): Part =
        send("POST", url("api/parts/$partId/stock"), buildJsonObject {
            put("location", location)
            put("quantity", quantity)
            putNote(note)
        }, Part.serializer())

    override suspend fun adjustStock(partId: Int, location: String, delta: Int, note: String): Part =
        send("POST", url("api/parts/$partId/stock"), buildJsonObject {
            put("location", location)
            put("delta", delta)
            putNote(note)
        }, Part.serializer())

    override suspend fun moveStock(partId: Int, from: String, to: String, quantity: Int?, note: String): Part =
        send("POST", url("api/parts/$partId/move"), buildJsonObject {
            put("from", from)
            put("to", to)
            if (quantity != null) put("quantity", quantity)
            putNote(note)
        }, Part.serializer())

    override suspend fun moveAllStock(partId: Int, to: String, note: String): Part =
        send("POST", url("api/parts/$partId/move"), buildJsonObject {
            put("to", to)
            putNote(note)
        }, Part.serializer())

    override suspend fun addLocation(path: String, description: String): Location =
        send("POST", url("api/locations"), buildJsonObject {
            put("path", path)
            if (description.isNotEmpty()) put("description", description)
        }, Location.serializer())

    override suspend fun updateLocation(id: Int, name: String?, description: String?, parent: Patch<String?>): Location =
        send("PATCH", url("api/locations/$id"), buildJsonObject {
            putIfSet("name", name)
            putIfSet("description", description)
            putPatch("parent", parent)
        }, Location.serializer())

    override suspend fun deleteLocation(id: Int) = sendWithoutAnswer("DELETE", url("api/locations/$id"))

    override suspend fun addLink(partId: Int, url: String, kind: AttachmentKind?, download: Boolean): Attachment =
        send("POST", url("api/parts/$partId/attachments"), buildJsonObject {
            put("url", url)
            if (kind != null) put("kind", kind.value)
            if (!download) put("download", false)
        }, Attachment.serializer(), if (download) slowClient else client)

    override suspend fun uploadAttachment(
        partId: Int,
        upload: Upload,
        kind: AttachmentKind?,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): Attachment {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            if (kind != null) addFormDataPart("kind", kind.value)
            addFormDataPart("file", upload.fileName, ProgressBody(upload, onProgress))
        }.build()
        val request = Request.Builder().url(url("api/parts/$partId/attachments")).post(body).authorized().build()
        return decode(call(request, slowClient), Attachment.serializer())
    }

    override suspend fun downloadAttachment(partId: Int, attachmentId: Int): Attachment {
        val request = Request.Builder().url(url("api/parts/$partId/attachments/$attachmentId/download"))
            .post(EMPTY).authorized().build()
        return decode(call(request, slowClient), Attachment.serializer())
    }

    override suspend fun removeAttachment(partId: Int, attachmentId: Int) =
        sendWithoutAnswer("DELETE", url("api/parts/$partId/attachments/$attachmentId"))

    // -- plumbing -------------------------------------------------------------

    private fun url(path: String): HttpUrl = base.resolve(path) ?: error("bad API path $path")

    /** Locations given to the server as an exact path, so they can't match another one by its end. */
    private fun exact(location: String): String = "/" + location.trimStart('/')

    private fun Request.Builder.authorized(): Request.Builder = apply {
        token()?.trim()?.takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") }
    }

    private suspend fun <T> get(url: HttpUrl, answer: DeserializationStrategy<T>, authorized: Boolean = false): T {
        val request = Request.Builder().url(url).apply { if (authorized) authorized() }.build()
        return decode(call(request), answer)
    }

    private suspend fun <T> send(
        method: String,
        url: HttpUrl,
        body: JsonObject,
        answer: DeserializationStrategy<T>,
        http: OkHttpClient = client,
    ): T {
        val json = PartstrackJson.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON)
        val request = Request.Builder().url(url).method(method, json).authorized().build()
        return decode(call(request, http), answer)
    }

    private suspend fun sendWithoutAnswer(method: String, url: HttpUrl) {
        call(Request.Builder().url(url).method(method, null).authorized().build())
    }

    /** Sends [request] and returns the answer's body; error answers become exceptions. */
    private suspend fun call(request: Request, http: OkHttpClient = client): String {
        val response = try {
            http.newCall(request).executeAsync()
        } catch (e: IOException) {
            throw NetworkException(e)
        }
        return withContext(ioDispatcher) {
            response.use {
                val body = try {
                    it.body.string()
                } catch (e: IOException) {
                    throw NetworkException(e)
                }
                if (!it.isSuccessful) throw ApiException(it.code, errorMessage(body))
                body
            }
        }
    }

    private fun <T> decode(body: String, answer: DeserializationStrategy<T>): T =
        try {
            PartstrackJson.decodeFromString(answer, body)
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw UnexpectedResponseException("the server's answer isn't what partstrack answers", e)
        }

    /** The `error` text of an error answer, if it has one. */
    private fun errorMessage(body: String): String? =
        try {
            (PartstrackJson.parseToJsonElement(body) as? JsonObject)
                ?.get("error")
                ?.let { it as? JsonPrimitive }
                ?.takeIf { it.isString }
                ?.content
        } catch (_: IllegalArgumentException) {
            null
        }

    /** Sends an [Upload], reporting the bytes sent so far. */
    private class ProgressBody(
        private val upload: Upload,
        private val onProgress: (sent: Long, total: Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = upload.mediaType?.toMediaTypeOrNull()

        override fun contentLength() = upload.length

        override fun writeTo(sink: BufferedSink) {
            upload.open().use { source ->
                val buffer = Buffer()
                var sent = 0L
                while (true) {
                    val read = source.read(buffer, CHUNK)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    sent += read
                    onProgress(sent, upload.length)
                }
            }
        }
    }

    companion object {
        /** Sent with every request, so the server's log shows what asked. */
        const val USER_AGENT = "partstrack-android/" + BuildConfig.VERSION_NAME

        private const val SLOW_READ_TIMEOUT_MINUTES = 10L
        private const val CHUNK = 64 * 1024L
        private val JSON = "application/json".toMediaType()
        private val EMPTY = ByteArray(0).toRequestBody()

        /**
         * An OkHttp client for the app: timeouts that suit a server on the local
         * network or behind a VPN, and the app's user agent. Share it (it holds
         * the connection pool), e.g. with the image loader.
         */
        fun httpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }
}

/** Ignores fields the app doesn't know, so a newer server's answers still work. */
internal val PartstrackJson = Json { ignoreUnknownKeys = true }
