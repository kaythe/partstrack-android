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

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The client against a real partstrack-web, so the server accepts what the
 * client sends. Skipped unless PARTSTRACK_TEST_SERVER (its address) and
 * PARTSTRACK_TEST_TOKEN are set. It adds and deletes parts and locations, so
 * only ever point it at a scratch database:
 *
 *     partstrack-web --db /tmp/scratch/parts.db --port 8765
 *     PARTSTRACK_DB=/tmp/scratch/parts.db partstrack token add test
 *     PARTSTRACK_TEST_SERVER=http://127.0.0.1:8765/ PARTSTRACK_TEST_TOKEN=pt_... ./gradlew testDebugUnitTest
 */
class LiveServerTest {
    private lateinit var server: String
    private lateinit var api: HttpPartstrackApi
    private val client = HttpPartstrackApi.httpClient()

    @Before
    fun setUp() {
        val token = System.getenv("PARTSTRACK_TEST_TOKEN")
        server = System.getenv("PARTSTRACK_TEST_SERVER") ?: ""
        assumeTrue("PARTSTRACK_TEST_SERVER and PARTSTRACK_TEST_TOKEN not set", server.isNotEmpty() && !token.isNullOrEmpty())
        api = HttpPartstrackApi(server.toHttpUrl(), client, { token })
    }

    @Test
    fun everyEndpoint() = runTest {
        assertNotNull(api.serverInfo().version)
        assertFalse(api.checkToken().readOnly)

        // A unique name, so the test can run again on the same database.
        val unique = "live-test-${System.nanoTime()}"
        val top = api.addLocation("$unique/Drawer 1", "made by LiveServerTest")
        assertEquals("$unique/Drawer 1", top.path)

        var part = api.addPart(
            PartFields(
                name = "$unique résistance 10 kΩ",
                description = "Ünïcode \"quoted\"",
                packageName = "0603",
                tags = listOf("live-test", "smd"),
                attributes = mapOf("resistance" to "10k"),
                minQuantity = Patch.Set(50),
            ),
            stock = listOf(StockAmount("$unique/Drawer 1", 100)),
        )
        assertEquals("$unique résistance 10 kΩ", part.name)
        assertEquals(100, part.totalQuantity)
        assertEquals(50, part.minQuantity)
        assertEquals(listOf(part.id), api.parts("resistance>=1k", location = unique).map { it.id })
        assertEquals(part, api.part(part.id))

        part = api.updatePart(part.id, PartFields(notes = "changed", minQuantity = Patch.Set(null), tags = listOf("live-test")))
        assertEquals("changed", part.notes)
        assertNull(part.minQuantity)
        assertEquals(listOf("live-test"), part.tags)
        assertEquals("0603", part.packageName) // left as it was

        part = api.adjustStock(part.id, "$unique/Drawer 1", -3, "taken")
        part = api.setStock(part.id, "$unique/Drawer 2", 20)
        part = api.moveStock(part.id, "$unique/Drawer 2", "$unique/Drawer 1", 5, "sorting")
        assertEquals(listOf(StockEntry("$unique/Drawer 1", 102), StockEntry("$unique/Drawer 2", 15)), part.stock)
        part = api.moveAllStock(part.id, "$unique/Drawer 1")
        assertEquals(listOf(StockEntry("$unique/Drawer 1", 117)), part.stock)
        val history = api.history(partId = part.id, limit = 0)
        // Each move is two entries: taken from one location, put at the other.
        assertEquals(listOf("move", "move", "move", "move", "set", "adjust", "set"), history.map { it.action })
        assertTrue(api.history(location = "$unique/Drawer 2").isNotEmpty())

        // An upload with a name that isn't ASCII, and its content read back.
        val content = ByteArray(300_000) { (it % 253).toByte() }
        var progress = 0L
        val photo = api.uploadAttachment(
            part.id,
            Upload("Foto Bauteil ä.png", "image/png", content.size.toLong()) { Buffer().write(content) },
            AttachmentKind.IMAGE,
        ) { sent, _ -> progress = sent }
        assertEquals(content.size.toLong(), progress)
        assertTrue(photo.stored)
        assertEquals("Foto Bauteil ä.png", photo.name)
        val stored = client.newCall(Request.Builder().url(photo.url).build()).execute().use { it.body.bytes() }
        assertTrue(stored.contentEquals(content))

        val link = api.addLink(part.id, "https://www.example.com/datasheet.pdf", AttachmentKind.DATASHEET, download = false)
        assertFalse(link.stored)
        api.removeAttachment(part.id, link.id)
        assertEquals(listOf(photo.id), api.part(part.id).attachments.map { it.id })

        assertTrue(api.tags().any { it.name == "live-test" })
        assertTrue(api.locations().any { it.path == "$unique/Drawer 2" })

        val drawer2 = api.locations().single { it.path == "$unique/Drawer 2" }
        val moved = api.updateLocation(drawer2.id, name = "Box", description = "moved", parent = Patch.Set(null))
        assertEquals("Box", moved.path.substringAfterLast('/'))
        assertNull(moved.parent)
        assertEquals(moved, api.location(drawer2.id))

        // Clean up: the part first, so its locations are empty.
        api.deletePart(part.id)
        api.deleteLocation(moved.id)
        val all = api.locations()
        for (location in all.filter { it.path.startsWith(unique) }.sortedByDescending { it.path.length }) {
            api.deleteLocation(location.id)
        }
        assertTrue(api.locations().none { it.path.startsWith(unique) })
    }

    @Test
    fun errors() = runTest {
        val e = try {
            api.part(Int.MAX_VALUE)
            null
        } catch (e: ApiException) {
            e
        }
        assertEquals(404, e?.status)
        val unauthorized = try {
            HttpPartstrackApi(server.toHttpUrl(), client, { "pt_wrong" }).checkToken()
            null
        } catch (e: ApiException) {
            e
        }
        assertTrue(unauthorized!!.isUnauthorized)
    }
}
