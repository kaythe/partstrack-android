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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The form with its state held by the test, changed as the ViewModel would. */
@RunWith(AndroidJUnit4::class)
class EditPartContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(EditPartUiState(isNew = true))
    private val events = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            PartstrackTheme {
                EditPartContent(
                    state,
                    EditPartActions(
                        onChange = { change -> state = state.copy(form = change(state.form)) },
                        onAddTypedTag = { state = state.copy(form = state.form.copy(tags = state.form.allTags, tagInput = "")) },
                        onAddTag = { tag -> state = state.copy(form = state.form.copy(tags = addTags(state.form.tags, tag), tagInput = "")) },
                        onRemoveTag = { tag -> state = state.copy(form = state.form.copy(tags = state.form.tags - tag)) },
                        onAddAttribute = { state = state.copy(form = state.form.copy(attributes = state.form.attributes + AttributeRow())) },
                        onChangeAttribute = { row, attribute ->
                            state = state.copy(
                                form = state.form.copy(
                                    attributes = state.form.attributes.mapIndexed { i, old -> if (i == row) attribute else old },
                                ),
                            )
                        },
                        onSave = { events += "save" },
                        onClose = { events += "close" },
                        onOpenSettings = { events += "settings" },
                        onSaveErrorShown = { state = state.copy(saveError = null) },
                    ),
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("form").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun newPart() {
        state = EditPartUiState(isNew = true, tagSuggestions = listOf("smd", "tht"), categorySuggestions = listOf("IC"))
        show()
        compose.onNodeWithText("New part").assertIsDisplayed()
        compose.onNodeWithTag("name").performTextInput("LM317")
        compose.onNodeWithTag("category").performTextInput("I")
        compose.onNodeWithText("IC").performClick() // a suggestion
        scrollTo("tag_input")
        compose.onNodeWithTag("tag_input").performTextInput("power,")
        compose.onNodeWithTag("tag_input").performTextInput("tht")
        compose.onNodeWithTag("tag_input").performImeAction()
        scrollTo("quantity")
        compose.onNodeWithTag("location").performTextInput("Shelf/Box B")
        compose.onNodeWithTag("quantity").performTextInput("6")
        assertEquals(
            PartForm(name = "LM317", category = "IC", tags = listOf("power", "tht"), location = "Shelf/Box B", quantity = "6"),
            state.form,
        )
        compose.onNodeWithTag("save").performClick()
        assertEquals(listOf("save"), events)
    }

    @Test
    fun tagsCanBeRemoved() {
        state = EditPartUiState(isNew = true, form = PartForm(tags = listOf("smd", "tht")))
        show()
        compose.onNodeWithContentDescription("Remove smd").performClick()
        assertEquals(listOf("tht"), state.form.tags)
    }

    @Test
    fun attributes() {
        show()
        scrollTo("add_attribute")
        compose.onNodeWithTag("add_attribute").performClick()
        compose.onNodeWithTag("attribute_key_0").performTextInput("resistance")
        compose.onNodeWithTag("attribute_value_0").performTextInput("10k")
        assertEquals(listOf(AttributeRow("resistance", "10k")), state.form.attributes)
    }

    @Test
    fun problemsAreShownAtTheirFields() {
        state = EditPartUiState(
            isNew = true,
            form = PartForm(minQuantity = "x", location = "Shelf", attributes = listOf(AttributeRow("", "1%"))),
            showProblems = true,
        )
        show()
        compose.onNodeWithText("Enter a name.").assertIsDisplayed()
        compose.onNodeWithText("Enter a whole number, or leave it empty.").assertIsDisplayed()
        compose.onNodeWithTag("form").performScrollToNode(hasText("Enter a name for this value."))
        compose.onNodeWithTag("form").performScrollToNode(hasText("Enter how many are there."))
    }

    @Test
    fun editingAPartHasNoInitialStock() {
        state = EditPartUiState(isNew = false, loading = false, form = PartForm(name = "10k resistor"))
        show()
        compose.onNodeWithText("Edit part").assertIsDisplayed()
        compose.onNodeWithTag("form").performScrollToNode(hasTestTag("notes"))
        compose.onNodeWithTag("quantity").assertDoesNotExist()
    }

    @Test
    fun closingWithoutChanges() {
        show()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("close"), events)
    }

    @Test
    fun closingWithChangesAsksFirst() {
        state = EditPartUiState(isNew = true, form = PartForm(name = "typed"))
        show()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        assertEquals(emptyList<String>(), events)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("discard").performClick()
        assertEquals(listOf("close"), events)
    }

    @Test
    fun saveErrorWithTheWayToTheSettings() {
        state = EditPartUiState(isNew = true, form = PartForm(name = "x"), saveError = LoadError.Server(401, "unknown or revoked API token"))
        show()
        compose.onNodeWithText("unknown or revoked API token").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(listOf("settings"), events)
        assertEquals("x", state.form.name) // the input stays
    }

    @Test
    fun loadingAndError() {
        state = EditPartUiState(isNew = false)
        show()
        compose.onNodeWithTag("loading").assertIsDisplayed()
        state = EditPartUiState(isNew = false, loading = false, loadError = LoadError.Unreachable(null))
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }
}
