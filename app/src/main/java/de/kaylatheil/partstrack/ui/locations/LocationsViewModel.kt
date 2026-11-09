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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kaylatheil.partstrack.ApiFactory
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.api.PartstrackException
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.toLoadError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An open location dialog. */
sealed interface LocationDialog {
    /** A new location, in [parent] (a path; null: at the top level, or to be typed). */
    data class Add(val parent: String?) : LocationDialog

    data class Edit(val location: Location) : LocationDialog

    data class Delete(val location: Location) : LocationDialog
}

data class LocationsUiState(
    val locations: List<Location> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** Loading failed (with nothing to show, or as a message over the tree shown). */
    val error: LoadError? = null,
    /** There is a token, so locations can be changed. */
    val canWrite: Boolean = false,
    val dialog: LocationDialog? = null,
    val saving: Boolean = false,
    /** The dialog's change failed; it stays open with its input. */
    val dialogError: LoadError? = null,
    /** The paths of the locations whose sublocations are hidden. */
    val collapsed: Set<String> = emptySet(),
) {
    /** The locations to show, without those inside collapsed ones. */
    val visible: List<Location> get() = visibleLocations(locations, collapsed)

    /** The locations that have sublocations to show or hide. */
    val parents: Set<String> get() = parentPaths(locations)
}

/** The location tree, and adding, changing and deleting locations. */
class LocationsViewModel(
    settings: Flow<Settings>,
    private val apiFactory: ApiFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(LocationsUiState())
    val state: StateFlow<LocationsUiState> = _state.asStateFlow()

    private var api: PartstrackApi? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.serverUrl to it.token }.distinctUntilChanged().collect { (url, token) ->
                api = url?.let { apiFactory(it, token) }
                _state.update { it.copy(canWrite = url != null && token != null) }
                load()
            }
        }
    }

    fun retry() = load()

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    /** Shown again: another device may have changed something, so reload quietly. */
    fun onResume() {
        val state = _state.value
        when {
            state.loaded -> load(quiet = true)
            // E.g. back from allowing the local network, or with the VPN on now.
            state.error != null -> retry()
        }
    }

    private fun load(quiet: Boolean = false) {
        val api = api ?: return
        loadJob?.cancel()
        if (!quiet) _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val locations = api.locations()
                _state.update {
                    it.copy(
                        locations = locations,
                        // A location renamed, moved or deleted meanwhile is no longer collapsed.
                        collapsed = it.collapsed intersect parentPaths(locations),
                        loaded = true,
                        error = null,
                        loading = false,
                        refreshing = false,
                    )
                }
            } catch (e: PartstrackException) {
                _state.update { it.copy(error = e.toLoadError(), loading = false, refreshing = false) }
            }
        }
    }

    /** Hides the sublocations of [path], or shows them again. */
    fun toggle(path: String) {
        _state.update { it.copy(collapsed = if (path in it.collapsed) it.collapsed - path else it.collapsed + path) }
    }

    fun showAdd(parent: String? = null) = showDialog(LocationDialog.Add(parent))

    fun showEdit(location: Location) = showDialog(LocationDialog.Edit(location))

    fun showDelete(location: Location) = showDialog(LocationDialog.Delete(location))

    private fun showDialog(dialog: LocationDialog) {
        if (_state.value.canWrite) _state.update { it.copy(dialog = dialog, saving = false, dialogError = null) }
    }

    /** Closes the dialog, unless its change is on its way. */
    fun dismissDialog() {
        _state.update { if (it.saving) it else it.copy(dialog = null, dialogError = null) }
    }

    /** Sends the dialog's change; it closes when saved, and the tree reloads (a rename changes the paths below). */
    fun save(request: LocationRequest) = send {
        when (request) {
            is LocationRequest.Add -> it.addLocation(request.path, request.description)
            is LocationRequest.Change -> it.updateLocation(request.id, request.name, request.description, request.parent)
        }
    }

    /** Deletes the location of the open delete dialog (the server refuses one that isn't empty). */
    fun delete() {
        val dialog = _state.value.dialog as? LocationDialog.Delete ?: return
        send { it.deleteLocation(dialog.location.id) }
    }

    private fun send(call: suspend (PartstrackApi) -> Unit) {
        val api = api ?: return
        if (_state.value.saving || _state.value.dialog == null) return
        _state.update { it.copy(saving = true, dialogError = null) }
        viewModelScope.launch {
            try {
                call(api)
                _state.update { it.copy(saving = false, dialog = null) }
                load(quiet = true)
            } catch (e: PartstrackException) {
                _state.update { it.copy(saving = false, dialogError = e.toLoadError()) }
            }
        }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = if (it.loaded) null else it.error) }
    }
}
