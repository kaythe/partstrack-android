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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * What to suggest for [text]: the [suggestions] that contain it (ignoring
 * case), apart from one that is exactly what is typed already. All of them
 * for an empty text.
 */
fun matchingSuggestions(suggestions: List<String>, text: String): List<String> {
    val wanted = text.trim()
    if (wanted.isEmpty()) return suggestions
    return suggestions.filter { it.contains(wanted, ignoreCase = true) && it != wanted }
}

/**
 * A text field that suggests [suggestions] matching what is typed; any other
 * text is fine too. Choosing a suggestion calls [onPick] (by default: puts it
 * in the field).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit = onValueChange,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    supportingText: String? = null,
    /** For UI tests, on the text field itself. */
    tag: String = "suggest",
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = matchingSuggestions(suggestions, value)
    val open = expanded && enabled && matches.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                // Emptied (e.g. a tag was just added): don't cover the form with every
                // suggestion; tapping the field still shows them.
                expanded = it.isNotBlank()
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = if (suggestions.isNotEmpty()) {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) }
            } else {
                null
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .testTag(tag),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            matches.forEach { match ->
                DropdownMenuItem(
                    text = { Text(match) },
                    onClick = {
                        onPick(match)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
