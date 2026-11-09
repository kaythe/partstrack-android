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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.ui.common.ErrorView
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.LocationField
import de.kaylatheil.partstrack.ui.common.LocationOption
import de.kaylatheil.partstrack.ui.common.SuggestField
import de.kaylatheil.partstrack.ui.common.message

@Composable
fun EditPartScreen(
    viewModel: EditPartViewModel,
    /** Saved: show the part with this id. */
    onSaved: (Int) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedPartId) {
        state.savedPartId?.let(onSaved)
    }
    EditPartContent(
        state = state,
        actions = EditPartActions(
            onChange = viewModel::update,
            onAddTypedTag = viewModel::addTypedTag,
            onAddTag = viewModel::addTag,
            onRemoveTag = viewModel::removeTag,
            onAddAttribute = viewModel::addAttribute,
            onChangeAttribute = viewModel::updateAttribute,
            onRemoveAttribute = viewModel::removeAttribute,
            onSave = viewModel::save,
            onRetry = viewModel::retry,
            onSaveErrorShown = viewModel::onSaveErrorShown,
            onClose = onClose,
            onOpenSettings = onOpenSettings,
        ),
    )
}

class EditPartActions(
    val onChange: ((PartForm) -> PartForm) -> Unit = {},
    val onAddTypedTag: () -> Unit = {},
    val onAddTag: (String) -> Unit = {},
    val onRemoveTag: (String) -> Unit = {},
    val onAddAttribute: () -> Unit = {},
    val onChangeAttribute: (row: Int, AttributeRow) -> Unit = { _, _ -> },
    val onRemoveAttribute: (row: Int) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSaveErrorShown: () -> Unit = {},
    /** Leave without saving (after asking, if there is input to lose). */
    val onClose: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/** The screen without its ViewModel, for UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPartContent(state: EditPartUiState, actions: EditPartActions) {
    val snackbar = remember { SnackbarHostState() }
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.hasChanges) askDiscard = true else actions.onClose() }
    BackHandler(enabled = state.hasChanges && !state.saving) { askDiscard = true }

    val error = state.saveError
    val errorMessage = error?.message()
    val settingsLabel = stringResource(R.string.open_settings).takeIf { error is LoadError.Server && error.status == 401 }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val result = snackbar.showSnackbar(errorMessage, actionLabel = settingsLabel, withDismissAction = true)
            actions.onSaveErrorShown()
            if (result == SnackbarResult.ActionPerformed) actions.onOpenSettings()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_part_title else R.string.edit_part_title)) },
                navigationIcon = {
                    IconButton(onClick = leave, enabled = !state.saving) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = actions.onSave,
                            enabled = !state.loading && state.loadError == null,
                            modifier = Modifier.testTag("save"),
                        ) { Text(stringResource(R.string.save)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loadError != null -> ErrorView(state.loadError, actions.onRetry, Modifier.fillMaxSize())
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("loading"))
                else -> Fields(state, actions)
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_text)) },
            confirmButton = {
                TextButton(onClick = { askDiscard = false; actions.onClose() }, modifier = Modifier.testTag("discard")) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}

@Composable
private fun Fields(state: EditPartUiState, actions: EditPartActions) {
    val form = state.form
    val problems = state.problems
    val enabled = !state.saving
    val change = actions.onChange
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("form"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val nameError = when {
            FormProblem.NameMissing in problems -> stringResource(R.string.problem_name_missing)
            FormProblem.NameStartsWithHash in problems -> stringResource(R.string.problem_name_hash)
            else -> null
        }
        TextInput(form.name, { v -> change { it.copy(name = v) } }, stringResource(R.string.field_name), "name", enabled, nameError)
        TextInput(form.description, { v -> change { it.copy(description = v) } }, stringResource(R.string.field_description), "description", enabled)
        TextInput(form.manufacturer, { v -> change { it.copy(manufacturer = v) } }, stringResource(R.string.field_manufacturer), "manufacturer", enabled)
        TextInput(form.mpn, { v -> change { it.copy(mpn = v) } }, stringResource(R.string.field_mpn), "mpn", enabled, capitalize = false)
        SuggestField(
            value = form.category,
            onValueChange = { v -> change { it.copy(category = v) } },
            label = stringResource(R.string.field_category),
            suggestions = state.categorySuggestions,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            tag = "category",
        )
        TextInput(form.packageName, { v -> change { it.copy(packageName = v) } }, stringResource(R.string.field_package), "package", enabled, capitalize = false)
        OutlinedTextField(
            value = form.minQuantity,
            onValueChange = { v -> change { it.copy(minQuantity = v.filter { c -> !c.isWhitespace() }) } },
            label = { Text(stringResource(R.string.field_min_quantity)) },
            singleLine = true,
            enabled = enabled,
            isError = FormProblem.BadMinQuantity in problems,
            supportingText = {
                Text(
                    stringResource(
                        if (FormProblem.BadMinQuantity in problems) R.string.problem_bad_min_quantity else R.string.min_quantity_help,
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("min_quantity"),
        )

        Heading(stringResource(R.string.section_tags))
        Tags(state, actions)

        Heading(stringResource(R.string.section_attributes))
        Attributes(form.attributes, problems, enabled, actions)

        if (state.isNew) {
            Heading(stringResource(R.string.section_initial_stock))
            InitialStock(state, enabled, change)
        }

        Heading(stringResource(R.string.section_notes))
        OutlinedTextField(
            value = form.notes,
            onValueChange = { v -> change { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().testTag("notes"),
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    enabled: Boolean,
    error: String? = null,
    capitalize: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = capitalize,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Tags(state: EditPartUiState, actions: EditPartActions) {
    val form = state.form
    val enabled = !state.saving
    if (form.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            form.tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { actions.onRemoveTag(tag) },
                    enabled = enabled,
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_tag, tag), Modifier.size(18.dp))
                    },
                )
            }
        }
    }
    val present = form.tags.map(::nocase).toSet()
    SuggestField(
        value = form.tagInput,
        onValueChange = { text ->
            actions.onChange { it.copy(tagInput = text) }
            // A comma ends a tag, as in the web interface.
            if (text.endsWith(',')) actions.onAddTypedTag()
        },
        onPick = actions.onAddTag,
        label = stringResource(R.string.field_add_tag),
        suggestions = state.tagSuggestions.filter { nocase(it) !in present },
        enabled = enabled,
        supportingText = stringResource(R.string.tags_help),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.onAddTypedTag() }),
        tag = "tag_input",
    )
}

@Composable
private fun Attributes(rows: List<AttributeRow>, problems: List<FormProblem>, enabled: Boolean, actions: EditPartActions) {
    rows.forEachIndexed { index, row ->
        val keyError = when {
            FormProblem.AttributeKeyMissing(index) in problems -> stringResource(R.string.problem_attribute_name_missing)
            FormProblem.AttributeKeyRepeated(index) in problems -> stringResource(R.string.problem_attribute_name_repeated)
            else -> null
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.key,
                onValueChange = { actions.onChangeAttribute(index, row.copy(key = it)) },
                label = { Text(stringResource(R.string.field_attribute_name)) },
                singleLine = true,
                enabled = enabled,
                isError = keyError != null,
                supportingText = keyError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f).testTag("attribute_key_$index"),
            )
            OutlinedTextField(
                value = row.value,
                onValueChange = { actions.onChangeAttribute(index, row.copy(value = it)) },
                label = { Text(stringResource(R.string.field_attribute_value)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f).testTag("attribute_value_$index"),
            )
            IconButton(onClick = { actions.onRemoveAttribute(index) }, enabled = enabled, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_attribute))
            }
        }
    }
    TextButton(onClick = actions.onAddAttribute, enabled = enabled, modifier = Modifier.testTag("add_attribute")) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.add_attribute))
    }
    if (rows.isEmpty()) {
        Text(
            stringResource(R.string.attributes_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InitialStock(state: EditPartUiState, enabled: Boolean, change: ((PartForm) -> PartForm) -> Unit) {
    val form = state.form
    val problems = state.problems
    LocationField(
        value = form.location,
        onValueChange = { v -> change { it.copy(location = v) } },
        label = stringResource(R.string.field_location),
        options = state.locations.orEmpty().map { LocationOption(it.path) },
        known = state.locations?.map { it.path }?.toSet(),
        error = if (FormProblem.LocationMissing in problems) stringResource(R.string.problem_location_missing) else null,
        enabled = enabled,
    )
    val quantityError = when {
        FormProblem.QuantityMissing in problems -> stringResource(R.string.problem_quantity_missing)
        FormProblem.BadQuantity in problems -> stringResource(R.string.problem_bad_amount_set)
        else -> null
    }
    OutlinedTextField(
        value = form.quantity,
        onValueChange = { v -> change { it.copy(quantity = v.filter { c -> !c.isWhitespace() }) } },
        label = { Text(stringResource(R.string.field_quantity)) },
        singleLine = true,
        enabled = enabled,
        isError = quantityError != null,
        supportingText = { Text(quantityError ?: stringResource(R.string.initial_stock_help)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("quantity"),
    )
}
