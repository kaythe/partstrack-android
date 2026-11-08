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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.common.LocationField
import de.kaylatheil.partstrack.ui.common.LocationOption
import de.kaylatheil.partstrack.ui.common.message
import de.kaylatheil.partstrack.ui.common.normalizeLocationPath

/** What a stock dialog needs besides its own fields. */
class DialogContext(
    val part: Part,
    /** All locations, or null if they aren't loaded. */
    val locations: List<Location>?,
    val saving: Boolean,
    val error: LoadError?,
    val onSave: (StockRequest) -> Unit,
    val onDismiss: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/** Where the part is stored, with what is there. */
@Composable
private fun ownLocations(part: Part): List<LocationOption> =
    part.stock.map { LocationOption(it.location, stringResource(R.string.quantity_here, it.quantity)) }

/** The part's own locations first, then all others. */
@Composable
private fun allLocations(part: Part, locations: List<Location>?, except: String? = null): List<LocationOption> {
    val ownPaths = part.stock.map { it.location }.toSet()
    val others = locations.orEmpty().filter { it.path !in ownPaths }.map { LocationOption(it.path) }
    return (ownLocations(part) + others).filter { it.path != except }
}

/** Take, add or set the amount at a location. */
@Composable
fun StockChangeDialog(dialog: StockDialog.Change, context: DialogContext) {
    val part = context.part
    var mode by rememberSaveable { mutableStateOf(dialog.mode) }
    var location by rememberSaveable { mutableStateOf(dialog.location.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val result = stockRequest(mode, location, amount, note, part.stock)
    val problem = (result as? StockFormResult.Invalid)?.problem
    val there = part.stock.quantityAt(normalizeLocationPath(location))
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (dialog.location != null) amountFocus.requestFocus() }

    StockDialogFrame(
        title = stringResource(R.string.stock_dialog_title),
        result = result,
        context = context,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StockMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { mode = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, StockMode.entries.size),
                    enabled = !context.saving,
                    modifier = Modifier.testTag("mode_" + entry.name.lowercase()),
                ) {
                    Text(
                        stringResource(
                            when (entry) {
                                StockMode.TAKE -> R.string.mode_take
                                StockMode.ADD -> R.string.mode_add
                                StockMode.SET -> R.string.mode_set
                            },
                        ),
                    )
                }
            }
        }
        val takeOnly = mode == StockMode.TAKE
        LocationField(
            value = location,
            onValueChange = { location = it },
            label = stringResource(R.string.field_location),
            options = if (takeOnly) ownLocations(part) else allLocations(part, context.locations),
            known = context.locations?.map { it.path }?.toSet(),
            onlyOptions = takeOnly,
            error = if (problem == StockProblem.NotStoredThere && location.isNotBlank()) stringResource(R.string.problem_not_there) else null,
            enabled = !context.saving,
        )
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            label = stringResource(
                when (mode) {
                    StockMode.TAKE -> R.string.field_amount_take
                    StockMode.ADD -> R.string.field_amount_add
                    StockMode.SET -> R.string.field_amount_set
                },
            ),
            problem = problem,
            hint = there?.let { stringResource(R.string.quantity_there_now, it) },
            allowZero = mode == StockMode.SET,
            enabled = !context.saving,
            modifier = Modifier.focusRequester(amountFocus),
        )
        NoteField(note, { note = it }, enabled = !context.saving)
    }
}

/** Moves some or all of the stock at one location to another. */
@Composable
fun MoveDialog(dialog: StockDialog.Move, context: DialogContext) {
    val part = context.part
    var from by rememberSaveable { mutableStateOf(dialog.from) }
    var to by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val source = normalizeLocationPath(from)
    val result = moveRequest(source, to, amount, note, part.stock)
    val problem = (result as? StockFormResult.Invalid)?.problem
    val there = part.stock.quantityAt(source)

    StockDialogFrame(
        title = stringResource(R.string.move_dialog_title),
        result = result,
        context = context,
    ) {
        LocationField(
            value = from,
            onValueChange = { from = it },
            label = stringResource(R.string.field_from),
            options = ownLocations(part),
            known = null,
            onlyOptions = true,
            error = if (problem == StockProblem.NotStoredThere && from.isNotBlank()) stringResource(R.string.problem_not_there) else null,
            enabled = !context.saving,
            tag = "from",
        )
        LocationField(
            value = to,
            onValueChange = { to = it },
            label = stringResource(R.string.field_to),
            options = allLocations(part, context.locations, except = source),
            known = context.locations?.map { it.path }?.toSet(),
            error = if (problem == StockProblem.SameLocation) stringResource(R.string.problem_same_location) else null,
            enabled = !context.saving,
            tag = "to",
        )
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            label = stringResource(R.string.field_amount_move),
            problem = problem,
            hint = there?.let { stringResource(R.string.amount_all, it) },
            enabled = !context.saving,
        )
        NoteField(note, { note = it }, enabled = !context.saving)
    }
}

/** The dialog around the fields: Save sends [result] once it is ready; errors from the server show above the buttons. */
@Composable
private fun StockDialogFrame(
    title: String,
    result: StockFormResult,
    context: DialogContext,
    fields: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = context.onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fields()
                context.error?.let { error ->
                    Text(
                        error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("dialog_error"),
                    )
                    if (error is LoadError.Server && error.status == 401) {
                        TextButton(onClick = context.onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (result as? StockFormResult.Ready)?.let { context.onSave(it.request) } },
                enabled = result is StockFormResult.Ready && !context.saving,
                modifier = Modifier.testTag("save"),
            ) {
                if (context.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = context.onDismiss, enabled = !context.saving) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    problem: StockProblem?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null,
    allowZero: Boolean = false,
) {
    val error = when (problem) {
        StockProblem.BadAmount -> stringResource(if (allowZero) R.string.problem_bad_amount_set else R.string.problem_bad_amount)
        is StockProblem.TooMany -> stringResource(R.string.problem_too_many, problem.available)
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { !it.isWhitespace() }) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth().testTag("amount"),
    )
}

@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.field_note)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("note"),
    )
}
