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

package de.kaylatheil.partstrack.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.kaylatheil.partstrack.R

/** A location to suggest; [detail] is shown next to it (e.g. what is stored there). */
data class LocationOption(val path: String, val detail: String? = null)

/**
 * A field for a location's full path: suggests the [options] that match what
 * is typed, and takes any other path, which the server then creates. Says so
 * when [known] (all existing paths, null if unknown) doesn't have it.
 * [onlyOptions] limits it to the options (e.g. where a part is stored), still
 * typed or chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<LocationOption>,
    known: Set<String>?,
    modifier: Modifier = Modifier,
    onlyOptions: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    /** For UI tests, on the text field itself. */
    tag: String = "location",
) {
    var expanded by remember { mutableStateOf(false) }
    val byPath = options.associateBy { it.path }
    val matches = matchingLocations(options.map { it.path }, value).let { found ->
        // The exact choice is already in the field; the whole list stays offered then.
        if (found.size == 1 && found[0] == normalizeLocationPath(value)) options.map { it.path } else found
    }
    val path = normalizeLocationPath(value)
    val isNew = !onlyOptions && path.isNotEmpty() && known != null && path !in known
    ExposedDropdownMenuBox(
        expanded = expanded && enabled && matches.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                // Only while something is typed: the whole list would cover the rest
                // of a dialog, its buttons too. Tapping the field still shows it.
                expanded = it.isNotBlank()
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = when {
                error != null -> { { Text(error) } }
                isNew -> { { Text(stringResource(R.string.location_new)) } }
                else -> null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .onFocusChanged { if (!it.isFocused) expanded = false }
                .testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded && enabled && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { match ->
                val detail = byPath[match]?.detail
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(match.substringAfterLast('/'))
                            val parent = match.substringBeforeLast('/', "")
                            val line = listOfNotNull(parent.ifEmpty { null }, detail).joinToString(" · ")
                            if (line.isNotEmpty()) {
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        onValueChange(match)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
