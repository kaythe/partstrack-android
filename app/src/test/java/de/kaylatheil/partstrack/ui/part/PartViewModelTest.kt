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

package de.kaylatheil.partstrack.ui.part

import de.kaylatheil.partstrack.MainDispatcherRule
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.AttachmentDownloader
import de.kaylatheil.partstrack.data.api.FakePartstrackApi
import de.kaylatheil.partstrack.data.api.HttpPartstrackApi
import de.kaylatheil.partstrack.data.api.HttpPartstrackApiTest
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartstrackJson
import de.kaylatheil.partstrack.data.api.StockChange
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import de.kaylatheil.partstrack.ui.common.LoadError
import java.net.ConnectException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PartViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    // A stored attachment downloads from here.
    private lateinit var files: MockWebServer

    private val settings = MutableStateFlow(Settings("http://parts.lan:8080/".toHttpUrl(), null, ThemeChoice.SYSTEM))
    private val part: Part = PartstrackJson.decodeFromString(Part.serializer(), HttpPartstrackApiTest.fixture("part"))
    private val history: List<StockChange> =
        PartstrackJson.decodeFromString(ListSerializer(StockChange.serializer()), HttpPartstrackApiTest.fixture("history"))

    private var partAnswer: suspend () -> Part = { part }
    private var historyAnswer: suspend () -> List<StockChange> = { history }
    private val historyCalls = mutableListOf<Pair<Int?, Int?>>()
    private var partCalls = 0

    private val api = object : FakePartstrackApi() {
        override suspend fun part(id: Int): Part {
            assertEquals(1, id)
            partCalls++
            return partAnswer()
        }

        override suspend fun history(partId: Int?, location: String?, limit: Int?): List<StockChange> {
            historyCalls += partId to limit
            return historyAnswer()
        }
    }

    @Before
    fun setUp() {
        files = MockWebServer()
        files.start()
    }

    @After
    fun tearDown() {
        files.close()
    }

    private fun newViewModel() = PartViewModel(
        1, settings, { _, _ -> api }, AttachmentDownloader(HttpPartstrackApi.httpClient(), folder.root),
    )

    @Test
    fun loadsThePartAndItsHistory() = runTest {
        val state = newViewModel().state.value
        assertFalse(state.loading)
        assertEquals(part, state.part)
        assertEquals(history, state.history)
        assertEquals(listOf(1 to PartViewModel.HISTORY_ENTRIES), historyCalls)
        assertNull(state.error)
    }

    @Test
    fun partNotFound() = runTest {
        partAnswer = { throw ApiException(404, "no part with id 1") }
        val state = newViewModel().state.value
        assertNull(state.part)
        assertEquals(LoadError.Server(404, "no part with id 1"), state.error)
    }

    @Test
    fun withoutHistoryThePartStillShows() = runTest {
        historyAnswer = { throw NetworkException(ConnectException("gone")) }
        val state = newViewModel().state.value
        assertEquals(part, state.part)
        assertNull(state.history)
        assertNull(state.error)
    }

    @Test
    fun retryAfterAnError() = runTest {
        partAnswer = { throw NetworkException(ConnectException("gone")) }
        val viewModel = newViewModel()
        assertEquals(LoadError.Unreachable("gone"), viewModel.state.value.error)
        partAnswer = { part }
        viewModel.retry()
        assertEquals(part, viewModel.state.value.part)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun resumeReloadsQuietly() = runTest {
        val viewModel = newViewModel()
        val gate = CompletableDeferred<Unit>()
        val changed = part.copy(totalQuantity = 1)
        partAnswer = { gate.await(); changed }
        viewModel.onResume()
        assertFalse(viewModel.state.value.loading)
        assertEquals(part, viewModel.state.value.part) // the old one stays until the new one is there
        gate.complete(Unit)
        runCurrent()
        assertEquals(changed, viewModel.state.value.part)
        assertEquals(2, partCalls)
    }

    @Test
    fun aFailedReloadKeepsThePart() = runTest {
        val viewModel = newViewModel()
        partAnswer = { throw NetworkException(ConnectException("gone")) }
        historyAnswer = { throw NetworkException(ConnectException("gone")) }
        viewModel.retry()
        val state = viewModel.state.value
        assertEquals(part, state.part)
        assertEquals(history, state.history)
        assertEquals(LoadError.Unreachable("gone"), state.error) // shown as a message
        viewModel.onErrorShown()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun linksOpenInTheBrowser() = runTest {
        val viewModel = newViewModel()
        val link = part.attachments.single { !it.stored }
        viewModel.open(link)
        assertEquals(OpenRequest.Link("https://www.example.com/rc0603.pdf"), viewModel.state.value.open)
        viewModel.onOpened()
        assertNull(viewModel.state.value.open)
    }

    @Test
    fun storedFilesAreDownloadedFirst() = runTest {
        files.enqueue(MockResponse.Builder().body("%PDF-1.7").addHeader("Content-Type", "application/pdf").build())
        val viewModel = newViewModel()
        val stored = Attachment(3, "datasheet", "ne555.pdf", true, files.url("/attachments/3").toString(), null, "")
        viewModel.open(stored)
        val open = viewModel.state.first { it.open != null }.open as OpenRequest.File
        assertEquals("%PDF-1.7", open.file.file.readText())
        assertEquals("application/pdf", open.file.mediaType)
        assertEquals(stored.url, open.fallbackUrl)
        assertNull(viewModel.state.value.downloadingId)
        assertEquals("/attachments/3", files.takeRequest(5, TimeUnit.SECONDS)!!.url.encodedPath)
    }

    @Test
    fun aFailedDownload() = runTest {
        files.enqueue(MockResponse.Builder().code(404).build())
        val viewModel = newViewModel()
        val stored = Attachment(3, "datasheet", "ne555.pdf", true, files.url("/attachments/3").toString(), null, "")
        viewModel.open(stored)
        assertEquals(3, viewModel.state.value.downloadingId)
        val state = viewModel.state.first { it.downloadError != null }
        assertEquals(LoadError.Server(404, null), state.downloadError)
        assertNull(state.downloadingId)
        assertNull(state.open)
    }

    @Test
    fun webInterface() = runTest {
        val viewModel = newViewModel()
        viewModel.openInWebInterface()
        assertEquals(OpenRequest.Link(part.webUrl), viewModel.state.value.open)
    }
}
