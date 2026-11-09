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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kaylatheil.partstrack.ApiFactory
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.api.PartstrackException
import de.kaylatheil.partstrack.data.api.TagCount
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.toLoadError
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the list shows: the search text (sent as it is, so attribute filters work) and filters. */
data class PartsFilter(
    val query: String = "",
    val tag: String? = null,
    val category: String? = null,
    /** A location's full path; the list includes its sublocations. */
    val location: String? = null,
    val lowOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = this == PartsFilter()
}

/** Choices for the filter chips; null until loaded (or if loading them failed). */
data class FilterOptions(
    val tags: List<TagCount>? = null,
    val categories: List<String>? = null,
    val locations: List<Location>? = null,
)

data class PartsUiState(
    val filter: PartsFilter = PartsFilter(),
    val parts: List<Part> = emptyList(),
    /** The parts shown belong to the current filter (false before the first answer). */
    val loaded: Boolean = false,
    /** Loading after a change of filter or server (the old list may still be shown). */
    val loading: Boolean = false,
    /** Pull to refresh in progress. */
    val refreshing: Boolean = false,
    val error: LoadError? = null,
    val options: FilterOptions = FilterOptions(),
    /** There is a token, so parts can be added. */
    val canWrite: Boolean = false,
)

/**
 * The parts list. It loads from the server in [settings], again when the
 * server or token changes, and keeps only the newest answer when filters
 * change quickly.
 */
class PartsViewModel(
    settings: Flow<Settings>,
    private val apiFactory: ApiFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(PartsUiState())
    val state: StateFlow<PartsUiState> = _state.asStateFlow()

    private var api: PartstrackApi? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var optionsJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.serverUrl to it.token }.distinctUntilChanged().collect { (url, token) ->
                api = url?.let { apiFactory(it, token) }
                _state.update { it.copy(options = FilterOptions(), canWrite = url != null && token != null) }
                load(withOptions = true)
            }
        }
    }

    /** Typing searches after a short pause, so not every letter asks the server. */
    fun onQueryChange(query: String) {
        _state.update { it.copy(filter = it.filter.copy(query = query)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            load()
        }
    }

    fun onTagChange(tag: String?) = changeFilter { it.copy(tag = tag) }

    fun onCategoryChange(category: String?) = changeFilter { it.copy(category = category) }

    fun onLocationChange(location: String?) = changeFilter { it.copy(location = location) }

    fun onLowOnlyChange(lowOnly: Boolean) = changeFilter { it.copy(lowOnly = lowOnly) }

    fun clearFilters() = changeFilter { PartsFilter() }

    /** Everything at [location] (a full path) and in its sublocations, whatever was searched before. */
    fun showLocation(location: String) = changeFilter { PartsFilter(location = location) }

    /** Pull to refresh: the list and the filter choices. */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load(withOptions = true)
    }

    fun retry() = load(withOptions = true)

    /** The screen is shown again: another device may have changed something, so reload quietly. */
    fun onResume() {
        if (_state.value.loaded) load(quiet = true)
    }

    private fun changeFilter(change: (PartsFilter) -> PartsFilter) {
        searchJob?.cancel()
        _state.update { it.copy(filter = change(it.filter)) }
        load()
    }

    /** Loads the list for the current filter; a newer load replaces an older one. */
    private fun load(withOptions: Boolean = false, quiet: Boolean = false) {
        val api = api ?: return
        if (withOptions) loadOptions(api)
        val filter = _state.value.filter
        loadJob?.cancel()
        if (!quiet) _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val parts = api.parts(
                    query = filter.query.trim(),
                    tag = filter.tag,
                    location = filter.location,
                    category = filter.category,
                    lowOnly = filter.lowOnly,
                )
                _state.update { it.copy(parts = parts, loaded = true, error = null, loading = false, refreshing = false) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(error = e.toLoadError(), loading = false, refreshing = false) }
            }
        }
    }

    /**
     * The filter chips' choices, apart from the list, so a new search doesn't
     * cancel them. Each on its own: one that fails leaves only its chip without choices.
     */
    private fun loadOptions(api: PartstrackApi) {
        optionsJob?.cancel()
        optionsJob = viewModelScope.launch {
            suspend fun <T> orNull(load: suspend () -> T): T? =
                try {
                    load()
                } catch (_: PartstrackException) {
                    null
                }
            val tags = async { orNull { api.tags() } }
            val categories = async { orNull { api.categories() } }
            val locations = async { orNull { api.locations() } }
            val options = FilterOptions(tags.await(), categories.await(), locations.await())
            _state.update { it.copy(options = options) }
        }
    }

    /** The error has been shown (as a message over a list that is still there). */
    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}
