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

package de.kaylatheil.partstrack.ui.parts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.TagCount
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(PartsUiState())
    private val events = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            PartstrackTheme {
                PartsContent(
                    state,
                    PartsActions(
                        onQueryChange = { events += "query $it" },
                        onAddPart = { events += "add part" },
                        onTagChange = { events += "tag $it" },
                        onLocationChange = { events += "location $it" },
                        onLowOnlyChange = { events += "low $it" },
                        onClearFilters = { events += "clear" },
                        onRetry = { events += "retry" },
                        onErrorShown = { events += "error shown" },
                    ),
                )
            }
        }
    }

    private fun part(id: Int, name: String, description: String, packageName: String, total: Int, low: Boolean) = Part(
        id = id, name = name, description = description, manufacturer = "", mpn = "", category = "",
        packageName = packageName, notes = "", minQuantity = null, totalQuantity = total, low = low,
        stock = emptyList(), tags = emptyList(), attributes = emptyMap(), attachments = emptyList(),
        createdAt = "", updatedAt = "", webUrl = "",
    )

    private val parts = listOf(
        part(1, "10k resistor", "Thick film resistor, 1%", "0603", 117, low = false),
        part(2, "NE555", "", "DIP-8", 3, low = true),
    )

    @Test
    fun rows() {
        state = PartsUiState(loaded = true, parts = parts)
        show()
        compose.onNodeWithText("10k resistor").assertIsDisplayed()
        compose.onNodeWithText("Thick film resistor, 1%").assertIsDisplayed() // the description, if there is one
        compose.onNodeWithText("117").assertIsDisplayed()
        compose.onNodeWithText("DIP-8").assertIsDisplayed() // otherwise the package
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("low").assertIsDisplayed() // only for the NE555
    }

    @Test
    fun loadingAtFirst() {
        show()
        compose.onNodeWithTag("loading").assertIsDisplayed()
    }

    @Test
    fun emptyInventory() {
        state = PartsUiState(loaded = true)
        show()
        compose.onNodeWithText("No parts yet.").assertIsDisplayed()
        compose.onNodeWithText("Clear search and filters").assertDoesNotExist()
    }

    @Test
    fun nothingMatches() {
        state = PartsUiState(loaded = true, filter = PartsFilter(query = "xyz"))
        show()
        compose.onNodeWithText("No parts match.").assertIsDisplayed()
        compose.onNodeWithText("Clear search and filters").performClick()
        assertEquals(listOf("clear"), events)
    }

    @Test
    fun errorBeforeAnythingLoaded() {
        state = PartsUiState(error = LoadError.Unreachable("Connection refused"))
        show()
        compose.onNodeWithText("Can't reach the server", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Connection refused").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun errorOverAList() {
        state = PartsUiState(loaded = true, parts = parts, error = LoadError.Server(400, "'resistance>abc': 'abc' is not a number"))
        show()
        compose.onNodeWithText("'resistance>abc': 'abc' is not a number").assertIsDisplayed() // as a snackbar
        compose.onNodeWithText("10k resistor").assertIsDisplayed() // over the list
    }

    @Test
    fun search() {
        state = PartsUiState(loaded = true, parts = parts)
        show()
        compose.onNodeWithTag("search").performTextInput("ne5")
        assertEquals(listOf("query ne5"), events)
    }

    @Test
    fun filters() {
        state = PartsUiState(
            loaded = true,
            parts = parts,
            options = FilterOptions(
                tags = listOf(TagCount("smd", 2), TagCount("unused", 0)),
                categories = null,
                locations = listOf(
                    Location(1, "Cabinet", "Cabinet", null, "", 0, 0),
                    Location(2, "Drawer 1", "Cabinet/Drawer 1", "Cabinet", "", 1, 117),
                ),
            ),
        )
        show()
        compose.onNodeWithTag("filter_low").performClick()
        compose.onNodeWithTag("filter_tag").performClick()
        compose.onNodeWithText("unused (0)").assertDoesNotExist() // tags without parts aren't offered
        compose.onNodeWithText("smd (2)").performClick()
        compose.onNodeWithTag("filter_location").performClick()
        compose.onNodeWithText("Drawer 1").performClick()
        compose.onNodeWithTag("filter_category").performClick()
        compose.onNodeWithText("Couldn't load the choices").assertIsDisplayed()
        assertEquals(listOf("low true", "tag smd", "location Cabinet/Drawer 1"), events)
    }

    @Test
    fun chosenFilters() {
        state = PartsUiState(loaded = true, parts = parts, filter = PartsFilter(tag = "smd", lowOnly = true))
        show()
        compose.onNodeWithTag("filter_low").assertIsSelected()
        compose.onNodeWithTag("filter_tag").assertIsSelected()
        compose.onNodeWithText("smd").assertIsDisplayed() // the chip shows the value
        compose.onNodeWithTag("filter_tag").performClick()
        compose.onNodeWithText("Any tag").performClick()
        assertEquals(listOf("tag null"), events)
    }

    @Test
    fun addingNeedsAToken() {
        state = PartsUiState(loaded = true, parts = parts)
        show()
        compose.onNodeWithTag("add_part").assertDoesNotExist()
    }

    @Test
    fun addAPart() {
        state = PartsUiState(loaded = true, parts = parts, canWrite = true)
        show()
        compose.onNodeWithTag("add_part").performClick()
        assertEquals(listOf("add part"), events)
    }
}
