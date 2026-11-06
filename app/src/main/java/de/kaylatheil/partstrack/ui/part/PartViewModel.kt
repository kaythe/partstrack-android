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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kaylatheil.partstrack.ApiFactory
import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.AttachmentDownloader
import de.kaylatheil.partstrack.data.api.DownloadedFile
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.api.PartstrackException
import de.kaylatheil.partstrack.data.api.StockChange
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.toLoadError
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something the screen should open in another app, once. */
sealed interface OpenRequest {
    /** A link, or the web interface: for the browser. */
    data class Link(val url: String) : OpenRequest

    /** A downloaded file, for whatever app opens its type; [fallbackUrl] if none does. */
    data class File(val file: DownloadedFile, val fallbackUrl: String) : OpenRequest
}

data class PartUiState(
    val part: Part? = null,
    val loading: Boolean = true,
    /** Loading the part failed (with nothing to show, or as a message over the part shown). */
    val error: LoadError? = null,
    /** The latest stock changes; null if they couldn't be loaded. */
    val history: List<StockChange>? = null,
    /** The attachment being downloaded to open it. */
    val downloadingId: Int? = null,
    /** Downloading an attachment failed. */
    val downloadError: LoadError? = null,
    val open: OpenRequest? = null,
)

/** One part: its details, stock, attachments and latest history, read only. */
class PartViewModel(
    private val partId: Int,
    settings: Flow<Settings>,
    private val apiFactory: ApiFactory,
    private val downloader: AttachmentDownloader,
) : ViewModel() {
    private val _state = MutableStateFlow(PartUiState())
    val state: StateFlow<PartUiState> = _state.asStateFlow()

    private var api: PartstrackApi? = null
    private var loadJob: Job? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.serverUrl to it.token }.distinctUntilChanged().collect { (url, token) ->
                api = url?.let { apiFactory(it, token) }
                load()
            }
        }
    }

    fun retry() = load()

    /** Shown again: another device may have changed the part, so reload quietly. */
    fun onResume() {
        if (_state.value.part != null) load(quiet = true)
    }

    private fun load(quiet: Boolean = false) {
        val api = api ?: return
        loadJob?.cancel()
        if (!quiet) _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val history = async {
                try {
                    api.history(partId = partId, limit = HISTORY_ENTRIES)
                } catch (_: PartstrackException) {
                    null
                }
            }
            try {
                val part = api.part(partId)
                _state.update { it.copy(part = part, error = null) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(error = e.toLoadError()) }
            }
            // A failed reload keeps the history shown before.
            val changes = history.await()
            _state.update { it.copy(history = changes ?: it.history, loading = false) }
        }
    }

    /** Pictures are shown in the app; this opens the other attachments. */
    fun open(attachment: Attachment) {
        if (!attachment.stored) {
            _state.update { it.copy(open = OpenRequest.Link(attachment.url)) }
            return
        }
        downloadJob?.cancel()
        _state.update { it.copy(downloadingId = attachment.id, downloadError = null) }
        downloadJob = viewModelScope.launch {
            try {
                val file = downloader.download(attachment)
                _state.update { it.copy(downloadingId = null, open = OpenRequest.File(file, attachment.url)) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(downloadingId = null, downloadError = e.toLoadError()) }
            }
        }
    }

    fun openInWebInterface() {
        val part = _state.value.part ?: return
        _state.update { it.copy(open = OpenRequest.Link(part.webUrl)) }
    }

    /** The screen has started the other app (or failed to). */
    fun onOpened() {
        _state.update { it.copy(open = null) }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = if (it.part == null) it.error else null, downloadError = null) }
    }

    companion object {
        const val HISTORY_ENTRIES = 10
    }
}
