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

package de.kaylatheil.partstrack.ui.edit

import de.kaylatheil.partstrack.MainDispatcherRule
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.FakePartstrackApi
import de.kaylatheil.partstrack.data.api.HttpPartstrackApiTest
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartFields
import de.kaylatheil.partstrack.data.api.PartstrackJson
import de.kaylatheil.partstrack.data.api.StockAmount
import de.kaylatheil.partstrack.data.api.TagCount
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import de.kaylatheil.partstrack.ui.common.LoadError
import java.net.ConnectException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditPartViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = MutableStateFlow(Settings("http://parts.lan:8080/".toHttpUrl(), "pt_secret", ThemeChoice.SYSTEM))
    private val part: Part = PartstrackJson.decodeFromString(Part.serializer(), HttpPartstrackApiTest.fixture("part"))
    private val locations: List<Location> =
        PartstrackJson.decodeFromString(ListSerializer(Location.serializer()), HttpPartstrackApiTest.fixture("locations"))

    private var partAnswer: suspend () -> Part = { part }
    private val sent = mutableListOf<Any>()
    private var gate = CompletableDeferred(Unit)
    private var failure: Exception? = null

    private val api = object : FakePartstrackApi() {
        override suspend fun part(id: Int): Part {
            assertEquals(1, id)
            return partAnswer()
        }

        override suspend fun tags() = listOf(TagCount("smd", 2), TagCount("unused", 0))
        override suspend fun categories() = listOf("IC", "Resistor")
        override suspend fun locations() = locations

        override suspend fun addPart(fields: PartFields, stock: List<StockAmount>): Part {
            sent += fields to stock
            gate.await()
            failure?.let { throw it }
            return part.copy(id = 42, name = fields.name!!)
        }

        override suspend fun updatePart(id: Int, fields: PartFields): Part {
            sent += id to fields
            gate.await()
            failure?.let { throw it }
            return part
        }
    }

    private fun adding() = EditPartViewModel(null, settings) { _, _ -> api }

    private fun editing() = EditPartViewModel(1, settings) { _, _ -> api }

    // -- adding -----------------------------------------------------------------

    @Test
    fun aNewPartStartsEmpty() = runTest {
        val state = adding().state.value
        assertTrue(state.isNew)
        assertFalse(state.loading)
        assertEquals(PartForm(), state.form)
        assertFalse(state.hasChanges)
        assertEquals(listOf("smd"), state.tagSuggestions) // tags without parts aren't suggested
        assertEquals(listOf("IC", "Resistor"), state.categorySuggestions)
        assertEquals(locations, state.locations)
    }

    @Test
    fun addAPart() = runTest {
        val viewModel = adding()
        viewModel.update { it.copy(name = "LM317", location = "Shelf/Box B", quantity = "6") }
        viewModel.addTag("ic")
        assertTrue(viewModel.state.value.hasChanges)
        viewModel.save()
        assertEquals(
            listOf(PartFields(name = "LM317", tags = listOf("ic")) to listOf(StockAmount("Shelf/Box B", 6))),
            sent,
        )
        assertEquals(42, viewModel.state.value.savedPartId)
    }

    @Test
    fun problemsShowOnlyAfterSaving() = runTest {
        val viewModel = adding()
        assertTrue(viewModel.state.value.problems.isEmpty()) // nothing typed yet is no problem yet
        viewModel.save()
        assertEquals(listOf(FormProblem.NameMissing), viewModel.state.value.problems)
        assertTrue(sent.isEmpty())
        viewModel.update { it.copy(name = "x") }
        assertTrue(viewModel.state.value.problems.isEmpty())
    }

    @Test
    fun refusedKeepsTheInput() = runTest {
        failure = ApiException(400, "a part named 'LM317' already exists")
        val viewModel = adding()
        viewModel.update { it.copy(name = "LM317", notes = "keep me") }
        viewModel.save()
        val state = viewModel.state.value
        assertEquals(LoadError.Server(400, "a part named 'LM317' already exists"), state.saveError)
        assertNull(state.savedPartId)
        assertFalse(state.saving)
        assertEquals("keep me", state.form.notes)

        viewModel.onSaveErrorShown()
        failure = null
        viewModel.update { it.copy(name = "LM317 (2)") }
        viewModel.save()
        assertEquals(42, viewModel.state.value.savedPartId)
    }

    @Test
    fun noInputWhileSaving() = runTest {
        gate = CompletableDeferred()
        val viewModel = adding()
        viewModel.update { it.copy(name = "LM317") }
        viewModel.save()
        assertTrue(viewModel.state.value.saving)
        viewModel.update { it.copy(name = "changed meanwhile") }
        viewModel.save() // a second tap doesn't send it again
        assertEquals("LM317", viewModel.state.value.form.name)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, sent.size)
        assertEquals(42, viewModel.state.value.savedPartId)
    }

    @Test
    fun tagsAndAttributes() = runTest {
        val viewModel = adding()
        viewModel.update { it.copy(tagInput = "smd, tht") }
        viewModel.addTypedTag()
        viewModel.addTag("SMD")
        assertEquals(listOf("smd", "tht"), viewModel.state.value.form.tags)
        assertEquals("", viewModel.state.value.form.tagInput)
        viewModel.removeTag("smd")
        assertEquals(listOf("tht"), viewModel.state.value.form.tags)

        viewModel.addAttribute()
        viewModel.addAttribute()
        viewModel.updateAttribute(0, AttributeRow("resistance", "10k"))
        viewModel.updateAttribute(1, AttributeRow("power", "0.1W"))
        viewModel.removeAttribute(0)
        assertEquals(listOf(AttributeRow("power", "0.1W")), viewModel.state.value.form.attributes)
    }

    // -- changing -------------------------------------------------------------

    @Test
    fun editLoadsThePart() = runTest {
        val state = editing().state.value
        assertFalse(state.isNew)
        assertFalse(state.loading)
        assertEquals(part, state.original)
        assertEquals(PartForm.of(part), state.form)
        assertFalse(state.hasChanges)
        assertNull(state.locations) // no initial stock for a part that exists
    }

    @Test
    fun editSendsOnlyWhatChanged() = runTest {
        val viewModel = editing()
        viewModel.update { it.copy(mpn = "RC0603FR-0710KL-X") }
        viewModel.save()
        assertEquals(listOf(1 to PartFields(mpn = "RC0603FR-0710KL-X")), sent)
        assertEquals(1, viewModel.state.value.savedPartId)
    }

    @Test
    fun saveWithoutChangesSendsNothing() = runTest {
        val viewModel = editing()
        viewModel.save()
        assertTrue(sent.isEmpty())
        assertEquals(1, viewModel.state.value.savedPartId)
    }

    @Test
    fun loadingFails() = runTest {
        partAnswer = { throw NetworkException(ConnectException("gone")) }
        val viewModel = editing()
        assertEquals(LoadError.Unreachable("gone"), viewModel.state.value.loadError)
        viewModel.save()
        assertTrue(sent.isEmpty())
        partAnswer = { part }
        viewModel.retry()
        assertEquals(PartForm.of(part), viewModel.state.value.form)
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun aNewTokenKeepsTheInput() = runTest {
        val viewModel = editing()
        viewModel.update { it.copy(notes = "typed") }
        settings.value = settings.value.copy(token = "pt_new") // e.g. after fixing it in the settings
        assertEquals("typed", viewModel.state.value.form.notes)
    }

    @Test
    fun tokenNotAccepted() = runTest {
        failure = ApiException(401, "unknown or revoked API token")
        val viewModel = editing()
        viewModel.update { it.copy(notes = "typed") }
        viewModel.save()
        assertEquals(LoadError.Server(401, "unknown or revoked API token"), viewModel.state.value.saveError)
        assertEquals("typed", viewModel.state.value.form.notes)
    }
}
