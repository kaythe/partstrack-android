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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.AttachmentKind
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.StockChange
import de.kaylatheil.partstrack.data.api.parseServerTime
import de.kaylatheil.partstrack.ui.common.ErrorView
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.Remove
import de.kaylatheil.partstrack.ui.common.message
import de.kaylatheil.partstrack.ui.scan.openAppSettings
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun PartScreen(viewModel: PartViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit, onEdit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.no_app_to_open)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.open) {
        val request = state.open ?: return@LaunchedEffect
        viewModel.onOpened()
        if (!openElsewhere(context, request)) scope.launch { snackbar.showSnackbar(noApp) }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    val cameraMessage = stringResource(R.string.photo_no_camera)
    val appSettings = stringResource(R.string.app_settings)
    val pickers = rememberAttachmentPickers(
        onUpload = viewModel::upload,
        onCameraRefused = {
            scope.launch {
                val result = snackbar.showSnackbar(cameraMessage, actionLabel = appSettings, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) openAppSettings(context)
            }
        },
    )
    PartContent(
        state = state,
        snackbar = snackbar,
        actions = PartActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onOpenAttachment = viewModel::open,
            onOpenInWebInterface = viewModel::openInWebInterface,
            onErrorShown = viewModel::onErrorShown,
            onAdjust = viewModel::adjust,
            onShowStockDialog = viewModel::showStockDialog,
            onShowMoveDialog = viewModel::showMoveDialog,
            onDismissDialog = viewModel::dismissDialog,
            onSave = viewModel::save,
            onOpenSettings = onOpenSettings,
            onEdit = onEdit,
            onAskDelete = viewModel::askDelete,
            onDismissDelete = viewModel::dismissDelete,
            onDelete = viewModel::delete,
            onTakePhoto = pickers.takePhoto,
            onChooseFile = pickers.chooseFile,
            onCancelUpload = viewModel::cancelUpload,
            onRetryUpload = viewModel::retryUpload,
            onShowLinkDialog = viewModel::showLinkDialog,
            onDismissLinkDialog = viewModel::dismissLinkDialog,
            onAddLink = viewModel::addLink,
            onLinkNoticeShown = viewModel::onLinkNoticeShown,
            onStoreCopy = viewModel::storeCopy,
            onAskRemove = viewModel::askRemove,
            onDismissRemove = viewModel::dismissRemove,
            onRemove = viewModel::remove,
        ),
    )
}

/** Starts the app that shows [request]; false if there is none. */
private fun openElsewhere(context: Context, request: OpenRequest): Boolean {
    fun start(intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    fun browse(url: String) = start(Intent(Intent.ACTION_VIEW, url.toUri()))
    return when (request) {
        is OpenRequest.Link -> browse(request.url)
        is OpenRequest.File -> {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", request.file.file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, request.file.mediaType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // No app for the type: the browser may still show or save it.
            start(intent) || browse(request.fallbackUrl)
        }
    }
}

class PartActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenAttachment: (Attachment) -> Unit = {},
    val onOpenInWebInterface: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
    /** −/+ at a location. */
    val onAdjust: (location: String, delta: Int) -> Unit = { _, _ -> },
    val onShowStockDialog: (StockMode, location: String?) -> Unit = { _, _ -> },
    val onShowMoveDialog: (from: String) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onSave: (StockRequest) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onChooseFile: () -> Unit = {},
    val onCancelUpload: () -> Unit = {},
    val onRetryUpload: () -> Unit = {},
    val onShowLinkDialog: () -> Unit = {},
    val onDismissLinkDialog: () -> Unit = {},
    val onAddLink: (url: String, kind: AttachmentKind?, download: Boolean) -> Unit = { _, _, _ -> },
    val onLinkNoticeShown: () -> Unit = {},
    val onStoreCopy: (Attachment) -> Unit = {},
    val onAskRemove: (Attachment) -> Unit = {},
    val onDismissRemove: () -> Unit = {},
    val onRemove: () -> Unit = {},
)

