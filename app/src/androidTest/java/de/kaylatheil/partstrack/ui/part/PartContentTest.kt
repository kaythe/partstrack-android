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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.StockChange
import de.kaylatheil.partstrack.data.api.StockEntry
import de.kaylatheil.partstrack.ui.common.LoadError
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(PartUiState())
    private val events = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            PartstrackTheme {
                PartContent(
                    state,
                    PartActions(
                        onBack = { events += "back" },
                        onRetry = { events += "retry" },
                        onOpenAttachment = { events += "open ${it.name}" },
                        onOpenInWebInterface = { events += "web" },
                    ),
                )
            }
        }
    }

    private val datasheet = Attachment(2, "datasheet", "rc0603.pdf", false, "https://www.example.com/rc0603.pdf", null, "")
    private val notes = Attachment(3, "other", "notes.txt", true, "http://parts.lan/attachments/3", null, "")
    private val photo = Attachment(1, "image", "photo.png", true, "http://parts.lan/attachments/1", null, "")

    private val part = Part(
        id = 1, name = "10k resistor", description = "Thick film resistor, 1%", manufacturer = "Yageo",
        mpn = "RC0603FR-0710KL", category = "Resistor", packageName = "0603", notes = "Bought in 2026",
        minQuantity = 50, totalQuantity = 117, low = false,
        stock = listOf(StockEntry("Cabinet/Drawer 1", 97), StockEntry("Shelf", 20)),
        tags = listOf("resistor", "smd"), attributes = mapOf("resistance" to "10k", "tolerance" to "1%"),
        attachments = listOf(datasheet, notes, photo), createdAt = "", updatedAt = "", webUrl = "http://parts.lan/parts/1",
    )

    private val history = listOf(
        StockChange(6, "2026-11-06 10:24:09", 1, "10k resistor", "Shelf", 5, 20, "move", "sorting"),
        StockChange(3, "2026-11-06 10:20:00", 1, "10k resistor", "Cabinet/Drawer 1", -3, 97, "adjust", "for the amplifier"),
        StockChange(1, "2026-11-01 08:00:00", 1, "10k resistor", "Cabinet/Drawer 1", 100, 100, "set", "new part"),
    )

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("part").performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun details() {
        state = PartUiState(part = part, history = history, loading = false)
        show()
        compose.onNodeWithText("10k resistor").assertIsDisplayed() // the title
        compose.onNodeWithText("Thick film resistor, 1%").assertIsDisplayed()
        compose.onNodeWithText("117 in stock").assertIsDisplayed()
        compose.onNodeWithText("minimum 50").assertIsDisplayed()
        compose.onNodeWithText("Yageo").assertIsDisplayed()
        compose.onNodeWithText("RC0603FR-0710KL").assertIsDisplayed()
        compose.onNodeWithText("smd").assertIsDisplayed()
        for (text in listOf("Cabinet/Drawer 1", "97", "resistance", "10k", "Bought in 2026")) {
            scrollTo(text)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun lowStock() {
        state = PartUiState(part = part.copy(totalQuantity = 3, low = true), loading = false)
        show()
        compose.onNodeWithText("3 in stock").assertIsDisplayed()
        compose.onNodeWithText("below the minimum of 50").assertIsDisplayed()
    }

    @Test
    fun files() {
        state = PartUiState(part = part, loading = false)
        show()
        scrollTo("rc0603.pdf")
        compose.onNodeWithText("Datasheet, link").assertIsDisplayed() // datasheets first
        compose.onNodeWithText("rc0603.pdf").performClick()
        scrollTo("notes.txt")
        compose.onNodeWithText("notes.txt").performClick()
        assertEquals(listOf("open rc0603.pdf", "open notes.txt"), events)
        compose.onNodeWithText("photo.png").assertDoesNotExist() // pictures aren't files
    }

    @Test
    fun pictureFullSize() {
        state = PartUiState(part = part, loading = false)
        show()
        scrollTo("Pictures")
        compose.onNodeWithTag("picture").performClick()
        compose.onNodeWithTag("full_size").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("full_size").assertDoesNotExist()
    }

    @Test
    fun history() {
        state = PartUiState(part = part, history = history, loading = false)
        show()
        scrollTo("Moved 5 to Shelf")
        compose.onNodeWithText("+5").assertIsDisplayed()
        scrollTo("Took 3 from Cabinet/Drawer 1")
        compose.onNodeWithText("for the amplifier", substring = true).assertIsDisplayed()
        compose.onNodeWithText("−3").assertIsDisplayed()
        scrollTo("Set to 100 at Cabinet/Drawer 1")
    }

    @Test
    fun historyMissing() {
        state = PartUiState(part = part.copy(stock = emptyList()), history = null, loading = false)
        show()
        scrollTo("Not stored anywhere.")
        scrollTo("Couldn't load the history.")
    }

    @Test
    fun menu() {
        state = PartUiState(part = part, loading = false)
        show()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Open in web interface").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("web", "back"), events)
    }

    @Test
    fun loadingAndError() {
        show()
        compose.onNodeWithTag("loading").assertIsDisplayed()
        state = PartUiState(loading = false, error = LoadError.Server(404, "no part with id 1"))
        compose.onNodeWithText("no part with id 1").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), events)
    }
}
