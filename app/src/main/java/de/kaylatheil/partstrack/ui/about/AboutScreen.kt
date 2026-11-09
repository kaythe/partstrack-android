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

import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kaylatheil.partstrack.BuildConfig
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme

const val SOURCE_URL = "https://github.com/kaythe/partstrack-android"
const val PUBLIC_SUFFIX_LIST_URL = "https://publicsuffix.org/list/"

/** A license whose text comes with the app, in res/raw. */
enum class License(val title: String, @param:RawRes val text: Int) {
    GPL_3_0("GNU General Public License 3.0", R.raw.license_gpl_3_0),
    APACHE_2_0("Apache License 2.0", R.raw.license_apache_2_0),
    BSD_3_CLAUSE_PROTOBUF("BSD 3-Clause License (Protocol Buffers)", R.raw.license_protobuf),
    MPL_2_0("Mozilla Public License 2.0", R.raw.license_mpl_2_0),
}

/**
 * Something the APK contains that isn't the app's own, as docs/SBOM.md lists it
 * (grouped by project). [source] is where to get it, when its license asks to say so.
 */
data class Component(val name: String, val by: String, val license: License, val source: String? = null)

val components = listOf(
    Component("AndroidX: Jetpack Compose, Material 3, Navigation, Lifecycle, DataStore and more", "The Android Open Source Project", License.APACHE_2_0),
    Component("Compose Multiplatform, Lifecycle and SavedState for Compose", "JetBrains", License.APACHE_2_0),
    Component("Kotlin standard library, kotlinx.coroutines, kotlinx.serialization, JetBrains annotations", "JetBrains", License.APACHE_2_0),
    Component("OkHttp and Okio", "Square", License.APACHE_2_0),
    Component("Coil", "Coil Contributors", License.APACHE_2_0),
    Component("ZXing", "ZXing authors", License.APACHE_2_0),
    Component("ZXing Android Embedded", "JourneyApps", License.APACHE_2_0),
    Component("Accompanist Drawable Painter", "Google", License.APACHE_2_0),
    Component("Guava ListenableFuture", "Google", License.APACHE_2_0),
    Component("JSpecify", "The JSpecify authors", License.APACHE_2_0),
    Component("Protocol Buffers, inside DataStore", "Google", License.BSD_3_CLAUSE_PROTOBUF),
    Component("Public Suffix List, inside OkHttp", "Mozilla Foundation", License.MPL_2_0, source = PUBLIC_SUFFIX_LIST_URL),
)

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicense: (License) -> Unit) {
    val uriHandler = LocalUriHandler.current
    AboutContent(
        version = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onOpenLicense = onOpenLicense,
        // Without a browser there is nothing to open it with; the address is on the screen.
        onOpenLink = { runCatching { uriHandler.openUri(it) } },
    )
}

/** The screen without its links to the system, for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    version: String,
    onBack: () -> Unit,
    onOpenLicense: (License) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("about"), contentPadding = padding) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.about_version, version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.about_copyright),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.about_gpl),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onOpenLink(SOURCE_URL) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.about_source, SOURCE_URL))
                    }
                }
            }
            item { LicenseItem(stringResource(R.string.about_license), License.GPL_3_0, onOpenLicense) }
            item {
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_components), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_components_intro),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(components) { component ->
                ListItem(
                    headlineContent = { Text(component.name) },
                    supportingContent = {
                        Column {
                            Text("${component.by} · ${component.license.title}")
                            component.source?.let { Text(stringResource(R.string.about_component_source, it)) }
                        }
                    },
                    modifier = Modifier.clickable { onOpenLicense(component.license) },
                )
            }
        }
    }
}

@Composable
private fun LicenseItem(headline: String, license: License, onOpenLicense: (License) -> Unit) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(license.title) },
        modifier = Modifier.clickable { onOpenLicense(license) },
    )
}

/** A license's full text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(license: License, onBack: () -> Unit) {
    val resources = LocalResources.current
    // Paragraphs, so the long texts are laid out as they scroll into view.
    val paragraphs = remember(license) {
        resources.openRawResource(license.text).bufferedReader().use { it.readText() }
            .split(Regex("\n\\s*\n")).map { it.trimEnd() }.filter { it.isNotBlank() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(license.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("license_text"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(paragraphs) { paragraph ->
                Text(paragraph, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutPreview() {
    PartstrackTheme {
        AboutContent(version = "1.0.0", onBack = {}, onOpenLicense = {}, onOpenLink = {})
    }
}