/** The screen without its ViewModel, for UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartContent(state: PartUiState, actions: PartActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val part = state.part
    val passingError = state.error?.takeIf { part != null } ?: state.downloadError ?: state.changeError
    val passingMessage = passingError?.message()
    // A token the server doesn't take: the way to fix it is in the settings.
    val settingsLabel = stringResource(R.string.open_settings).takeIf { passingError.isUnauthorized() }
    LaunchedEffect(passingMessage) {
        if (passingMessage != null) {
            val result = snackbar.showSnackbar(passingMessage, actionLabel = settingsLabel, withDismissAction = settingsLabel != null)
            actions.onErrorShown()
            if (result == SnackbarResult.ActionPerformed) actions.onOpenSettings()
        }
    }
    val notStored = state.linkNotStored?.let { stringResource(R.string.link_not_stored, it.reason) }
    LaunchedEffect(notStored) {
        if (notStored != null) {
            snackbar.showSnackbar(notStored, withDismissAction = true)
            actions.onLinkNoticeShown()
        }
    }
    var fullSize by remember { mutableStateOf<Attachment?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(part?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (part != null && state.canWrite) {
                        IconButton(onClick = actions.onEdit, modifier = Modifier.testTag("edit")) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_part))
                        }
                    }
                    if (part != null) MoreMenu(state.canWrite, actions)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && part != null,
            onRefresh = actions.onRetry,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                part != null -> PartDetails(part, state, actions, onShowPicture = { fullSize = it })
                state.error != null -> ErrorView(state.error, actions.onRetry, Modifier.fillMaxSize())
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("loading"))
                }
            }
        }
    }
    fullSize?.let { picture ->
        FullSizePicture(
            picture,
            onClose = { fullSize = null },
            onRemove = if (state.canWrite) {
                { fullSize = null; actions.onAskRemove(picture) }
            } else {
                null
            },
        )
    }
    if (state.linkDialog) {
        LinkDialog(state.linkSaving, state.linkError, actions.onAddLink, actions.onDismissLinkDialog, actions.onOpenSettings)
    }
    state.removing?.let { RemoveAttachmentDialog(it, state.removeBusy, actions.onRemove, actions.onDismissRemove) }
    if (part != null) StockDialogs(part, state, actions)
    if (part != null && state.confirmDelete) DeleteDialog(part, state.deleting, actions)
}

@Composable
private fun DeleteDialog(part: Part, deleting: Boolean, actions: PartActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDelete,
        title = { Text(stringResource(R.string.delete_title, part.name)) },
        text = { Text(stringResource(R.string.delete_text)) },
        confirmButton = {
            TextButton(onClick = actions.onDelete, enabled = !deleting, modifier = Modifier.testTag("confirm_delete")) {
                if (deleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDelete, enabled = !deleting) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun LoadError?.isUnauthorized() = this is LoadError.Server && status == 401

@Composable
private fun StockDialogs(part: Part, state: PartUiState, actions: PartActions) {
    val dialog = state.dialog ?: return
    val context = DialogContext(
        part = part,
        locations = state.locations,
        saving = state.saving,
        error = state.dialogError,
        onSave = actions.onSave,
        onDismiss = actions.onDismissDialog,
        onOpenSettings = actions.onOpenSettings,
    )
    when (dialog) {
        is StockDialog.Change -> StockChangeDialog(dialog, context)
        is StockDialog.Move -> MoveDialog(dialog, context)
    }
}

@Composable
private fun MoreMenu(canWrite: Boolean, actions: PartActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_in_web_interface)) },
                onClick = { open = false; actions.onOpenInWebInterface() },
            )
            if (canWrite) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_part)) },
                    onClick = { open = false; actions.onAskDelete() },
                    modifier = Modifier.testTag("delete"),
                )
            }
        }
    }
}

@Composable
private fun PartDetails(part: Part, state: PartUiState, actions: PartActions, onShowPicture: (Attachment) -> Unit) {
    val pictures = part.attachments.filter { it.isImage }
    val files = part.attachments.filterNot { it.isImage }
        .sortedBy { if (it.kind == AttachmentKind.DATASHEET.value) 0 else 1 }
    LazyColumn(Modifier.fillMaxSize().testTag("part"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Overview(part) }
        if (part.tags.isNotEmpty()) item { Tags(part.tags) }

        section(R.string.section_stock)
        if (part.stock.isEmpty()) {
            item { Hint(stringResource(R.string.no_stock)) }
        } else {
            items(part.stock, key = { "stock " + it.location }) { entry ->
                val quantity = state.shownQuantity(entry.location, entry.quantity)
                if (state.canWrite) {
                    StockRow(entry.location, quantity, actions)
                } else {
                    ListItem(
                        headlineContent = { Text(entry.location) },
                        trailingContent = { Text(quantity.toString(), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }
        if (state.canWrite) {
            item(key = "add stock") {
                TextButton(
                    onClick = { actions.onShowStockDialog(StockMode.ADD, null) },
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("add_stock"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.add_stock))
                }
            }
        }

        if (part.attributes.isNotEmpty()) {
            section(R.string.section_attributes)
            items(part.attributes.entries.toList(), key = { "attribute " + it.key }) { (key, value) ->
                Field(key, value)
            }
        }

        if (pictures.isNotEmpty()) {
            section(R.string.section_pictures)
            item { Pictures(pictures, onShowPicture) }
        }

        if (files.isNotEmpty()) {
            section(R.string.section_files)
            items(files, key = { "file " + it.id }) { file ->
                FileRow(
                    file,
                    busy = state.downloadingId == file.id || state.storingId == file.id,
                    onOpen = { actions.onOpenAttachment(file) },
                    menu = if (state.canWrite) {
                        { AttachmentMenu(file, onStoreCopy = { actions.onStoreCopy(file) }, onRemove = { actions.onAskRemove(file) }) }
                    } else {
                        null
                    },
                )
            }
        }

        if (state.canWrite) {
            if (pictures.isEmpty() && files.isEmpty()) section(R.string.section_attachments)
            state.upload?.let { upload ->
                item(key = "upload") { UploadCard(upload, actions.onCancelUpload, actions.onRetryUpload) }
            }
            item(key = "add attachment") {
                // One upload at a time; a link can be added meanwhile.
                AttachmentButtons(state.upload == null, actions.onTakePhoto, actions.onChooseFile, actions.onShowLinkDialog)
            }
        }

        if (part.notes.isNotBlank()) {
            section(R.string.section_notes)
            item { Text(part.notes, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
        }

        section(R.string.section_history)
        val history = state.history
        when {
            history == null -> item { Hint(stringResource(R.string.history_unavailable)) }
            history.isEmpty() -> item { Hint(stringResource(R.string.history_empty)) }
            else -> items(history, key = { "history " + it.id }) { HistoryRow(it) }
        }
    }
}

private fun LazyListScope.section(title: Int) {
    item(key = "section $title") {
        Column {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                stringResource(title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Overview(part: Part) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (part.description.isNotBlank()) {
            Text(part.description, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val color = if (part.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Text(
                stringResource(R.string.in_stock, part.totalQuantity),
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                modifier = Modifier.testTag("total"),
            )
            part.minQuantity?.let {
                Text(
                    stringResource(if (part.low) R.string.minimum_low else R.string.minimum, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
    Field(stringResource(R.string.field_manufacturer), part.manufacturer)
    Field(stringResource(R.string.field_mpn), part.mpn)
    Field(stringResource(R.string.field_category), part.category)
    Field(stringResource(R.string.field_package), part.packageName)
}

/** A location with its amount, −/+ to take or add one, and a menu for the rest. */
@Composable
private fun StockRow(location: String, quantity: Int, actions: PartActions) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(location) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { actions.onAdjust(location, -1) },
                    enabled = quantity > 0,
                    modifier = Modifier.testTag("take_one $location"),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.take_one, location))
                }
                Text(
                    quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 32.dp).testTag("quantity $location"),
                )
                IconButton(onClick = { actions.onAdjust(location, 1) }, modifier = Modifier.testTag("add_one $location")) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_one, location))
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("stock_menu $location")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.stock_menu, location))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_change_stock)) },
                            onClick = { menu = false; actions.onShowStockDialog(StockMode.TAKE, location) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_set_stock)) },
                            onClick = { menu = false; actions.onShowStockDialog(StockMode.SET, location) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_move_stock)) },
                            onClick = { menu = false; actions.onShowMoveDialog(location) },
                        )
                    }
                }
            }
        },
    )
}

