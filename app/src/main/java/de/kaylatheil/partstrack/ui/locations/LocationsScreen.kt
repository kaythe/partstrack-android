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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.ui.common.ErrorView
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.LocationField
import de.kaylatheil.partstrack.ui.common.LocationOption
import de.kaylatheil.partstrack.ui.common.message

@Composable
fun LocationsScreen(
    viewModel: LocationsViewModel,
    onBack: () -> Unit,
    /** Shows the parts at a location (a full path), sublocations included. */
    onOpenLocation: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LocationsContent(
        state = state,
        actions = LocationsActions(
            onBack = onBack,
            onOpenLocation = onOpenLocation,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onErrorShown = viewModel::onErrorShown,
            onToggle = viewModel::toggle,
            onShowAdd = viewModel::showAdd,
            onShowEdit = viewModel::showEdit,
            onShowDelete = viewModel::showDelete,
            onDismissDialog = viewModel::dismissDialog,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onOpenSettings = onOpenSettings,
        ),
    )
}

class LocationsActions(
    val onBack: () -> Unit = {},
    val onOpenLocation: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
    /** Hides or shows the sublocations of a location (a path). */
    val onToggle: (String) -> Unit = {},
    /** A new location, in a parent (a path) or (null) at the top level. */
    val onShowAdd: (String?) -> Unit = {},
    val onShowEdit: (Location) -> Unit = {},
    val onShowDelete: (Location) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onSave: (LocationRequest) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/** The screen without its ViewModel, for UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsContent(state: LocationsUiState, actions: LocationsActions) {
    val snackbar = remember { SnackbarHostState() }
    val passingError = state.error?.takeIf { state.loaded }?.message()
    LaunchedEffect(passingError) {
        if (passingError != null) {
            snackbar.showSnackbar(passingError)
            actions.onErrorShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.locations_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.canWrite) {
                FloatingActionButton(onClick = { actions.onShowAdd(null) }, modifier = Modifier.testTag("add_location")) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_location))
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                !state.loaded && state.error != null ->
                    ErrorView(state.error, actions.onRetry, Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("loading"))
                }
                state.locations.isEmpty() -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.no_locations_yet), textAlign = TextAlign.Center)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("locations"),
                    contentPadding = PaddingValues(bottom = if (state.canWrite) 88.dp else 0.dp),
                ) {
                    val parents = state.parents
                    items(state.visible, key = { it.id }) { location ->
                        val toggle = when {
                            location.path !in parents -> Toggle.NONE
                            location.path in state.collapsed -> Toggle.COLLAPSED
                            else -> Toggle.EXPANDED
                        }
                        LocationRow(location, toggle, state.canWrite, actions)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    LocationDialogs(state, actions)
}

/** Whether a row has sublocations, and whether they show. */
private enum class Toggle { NONE, EXPANDED, COLLAPSED }

@Composable
private fun LocationRow(location: Location, toggle: Toggle, canWrite: Boolean, actions: LocationsActions) {
    ListItem(
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width((24 * location.depth).dp))
                if (toggle == Toggle.NONE) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    val expanded = toggle == Toggle.EXPANDED
                    IconButton(onClick = { actions.onToggle(location.path) }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(
                                if (expanded) R.string.collapse_location else R.string.expand_location,
                                location.path,
                            ),
                        )
                    }
                }
            }
        },
        headlineContent = { Text(location.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (location.description.isNotBlank()) {
            { Text(location.description, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (location.partCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(location.totalQuantity.toString(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            pluralStringResource(R.plurals.location_parts, location.partCount, location.partCount),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (canWrite) RowMenu(location, actions)
            }
        },
        modifier = Modifier.clickable { actions.onOpenLocation(location.path) },
    )
}

@Composable
private fun RowMenu(location: Location, actions: LocationsActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.location_menu, location.path))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_location_inside)) },
                onClick = { open = false; actions.onShowAdd(location.path) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_location)) },
                onClick = { open = false; actions.onShowEdit(location) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_location)) },
                onClick = { open = false; actions.onShowDelete(location) },
            )
        }
    }
}

