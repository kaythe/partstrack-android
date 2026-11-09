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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kaylatheil.partstrack.ApiFactory
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Part
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

data class EditPartUiState(
    /** Adding a part, rather than changing one. */
    val isNew: Boolean,
    /** Loading the part to change. */
    val loading: Boolean = !isNew,
    val loadError: LoadError? = null,
    /** The part as loaded, which an edit is compared with. */
    val original: Part? = null,
    val form: PartForm = PartForm(),
    /** The form as it was when it opened, to tell whether there is anything to lose. */
    val initialForm: PartForm = PartForm(),
    /** Save was pressed: problems in the form show from then on. */
    val showProblems: Boolean = false,
    val saving: Boolean = false,
    /** The server refused the change; the form keeps its input. */
    val saveError: LoadError? = null,
    /** Saved: the id of the part, for the screen to show it. */
    val savedPartId: Int? = null,
    /** Suggestions; empty until loaded. */
    val tagSuggestions: List<String> = emptyList(),
    val categorySuggestions: List<String> = emptyList(),
    /** All locations, for a new part's stock; null until loaded (or if that failed). */
    val locations: List<Location>? = null,
) {
    val result: PartFormResult
        get() = when {
            isNew -> form.toNewPart()
            original != null -> form.toChanges(original)
            else -> PartFormResult.Unchanged
        }

    /** The problems to show: none before the first try to save. */
    val problems: List<FormProblem>
        get() = if (showProblems) (result as? PartFormResult.Invalid)?.problems.orEmpty() else emptyList()

    /** Leaving now would lose input. */
    val hasChanges: Boolean get() = form != initialForm
}

/**
 * The add/edit form. A new part is sent as one request with its initial
 * stock; an edit sends only what changed. On an error the input stays.
 */
class EditPartViewModel(
    /** The part to change, or null to add one. */
    private val partId: Int?,
    settings: Flow<Settings>,
    private val apiFactory: ApiFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(EditPartUiState(isNew = partId == null))
    val state: StateFlow<EditPartUiState> = _state.asStateFlow()

    private var api: PartstrackApi? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.serverUrl to it.token }.distinctUntilChanged().collect { (url, token) ->
                api = url?.let { apiFactory(it, token) }
                if (_state.value.original == null) load()
                loadSuggestions()
            }
        }
    }

    /** Loads the part to change (once: later reloads would overwrite the input). */
    private fun load() {
        val api = api ?: return
        val id = partId ?: return
        loadJob?.cancel()
        _state.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val part = api.part(id)
                val form = PartForm.of(part)
                _state.update { it.copy(loading = false, original = part, form = form, initialForm = form) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(loading = false, loadError = e.toLoadError()) }
            }
        }
    }

    fun retry() = load()

    /** Each list on its own; without them, everything can still be typed. */
    private fun loadSuggestions() {
        val api = api ?: return
        viewModelScope.launch {
            optional { api.tags() }?.let { tags ->
                _state.update { it.copy(tagSuggestions = tags.filter { tag -> tag.parts > 0 }.map { tag -> tag.name }) }
            }
        }
        viewModelScope.launch {
            optional { api.categories() }?.let { categories -> _state.update { it.copy(categorySuggestions = categories) } }
        }
        if (partId == null) {
            viewModelScope.launch {
                optional { api.locations() }?.let { locations -> _state.update { it.copy(locations = locations) } }
            }
        }
    }

    private suspend fun <T> optional(load: suspend () -> T): T? =
        try {
            load()
        } catch (_: PartstrackException) {
            null
        }

    /** Changes the input (not while it is being saved). */
    fun update(change: (PartForm) -> PartForm) {
        _state.update { if (it.saving) it else it.copy(form = change(it.form), saveError = null) }
    }

    /** Adds the tag being typed (several, separated by commas). */
    fun addTypedTag() = update { it.copy(tags = it.allTags, tagInput = "") }

    fun addTag(tag: String) = update { it.copy(tags = addTags(it.tags, tag), tagInput = "") }

    fun removeTag(tag: String) = update { form -> form.copy(tags = form.tags.filter { it != tag }) }

    fun addAttribute() = update { it.copy(attributes = it.attributes + AttributeRow()) }

    fun updateAttribute(row: Int, attribute: AttributeRow) = update { form ->
        form.copy(attributes = form.attributes.mapIndexed { index, old -> if (index == row) attribute else old })
    }

    fun removeAttribute(row: Int) = update { form ->
        form.copy(attributes = form.attributes.filterIndexed { index, _ -> index != row })
    }

    fun save() {
        val state = _state.value
        if (state.saving || state.loading) return
        when (val result = state.result) {
            is PartFormResult.Invalid -> _state.update { it.copy(showProblems = true) }
            PartFormResult.Unchanged -> _state.update { it.copy(savedPartId = state.original?.id) }
            is PartFormResult.Ready -> send(result)
        }
    }

    private fun send(result: PartFormResult.Ready) {
        val api = api ?: return
        _state.update { it.copy(saving = true, saveError = null, showProblems = true) }
        viewModelScope.launch {
            try {
                val part = if (partId == null) {
                    api.addPart(result.fields, result.stock)
                } else {
                    api.updatePart(partId, result.fields)
                }
                _state.update { it.copy(saving = false, savedPartId = part.id) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(saving = false, saveError = e.toLoadError()) }
            }
        }
    }

    fun onSaveErrorShown() {
        _state.update { it.copy(saveError = null) }
    }
}
