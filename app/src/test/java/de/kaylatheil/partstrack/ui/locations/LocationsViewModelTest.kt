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

package de.kaylatheil.partstrack.ui.locations

import de.kaylatheil.partstrack.MainDispatcherRule
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.FakePartstrackApi
import de.kaylatheil.partstrack.data.api.HttpPartstrackApiTest
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.PartstrackJson
import de.kaylatheil.partstrack.data.api.Patch
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
class LocationsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = MutableStateFlow(Settings("http://parts.lan:8080/".toHttpUrl(), "pt_secret", ThemeChoice.SYSTEM))
    private val locations: List<Location> =
        PartstrackJson.decodeFromString(ListSerializer(Location.serializer()), HttpPartstrackApiTest.fixture("locations"))

    private var locationsAnswer: suspend () -> List<Location> = { locations }
    private var loads = 0
    private val sent = mutableListOf<String>()
    private var gate = CompletableDeferred(Unit)
    private var failure: Exception? = null

    private suspend fun change(description: String) {
        sent += description
        gate.await()
        failure?.let { throw it }
    }

    private val api = object : FakePartstrackApi() {
        override suspend fun locations(): List<Location> {
            loads++
            return locationsAnswer()
        }

        override suspend fun addLocation(path: String, description: String): Location {
            change("add $path $description".trim())
            return locations.first()
        }

        override suspend fun updateLocation(id: Int, name: String?, description: String?, parent: Patch<String?>): Location {
            change("update $id $name $description $parent")
            return locations.first()
        }

        override suspend fun deleteLocation(id: Int) = change("delete $id")
    }

    private fun newViewModel() = LocationsViewModel(settings) { _, _ -> api }

    private val drawer get() = locations.single { it.path == "Cabinet/Drawer 1" }

    @Test
    fun loadsTheTree() = runTest {
        val state = newViewModel().state.value
        assertTrue(state.loaded)
        assertEquals(locations, state.locations)
        assertTrue(state.canWrite)
    }

    @Test
    fun unreachable() = runTest {
        locationsAnswer = { throw NetworkException(ConnectException("gone")) }
        val viewModel = newViewModel()
        assertEquals(LoadError.Unreachable("gone"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.loaded)
        locationsAnswer = { locations }
        viewModel.retry()
        assertTrue(viewModel.state.value.loaded)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun withoutTokenNoDialogs() = runTest {
        settings.value = settings.value.copy(token = null)
        val viewModel = newViewModel()
        assertFalse(viewModel.state.value.canWrite)
        viewModel.showAdd()
        viewModel.showEdit(drawer)
        viewModel.showDelete(drawer)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun addThenReload() = runTest {
        val viewModel = newViewModel()
        viewModel.showAdd("Cabinet")
        assertEquals(LocationDialog.Add("Cabinet"), viewModel.state.value.dialog)
        viewModel.save(LocationRequest.Add("Cabinet/Drawer 4", "Capacitors"))
        assertEquals(listOf("add Cabinet/Drawer 4 Capacitors"), sent)
        assertNull(viewModel.state.value.dialog)
        assertEquals(2, loads) // a change may move paths around, so the tree is read again
    }

    @Test
    fun editKeepsTheDialogWhenRefused() = runTest {
        failure = ApiException(400, "there already is a location with that name in that place")
        val viewModel = newViewModel()
        viewModel.showEdit(drawer)
        viewModel.save(LocationRequest.Change(drawer.id, name = "Drawer 2"))
        val state = viewModel.state.value
        assertEquals(LocationDialog.Edit(drawer), state.dialog)
        assertEquals(LoadError.Server(400, "there already is a location with that name in that place"), state.dialogError)
        assertFalse(state.saving)
        assertEquals(1, loads)
    }

    @Test
    fun moveToTheTopLevel() = runTest {
        val viewModel = newViewModel()
        viewModel.showEdit(drawer)
        viewModel.save(LocationRequest.Change(drawer.id, parent = Patch.Set(null)))
        assertEquals(listOf("update ${drawer.id} null null Set(value=null)"), sent)
    }

    @Test
    fun deleteRefusedForALocationThatIsntEmpty() = runTest {
        failure = ApiException(400, "location 'Drawer 1' still holds 1 part(s); move them first")
        val viewModel = newViewModel()
        viewModel.showDelete(drawer)
        viewModel.delete()
        assertEquals(listOf("delete ${drawer.id}"), sent)
        assertEquals(LoadError.Server(400, "location 'Drawer 1' still holds 1 part(s); move them first"), viewModel.state.value.dialogError)
        viewModel.dismissDialog()
        assertNull(viewModel.state.value.dialog)
        assertNull(viewModel.state.value.dialogError)
    }

    @Test
    fun noClosingOrSecondSendWhileSaving() = runTest {
        gate = CompletableDeferred()
        val viewModel = newViewModel()
        viewModel.showDelete(drawer)
        viewModel.delete()
        viewModel.delete()
        viewModel.dismissDialog()
        assertTrue(viewModel.state.value.saving)
        assertEquals(LocationDialog.Delete(drawer), viewModel.state.value.dialog)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, sent.size)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun resumeReloadsQuietly() = runTest {
        val viewModel = newViewModel()
        val slow = CompletableDeferred<List<Location>>()
        locationsAnswer = { slow.await() }
        viewModel.onResume()
        assertFalse(viewModel.state.value.loading)
        slow.complete(locations.take(1))
        runCurrent()
        assertEquals(locations.take(1), viewModel.state.value.locations)
    }

    @Test
    fun collapseAndExpand() = runTest {
        val viewModel = newViewModel()
        viewModel.toggle("Cabinet")
        val state = viewModel.state.value
        assertEquals(setOf("Cabinet"), state.collapsed)
        assertTrue(state.visible.none { it.path.startsWith("Cabinet/") })
        assertTrue(state.visible.any { it.path == "Cabinet" })
        viewModel.toggle("Cabinet")
        assertEquals(locations, viewModel.state.value.visible)
    }

    @Test
    fun collapsedLocationsThatAreGoneAreForgotten() = runTest {
        val viewModel = newViewModel()
        viewModel.toggle("Cabinet")
        viewModel.retry()
        assertEquals(setOf("Cabinet"), viewModel.state.value.collapsed) // still there: stays collapsed
        // Cabinet was renamed elsewhere, say: its old path has no sublocations any more.
        locationsAnswer = { locations.filterNot { it.path.startsWith("Cabinet") } }
        viewModel.retry()
        assertEquals(emptySet<String>(), viewModel.state.value.collapsed)
    }
}
