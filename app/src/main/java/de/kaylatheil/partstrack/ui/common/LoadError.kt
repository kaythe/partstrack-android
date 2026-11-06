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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kaylatheil.partstrack.R
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.PartstrackException
import de.kaylatheil.partstrack.data.api.UnexpectedResponseException

/** Why something couldn't be loaded, in the terms the screens show. */
sealed interface LoadError {
    /** No answer from the server. */
    data class Unreachable(val detail: String?) : LoadError

    /** The server answered with an error; [message] is its text, meant for users. */
    data class Server(val status: Int, val message: String?) : LoadError

    /** The answer wasn't partstrack's (the address may be wrong). */
    data object NotPartstrack : LoadError
}

fun PartstrackException.toLoadError(): LoadError = when (this) {
    is NetworkException -> LoadError.Unreachable(cause?.message)
    is ApiException -> LoadError.Server(status, serverMessage)
    is UnexpectedResponseException -> LoadError.NotPartstrack
}

@Composable
fun LoadError.message(): String = when (this) {
    is LoadError.Unreachable -> stringResource(R.string.error_unreachable)
    is LoadError.Server -> message ?: stringResource(R.string.error_http, status)
    LoadError.NotPartstrack -> stringResource(R.string.error_not_partstrack)
}

/** Fills the space of something that couldn't be loaded, with a way to try again. */
@Composable
fun ErrorView(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp).testTag("error"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(error.message(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (error is LoadError.Unreachable && !error.detail.isNullOrBlank()) {
            Text(
                error.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
