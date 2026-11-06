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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kaylatheil.partstrack.ApiFactory
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.api.UnexpectedResponseException
import de.kaylatheil.partstrack.data.settings.ServerAddress
import de.kaylatheil.partstrack.data.settings.SettingsStore
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What checking a server address and token found. */
sealed interface CheckResult {
    /** Everything works: the token may change the inventory. */
    data class Ready(val version: String, val tokenName: String) : CheckResult

    /** The server can be read; without a token the app only shows the inventory. */
    data class NoToken(val version: String) : CheckResult

    /** The token is valid, but the server shows a read-only copy. */
    data class ReadOnlyCopy(val version: String, val tokenName: String) : CheckResult

    /** A partstrack server without `/api/token` (1.3.0 or older). */
    data class TooOld(val version: String) : CheckResult

    /** The server doesn't know the token; [message] is the server's explanation. */
    data class TokenInvalid(val message: String?) : CheckResult

    /** No answer: wrong address, server down, or not on the network or VPN. */
    data class Unreachable(val detail: String?) : CheckResult

    /** Something answered, but not partstrack (or a version without the API). */
    data object NotPartstrack : CheckResult

    /** Any other error answer. */
    data class Failed(val message: String) : CheckResult
}

data class SetupUiState(
    val loading: Boolean = true,
    val address: String = "",
    val token: String = "",
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** A token is saved but can no longer be decrypted, so it has to be entered again. */
    val savedTokenUnreadable: Boolean = false,
    val addressInvalid: Boolean = false,
    val checking: Boolean = false,
    val result: CheckResult? = null,
    /** Saved; the screen should close. */
    val saved: Boolean = false,
    /** The token couldn't be encrypted for storage (the Keystore failed), so nothing was saved. */
    val saveFailed: Boolean = false,
) {
    /** The token would travel unencrypted. */
    val tokenOverPlainHttp: Boolean
        get() = token.isNotBlank() && ServerAddress.parse(address)?.let(ServerAddress::isPlainHttp) == true
}

/** The setup and settings screen: server address, token, check and theme. */
class SetupViewModel(
    private val store: SettingsStore,
    private val apiFactory: ApiFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()
    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = store.settings.first()
            _state.update {
                it.copy(
                    loading = false,
                    address = settings.serverUrl?.toString() ?: "",
                    token = settings.token ?: "",
                    theme = settings.theme,
                    savedTokenUnreadable = settings.tokenUnreadable,
                )
            }
        }
    }

    fun onAddressChange(address: String) {
        checkJob?.cancel()
        _state.update { it.copy(address = address, addressInvalid = false, checking = false, result = null) }
    }

    fun onTokenChange(token: String) {
        checkJob?.cancel()
        _state.update { it.copy(token = token, savedTokenUnreadable = false, checking = false, result = null) }
    }

    /** The theme applies at once, without saving the rest. */
    fun onThemeChange(theme: ThemeChoice) {
        _state.update { it.copy(theme = theme) }
        viewModelScope.launch { store.setTheme(theme) }
    }

    fun check() {
        val current = _state.value
        val url = ServerAddress.parse(current.address)
        if (url == null) {
            _state.update { it.copy(addressInvalid = true) }
            return
        }
        val token = current.token.trim().ifEmpty { null }
        checkJob?.cancel()
        _state.update { it.copy(checking = true, result = null) }
        checkJob = viewModelScope.launch {
            val result = checkServer(apiFactory(url, token), hasToken = token != null)
            _state.update { it.copy(checking = false, result = result) }
        }
    }

    /** Saves without checking, so the app can also be set up while the server is away. */
    fun save() {
        val current = _state.value
        val url = ServerAddress.parse(current.address)
        if (url == null) {
            _state.update { it.copy(addressInvalid = true) }
            return
        }
        _state.update { it.copy(saveFailed = false) }
        viewModelScope.launch {
            try {
                store.saveConnection(url, current.token)
            } catch (_: GeneralSecurityException) {
                _state.update { it.copy(saveFailed = true) }
                return@launch
            } catch (_: ProviderException) {
                _state.update { it.copy(saveFailed = true) }
                return@launch
            }
            _state.update { it.copy(saved = true) }
        }
    }
}

/** Asks the server for its version, then (with a token) checks the token. */
internal suspend fun checkServer(api: PartstrackApi, hasToken: Boolean): CheckResult {
    val version = try {
        api.serverInfo().version
    } catch (e: NetworkException) {
        return CheckResult.Unreachable(e.cause?.message)
    } catch (_: UnexpectedResponseException) {
        return CheckResult.NotPartstrack
    } catch (e: ApiException) {
        // Another web server, or a partstrack-web from before the API.
        return if (e.isNotFound) CheckResult.NotPartstrack else CheckResult.Failed(e.message ?: "")
    }
    if (!hasToken) return CheckResult.NoToken(version)
    return try {
        val token = api.checkToken()
        if (token.readOnly) CheckResult.ReadOnlyCopy(version, token.name) else CheckResult.Ready(version, token.name)
    } catch (e: NetworkException) {
        CheckResult.Unreachable(e.cause?.message)
    } catch (_: UnexpectedResponseException) {
        CheckResult.NotPartstrack
    } catch (e: ApiException) {
        when {
            e.isNotFound -> CheckResult.TooOld(version)
            e.isUnauthorized -> CheckResult.TokenInvalid(e.serverMessage)
            else -> CheckResult.Failed(e.message ?: "")
        }
    }
}
