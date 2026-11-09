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

package de.kaylatheil.partstrack.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            PartstrackTheme {
                AboutContent(
                    version = "1.2.3",
                    onBack = { events += "back" },
                    onOpenLicense = { events += "license $it" },
                    onOpenLink = { events += "link $it" },
                )
            }
        }
    }

    @Test
    fun versionCopyrightAndSource() {
        show()
        compose.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        compose.onNodeWithText("Copyright", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Source code", substring = true).performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("link $SOURCE_URL", "back"), events)
    }

    @Test
    fun theAppsOwnLicense() {
        show()
        compose.onNodeWithText("GNU General Public License 3.0").performClick()
        assertEquals(listOf("license ${License.GPL_3_0}"), events)
    }

    @Test
    fun everyComponentLeadsToItsLicense() {
        show()
        for (component in components) {
            compose.onNodeWithTag("about").performScrollToNode(hasText(component.name))
            compose.onNodeWithText(component.name).performClick()
        }
        assertEquals(components.map { "license ${it.license}" }, events)
    }

    @Test
    fun saysWhereThePublicSuffixListIs() {
        // MPL-2.0 asks to tell where its source is.
        show()
        compose.onNodeWithTag("about").performScrollToNode(hasText(PUBLIC_SUFFIX_LIST_URL, substring = true))
        compose.onNodeWithText(PUBLIC_SUFFIX_LIST_URL, substring = true).assertIsDisplayed()
    }

    @Test
    fun everyLicenseTextIsThere() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val firstLines = mapOf(
            License.GPL_3_0 to "GNU GENERAL PUBLIC LICENSE",
            License.APACHE_2_0 to "Apache License",
            License.BSD_3_CLAUSE_PROTOBUF to "Copyright 2008 Google Inc.",
            License.MPL_2_0 to "Mozilla Public License Version 2.0",
        )
        assertEquals(License.entries.toSet(), firstLines.keys)
        for ((license, start) in firstLines) {
            val text = resources.openRawResource(license.text).bufferedReader().use { it.readText() }
            assertTrue("$license starts with '$start'", text.trimStart().startsWith(start))
        }
    }

    @Test
    fun licenseScreenShowsTheText() {
        compose.setContent {
            PartstrackTheme { LicenseScreen(License.APACHE_2_0, onBack = { events += "back" }) }
        }
        compose.onNodeWithText("Version 2.0, January 2004", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }
}
