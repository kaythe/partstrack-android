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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.StockEntry
import de.kaylatheil.partstrack.ui.common.ErrorView
import de.kaylatheil.partstrack.ui.common.message
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme

@Composable
fun PartsScreen(viewModel: PartsViewModel, onOpenSettings: () -> Unit, onOpenPart: (Int) -> Unit, onAddPart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    PartsContent(
        state = state,
        actions = PartsActions(
            onQueryChange = viewModel::onQueryChange,
            onTagChange = viewModel::onTagChange,
            onCategoryChange = viewModel::onCategoryChange,
            onLocationChange = viewModel::onLocationChange,
            onLowOnlyChange = viewModel::onLowOnlyChange,
            onClearFilters = viewModel::clearFilters,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onErrorShown = viewModel::onErrorShown,
            onOpenSettings = onOpenSettings,
            onOpenPart = onOpenPart,
            onAddPart = onAddPart,
        ),
    )
}

/** What the parts screen can ask for. */
class PartsActions(
    val onQueryChange: (String) -> Unit = {},
    val onTagChange: (String?) -> Unit = {},
    val onCategoryChange: (String?) -> Unit = {},
    val onLocationChange: (String?) -> Unit = {},
    val onLowOnlyChange: (Boolean) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenPart: (Int) -> Unit = {},
    val onAddPart: () -> Unit = {},
)

/** The screen without its ViewModel, for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsContent(state: PartsUiState, actions: PartsActions) {
    val snackbar = remember { SnackbarHostState() }
    // An error over a list that is still shown (e.g. a failed refresh) is only a message.
    val passingError = state.error?.takeIf { state.loaded }?.message()
    LaunchedEffect(passingError) {
        if (passingError != null) {
            snackbar.showSnackbar(passingError)
            actions.onErrorShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.canWrite) {
                FloatingActionButton(onClick = actions.onAddPart, modifier = Modifier.testTag("add_part")) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_part))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(state.filter.query, actions.onQueryChange)
            FilterChips(state, actions)
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.loading && !state.refreshing && state.loaded) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.loaded && state.error != null ->
                        ErrorView(state.error, actions.onRetry, Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                    !state.loaded ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.testTag("loading"))
                        }
                    state.parts.isEmpty() -> NoParts(state.filter.isEmpty, actions.onClearFilters)
                    else -> PartList(state.parts, actions.onOpenPart, roomForButton = state.canWrite)
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search"),
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

/** One choice in a filter chip's menu; [depth] indents locations under their parents. */
private data class Choice(val value: String, val label: String, val depth: Int = 0)

@Composable
private fun FilterChips(state: PartsUiState, actions: PartsActions) {
    val filter = state.filter
    val options = state.options
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.lowOnly,
            onClick = { actions.onLowOnlyChange(!filter.lowOnly) },
            label = { Text(stringResource(R.string.filter_low)) },
            modifier = Modifier.testTag("filter_low"),
        )
        MenuChip(
            name = stringResource(R.string.filter_location),
            anyLabel = stringResource(R.string.filter_any_location),
            selected = filter.location,
            choices = options.locations?.map { Choice(it.path, it.name, it.path.count { c -> c == '/' }) },
            onChange = actions.onLocationChange,
            tag = "filter_location",
        )
        MenuChip(
            name = stringResource(R.string.filter_tag),
            anyLabel = stringResource(R.string.filter_any_tag),
            selected = filter.tag,
            choices = options.tags?.filter { it.parts > 0 }?.map { Choice(it.name, "${it.name} (${it.parts})") },
            onChange = actions.onTagChange,
            tag = "filter_tag",
        )
        MenuChip(
            name = stringResource(R.string.filter_category),
            anyLabel = stringResource(R.string.filter_any_category),
            selected = filter.category,
            choices = options.categories?.map { Choice(it, it) },
            onChange = actions.onCategoryChange,
            tag = "filter_category",
        )
    }
}

/** A chip that shows its filter's value and opens a menu to choose one ([choices] null: not loaded). */
@Composable
private fun MenuChip(
    name: String,
    anyLabel: String,
    selected: String?,
    choices: List<Choice>?,
    onChange: (String?) -> Unit,
    tag: String,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.testTag(tag),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = { open = false; onChange(null) },
            )
            when {
                choices == null -> DropdownMenuItem(text = { Text(stringResource(R.string.filter_unavailable)) }, onClick = {}, enabled = false)
                choices.isEmpty() -> DropdownMenuItem(text = { Text(stringResource(R.string.filter_none)) }, onClick = {}, enabled = false)
                else -> choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label, Modifier.padding(start = (16 * choice.depth).dp)) },
                        onClick = { open = false; onChange(choice.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoParts(noFilters: Boolean, onClearFilters: () -> Unit) {
    // Scrollable, so pull to refresh works here too.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (noFilters) R.string.no_parts_yet else R.string.no_parts_match),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (!noFilters) {
            OutlinedButton(onClick = onClearFilters) { Text(stringResource(R.string.clear_filters)) }
        }
    }
}

@Composable
private fun PartList(parts: List<Part>, onOpenPart: (Int) -> Unit, roomForButton: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("parts"),
        // The last row can scroll out from under the add button.
        contentPadding = PaddingValues(bottom = if (roomForButton) 88.dp else 0.dp),
    ) {
        items(parts, key = { it.id }) { part ->
            PartRow(part, onClick = { onOpenPart(part.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun PartRow(part: Part, onClick: () -> Unit) {
    val detail = part.description.ifBlank { part.packageName }
    val quantityColor = if (part.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(part.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (detail.isNotBlank()) {
            { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(part.totalQuantity.toString(), style = MaterialTheme.typography.titleMedium, color = quantityColor)
                if (part.low) {
                    Text(stringResource(R.string.low), style = MaterialTheme.typography.labelSmall, color = quantityColor)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun PartsPreview() {
    fun part(id: Int, name: String, description: String, total: Int, low: Boolean) = Part(
        id = id, name = name, description = description, manufacturer = "", mpn = "", category = "",
        packageName = "0603", notes = "", minQuantity = null, totalQuantity = total, low = low,
        stock = listOf(StockEntry("Cabinet/Drawer 1", total)), tags = emptyList(), attributes = emptyMap(),
        attachments = emptyList(), createdAt = "", updatedAt = "", webUrl = "",
    )
    PartstrackTheme {
        PartsContent(
            PartsUiState(
                loaded = true,
                parts = listOf(part(1, "10k resistor", "Thick film resistor, 1%", 117, false), part(2, "NE555", "", 3, true)),
            ),
            PartsActions(),
        )
    }
}

