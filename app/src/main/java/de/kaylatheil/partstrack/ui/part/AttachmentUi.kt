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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.AttachmentKind
import de.kaylatheil.partstrack.data.api.Upload
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.message
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDateTime
import okio.source

/** Starts taking a photo or choosing a file; what comes of it goes to the upload given to [rememberAttachmentPickers]. */
class AttachmentPickers(val takePhoto: () -> Unit, val chooseFile: () -> Unit)

/**
 * The camera app and the system's file picker, for attachments. A photo is
 * taken at full size into the app's cache (shared with the camera app
 * through the FileProvider) and deleted when [onUpload]'s cleanup runs.
 */
@Composable
fun rememberAttachmentPickers(
    onUpload: (Upload, AttachmentKind?, cleanup: () -> Unit) -> Unit,
    onCameraRefused: () -> Unit,
): AttachmentPickers {
    val context = LocalContext.current
    val upload by rememberUpdatedState(onUpload)
    val refused by rememberUpdatedState(onCameraRefused)
    // Kept across the camera app possibly ending this process meanwhile.
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = photoPath?.let(::File) ?: return@rememberLauncherForActivityResult
        photoPath = null
        if (taken && file.length() > 0) {
            upload(Upload(file.name, "image/jpeg", file.length()) { file.source() }, AttachmentKind.IMAGE) { file.delete() }
        } else {
            file.delete()
        }
    }
    fun startCamera() {
        val folder = File(context.cacheDir, PHOTO_FOLDER).apply { mkdirs() }
        val file = File(folder, photoFileName(LocalDateTime.now()))
        photoPath = file.path
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
    }
    // The app declares the camera permission (for scanning), so Android lets
    // it start the camera app only once the permission is given.
    val askForCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else refused()
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val chosen = uploadOf(context, uri)
            upload(chosen, kindForUpload(chosen.mediaType)) {}
        }
    }
    return remember {
        AttachmentPickers(
            takePhoto = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    askForCamera.launch(Manifest.permission.CAMERA)
                }
            },
            chooseFile = { openDocument.launch(arrayOf("*/*")) },
        )
    }
}

/** The cache folder for photos taken for upload (res/xml/file_paths.xml shares it with the camera app). */
private const val PHOTO_FOLDER = "photos"

/** A chosen document as an upload: its name, type and size as the system knows them. */
private fun uploadOf(context: Context, uri: Uri): Upload {
    val resolver = context.contentResolver
    var name = "file"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            if (!cursor.isNull(0)) name = cursor.getString(0)
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    return Upload(name, resolver.getType(uri), size) {
        (resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())).source()
    }
}

/** Take photo, choose file, add link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentButtons(enabled: Boolean, onTakePhoto: () -> Unit, onChooseFile: () -> Unit, onAddLink: () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.testTag("take_photo")) {
            Text(stringResource(R.string.take_photo))
        }
        OutlinedButton(onClick = onChooseFile, enabled = enabled, modifier = Modifier.testTag("choose_file")) {
            Text(stringResource(R.string.choose_file))
        }
        OutlinedButton(onClick = onAddLink, modifier = Modifier.testTag("add_link")) {
            Text(stringResource(R.string.add_link))
        }
    }
}

/** An upload's progress with Cancel; a failed one says why and offers to try again. */
@Composable
fun UploadCard(upload: UploadProgress, onCancel: () -> Unit, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("upload")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (upload.error == null) R.string.uploading else R.string.upload_failed, upload.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (upload.error == null) {
                val fraction = upload.fraction
                if (fraction == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.upload_progress, megabytes(upload.sent), megabytes(upload.total)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(upload.error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_upload")) {
                    Text(stringResource(if (upload.error == null) R.string.cancel else R.string.give_up))
                }
                if (upload.error != null) {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_upload")) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)

/** "Store a copy" for links, and "Remove…", for one attachment. */
@Composable
fun AttachmentMenu(attachment: Attachment, onStoreCopy: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.attachment_menu, attachment.name.ifBlank { attachment.url }))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!attachment.stored) {
                DropdownMenuItem(text = { Text(stringResource(R.string.store_copy)) }, onClick = { open = false; onStoreCopy() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.remove_attachment)) }, onClick = { open = false; onRemove() })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkDialog(
    saving: Boolean,
    error: LoadError?,
    onAdd: (url: String, kind: AttachmentKind?, download: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<AttachmentKind?>(null) }
    var download by rememberSaveable { mutableStateOf(true) }
    val link = linkOrNull(url)
    val kinds = listOf(null, AttachmentKind.DATASHEET, AttachmentKind.IMAGE, AttachmentKind.OTHER)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_link)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.field_link)) },
                    singleLine = true,
                    enabled = !saving,
                    isError = url.isNotBlank() && link == null,
                    supportingText = { Text(stringResource(if (url.isNotBlank() && link == null) R.string.problem_link else R.string.link_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("link"),
                )
                Text(stringResource(R.string.field_kind), style = MaterialTheme.typography.labelLarge)
                // Chips, which wrap: four segments are too narrow for their words on a phone.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { entry ->
                        FilterChip(
                            selected = kind == entry,
                            onClick = { kind = entry },
                            enabled = !saving,
                            label = {
                                Text(
                                    stringResource(
                                        when (entry) {
                                            null -> R.string.kind_auto
                                            AttachmentKind.DATASHEET -> R.string.kind_datasheet
                                            AttachmentKind.IMAGE -> R.string.kind_picture
                                            AttachmentKind.OTHER -> R.string.kind_other
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = download, onCheckedChange = { download = it }, enabled = !saving, modifier = Modifier.testTag("store_copy"))
                    Text(stringResource(R.string.store_copy_help), style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (it is LoadError.Server && it.status == 401) {
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { link?.let { onAdd(it, kind, download) } },
                enabled = link != null && !saving,
                modifier = Modifier.testTag("confirm_link"),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun RemoveAttachmentDialog(attachment: Attachment, busy: Boolean, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_attachment_title, attachment.name.ifBlank { attachment.url })) },
        text = { Text(stringResource(if (attachment.stored) R.string.remove_stored_text else R.string.remove_link_text)) },
        confirmButton = {
            TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.testTag("confirm_remove")) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}
