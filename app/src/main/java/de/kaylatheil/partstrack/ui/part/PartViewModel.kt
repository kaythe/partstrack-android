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
import de.kaylatheil.partstrack.data.api.AttachmentKind
import de.kaylatheil.partstrack.data.api.DownloadedFile
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.api.PartstrackException
import de.kaylatheil.partstrack.data.api.StockChange
import de.kaylatheil.partstrack.data.api.Upload
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Something the screen should open in another app, once. */
sealed interface OpenRequest {
    /** A link, or the web interface: for the browser. */
    data class Link(val url: String) : OpenRequest

    /** A downloaded file, for whatever app opens its type; [fallbackUrl] if none does. */
    data class File(val file: DownloadedFile, val fallbackUrl: String) : OpenRequest
}

/** An upload on its way, or one that failed and can be tried again. */
data class UploadProgress(val name: String, val sent: Long, val total: Long, val error: LoadError? = null) {
    /** How much is sent, from 0 to 1; null if the size isn't known. */
    val fraction: Float? get() = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else null
}

/** A link was added, but the server couldn't store a copy of its file: [reason] is the server's. */
data class LinkNotStored(val reason: String)

/** An open stock dialog. */
sealed interface StockDialog {
    /** Take, add or set; [location] null: none chosen yet. */
    data class Change(val mode: StockMode, val location: String?) : StockDialog

    data class Move(val from: String) : StockDialog
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
    /** There is a token, so changes are offered. */
    val canWrite: Boolean = false,
    /** Changes from the −/+ buttons per location that the server hasn't answered yet. */
    val pending: Map<String, Int> = emptyMap(),
    /** A change from the −/+ buttons failed. */
    val changeError: LoadError? = null,
    val dialog: StockDialog? = null,
    /** The dialog's change is being sent. */
    val saving: Boolean = false,
    /** The dialog's change failed; the dialog stays open with its input. */
    val dialogError: LoadError? = null,
    /** All locations, for the location fields; null until loaded (or if that failed). */
    val locations: List<Location>? = null,
    /** Asking whether to delete the part. */
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    /** The part is deleted: the screen should close. */
    val deleted: Boolean = false,
    val upload: UploadProgress? = null,
    val linkDialog: Boolean = false,
    val linkSaving: Boolean = false,
    /** Adding the link failed; the dialog stays open with its input. */
    val linkError: LoadError? = null,
    val linkNotStored: LinkNotStored? = null,
    /** Asking whether to remove this attachment. */
    val removing: Attachment? = null,
    val removeBusy: Boolean = false,
    /** The link whose file the server is downloading to store a copy. */
    val storingId: Int? = null,
) {
    /** What to show at [location]: the server's amount with the changes still on their way. */
    fun shownQuantity(location: String, quantity: Int): Int = quantity + (pending[location] ?: 0)
}

