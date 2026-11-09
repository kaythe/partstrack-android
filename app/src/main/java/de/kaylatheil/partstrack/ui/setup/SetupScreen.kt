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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.settings.ServerAddress
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import de.kaylatheil.partstrack.ui.common.LocalNetworkHint
import de.kaylatheil.partstrack.ui.common.rememberLocalNetworkRequest
import de.kaylatheil.partstrack.ui.scan.QrScanner
import de.kaylatheil.partstrack.ui.scan.ScanOutcome
import de.kaylatheil.partstrack.ui.scan.openAppSettings
import de.kaylatheil.partstrack.ui.scan.rememberQrScanner
import de.kaylatheil.partstrack.ui.theme.PartstrackTheme

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    firstStart: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askForLocalNetwork = rememberLocalNetworkRequest()
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    val scanToken = rememberQrScanner(stringResource(R.string.scan_token_prompt)) { outcome ->
        when (outcome) {
            is ScanOutcome.Code -> viewModel.onTokenScanned(outcome.text)
            ScanOutcome.Cancelled -> {}
            ScanOutcome.NoCamera -> viewModel.onCameraRefused()
        }
    }
    SetupContent(
        state = state,
        firstStart = firstStart,
        onAddressChange = viewModel::onAddressChange,
        onTokenChange = viewModel::onTokenChange,
        onThemeChange = viewModel::onThemeChange,
        // Checking reaches the server, which needs the local network permission on Android 17.
        onCheck = { if (ServerAddress.parse(state.address) == null) viewModel.check() else askForLocalNetwork(viewModel::check) },
        onSave = viewModel::save,
        onBack = onBack,
        onScanToken = scanToken,
        onOpenAppSettings = { openAppSettings(context) },
    )
}

/** The screen without its ViewModel, for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupContent(
    state: SetupUiState,
    firstStart: Boolean,
    onAddressChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onCheck: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onScanToken: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (firstStart) R.string.setup_title else R.string.settings_title)) },
                navigationIcon = {
                    if (!firstStart) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (firstStart) {
                Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth().testTag("address"),
                label = { Text(stringResource(R.string.server_address)) },
                placeholder = { Text("http://parts.lan:8080/") },
                isError = state.addressInvalid,
                supportingText = {
                    Text(stringResource(if (state.addressInvalid) R.string.server_address_invalid else R.string.server_address_help))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
            TokenField(state, onTokenChange)
            TokenScan(state.tokenScanProblem, onScanToken, onOpenAppSettings)
            if (state.tokenOverPlainHttp) {
                Text(
                    stringResource(R.string.token_plain_http),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCheck, enabled = !state.checking, modifier = Modifier.testTag("check")) {
                    Text(stringResource(R.string.check))
                }
                Button(onClick = onSave, modifier = Modifier.testTag("save")) {
                    Text(stringResource(R.string.save))
                }
                if (state.checking) {
                    CircularProgressIndicator(Modifier.size(24.dp).testTag("checking"), strokeWidth = 2.dp)
                }
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.result?.let { CheckResultCard(it) }

            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            ThemeChooser(state.theme, onThemeChange)
        }
    }
}

/** Fills in the token from the QR code 'partstrack token add' shows, instead of typing it. */
@Composable
private fun TokenScan(problem: TokenScanProblem?, onScanToken: () -> Unit, onOpenAppSettings: () -> Unit) {
    TextButton(onClick = onScanToken, modifier = Modifier.testTag("scan_token")) {
        Icon(Icons.Filled.QrScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.scan_token))
    }
    when (problem) {
        null -> {}
        TokenScanProblem.NOT_A_TOKEN -> Text(
            stringResource(R.string.scan_not_a_token),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TokenScanProblem.NO_CAMERA -> Column {
            Text(
                stringResource(R.string.scan_no_camera),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.app_settings)) }
        }
    }
}

@Composable
private fun TokenField(state: SetupUiState, onTokenChange: (String) -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = state.token,
        onValueChange = onTokenChange,
        modifier = Modifier.fillMaxWidth().testTag("token"),
        label = { Text(stringResource(R.string.api_token)) },
        isError = state.savedTokenUnreadable,
        supportingText = {
            Text(stringResource(if (state.savedTokenUnreadable) R.string.api_token_unreadable else R.string.api_token_help))
        },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { shown = !shown }) {
                Text(stringResource(if (shown) R.string.hide else R.string.show))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
    )
}

@Composable
private fun CheckResultCard(result: CheckResult) {
    val good = result is CheckResult.Ready || result is CheckResult.NoToken
    val text = when (result) {
        is CheckResult.Ready -> stringResource(R.string.check_ready, result.version, result.tokenName)
        is CheckResult.NoToken -> stringResource(R.string.check_no_token, result.version)
        is CheckResult.ReadOnlyCopy -> stringResource(R.string.check_read_only_copy, result.version, result.tokenName)
        is CheckResult.TooOld -> stringResource(R.string.check_too_old, result.version)
        is CheckResult.TokenInvalid -> stringResource(R.string.check_token_invalid, result.message ?: "")
        is CheckResult.Unreachable -> stringResource(R.string.check_unreachable, result.detail ?: "")
        CheckResult.NotPartstrack -> stringResource(R.string.check_not_partstrack)
        is CheckResult.Failed -> stringResource(R.string.check_failed, result.message)
    }.trim()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("result"),
        colors = if (good) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        },
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        if (result is CheckResult.Unreachable) LocalNetworkHint(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChooser(theme: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit) {
    val labels = mapOf(
        ThemeChoice.SYSTEM to R.string.theme_system,
        ThemeChoice.LIGHT to R.string.theme_light,
        ThemeChoice.DARK to R.string.theme_dark,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeChoice.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = theme == choice,
                onClick = { onThemeChange(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeChoice.entries.size),
            ) {
                Text(stringResource(labels.getValue(choice)))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    PartstrackTheme {
        SetupContent(
            state = SetupUiState(
                loading = false,
                address = "http://parts.lan:8080/",
                token = "pt_example",
                result = CheckResult.Ready("1.4.0", "phone"),
            ),
            firstStart = true,
            onAddressChange = {}, onTokenChange = {}, onThemeChange = {}, onCheck = {}, onSave = {}, onBack = {},
        )
    }
}
