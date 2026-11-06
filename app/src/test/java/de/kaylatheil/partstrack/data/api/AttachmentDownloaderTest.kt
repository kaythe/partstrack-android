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

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: AttachmentDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = AttachmentDownloader(HttpPartstrackApi.httpClient(), folder.root)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun attachment(id: Int = 7, name: String = "rc0603.pdf") = Attachment(
        id = id, kind = "datasheet", name = name, stored = true, url = server.url("/attachments/$id").toString(),
        sourceUrl = null, addedAt = "2026-11-06 10:24:09",
    )

    private fun answer(body: ByteArray, contentType: String?) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(body)).apply {
            if (contentType != null) addHeader("Content-Type", contentType)
        }.build())
    }

    @Test
    fun downloads() = runTest {
        val content = ByteArray(100_000) { (it % 251).toByte() }
        answer(content, "application/pdf")
        val downloaded = downloader.download(attachment())
        assertEquals(File(File(folder.root, "7"), "rc0603.pdf"), downloaded.file)
        assertTrue(downloaded.file.readBytes().contentEquals(content))
        assertEquals("application/pdf", downloaded.mediaType)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/attachments/7", request.url.encodedPath)
        assertEquals(HttpPartstrackApi.USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun replacesAnEarlierCopy() = runTest {
        answer("old".toByteArray(), "text/plain")
        downloader.download(attachment(name = "notes.txt"))
        answer("new".toByteArray(), "text/plain")
        assertEquals("new", downloader.download(attachment(name = "notes.txt")).file.readText())
    }

    @Test
    fun typeFromTheNameWhenTheServerDoesNotSay() = runTest {
        answer("%PDF".toByteArray(), "application/octet-stream")
        assertEquals("application/pdf", downloader.download(attachment()).mediaType)
        answer("x".toByteArray(), null)
        assertEquals("image/png", downloader.download(attachment(name = "photo.png")).mediaType)
        answer("x".toByteArray(), "application/octet-stream")
        assertEquals("application/octet-stream", downloader.download(attachment(name = "firmware.xyz")).mediaType)
    }

    @Test
    fun parametersOfTheTypeAreLeftOut() = runTest {
        answer("hello".toByteArray(), "text/plain; charset=utf-8")
        assertEquals("text/plain", downloader.download(attachment(name = "a.txt")).mediaType)
    }

    @Test
    fun namesCannotLeaveTheFolder() = runTest {
        answer("x".toByteArray(), "text/plain")
        val file = downloader.download(attachment(name = "../../evil.txt")).file
        assertEquals(File(File(folder.root, "7"), "evil.txt"), file)
        assertEquals("attachment", AttachmentDownloader.safeName(".."))
        assertEquals("attachment", AttachmentDownloader.safeName(""))
        assertEquals("b.pdf", AttachmentDownloader.safeName("a\\b.pdf"))
        assertEquals("ab.pdf", AttachmentDownloader.safeName("a\u0000b.pdf"))
        assertEquals("Datenblatt ä.pdf", AttachmentDownloader.safeName("Datenblatt ä.pdf"))
    }

    @Test
    fun errorAnswer() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("<html>not found</html>").build())
        val e = try {
            downloader.download(attachment())
            null
        } catch (e: ApiException) {
            e
        }
        assertEquals(404, e?.status)
        assertFalse(File(folder.root, "7/rc0603.pdf").exists())
    }

    @Test
    fun unreachable() = runTest {
        val gone = attachment()
        server.close()
        val e = try {
            downloader.download(gone)
            null
        } catch (e: NetworkException) {
            e
        }
        assertTrue(e != null)
    }
}