/**
 * One part: its details, stock, attachments and latest history, and changes
 * to its stock. Changes are sent one after the other, in the order they were
 * made, and the part is shown as the server answers.
 */
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
    private var historyJob: Job? = null
    private var locationsJob: Job? = null
    private var uploadJob: Job? = null

    /** What is being uploaded, kept to try again; [cleanup] deletes a photo taken for it. */
    private class PendingUpload(val upload: Upload, val kind: AttachmentKind?, val cleanup: () -> Unit)

    private var pending: PendingUpload? = null

    /** Changes go to the server one at a time, so their answers arrive in order. */
    private val changeLock = Mutex()

    /**
     * Goes up when a change starts and when it is answered: a load that ran
     * meanwhile may have read the part from before the change.
     */
    private var changeGeneration = 0

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

    /** Shown again: another device may have changed the part, so reload quietly. */
    fun onResume() {
        if (_state.value.part != null) load(quiet = true)
    }

    private fun load(quiet: Boolean = false) {
        val api = api ?: return
        loadJob?.cancel()
        if (!quiet) _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val generation = changeGeneration
            val history = async {
                try {
                    api.history(partId = partId, limit = HISTORY_ENTRIES)
                } catch (_: PartstrackException) {
                    null
                }
            }
            try {
                val part = api.part(partId)
                // A change answered meanwhile has the newer part.
                if (generation == changeGeneration) _state.update { it.copy(part = part, error = null) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(error = e.toLoadError()) }
            }
            // A failed reload keeps the history shown before.
            val entries = history.await()
            _state.update { it.copy(history = entries ?: it.history, loading = false) }
        }
    }

    private fun loadHistory() {
        val api = api ?: return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try {
                val entries = api.history(partId = partId, limit = HISTORY_ENTRIES)
                _state.update { it.copy(history = entries) }
            } catch (_: PartstrackException) {
                // The change itself worked; the older history stays until the next load.
            }
        }
    }

    private fun loadLocations() {
        val api = api ?: return
        if (locationsJob?.isActive == true) return
        locationsJob = viewModelScope.launch {
            try {
                val locations = api.locations()
                _state.update { it.copy(locations = locations) }
            } catch (_: PartstrackException) {
                // Without them, locations can still be typed.
            }
        }
    }

    /**
     * Sends a change after those before it and shows the part it answers
     * with; then [after] updates the state with the error, if any.
     */
    private suspend fun change(call: suspend (PartstrackApi) -> Part, after: (PartUiState, LoadError?) -> PartUiState) {
        val ok = changeLock.withLock {
            val api = api ?: run {
                _state.update { after(it, LoadError.Unreachable(null)) }
                return
            }
            changeGeneration++
            try {
                val part = call(api)
                _state.update { after(it.copy(part = part), null) }
                true
            } catch (e: PartstrackException) {
                _state.update { after(it, e.toLoadError()) }
                false
            } finally {
                changeGeneration++
            }
        }
        if (ok) loadHistory()
    }

    /** −/+ at a location: takes or adds [delta] at once, without a dialog. */
    fun adjust(location: String, delta: Int) {
        if (!_state.value.canWrite) return
        _state.update { it.copy(pending = it.pending.plus(location, delta)) }
        viewModelScope.launch {
            change({ it.adjustStock(partId, location, delta) }) { state, error ->
                state.copy(pending = state.pending.plus(location, -delta), changeError = error ?: state.changeError)
            }
        }
    }

    fun showStockDialog(mode: StockMode, location: String? = null) = showDialog(StockDialog.Change(mode, location))

    fun showMoveDialog(from: String) = showDialog(StockDialog.Move(from))

    private fun showDialog(dialog: StockDialog) {
        if (!_state.value.canWrite) return
        _state.update { it.copy(dialog = dialog, saving = false, dialogError = null) }
        loadLocations()
    }

    /** Closes the dialog, unless its change is on its way. */
    fun dismissDialog() {
        _state.update { if (it.saving) it else it.copy(dialog = null, dialogError = null) }
    }

    /** Sends the dialog's change; the dialog closes when it is saved. */
    fun save(request: StockRequest) {
        val state = _state.value
        if (state.dialog == null || state.saving) return
        _state.update { it.copy(saving = true, dialogError = null) }
        viewModelScope.launch {
            change({ api ->
                when (request) {
                    is StockRequest.Adjust -> api.adjustStock(partId, request.location, request.delta, request.note)
                    is StockRequest.Set -> api.setStock(partId, request.location, request.quantity, request.note)
                    is StockRequest.Move -> api.moveStock(partId, request.from, request.to, request.quantity, request.note)
                }
            }) { state, error ->
                if (error == null) {
                    state.copy(saving = false, dialog = null, dialogError = null)
                } else {
                    state.copy(saving = false, dialogError = error)
                }
            }
        }
    }

    fun askDelete() {
        if (_state.value.canWrite && _state.value.part != null) _state.update { it.copy(confirmDelete = true) }
    }

    fun dismissDelete() {
        _state.update { if (it.deleting) it else it.copy(confirmDelete = false) }
    }

    /** Deletes the part with its stock, history and attachments, after the changes still on their way. */
    fun delete() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            changeLock.withLock {
                val api = api ?: run {
                    _state.update { it.copy(deleting = false, confirmDelete = false) }
                    return@withLock
                }
                try {
                    api.deletePart(partId)
                    _state.update { it.copy(deleting = false, confirmDelete = false, deleted = true) }
                } catch (e: PartstrackException) {
                    _state.update { it.copy(deleting = false, confirmDelete = false, changeError = e.toLoadError()) }
                }
            }
        }
    }

    // -- adding and removing attachments -------------------------------------

    /**
     * Uploads a file (one at a time) with progress. [cleanup] runs when it is
     * done with: after the upload, or when it is cancelled or given up.
     */
    fun upload(upload: Upload, kind: AttachmentKind?, cleanup: () -> Unit = {}) {
        if (!_state.value.canWrite || pending != null) {
            cleanup()
            return
        }
        pending = PendingUpload(upload, kind, cleanup)
        startUpload()
    }

    private fun startUpload() {
        val upload = pending ?: return
        val api = api ?: return
        _state.update { it.copy(upload = UploadProgress(upload.upload.fileName, 0, upload.upload.length)) }
        uploadJob = viewModelScope.launch {
            // A load running meanwhile may have read the part without the new attachment.
            changeGeneration++
            try {
                val attachment = api.uploadAttachment(partId, upload.upload, upload.kind) { sent, total ->
                    // On a network thread; after a cancel there is nothing to update.
                    _state.update { state -> state.upload?.let { state.copy(upload = it.copy(sent = sent, total = total)) } ?: state }
                }
                endUpload()
                _state.update { it.copy(upload = null, part = it.part?.withAttachment(attachment)) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(upload = it.upload?.copy(error = e.toLoadError())) }
            } finally {
                changeGeneration++
            }
        }
    }

    fun retryUpload() {
        if (_state.value.upload?.error != null) startUpload()
    }

    /** Stops an upload on its way, or gives up one that failed. */
    fun cancelUpload() {
        uploadJob?.cancel()
        endUpload()
        _state.update { it.copy(upload = null) }
    }

    private fun endUpload() {
        pending?.cleanup?.invoke()
        pending = null
    }

    override fun onCleared() {
        // Leaving the part stops its upload; a photo taken for it isn't kept.
        endUpload()
    }

    fun showLinkDialog() {
        if (_state.value.canWrite) _state.update { it.copy(linkDialog = true, linkError = null) }
    }

    fun dismissLinkDialog() {
        _state.update { if (it.linkSaving) it else it.copy(linkDialog = false, linkError = null) }
    }

    /** Adds a link; with [download], the server stores a copy of the file it leads to. */
    fun addLink(url: String, kind: AttachmentKind?, download: Boolean) {
        val api = api ?: return
        if (_state.value.linkSaving) return
        _state.update { it.copy(linkSaving = true, linkError = null) }
        viewModelScope.launch {
            changeGeneration++
            try {
                val attachment = api.addLink(partId, url, kind, download)
                _state.update {
                    it.copy(
                        linkSaving = false,
                        linkDialog = false,
                        part = it.part?.withAttachment(attachment),
                        linkNotStored = attachment.downloadError?.let(::LinkNotStored),
                    )
                }
            } catch (e: PartstrackException) {
                _state.update { it.copy(linkSaving = false, linkError = e.toLoadError()) }
            } finally {
                changeGeneration++
            }
        }
    }

    fun onLinkNoticeShown() {
        _state.update { it.copy(linkNotStored = null) }
    }

    /** Tries again to store a copy of the file a link leads to. */
    fun storeCopy(attachment: Attachment) {
        val api = api ?: return
        if (!_state.value.canWrite || _state.value.storingId != null) return
        _state.update { it.copy(storingId = attachment.id) }
        viewModelScope.launch {
            try {
                val stored = api.downloadAttachment(partId, attachment.id)
                _state.update { it.copy(storingId = null, part = it.part?.withAttachment(stored)) }
            } catch (e: PartstrackException) {
                _state.update { it.copy(storingId = null, changeError = e.toLoadError()) }
            }
        }
    }

    fun askRemove(attachment: Attachment) {
        if (_state.value.canWrite) _state.update { it.copy(removing = attachment) }
    }

    fun dismissRemove() {
        _state.update { if (it.removeBusy) it else it.copy(removing = null) }
    }

    fun remove() {
        val api = api ?: return
        val attachment = _state.value.removing ?: return
        if (_state.value.removeBusy) return
        _state.update { it.copy(removeBusy = true) }
        viewModelScope.launch {
            changeGeneration++
            try {
                api.removeAttachment(partId, attachment.id)
                _state.update { state ->
                    state.copy(
                        removeBusy = false,
                        removing = null,
                        part = state.part?.let { part -> part.copy(attachments = part.attachments.filter { it.id != attachment.id }) },
                    )
                }
            } catch (e: PartstrackException) {
                _state.update { it.copy(removeBusy = false, removing = null, changeError = e.toLoadError()) }
            } finally {
                changeGeneration++
            }
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
        _state.update { it.copy(error = if (it.part == null) it.error else null, downloadError = null, changeError = null) }
    }

    companion object {
        const val HISTORY_ENTRIES = 10
    }
}

/** Adds [delta] to the amount for [key], leaving out amounts that come to 0. */
private fun Map<String, Int>.plus(key: String, delta: Int): Map<String, Int> {
    val sum = (this[key] ?: 0) + delta
    return if (sum == 0) this - key else this + (key to sum)
}
