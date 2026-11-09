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

package de.kaylatheil.partstrack.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The setup screen with a state held by the test instead of a ViewModel. */
@RunWith(AndroidJUnit4::class)
class SetupContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(SetupUiState(loading = false))
    private val events = mutableListOf<String>()

    private fun show(firstStart: Boolean = true) {
        compose.setContent {
            PartstrackTheme {
                SetupContent(
                    state = state,
                    firstStart = firstStart,
                    onAddressChange = { state = state.copy(address = it, result = null) },
                    onTokenChange = { state = state.copy(token = it, result = null) },
                    onThemeChange = { events += "theme $it" },
                    onCheck = { events += "check" },
                    onSave = { events += "save" },
                    onBack = { events += "back" },
                    onScanToken = { events += "scan token" },
                    onOpenAppSettings = { events += "app settings" },
                )
            }
        }
    }

    @Test
    fun firstStart() {
        show()
        compose.onNodeWithText("Connect to partstrack").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithTag("address").performTextInput("parts.lan:8080")
        compose.onNodeWithTag("token").performTextInput("pt_secret")
        assertEquals("parts.lan:8080", state.address)
        assertEquals("pt_secret", state.token)
        // Plain HTTP with a token: the warning shows.
        compose.onNodeWithText("travels unencrypted", substring = true).assertIsDisplayed()
        // The token is hidden until asked for.
        assertNotEquals("pt_secret", shownToken())
        compose.onNodeWithText("Show").performClick()
        assertEquals("pt_secret", shownToken())

        compose.onNodeWithTag("check").performClick()
        compose.onNodeWithTag("save").performClick()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(listOf("check", "save", "theme ${ThemeChoice.DARK}"), events)
    }

    /** The token field's text as drawn (masked or not). */
    private fun shownToken(): String =
        compose.onNodeWithTag("token").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun settingsCanGoBack() {
        show(firstStart = false)
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun checkResults() {
        show()
        state = state.copy(checking = true)
        compose.onNodeWithTag("checking").assertIsDisplayed()

        val expected = mapOf(
            CheckResult.Ready("1.4.0", "phone") to "Connected to partstrack 1.4.0 with the token 'phone'",
            CheckResult.NoToken("1.4.0") to "only shows the inventory",
            CheckResult.ReadOnlyCopy("1.4.0", "phone") to "shows a read-only copy",
            CheckResult.TooOld("1.3.0") to "runs partstrack 1.3.0, which can't check API tokens",
            CheckResult.TokenInvalid("unknown or revoked API token") to "unknown or revoked API token",
            CheckResult.Unreachable("Connection refused") to "Can't reach the server",
            CheckResult.NotPartstrack to "isn't a partstrack server",
            CheckResult.Failed("the server answered with HTTP 502") to "HTTP 502",
        )
        for ((result, text) in expected) {
            state = state.copy(checking = false, result = result)
            compose.onNode(hasTestTag("result") and hasAnyDescendant(hasText(text, substring = true))).assertIsDisplayed()
        }
    }

    @Test
    fun problemsWithTheInput() {
        state = state.copy(addressInvalid = true, savedTokenUnreadable = true, saveFailed = true)
        show()
        compose.onNodeWithText("This isn't a web address.").assertIsDisplayed()
        compose.onNodeWithText("can't be read any more", substring = true).assertIsDisplayed()
        compose.onNodeWithText("couldn't be stored securely", substring = true).assertIsDisplayed()
    }

    @Test
    fun scanTheToken() {
        show()
        compose.onNodeWithTag("scan_token").performClick()
        assertEquals(listOf("scan token"), events)
    }

    @Test
    fun scannedSomethingElse() {
        state = state.copy(tokenScanProblem = TokenScanProblem.NOT_A_TOKEN)
        show()
        compose.onNodeWithText("That QR code isn't an API token", substring = true).assertIsDisplayed()
    }

    @Test
    fun noCameraForTheToken() {
        state = state.copy(tokenScanProblem = TokenScanProblem.NO_CAMERA)
        show()
        compose.onNodeWithText("App settings").performClick()
        assertEquals(listOf("app settings"), events)
    }
}