/** A label and its value on one line; nothing for an empty value. */
@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(tags: List<String>) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Pictures(pictures: List<Attachment>, onShow: (Attachment) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pictures, key = { it.id }) { picture ->
            AsyncImage(
                model = picture.url,
                contentDescription = picture.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onShow(picture) }
                    .testTag("picture"),
            )
        }
    }
}

@Composable
private fun FileRow(file: Attachment, busy: Boolean, onOpen: () -> Unit, menu: (@Composable () -> Unit)?) {
    val datasheet = file.kind == AttachmentKind.DATASHEET.value
    val kind = stringResource(
        when {
            datasheet && file.stored -> R.string.kind_datasheet
            datasheet -> R.string.kind_datasheet_link
            file.stored -> R.string.kind_file
            else -> R.string.kind_link
        },
    )
    ListItem(
        // A link to a page has no file name; then its address names it.
        headlineContent = { Text(file.name.ifBlank { file.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(kind) },
        trailingContent = when {
            busy -> { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
            menu != null -> menu
            else -> null
        },
        modifier = Modifier.clickable(enabled = !busy, onClick = onOpen),
    )
}

private val TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
private fun HistoryRow(change: StockChange) {
    val amount = kotlin.math.abs(change.delta)
    val text = when {
        change.action == "set" -> stringResource(R.string.history_set, change.quantity, change.location)
        change.action == "initial" -> stringResource(R.string.history_initial, change.quantity, change.location)
        change.action == "move" && change.delta >= 0 -> stringResource(R.string.history_moved_in, amount, change.location)
        change.action == "move" -> stringResource(R.string.history_moved_out, amount, change.location)
        change.delta >= 0 -> stringResource(R.string.history_added, amount, change.location)
        else -> stringResource(R.string.history_taken, amount, change.location)
    }
    val time = parseServerTime(change.at)?.atZone(ZoneId.systemDefault())?.format(TIME) ?: change.at
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = { Text(if (change.note.isBlank()) time else "$time · ${change.note}") },
        trailingContent = {
            Text(
                (if (change.delta > 0) "+" else if (change.delta < 0) "−" else "±") + amount,
                style = MaterialTheme.typography.titleMedium,
                color = if (change.delta < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

/** A picture over the whole screen; pinch to zoom, tap the cross to close. [onRemove]: with a token. */
@Composable
private fun FullSizePicture(picture: Attachment, onClose: () -> Unit, onRemove: (() -> Unit)?) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        val transform = rememberTransformableState { _, zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 8f)
            offsetX += pan.x
            offsetY += pan.y
        }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("full_size")) {
            AsyncImage(
                model = picture.url,
                contentDescription = picture.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_attachment), tint = Color.White)
                }
            }
        }
    }
}
