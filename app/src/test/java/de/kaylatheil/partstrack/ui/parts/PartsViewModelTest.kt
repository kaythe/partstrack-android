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

package de.kaylatheil.partstrack.ui.parts

import de.kaylatheil.partstrack.MainDispatcherRule
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.FakePartstrackApi
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.TagCount
import de.kaylatheil.partstrack.data.labels.LabelLink
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import de.kaylatheil.partstrack.ui.common.LoadError
import java.net.ConnectException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PartsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** One call of api.parts(), with what it was asked. */
    data class Search(val query: String, val tag: String?, val location: String?, val category: String?, val lowOnly: Boolean)

    private val server = "http://parts.lan:8080/".toHttpUrl()
    private val settings = MutableStateFlow(Settings(server, "pt_secret", ThemeChoice.SYSTEM))
    private val searches = mutableListOf<Search>()
    private val apisMade = mutableListOf<Pair<HttpUrl, String?>>()

    /** What api.parts() does; by default it answers at once with [allParts]. */
    private var answer: suspend (Search) -> List<Part> = { allParts }
    private var tagsAnswer: suspend () -> List<TagCount> = { listOf(TagCount("smd", 1)) }

    private val api = object : FakePartstrackApi() {
        override suspend fun parts(query: String, tag: String?, location: String?, category: String?, lowOnly: Boolean): List<Part> {
            val search = Search(query, tag, location, category, lowOnly)
            searches += search
            return answer(search)
        }

        override suspend fun tags() = tagsAnswer()
        override suspend fun categories() = listOf("IC", "Resistor")
        override suspend fun locations() = listOf(location(1, "Cabinet"), location(2, "Cabinet/Drawer 1"))
    }

    private fun newViewModel() = PartsViewModel(settings) { url, token -> apisMade += url to token; api }

    @Test
    fun loadsAtOnce() = runTest {
        val viewModel = newViewModel()
        val state = viewModel.state.value
        assertTrue(state.loaded)
        assertFalse(state.loading)
        assertEquals(allParts, state.parts)
        assertEquals(listOf(Search("", null, null, null, false)), searches)
        assertEquals(listOf(server to "pt_secret"), apisMade)
        assertEquals(listOf(TagCount("smd", 1)), state.options.tags)
        assertEquals(listOf("IC", "Resistor"), state.options.categories)
        assertEquals(2, state.options.locations?.size)
    }

    @Test
    fun searchWaitsForAPauseInTyping() = runTest {
        val viewModel = newViewModel()
        searches.clear()
        viewModel.onQueryChange("r")
        advanceTimeBy(100)
        viewModel.onQueryChange("re")
        advanceTimeBy(100)
        viewModel.onQueryChange("resistance>=10k ")
        assertEquals("resistance>=10k ", viewModel.state.value.filter.query) // the field shows it at once
        advanceTimeBy(PartsViewModel.SEARCH_DELAY_MS - 1)
        assertTrue(searches.isEmpty())
        advanceTimeBy(2)
        assertEquals(listOf(Search("resistance>=10k", null, null, null, false)), searches)
    }

    @Test
    fun filtersApplyAtOnce() = runTest {
        val viewModel = newViewModel()
        searches.clear()
        viewModel.onTagChange("smd")
        viewModel.onCategoryChange("Resistor")
        viewModel.onLocationChange("Cabinet/Drawer 1")
        viewModel.onLowOnlyChange(true)
        assertEquals(Search("", "smd", "Cabinet/Drawer 1", "Resistor", true), searches.last())
        assertEquals(PartsFilter("", "smd", "Resistor", "Cabinet/Drawer 1", true), viewModel.state.value.filter)
        viewModel.clearFilters()
        assertEquals(Search("", null, null, null, false), searches.last())
        assertTrue(viewModel.state.value.filter.isEmpty)
    }

    @Test
    fun showALocation() = runTest {
        val viewModel = newViewModel()
        viewModel.onQueryChange("resistor")
        viewModel.onTagChange("smd")
        searches.clear()
        viewModel.showLocation("Shelf")
        // Everything there: the search and other filters from before don't apply.
        assertEquals(listOf(Search("", null, "Shelf", null, false)), searches)
        assertEquals(PartsFilter(location = "Shelf"), viewModel.state.value.filter)
        advanceTimeBy(PartsViewModel.SEARCH_DELAY_MS * 2)
        assertEquals(1, searches.size) // nor does the search typed just before
    }

    @Test
    fun aFilterChangeDoesNotWaitForTyping() = runTest {
        val viewModel = newViewModel()
        searches.clear()
        viewModel.onQueryChange("ne555")
        viewModel.onLowOnlyChange(true) // takes the typed text along, at once
        assertEquals(listOf(Search("ne555", null, null, null, true)), searches)
        advanceTimeBy(PartsViewModel.SEARCH_DELAY_MS * 2)
        assertEquals(1, searches.size) // and the pending search is dropped
    }

    @Test
    fun onlyTheNewestAnswerCounts() = runTest {
        val slow = CompletableDeferred<List<Part>>()
        val viewModel = newViewModel()
        answer = { search -> if (search.tag == "smd") slow.await() else listOf(allParts[1]) }
        viewModel.onTagChange("smd") // slow
        assertTrue(viewModel.state.value.loading)
        viewModel.onTagChange("ic") // fast, and replaces it
        slow.complete(allParts)
        runCurrent()
        assertEquals(listOf(allParts[1]), viewModel.state.value.parts)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun unreachableBeforeAnythingLoaded() = runTest {
        answer = { throw NetworkException(ConnectException("Connection refused")) }
        val viewModel = newViewModel()
        val state = viewModel.state.value
        assertFalse(state.loaded)
        assertFalse(state.loading)
        assertEquals(LoadError.Unreachable("Connection refused"), state.error)

        answer = { allParts }
        viewModel.retry()
        assertTrue(viewModel.state.value.loaded)
        assertNull(viewModel.state.value.error)
        assertEquals(allParts, viewModel.state.value.parts)
    }

    @Test
    fun badSearchShowsTheServersMessage() = runTest {
        val viewModel = newViewModel()
        answer = { throw ApiException(400, "'resistance>abc': 'abc' is not a number") }
        viewModel.onQueryChange("resistance>abc")
        advanceTimeBy(PartsViewModel.SEARCH_DELAY_MS + 1)
        val state = viewModel.state.value
        assertEquals(LoadError.Server(400, "'resistance>abc': 'abc' is not a number"), state.error)
        assertEquals(allParts, state.parts) // the last list stays
        viewModel.onErrorShown()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun refresh() = runTest {
        val viewModel = newViewModel()
        val gate = CompletableDeferred<Unit>()
        answer = { gate.await(); listOf(allParts[0]) }
        tagsAnswer = { listOf(TagCount("smd", 1), TagCount("new", 1)) }
        viewModel.refresh()
        assertTrue(viewModel.state.value.refreshing)
        gate.complete(Unit)
        runCurrent()
        val state = viewModel.state.value
        assertFalse(state.refreshing)
        assertEquals(listOf(allParts[0]), state.parts)
        assertEquals(2, state.options.tags?.size) // the choices are fresh too
    }

    @Test
    fun failingChoicesDontStopTheList() = runTest {
        tagsAnswer = { throw NetworkException(ConnectException("gone")) }
        val viewModel = newViewModel()
        val state = viewModel.state.value
        assertNull(state.error)
        assertEquals(allParts, state.parts)
        assertNull(state.options.tags)
        assertEquals(listOf("IC", "Resistor"), state.options.categories)
    }

    @Test
    fun resumeReloadsQuietly() = runTest {
        val viewModel = newViewModel()
        searches.clear()
        val gate = CompletableDeferred<Unit>()
        answer = { gate.await(); listOf(allParts[0]) }
        viewModel.onResume()
        assertFalse(viewModel.state.value.loading) // no progress shown for this
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(allParts[0]), viewModel.state.value.parts)
        assertEquals(1, searches.size)
    }

    @Test
    fun resumeBeforeTheFirstAnswerDoesNothing() = runTest {
        answer = { throw NetworkException(ConnectException("gone")) }
        val viewModel = newViewModel()
        searches.clear()
        viewModel.onResume()
        assertTrue(searches.isEmpty())
    }

    @Test
    fun anotherServerOrTokenReloads() = runTest {
        val viewModel = newViewModel()
        viewModel.onTagChange("smd")
        searches.clear()
        settings.value = settings.value.copy(token = null)
        assertEquals(server to null, apisMade.last())
        assertEquals(listOf(Search("", "smd", null, null, false)), searches) // the filter stays

        settings.value = settings.value.copy(theme = ThemeChoice.DARK) // not about the server
        assertEquals(1, searches.size)
    }

    @Test
    fun notSetUpLoadsNothing() = runTest {
        settings.value = Settings(null, null, ThemeChoice.SYSTEM)
        val viewModel = newViewModel()
        assertTrue(searches.isEmpty())
        assertFalse(viewModel.state.value.loaded)
        assertFalse(viewModel.state.value.loading)
    }

    companion object {
        fun part(id: Int, name: String, total: Int = 10, low: Boolean = false) = Part(
            id = id, name = name, description = "", manufacturer = "", mpn = "", category = "",
            packageName = "", notes = "", minQuantity = null, totalQuantity = total, low = low,
            stock = emptyList(), tags = emptyList(), attributes = emptyMap(), attachments = emptyList(),
            createdAt = "", updatedAt = "", webUrl = "",
        )

        fun location(id: Int, path: String) = Location(
            id = id, name = path.substringAfterLast('/'), path = path,
            parent = path.substringBeforeLast('/', "").ifEmpty { null }, description = "", partCount = 0, totalQuantity = 0,
        )

        val allParts = listOf(part(1, "10k resistor", 117), part(2, "NE555", 3, low = true))
    }

    // -- scanning labels ------------------------------------------------------

    @Test
    fun scanAPartsLabel() = runTest {
        val viewModel = newViewModel()
        viewModel.onScanned("http://parts.lan:8080/parts/12")
        assertEquals(ScanResult.OpenPart(12), viewModel.state.value.scan)
        viewModel.onScanHandled()
        assertNull(viewModel.state.value.scan)
    }

    @Test
    fun scanALocationsLabel() = runTest {
        val viewModel = newViewModel()
        viewModel.onTagChange("smd")
        searches.clear()
        viewModel.onScanned("http://parts.lan:8080/?location=/Cabinet/Drawer%201")
        assertNull(viewModel.state.value.scan) // nothing else to do: the list shows what is there
        assertEquals(PartsFilter(location = "Cabinet/Drawer 1"), viewModel.state.value.filter)
        assertEquals(listOf(Search("", null, "Cabinet/Drawer 1", null, false)), searches)
    }

    @Test
    fun scanAnotherServersLabel() = runTest {
        val viewModel = newViewModel()
        viewModel.onScanned("http://other.lan:8080/parts/12")
        assertEquals(ScanResult.NotHere(LabelLink.OtherServer("http://other.lan:8080/")), viewModel.state.value.scan)
    }

    @Test
    fun scanSomethingElse() = runTest {
        val viewModel = newViewModel()
        viewModel.onScanned("WIFI:S:lab;;")
        assertEquals(ScanResult.NotHere(LabelLink.Unknown("WIFI:S:lab;;")), viewModel.state.value.scan)
    }

    @Test
    fun cameraRefused() = runTest {
        val viewModel = newViewModel()
        viewModel.onCameraRefused()
        assertEquals(ScanResult.NoCamera, viewModel.state.value.scan)
    }
}
