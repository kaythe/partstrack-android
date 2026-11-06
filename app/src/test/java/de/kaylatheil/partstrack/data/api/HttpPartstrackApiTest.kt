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

import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The client against MockWebServer, answering with what a real partstrack-web
 * answered (src/test/resources/api, written by scripts/capture_fixtures.py).
 */
class HttpPartstrackApiTest {
    private lateinit var server: MockWebServer
    private var token: String? = TOKEN
    private lateinit var api: HttpPartstrackApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = newApi(HttpPartstrackApi.httpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun newApi(client: OkHttpClient, path: String = "/") = HttpPartstrackApi(server.url(path), client, { token })

    private fun answer(fixture: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(fixture(fixture)).addHeader("Content-Type", "application/json").build())
    }

    private fun answer(code: Int, body: String = "", contentType: String = "application/json") {
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", contentType).build())
    }

    private fun taken(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS) ?: error("no request")

    private fun RecordedRequest.bodyText(): String = body?.utf8() ?: ""

    private fun assertJson(expected: String, actual: String) {
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))
    }

    // -- reading --------------------------------------------------------------

    @Test
    fun serverInfo() = runTest {
        answer("index")
        val info = api.serverInfo()
        assertEquals("1.3.0", info.version)
        assertTrue("/api/token" in info.endpoints)
        val request = taken()
        assertEquals("GET", request.method)
        assertEquals("/api", request.url.encodedPath)
        assertEquals(HttpPartstrackApi.USER_AGENT, request.headers["User-Agent"])
        assertTrue(HttpPartstrackApi.USER_AGENT.startsWith("partstrack-android/"))
    }

    @Test
    fun readsSendNoToken() = runTest {
        answer("parts")
        api.parts()
        assertNull(taken().headers["Authorization"])
    }

    @Test
    fun checkTokenSendsTheToken() = runTest {
        answer("token")
        val info = api.checkToken()
        assertEquals(TokenInfo(id = 1, name = "phone", createdAt = info.createdAt, readOnly = false), info)
        val request = taken()
        assertEquals("/api/token", request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
    }

    @Test
    fun checkTokenOnAReadOnlyCopy() = runTest {
        answer("token_read_only")
        assertTrue(api.checkToken().readOnly)
    }

    @Test
    fun partsWithoutFilters() = runTest {
        answer("parts")
        val parts = api.parts()
        assertEquals(listOf("10k resistor", "NE555"), parts.map { it.name })
        val request = taken()
        assertEquals("/api/parts", request.url.encodedPath)
        assertEquals(0, request.url.querySize)
    }

    @Test
    fun partsWithFilters() = runTest {
        answer("parts_search")
        val parts = api.parts(query = "resistance>=1k", tag = "smd", location = "Cabinet/Drawer 1", category = "Resistor", lowOnly = true)
        assertEquals(listOf("10k resistor"), parts.map { it.name })
        val url = taken().url
        assertEquals("resistance>=1k", url.queryParameter("q"))
        assertEquals("smd", url.queryParameter("tag"))
        assertEquals("/Cabinet/Drawer 1", url.queryParameter("location")) // exact, never matched by its end
        assertEquals("Resistor", url.queryParameter("category"))
        assertEquals("1", url.queryParameter("low"))
    }

    @Test
    fun lowParts() = runTest {
        answer("parts_low")
        val parts = api.parts(lowOnly = true)
        assertEquals(listOf("NE555"), parts.map { it.name })
        assertTrue(parts.single().low)
    }

    @Test
    fun part() = runTest {
        answer("part")
        val part = api.part(1)
        assertEquals("/api/parts/1", taken().url.encodedPath)
        assertEquals(1, part.id)
        assertEquals("10k resistor", part.name)
        assertEquals("Thick film resistor, 1%", part.description)
        assertEquals("Yageo", part.manufacturer)
        assertEquals("RC0603FR-0710KL", part.mpn)
        assertEquals("Resistor", part.category)
        assertEquals("0603", part.packageName)
        assertEquals("", part.notes)
        assertEquals(50, part.minQuantity)
        assertEquals(117, part.totalQuantity)
        assertFalse(part.low)
        assertEquals(
            listOf(StockEntry("Cabinet/Drawer 1", 97), StockEntry("Cabinet/Drawer 2", 15), StockEntry("Shelf", 5)),
            part.stock,
        )
        assertEquals(listOf("resistor", "smd"), part.tags)
        assertEquals(mapOf("resistance" to "10k", "tolerance" to "1%"), part.attributes)
        assertTrue(part.webUrl.endsWith("/parts/1"))
        assertTrue(parseServerTime(part.createdAt) != null)

        val (link, photo) = part.attachments
        assertEquals("datasheet", link.kind)
        assertFalse(link.stored)
        assertEquals("https://www.example.com/rc0603.pdf", link.url)
        assertFalse(link.isImage)
        assertTrue(photo.isImage)
        assertTrue(photo.stored)
        assertEquals("photo.png", photo.name)
        assertTrue(photo.url.endsWith("/attachments/${photo.id}"))
        assertNull(photo.downloadError)
    }

    @Test
    fun locations() = runTest {
        answer("locations")
        val locations = api.locations()
        assertEquals("/api/locations", taken().url.encodedPath)
        val drawer = locations.single { it.path == "Cabinet/Drawer 1" }
        assertEquals("Drawer 1", drawer.name)
        assertEquals("Cabinet", drawer.parent)
        assertEquals("Resistors", drawer.description)
        assertEquals(1, drawer.partCount)
        assertEquals(97, drawer.totalQuantity)
        assertNull(locations.single { it.path == "Cabinet" }.parent)
    }

    @Test
    fun location() = runTest {
        answer("location")
        assertEquals("Cabinet", api.location(1).path)
        assertEquals("/api/locations/1", taken().url.encodedPath)
    }

    @Test
    fun tagsAndCategories() = runTest {
        answer("tags")
        answer("categories")
        assertEquals(TagCount("smd", 1), api.tags().single { it.name == "smd" })
        assertEquals(listOf("IC", "Resistor"), api.categories())
        assertEquals("/api/tags", taken().url.encodedPath)
        assertEquals("/api/categories", taken().url.encodedPath)
    }

    @Test
    fun history() = runTest {
        answer("history")
        val history = api.history(partId = 1, location = "Shelf", limit = 10)
        val url = taken().url
        assertEquals("/api/history", url.encodedPath)
        assertEquals("1", url.queryParameter("part"))
        assertEquals("/Shelf", url.queryParameter("location"))
        assertEquals("10", url.queryParameter("limit"))
        val newest = history.first()
        assertEquals("move", newest.action)
        assertEquals(5, newest.delta)
        assertEquals("Shelf", newest.location)
        assertEquals("sorting", newest.note)
        assertEquals("10k resistor", newest.partName)
        // A new part's stock is set, noted as such.
        assertEquals("set", history.last().action)
        assertEquals("new part", history.last().note)
    }

    @Test
    fun historyWithoutFilters() = runTest {
        answer("history")
        api.history()
        assertEquals(0, taken().url.querySize)
    }

    @Test
    fun serverBehindAPath() = runTest {
        // A reverse proxy may serve partstrack under a path; with or without the trailing slash.
        for (path in listOf("/partstrack/", "/partstrack")) {
            answer("index")
            newApi(HttpPartstrackApi.httpClient(), path).serverInfo()
            assertEquals("/partstrack/api", taken().url.encodedPath)
        }
    }

    // -- changes --------------------------------------------------------------

    @Test
    fun addPart() = runTest {
        answer("add_part", 201)
        val part = api.addPart(
            PartFields(
                name = "10k resistor",
                packageName = "0603",
                tags = listOf("smd", "resistor"),
                attributes = mapOf("resistance" to "10k"),
                minQuantity = Patch.Set(50),
            ),
            stock = listOf(StockAmount("Cabinet/Drawer 1", 100)),
        )
        assertEquals("10k resistor", part.name)
        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/api/parts", request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        assertJson(
            """{"name": "10k resistor", "package": "0603", "tags": ["smd", "resistor"],
                "attributes": {"resistance": "10k"}, "min_quantity": 50,
                "stock": [{"location": "Cabinet/Drawer 1", "quantity": 100}]}""",
            request.bodyText(),
        )
    }

    @Test
    fun addPartWithOnlyAName() = runTest {
        answer("add_part_minimal", 201)
        val part = api.addPart(PartFields(name = "Mystery part"))
        assertTrue(part.stock.isEmpty())
        assertNull(part.minQuantity)
        assertJson("""{"name": "Mystery part"}""", taken().bodyText())
    }

    @Test
    fun addPartNeedsAName() = runTest {
        assertFails<IllegalArgumentException> { api.addPart(PartFields(description = "no name")) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun updatePartSendsOnlyWhatChanges() = runTest {
        answer("update_part")
        val part = api.updatePart(3, PartFields(notes = "Found in a drawer", tags = listOf("unsorted"), minQuantity = Patch.Set(null)))
        assertEquals("Found in a drawer", part.notes)
        val request = taken()
        assertEquals("PATCH", request.method)
        assertEquals("/api/parts/3", request.url.encodedPath)
        assertJson("""{"notes": "Found in a drawer", "tags": ["unsorted"], "min_quantity": null}""", request.bodyText())
    }

    @Test
    fun updatePartWithEmptyValues() = runTest {
        // Empty text clears a field and empty lists remove all tags; both differ from leaving them out.
        answer("update_part")
        api.updatePart(3, PartFields(description = "", tags = emptyList(), attributes = emptyMap()))
        assertJson("""{"description": "", "tags": [], "attributes": {}}""", taken().bodyText())
    }

    @Test
    fun deletePart() = runTest {
        answer(204)
        api.deletePart(3)
        val request = taken()
        assertEquals("DELETE", request.method)
        assertEquals("/api/parts/3", request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
    }

    @Test
    fun adjustStock() = runTest {
        answer("stock_delta")
        val part = api.adjustStock(1, "Cabinet/Drawer 1", -3, note = "for the amplifier")
        assertEquals(97, part.stock.single { it.location == "Cabinet/Drawer 1" }.quantity)
        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/api/parts/1/stock", request.url.encodedPath)
        assertJson("""{"location": "Cabinet/Drawer 1", "delta": -3, "note": "for the amplifier"}""", request.bodyText())
    }

    @Test
    fun setStockWithoutNote() = runTest {
        answer("stock_set")
        api.setStock(1, "Cabinet/Drawer 2", 20)
        assertJson("""{"location": "Cabinet/Drawer 2", "quantity": 20}""", taken().bodyText())
    }

    @Test
    fun moveStock() = runTest {
        answer("move")
        val part = api.moveStock(1, from = "Cabinet/Drawer 2", to = "Shelf", quantity = 5, note = "sorting")
        assertEquals(5, part.stock.single { it.location == "Shelf" }.quantity)
        val request = taken()
        assertEquals("/api/parts/1/move", request.url.encodedPath)
        assertJson("""{"from": "Cabinet/Drawer 2", "to": "Shelf", "quantity": 5, "note": "sorting"}""", request.bodyText())
    }

    @Test
    fun moveAllFromOneLocation() = runTest {
        answer("move")
        api.moveStock(1, from = "Cabinet/Drawer 2", to = "Shelf")
        assertJson("""{"from": "Cabinet/Drawer 2", "to": "Shelf"}""", taken().bodyText())
    }

    @Test
    fun moveAllStock() = runTest {
        answer("move")
        api.moveAllStock(1, to = "Shelf")
        assertJson("""{"to": "Shelf"}""", taken().bodyText())
    }

    @Test
    fun addLocation() = runTest {
        answer("add_location", 201)
        val location = api.addLocation("Cabinet/Drawer 1", "Resistors")
        assertEquals("Cabinet/Drawer 1", location.path)
        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/api/locations", request.url.encodedPath)
        assertJson("""{"path": "Cabinet/Drawer 1", "description": "Resistors"}""", request.bodyText())
    }

    @Test
    fun updateLocation() = runTest {
        answer("update_location")
        val location = api.updateLocation(5, name = "Small box", description = "On the desk", parent = Patch.Set("Cabinet"))
        assertEquals("Cabinet/Small box", location.path)
        val request = taken()
        assertEquals("PATCH", request.method)
        assertEquals("/api/locations/5", request.url.encodedPath)
        assertJson("""{"name": "Small box", "description": "On the desk", "parent": "Cabinet"}""", request.bodyText())
    }

    @Test
    fun moveLocationToTheTopLevel() = runTest {
        answer("update_location")
        api.updateLocation(5, parent = Patch.Set(null))
        assertJson("""{"parent": null}""", taken().bodyText())
    }

    @Test
    fun deleteLocation() = runTest {
        answer(204)
        api.deleteLocation(5)
        val request = taken()
        assertEquals("DELETE", request.method)
        assertEquals("/api/locations/5", request.url.encodedPath)
    }

    // -- attachments ----------------------------------------------------------

    @Test
    fun addLink() = runTest {
        answer("attachment_link", 201)
        val attachment = api.addLink(1, "https://www.example.com/rc0603.pdf", AttachmentKind.DATASHEET, download = false)
        assertEquals("rc0603.pdf", attachment.name)
        val request = taken()
        assertEquals("/api/parts/1/attachments", request.url.encodedPath)
        assertJson("""{"url": "https://www.example.com/rc0603.pdf", "kind": "datasheet", "download": false}""", request.bodyText())
    }

    @Test
    fun addLinkThatCannotBeDownloaded() = runTest {
        answer("attachment_link_download_error", 201)
        val attachment = api.addLink(1, "http://127.0.0.1:8765/notes.pdf")
        assertJson("""{"url": "http://127.0.0.1:8765/notes.pdf"}""", taken().bodyText())
        assertFalse(attachment.stored)
        assertEquals("refusing to download from 127.0.0.1, a local or reserved address", attachment.downloadError)
    }

    @Test
    fun uploadAttachment() = runTest {
        answer("attachment_upload", 201)
        val content = ByteArray(200_000) { (it % 251).toByte() }
        val progress = mutableListOf<Pair<Long, Long>>()
        val attachment = api.uploadAttachment(
            1,
            Upload("photo.png", "image/png", content.size.toLong()) { Buffer().write(content) },
            AttachmentKind.IMAGE,
        ) { sent, total -> synchronized(progress) { progress += sent to total } }
        assertTrue(attachment.isImage)

        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/api/parts/1/attachments", request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        val contentType = request.headers["Content-Type"]!!
        assertTrue(contentType, contentType.startsWith("multipart/form-data; boundary="))
        val body = request.body!!
        val text = body.string(Charsets.ISO_8859_1) // the file's bytes survive as Latin-1 characters
        // Each part: its headers, an empty line, then its content.
        assertTrue(text, Regex("Content-Disposition: form-data; name=\"kind\"\r\n(.+\r\n)*\r\nimage\r\n").containsMatchIn(text))
        val file = Regex("Content-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n((.+\r\n)*)\r\n").find(text)
        assertTrue(text.take(500), file != null && "Content-Type: image/png\r\n" in file.groupValues[1])
        assertTrue(body.indexOf(okio.ByteString.of(*content)) >= 0)

        assertEquals(content.size.toLong() to content.size.toLong(), progress.last())
        assertTrue(progress.all { it.second == content.size.toLong() })
        assertEquals(progress.map { it.first }.sorted(), progress.map { it.first })
    }

    @Test
    fun uploadWithoutKind() = runTest {
        answer("attachment_upload", 201)
        api.uploadAttachment(1, Upload("notes.txt", null, 2) { Buffer().writeUtf8("hi") })
        val text = taken().body!!.utf8()
        assertFalse(text.contains("name=\"kind\""))
        assertTrue(text.contains("filename=\"notes.txt\""))
    }

    @Test
    fun downloadAttachment() = runTest {
        answer("error_download", 400)
        val e = assertApiError(400) { api.downloadAttachment(1, 3) }
        assertEquals("refusing to download from 127.0.0.1, a local or reserved address", e.serverMessage)
        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/api/parts/1/attachments/3/download", request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
    }

    @Test
    fun removeAttachment() = runTest {
        answer(204)
        api.removeAttachment(1, 3)
        val request = taken()
        assertEquals("DELETE", request.method)
        assertEquals("/api/parts/1/attachments/3", request.url.encodedPath)
    }

    // -- errors ---------------------------------------------------------------

    private suspend fun assertApiError(status: Int, block: suspend () -> Unit): ApiException {
        try {
            block()
        } catch (e: ApiException) {
            assertEquals(status, e.status)
            return e
        }
        throw AssertionError("expected ApiException $status")
    }

    private suspend inline fun <reified E : Throwable> assertFails(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw AssertionError("expected ${E::class.simpleName}, got $e", e)
        }
        throw AssertionError("expected ${E::class.simpleName}")
    }

    @Test
    fun badRequest() = runTest {
        answer("error_unknown_field", 400)
        val e = assertApiError(400) { api.addPart(PartFields(name = "x")) }
        assertEquals("unknown field(s): colour", e.serverMessage)
        assertEquals("unknown field(s): colour", e.message)
    }

    @Test
    fun badSearch() = runTest {
        answer("error_bad_filter", 400)
        val e = assertApiError(400) { api.parts("resistance>abc") }
        assertEquals("'resistance>abc': 'abc' is not a number", e.serverMessage)
    }

    @Test
    fun locationNotEmpty() = runTest {
        answer("error_location_not_empty", 400)
        val e = assertApiError(400) { api.deleteLocation(1) }
        assertEquals("location 'Cabinet' still contains 2 sublocation(s)", e.serverMessage)
    }

    @Test
    fun withoutTokenNoHeaderIsSent() = runTest {
        token = null
        answer("error_no_token", 401)
        val e = assertApiError(401) { api.addLocation("X") }
        assertTrue(e.isUnauthorized)
        assertTrue(e.serverMessage!!.startsWith("changes need an API token"))
        assertNull(taken().headers["Authorization"])
    }

    @Test
    fun blankTokenIsNotSent() = runTest {
        token = "  "
        answer("error_no_token", 401)
        assertApiError(401) { api.addLocation("X") }
        assertNull(taken().headers["Authorization"])
    }

    @Test
    fun tokenIsReadForEveryRequest() = runTest {
        answer("token")
        answer("token")
        api.checkToken()
        token = " pt_other \n"
        api.checkToken()
        assertEquals("Bearer $TOKEN", taken().headers["Authorization"])
        assertEquals("Bearer pt_other", taken().headers["Authorization"])
    }

    @Test
    fun badToken() = runTest {
        answer("error_bad_token", 401)
        val e = assertApiError(401) { api.checkToken() }
        assertTrue(e.isUnauthorized)
        assertEquals("unknown or revoked API token", e.serverMessage)
    }

    @Test
    fun notFound() = runTest {
        answer("error_part_not_found", 404)
        val e = assertApiError(404) { api.part(9999) }
        assertTrue(e.isNotFound)
        assertEquals("no part with id 9999", e.serverMessage)
    }

    @Test
    fun serverWithoutTokenCheck() = runTest {
        // partstrack 1.3.0 has the API, but not /api/token.
        answer("error_no_endpoint", 404)
        val e = assertApiError(404) { api.checkToken() }
        assertEquals("no such API endpoint; see /api", e.serverMessage)
    }

    @Test
    fun methodNotAllowed() = runTest {
        answer("error_method", 405)
        assertApiError(405) { api.deletePart(1) }
    }

    @Test
    fun readOnlyCopy() = runTest {
        answer("error_read_only", 409)
        val e = assertApiError(409) { api.adjustStock(1, "Shelf", 1) }
        assertTrue(e.isReadOnly)
        assertEquals("this server shows a read-only copy; changes can't be saved", e.serverMessage)
    }

    @Test
    fun tooLarge() = runTest {
        answer(413, """{"error": "the request is larger than 100 MB"}""")
        val e = assertApiError(413) { api.uploadAttachment(1, Upload("big.bin", null, 1) { Buffer().writeByte(0) }) }
        assertEquals("the request is larger than 100 MB", e.serverMessage)
    }

    @Test
    fun serverError() = runTest {
        answer(500, """{"error": "the server could not complete the request (see its log)"}""")
        val e = assertApiError(500) { api.parts() }
        assertEquals("the server could not complete the request (see its log)", e.serverMessage)
    }

    @Test
    fun errorWithoutJson() = runTest {
        // e.g. a reverse proxy whose server is down
        answer(502, "<html><body>Bad Gateway</body></html>", "text/html")
        val e = assertApiError(502) { api.parts() }
        assertNull(e.serverMessage)
        assertEquals("the server answered with HTTP 502", e.message)
    }

    @Test
    fun errorWithOtherJson() = runTest {
        answer(400, """{"detail": "something else"}""")
        assertNull(assertApiError(400) { api.parts() }.serverMessage)
        answer(400, """{"error": 42}""")
        assertNull(assertApiError(400) { api.parts() }.serverMessage)
    }

    @Test
    fun notPartstrack() = runTest {
        // Some other web server at the address.
        answer(200, "<html><body>Welcome</body></html>", "text/html")
        assertFails<UnexpectedResponseException> { api.serverInfo() }
        answer(200, """{"name": "something else"}""")
        assertFails<UnexpectedResponseException> { api.serverInfo() }
        answer(200, """[1, 2, 3]""")
        assertFails<UnexpectedResponseException> { api.parts() }
    }

    @Test
    fun unknownFieldsAreIgnored() = runTest {
        // A newer server may add fields.
        val json = Json.parseToJsonElement(fixture("token")).let { it as kotlinx.serialization.json.JsonObject }
        val newer: JsonElement = kotlinx.serialization.json.JsonObject(json + ("scopes" to Json.parseToJsonElement("[\"all\"]")))
        answer(200, newer.toString())
        assertEquals("phone", api.checkToken().name)
    }

    @Test
    fun unreachable() = runTest {
        val url = server.url("/")
        server.close()
        val e = assertFails<NetworkException> { HttpPartstrackApi(url, HttpPartstrackApi.httpClient(), { null }).parts() }
        assertTrue(e.message!!.startsWith("can't reach the server"))
    }

    @Test
    fun timeout() = runTest {
        server.enqueue(MockResponse.Builder().body("[]").headersDelay(2, TimeUnit.SECONDS).build())
        val client = HttpPartstrackApi.httpClient().newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        assertFails<NetworkException> { newApi(client).parts() }
    }

    @Test
    fun cancellingCancelsTheRequest() = runTest {
        server.enqueue(MockResponse.Builder().body("[]").headersDelay(10, TimeUnit.SECONDS).build())
        // On another thread: taken() blocks the test's own.
        val call = backgroundScope.async(Dispatchers.IO) { api.parts() }
        taken() // the request has arrived
        call.cancel()
        val started = System.nanoTime()
        try {
            call.await()
            throw AssertionError("not cancelled")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5))
    }

    @Test
    fun serverTimes() {
        assertEquals(Instant.parse("2026-11-06T10:24:09Z"), parseServerTime("2026-11-06 10:24:09"))
        assertNull(parseServerTime("yesterday"))
        assertNull(parseServerTime(""))
    }

    companion object {
        private const val TOKEN = "pt_test-token"

        fun fixture(name: String): String =
            HttpPartstrackApiTest::class.java.getResource("/api/$name.json")?.readText()
                ?: error("no fixture $name")
    }
}
