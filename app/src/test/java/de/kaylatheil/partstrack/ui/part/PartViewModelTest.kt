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
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartstrackJson
import de.kaylatheil.partstrack.data.api.StockChange
import de.kaylatheil.partstrack.data.api.StockEntry
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

    /** The part as the server has it after the changes so far. */
    private var current: Part = part
    private val changes = mutableListOf<String>()
    /** Every change waits for this before it is answered. */
    private var changeGate = CompletableDeferred(Unit)
    private var changeFailure: Exception? = null
    private val locations: List<Location> =
        PartstrackJson.decodeFromString(ListSerializer(Location.serializer()), HttpPartstrackApiTest.fixture("locations"))

    private suspend fun changed(description: String, change: (Map<String, Int>) -> Map<String, Int>): Part {
        changes += description
        changeGate.await()
        changeFailure?.let { throw it }
        val amounts = change(current.stock.associate { it.location to it.quantity })
        current = current.copy(
            stock = amounts.map { (location, quantity) -> StockEntry(location, quantity) },
            totalQuantity = amounts.values.sum(),
        )
        return current
    }

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

        override suspend fun locations(): List<Location> = locations

        override suspend fun adjustStock(partId: Int, location: String, delta: Int, note: String): Part {
            assertEquals(1, partId)
            return changed("adjust $location $delta $note".trim()) { it + (location to (it[location] ?: 0) + delta) }
        }

        override suspend fun setStock(partId: Int, location: String, quantity: Int, note: String): Part =
            changed("set $location $quantity $note".trim()) { it + (location to quantity) }

        override suspend fun moveStock(partId: Int, from: String, to: String, quantity: Int?, note: String): Part =
            changed("move $from $to $quantity $note".trim()) {
                val moved = quantity ?: it.getValue(from)
                it + (from to it.getValue(from) - moved) + (to to (it[to] ?: 0) + moved)
            }
    }

    private fun withToken() {
        settings.value = settings.value.copy(token = "secret")
    }

    private fun PartUiState.shownAt(location: String) =
        shownQuantity(location, part!!.stock.single { it.location == location }.quantity)

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

    @Test
    fun withoutATokenNothingChanges() = runTest {
        val viewModel = newViewModel()
        assertFalse(viewModel.state.value.canWrite)
        viewModel.adjust("Shelf", -1)
        viewModel.showStockDialog(StockMode.TAKE, "Shelf")
        assertEquals(emptyList<String>(), changes)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun takeOneShowsTheAnswerAndTheNewHistory() = runTest {
        withToken()
        val viewModel = newViewModel()
        assertTrue(viewModel.state.value.canWrite)
        viewModel.adjust("Shelf", -1)
        val state = viewModel.state.value
        assertEquals(listOf("adjust Shelf -1"), changes)
        assertEquals(4, state.part!!.stock.single { it.location == "Shelf" }.quantity)
        assertEquals(116, state.part.totalQuantity)
        assertEquals(emptyMap<String, Int>(), state.pending)
        assertEquals(4, state.shownAt("Shelf"))
        assertEquals(2, historyCalls.size) // loaded again, with the change
    }

    @Test
    fun quickChangesShowAtOnceAndGoOutInOrder() = runTest {
        withToken()
        val viewModel = newViewModel()
        changeGate = CompletableDeferred()
        viewModel.adjust("Shelf", -1)
        viewModel.adjust("Shelf", -1)
        viewModel.adjust("Cabinet/Drawer 1", 1)
        val waiting = viewModel.state.value
        assertEquals(3, waiting.shownAt("Shelf"))
        assertEquals(98, waiting.shownAt("Cabinet/Drawer 1"))
        assertEquals(listOf("adjust Shelf -1"), changes) // one at a time
        changeGate.complete(Unit)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(listOf("adjust Shelf -1", "adjust Shelf -1", "adjust Cabinet/Drawer 1 1"), changes)
        assertEquals(emptyMap<String, Int>(), state.pending)
        assertEquals(3, state.shownAt("Shelf"))
        assertEquals(98, state.shownAt("Cabinet/Drawer 1"))
    }

    @Test
    fun aFailedQuickChange() = runTest {
        withToken()
        val viewModel = newViewModel()
        changeFailure = ApiException(401, "unknown or revoked API token")
        viewModel.adjust("Shelf", -1)
        val state = viewModel.state.value
        assertEquals(LoadError.Server(401, "unknown or revoked API token"), state.changeError)
        assertEquals(part, state.part)
        assertEquals(5, state.shownAt("Shelf"))
        assertEquals(1, historyCalls.size)
        viewModel.onErrorShown()
        assertNull(viewModel.state.value.changeError)
    }

    @Test
    fun theDialogClosesWhenSaved() = runTest {
        withToken()
        val viewModel = newViewModel()
        viewModel.showStockDialog(StockMode.TAKE, "Shelf")
        assertEquals(StockDialog.Change(StockMode.TAKE, "Shelf"), viewModel.state.value.dialog)
        assertEquals(locations, viewModel.state.value.locations)
        viewModel.save(StockRequest.Adjust("Shelf", -3, "for the prototype"))
        val state = viewModel.state.value
        assertEquals(listOf("adjust Shelf -3 for the prototype"), changes)
        assertNull(state.dialog)
        assertFalse(state.saving)
        assertEquals(2, state.part!!.stock.single { it.location == "Shelf" }.quantity)
    }

    @Test
    fun theDialogStaysOpenWhileSavingAndOnErrors() = runTest {
        withToken()
        val viewModel = newViewModel()
        viewModel.showStockDialog(StockMode.SET, "Shelf")
        changeGate = CompletableDeferred()
        changeFailure = ApiException(409, "this server shows a read-only copy; changes can't be saved")
        viewModel.save(StockRequest.Set("Shelf", 12))
        assertTrue(viewModel.state.value.saving)
        viewModel.dismissDialog()
        viewModel.save(StockRequest.Set("Shelf", 12)) // a second tap sends nothing
        assertEquals(StockDialog.Change(StockMode.SET, "Shelf"), viewModel.state.value.dialog)
        changeGate.complete(Unit)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(listOf("set Shelf 12"), changes)
        assertFalse(state.saving)
        assertEquals(StockDialog.Change(StockMode.SET, "Shelf"), state.dialog)
        assertEquals(LoadError.Server(409, "this server shows a read-only copy; changes can't be saved"), state.dialogError)
        assertEquals(part, state.part)
        viewModel.dismissDialog()
        assertNull(viewModel.state.value.dialog)
        assertNull(viewModel.state.value.dialogError)
    }

    @Test
    fun move() = runTest {
        withToken()
        val viewModel = newViewModel()
        viewModel.showMoveDialog("Shelf")
        assertEquals(StockDialog.Move("Shelf"), viewModel.state.value.dialog)
        viewModel.save(StockRequest.Move("Shelf", "Cabinet/Drawer 2", null))
        val state = viewModel.state.value
        assertEquals(listOf("move Shelf Cabinet/Drawer 2 null"), changes)
        assertNull(state.dialog)
        assertEquals(0, state.shownAt("Shelf"))
        assertEquals(20, state.shownAt("Cabinet/Drawer 2"))
    }

    @Test
    fun aReloadDuringAChangeDoesNotUndoIt() = runTest {
        withToken()
        val viewModel = newViewModel()
        val gate = CompletableDeferred<Unit>()
        partAnswer = { gate.await(); part } // read before the change
        viewModel.onResume()
        viewModel.adjust("Shelf", -1)
        assertEquals(4, viewModel.state.value.shownAt("Shelf"))
        gate.complete(Unit)
        runCurrent()
        assertEquals(4, viewModel.state.value.shownAt("Shelf"))
        assertEquals(current, viewModel.state.value.part)
    }
}