@Composable
private fun LocationDialogs(state: LocationsUiState, actions: LocationsActions) {
    when (val dialog = state.dialog) {
        null -> {}
        is LocationDialog.Add -> AddLocationDialog(dialog, state, actions)
        is LocationDialog.Edit -> EditLocationDialog(dialog.location, state, actions)
        is LocationDialog.Delete -> DeleteLocationDialog(dialog.location, state, actions)
    }
}

@Composable
private fun AddLocationDialog(dialog: LocationDialog.Add, state: LocationsUiState, actions: LocationsActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var parent by rememberSaveable { mutableStateOf(dialog.parent.orEmpty()) }
    var description by rememberSaveable { mutableStateOf("") }
    val result = newLocation(name, parent, description)
    LocationDialogFrame(
        title = stringResource(R.string.add_location),
        confirm = stringResource(R.string.save),
        enabled = result is LocationFormResult.Ready,
        onConfirm = { (result as? LocationFormResult.Ready)?.let { actions.onSave(it.request) } },
        state = state,
        actions = actions,
    ) {
        NameField(name, { name = it }, (result as? LocationFormResult.Invalid)?.problem, !state.saving)
        LocationField(
            value = parent,
            onValueChange = { parent = it },
            label = stringResource(R.string.field_parent),
            options = state.locations.map { LocationOption(it.path) },
            known = state.locations.map { it.path }.toSet(),
            enabled = !state.saving,
            tag = "parent",
        )
        DescriptionField(description, { description = it }, !state.saving)
    }
}

@Composable
private fun EditLocationDialog(location: Location, state: LocationsUiState, actions: LocationsActions) {
    var name by rememberSaveable { mutableStateOf(location.name) }
    var parent by rememberSaveable { mutableStateOf(location.parent.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(location.description) }
    val result = locationChanges(location, name, parent, description)
    val problem = (result as? LocationFormResult.Invalid)?.problem
    LocationDialogFrame(
        title = stringResource(R.string.edit_location_title),
        confirm = stringResource(R.string.save),
        enabled = result !is LocationFormResult.Invalid,
        onConfirm = {
            when (result) {
                is LocationFormResult.Ready -> actions.onSave(result.request)
                LocationFormResult.Unchanged -> actions.onDismissDialog()
                is LocationFormResult.Invalid -> {}
            }
        },
        state = state,
        actions = actions,
    ) {
        NameField(name, { name = it }, problem, !state.saving)
        LocationField(
            value = parent,
            onValueChange = { parent = it },
            label = stringResource(R.string.field_parent),
            // Not into itself, nor anywhere inside it.
            options = state.locations.filterNot { isWithin(it.path, location) }.map { LocationOption(it.path) },
            known = state.locations.map { it.path }.toSet(),
            error = if (problem == LocationProblem.ParentInside) stringResource(R.string.problem_parent_inside) else null,
            enabled = !state.saving,
            tag = "parent",
        )
        Text(
            stringResource(R.string.parent_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DescriptionField(description, { description = it }, !state.saving)
    }
}

@Composable
private fun DeleteLocationDialog(location: Location, state: LocationsUiState, actions: LocationsActions) {
    LocationDialogFrame(
        title = stringResource(R.string.delete_location_title, location.name),
        confirm = stringResource(R.string.delete),
        enabled = true,
        onConfirm = actions.onDelete,
        state = state,
        actions = actions,
        destructive = true,
    ) {
        Text(stringResource(R.string.delete_location_text, location.path))
    }
}

/** A dialog with Cancel and [confirm]; the server's error shows above the buttons, with the settings when the token is the problem. */
@Composable
private fun LocationDialogFrame(
    title: String,
    confirm: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    state: LocationsUiState,
    actions: LocationsActions,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                state.dialogError?.let { error ->
                    Text(
                        error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("dialog_error"),
                    )
                    if (error is LoadError.Server && error.status == 401) {
                        TextButton(onClick = actions.onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled && !state.saving, modifier = Modifier.testTag("confirm")) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDialog, enabled = !state.saving) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, problem: LocationProblem?, enabled: Boolean) {
    val error = if (problem == LocationProblem.NameHasSlash) stringResource(R.string.problem_name_slash) else null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.field_name)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("location_name"),
    )
}

@Composable
private fun DescriptionField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.field_description)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("location_description"),
    )
}
