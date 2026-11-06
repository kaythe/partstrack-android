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

import de.kaylatheil.partstrack.MainDispatcherRule
import de.kaylatheil.partstrack.data.api.ApiException
import de.kaylatheil.partstrack.data.api.FakePartstrackApi
import de.kaylatheil.partstrack.data.api.NetworkException
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.api.ServerInfo
import de.kaylatheil.partstrack.data.api.TokenInfo
import de.kaylatheil.partstrack.data.api.UnexpectedResponseException
import de.kaylatheil.partstrack.data.settings.FakeTokenCipher
import de.kaylatheil.partstrack.data.settings.SettingsStore
import de.kaylatheil.partstrack.data.settings.ThemeChoice
import de.kaylatheil.partstrack.data.settings.TokenCipher
import de.kaylatheil.partstrack.data.settings.newSettingsStore
import java.net.ConnectException
import java.security.KeyStoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val store: SettingsStore = newSettingsStore()
    private var api: PartstrackApi = FakePartstrackApi()
    private val created = mutableListOf<Pair<HttpUrl, String?>>()

    private suspend fun loaded(): SetupViewModel {
        val viewModel = SetupViewModel(store) { url, token -> created += url to token; api }
        viewModel.state.first { !it.loading }
        return viewModel
    }

    private suspend fun SetupViewModel.checked(): CheckResult {
        check()
        return state.first { !it.checking && it.result != null }.result!!
    }

    private fun server(version: String = "1.4.0", token: () -> TokenInfo = { error("no token check expected") }) =
        object : FakePartstrackApi() {
            override suspend fun serverInfo() = ServerInfo(version)
            override suspend fun checkToken() = token()
        }

    private fun tokenInfo(readOnly: Boolean = false) = TokenInfo(1, "phone", "2026-11-06 10:00:00", readOnly)

    @Test
    fun firstStartIsEmpty() = runTest {
        val state = loaded().state.value
        assertEquals("", state.address)
        assertEquals("", state.token)
        assertEquals(ThemeChoice.SYSTEM, state.theme)
        assertNull(state.result)
    }

    @Test
    fun showsWhatIsSaved() = runTest {
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), "pt_secret")
        store.setTheme(ThemeChoice.DARK)
        val state = loaded().state.value
        assertEquals("http://parts.lan:8080/", state.address)
        assertEquals("pt_secret", state.token)
        assertEquals(ThemeChoice.DARK, state.theme)
        assertFalse(state.savedTokenUnreadable)
    }

    @Test
    fun unreadableSavedToken() = runTest {
        val cipher = FakeTokenCipher()
        val brokenStore = newSettingsStore(cipher = cipher)
        brokenStore.saveConnection("http://parts.lan:8080/".toHttpUrl(), "pt_secret")
        cipher.broken = true
        val viewModel = SetupViewModel(brokenStore) { _, _ -> api }
        val state = viewModel.state.first { !it.loading }
        assertTrue(state.savedTokenUnreadable)
        assertEquals("", state.token)
        viewModel.onTokenChange("pt_new")
        assertFalse(viewModel.state.value.savedTokenUnreadable)
    }

    @Test
    fun checkReady() = runTest {
        api = server { tokenInfo() }
        val viewModel = loaded()
        viewModel.onAddressChange("parts.lan:8080")
        viewModel.onTokenChange(" pt_secret ")
        assertEquals(CheckResult.Ready("1.4.0", "phone"), viewModel.checked())
        assertEquals(listOf("http://parts.lan:8080/".toHttpUrl() to "pt_secret"), created)
    }

    @Test
    fun checkWithoutToken() = runTest {
        api = server()
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        assertEquals(CheckResult.NoToken("1.4.0"), viewModel.checked())
        assertNull(created.single().second)
    }

    @Test
    fun checkReadOnlyCopy() = runTest {
        api = server { tokenInfo(readOnly = true) }
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        viewModel.onTokenChange("pt_secret")
        assertEquals(CheckResult.ReadOnlyCopy("1.4.0", "phone"), viewModel.checked())
    }

    @Test
    fun checkTooOld() = runTest {
        api = server("1.3.0") { throw ApiException(404, "no such API endpoint; see /api") }
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        viewModel.onTokenChange("pt_secret")
        assertEquals(CheckResult.TooOld("1.3.0"), viewModel.checked())
    }

    @Test
    fun checkTokenInvalid() = runTest {
        api = server { throw ApiException(401, "unknown or revoked API token") }
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        viewModel.onTokenChange("pt_wrong")
        assertEquals(CheckResult.TokenInvalid("unknown or revoked API token"), viewModel.checked())
    }

    @Test
    fun checkUnreachable() = runTest {
        api = object : FakePartstrackApi() {
            override suspend fun serverInfo(): ServerInfo = throw NetworkException(ConnectException("Connection refused"))
        }
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        assertEquals(CheckResult.Unreachable("Connection refused"), viewModel.checked())
    }

    @Test
    fun checkNotPartstrack() = runTest {
        for (failure in listOf(UnexpectedResponseException("HTML"), ApiException(404, null))) {
            api = object : FakePartstrackApi() {
                override suspend fun serverInfo(): ServerInfo = throw failure
            }
            val viewModel = loaded()
            viewModel.onAddressChange("http://example.com/")
            assertEquals(CheckResult.NotPartstrack, viewModel.checked())
        }
    }

    @Test
    fun checkFailed() = runTest {
        api = object : FakePartstrackApi() {
            override suspend fun serverInfo(): ServerInfo = throw ApiException(502, null)
        }
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        assertEquals(CheckResult.Failed("the server answered with HTTP 502"), viewModel.checked())
    }

    @Test
    fun checkInvalidAddress() = runTest {
        val viewModel = loaded()
        viewModel.onAddressChange("parts lan")
        viewModel.check()
        assertTrue(viewModel.state.value.addressInvalid)
        assertTrue(created.isEmpty())
        viewModel.onAddressChange("parts.lan")
        assertFalse(viewModel.state.value.addressInvalid)
    }

    @Test
    fun editingClearsTheResultAndStopsTheCheck() = runTest {
        val answer = CompletableDeferred<ServerInfo>()
        api = object : FakePartstrackApi() {
            override suspend fun serverInfo() = answer.await()
        }
        val viewModel = loaded()
        viewModel.onAddressChange("http://parts.lan:8080/")
        viewModel.check()
        assertTrue(viewModel.state.value.checking)
        viewModel.onAddressChange("http://parts.lan:8081/")
        assertFalse(viewModel.state.value.checking)
        answer.complete(ServerInfo("1.4.0"))
        assertNull(viewModel.state.value.result) // the old address's answer doesn't show
    }

    @Test
    fun save() = runTest {
        val viewModel = loaded()
        viewModel.onAddressChange(" parts.lan:8080 ")
        viewModel.onTokenChange(" pt_secret ")
        viewModel.save()
        viewModel.state.first { it.saved }
        val settings = store.settings.first()
        assertEquals("http://parts.lan:8080/".toHttpUrl(), settings.serverUrl)
        assertEquals("pt_secret", settings.token)
        assertTrue(created.isEmpty()) // saving doesn't need the server
    }

    @Test
    fun saveWithoutToken() = runTest {
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), "pt_old")
        val viewModel = loaded()
        viewModel.onTokenChange("")
        viewModel.save()
        viewModel.state.first { it.saved }
        assertNull(store.settings.first().token)
    }

    @Test
    fun saveInvalidAddress() = runTest {
        val viewModel = loaded()
        viewModel.onAddressChange("")
        viewModel.save()
        assertTrue(viewModel.state.value.addressInvalid)
        assertFalse(viewModel.state.value.saved)
        assertNull(store.settings.first().serverUrl)
    }

    @Test
    fun saveWhenTheKeystoreFails() = runTest {
        val failing = object : TokenCipher {
            override fun encrypt(token: String): String = throw KeyStoreException("no key")
            override fun decrypt(stored: String): String? = null
        }
        val failingStore = newSettingsStore(cipher = failing)
        val viewModel = SetupViewModel(failingStore) { _, _ -> api }
        viewModel.state.first { !it.loading }
        viewModel.onAddressChange("http://parts.lan:8080/")
        viewModel.onTokenChange("pt_secret")
        viewModel.save()
        assertTrue(viewModel.state.first { it.saveFailed }.saveFailed)
        assertFalse(viewModel.state.value.saved)
        assertNull(failingStore.settings.first().serverUrl)
    }

    @Test
    fun themeIsSavedAtOnce() = runTest {
        val viewModel = loaded()
        viewModel.onThemeChange(ThemeChoice.LIGHT)
        assertEquals(ThemeChoice.LIGHT, viewModel.state.value.theme)
        assertEquals(ThemeChoice.LIGHT, store.settings.first { it.theme == ThemeChoice.LIGHT }.theme)
        assertNull(store.settings.first().serverUrl) // nothing else saved
    }

    @Test
    fun plainHttpWarning() {
        assertTrue(SetupUiState(address = "parts.lan:8080", token = "pt_x").tokenOverPlainHttp)
        assertFalse(SetupUiState(address = "parts.lan:8080", token = "").tokenOverPlainHttp)
        assertFalse(SetupUiState(address = "https://parts.lan/", token = "pt_x").tokenOverPlainHttp)
    }
}
