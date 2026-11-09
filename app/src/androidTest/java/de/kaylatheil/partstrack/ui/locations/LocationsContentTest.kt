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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Patch
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(LocationsUiState())
    private val events = mutableListOf<String>()

    private val cabinet = Location(1, "Cabinet", "Cabinet", null, "", 0, 0)
    private val drawer = Location(2, "Drawer 1", "Cabinet/Drawer 1", "Cabinet", "Resistors", 1, 97)
    private val shelf = Location(3, "Shelf", "Shelf", null, "", 3, 5)
    private val tree = listOf(cabinet, drawer, shelf)

    private fun show() {
        compose.setContent {
            PartstrackTheme {
                LocationsContent(
                    state,
                    LocationsActions(
                        onBack = { events += "back" },
                        onOpenLocation = { events += "open $it" },
                        onRetry = { events += "retry" },
                        onToggle = { path ->
                            events += "toggle $path"
                            state = state.copy(collapsed = if (path in state.collapsed) state.collapsed - path else state.collapsed + path)
                        },
                        onShowAdd = { events += "add $it" },
                        onShowEdit = { events += "edit ${it.path}" },
                        onShowDelete = { events += "delete ${it.path}" },
                        onDismissDialog = { events += "dismiss" },
                        onSave = { events += "save $it" },
                        onDelete = { events += "confirm delete" },
                        onOpenSettings = { events += "settings" },
                    ),
                )
            }
        }
    }

    @Test
    fun tree() {
        state = LocationsUiState(locations = tree, loaded = true)
        show()
        compose.onNodeWithText("Drawer 1").assertIsDisplayed()
        compose.onNodeWithText("Resistors").assertIsDisplayed()
        compose.onNodeWithText("97").assertIsDisplayed()
        compose.onNodeWithText("1 part").assertIsDisplayed()
        compose.onNodeWithText("3 parts").assertIsDisplayed()
        compose.onNodeWithText("Drawer 1").performClick()
        assertEquals(listOf("open Cabinet/Drawer 1"), events)
        // Without a token, nothing to change.
        compose.onNodeWithTag("add_location").assertDoesNotExist()
        compose.onNodeWithContentDescription("More for Shelf").assertDoesNotExist()
    }

    @Test
    fun menus() {
        state = LocationsUiState(locations = tree, loaded = true, canWrite = true)
        show()
        compose.onNodeWithTag("add_location").performClick()
        compose.onNodeWithContentDescription("More for Cabinet/Drawer 1").performClick()
        compose.onNodeWithText("Add a location inside…").performClick()
        compose.onNodeWithContentDescription("More for Cabinet/Drawer 1").performClick()
        compose.onNodeWithText("Edit…").performClick()
        compose.onNodeWithContentDescription("More for Cabinet/Drawer 1").performClick()
        compose.onNodeWithText("Delete…").performClick()
        assertEquals(
            listOf("add null", "add Cabinet/Drawer 1", "edit Cabinet/Drawer 1", "delete Cabinet/Drawer 1"),
            events,
        )
    }

    @Test
    fun addDialog() {
        state = LocationsUiState(locations = tree, loaded = true, canWrite = true, dialog = LocationDialog.Add("Cabinet"))
        show()
        compose.onNodeWithTag("confirm").assertIsNotEnabled() // no name yet
        compose.onNodeWithTag("location_name").performTextInput("Drawer/4")
        compose.onNodeWithText("Names can't contain /", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("location_name").performTextClearance()
        compose.onNodeWithTag("location_name").performTextInput("Drawer 4")
        compose.onNodeWithTag("location_description").performTextInput("Capacitors")
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("save ${LocationRequest.Add("Cabinet/Drawer 4", "Capacitors")}"), events)
    }

    @Test
    fun editDialog() {
        state = LocationsUiState(locations = tree, loaded = true, canWrite = true, dialog = LocationDialog.Edit(cabinet))
        show()
        compose.onNodeWithTag("parent").performTextInput("Cabinet/Drawer 1")
        compose.onNodeWithText("A location can't be moved into itself.").assertIsDisplayed()
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
        compose.onNodeWithTag("parent").performTextClearance()
        compose.onNodeWithTag("parent").performTextInput("Room")
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("save ${LocationRequest.Change(1, parent = Patch.Set("Room"))}"), events)
    }

    @Test
    fun editWithoutChangesJustCloses() {
        state = LocationsUiState(locations = tree, loaded = true, canWrite = true, dialog = LocationDialog.Edit(drawer))
        show()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun deleteRefused() {
        state = LocationsUiState(
            locations = tree, loaded = true, canWrite = true, dialog = LocationDialog.Delete(drawer),
            dialogError = LoadError.Server(400, "location 'Drawer 1' still holds 1 part(s); move them first"),
        )
        show()
        compose.onNodeWithText("Delete Drawer 1?").assertIsDisplayed()
        compose.onNodeWithText("location 'Drawer 1' still holds 1 part(s); move them first").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("confirm delete"), events)
    }

    @Test
    fun tokenRefusedInADialog() {
        state = LocationsUiState(
            locations = tree, loaded = true, canWrite = true, dialog = LocationDialog.Add(null),
            dialogError = LoadError.Server(401, "unknown or revoked API token"),
        )
        show()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun emptyAndError() {
        state = LocationsUiState(loaded = true)
        show()
        compose.onNodeWithText("No locations yet", substring = true).assertIsDisplayed()
        state = LocationsUiState(error = LoadError.Unreachable(null))
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun collapsing() {
        state = LocationsUiState(locations = tree, loaded = true)
        show()
        compose.onNodeWithContentDescription("Show what is in Shelf").assertDoesNotExist() // nothing inside Shelf
        compose.onNodeWithContentDescription("Hide what is in Cabinet").performClick()
        compose.onNodeWithText("Drawer 1").assertDoesNotExist()
        compose.onNodeWithText("Cabinet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show what is in Cabinet").performClick()
        compose.onNodeWithText("Drawer 1").assertIsDisplayed()
        assertEquals(listOf("toggle Cabinet", "toggle Cabinet"), events)
    }

    @Test
    fun suggestionsDontCoverTheDialog() {
        state = LocationsUiState(locations = tree, loaded = true, canWrite = true, dialog = LocationDialog.Add(null))
        show()
        compose.onNodeWithTag("location_name").performTextInput("Drawer 4")
        compose.onNodeWithTag("parent").performTextInput("Cab")
        // Going on to the next field closes the suggestions, which would otherwise cover it and the buttons.
        compose.onNodeWithTag("location_description").performClick()
        compose.onNodeWithTag("location_description").performTextInput("Capacitors")
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("save ${LocationRequest.Add("Cab/Drawer 4", "Capacitors")}"), events)
    }
}
