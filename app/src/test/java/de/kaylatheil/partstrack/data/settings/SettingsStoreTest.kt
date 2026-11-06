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

package de.kaylatheil.partstrack.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reversible, and recognisable in the file: enough to see that the store encrypts. */
class FakeTokenCipher : TokenCipher {
    var broken = false

    override fun encrypt(token: String) = "enc:" + token.reversed()

    override fun decrypt(stored: String) = if (broken) null else stored.removePrefix("enc:").reversed()
}

/**
 * A DataStore in memory. (DataStore's own file storage can't replace its file
 * on Windows outside Android, so it isn't used in JVM tests.)
 */
class InMemoryDataStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

fun newSettingsStore(data: DataStore<Preferences> = InMemoryDataStore(), cipher: TokenCipher = FakeTokenCipher()) =
    SettingsStore(data, cipher)

class SettingsStoreTest {
    private val cipher = FakeTokenCipher()
    private val data = InMemoryDataStore()
    private val store = newSettingsStore(data, cipher)

    @Test
    fun nothingSavedYet() = runTest {
        val settings = store.settings.first()
        assertNull(settings.serverUrl)
        assertNull(settings.token)
        assertEquals(ThemeChoice.SYSTEM, settings.theme)
        assertFalse(settings.canWrite)
        assertFalse(settings.tokenUnreadable)
    }

    @Test
    fun saveConnection() = runTest {
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), "  pt_secret \n")
        val settings = store.settings.first()
        assertEquals("http://parts.lan:8080/".toHttpUrl(), settings.serverUrl)
        assertEquals("pt_secret", settings.token)
        assertTrue(settings.canWrite)
        // Stored only in encrypted form.
        val stored = data.data.value.asMap().values.joinToString()
        assertFalse("pt_secret" in stored)
        assertEquals("enc:terces_tp", data.data.value[stringPreferencesKey("token")])
    }

    @Test
    fun withoutTokenTheAppOnlyReads() = runTest {
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), "pt_secret")
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), "   ")
        assertNull(store.settings.first().token)
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), null)
        assertFalse(store.settings.first().canWrite)
    }

    @Test
    fun unreadableToken() = runTest {
        store.saveConnection("http://parts.lan:8080/".toHttpUrl(), "pt_secret")
        cipher.broken = true
        store.setTheme(ThemeChoice.DARK) // any change, so the settings are read again
        val settings = store.settings.first()
        assertNull(settings.token)
        assertTrue(settings.tokenUnreadable)
        assertEquals("http://parts.lan:8080/".toHttpUrl(), settings.serverUrl) // the rest still works
    }

    @Test
    fun theme() = runTest {
        store.setTheme(ThemeChoice.LIGHT)
        assertEquals(ThemeChoice.LIGHT, store.settings.first().theme)
        store.setTheme(ThemeChoice.SYSTEM)
        assertEquals(ThemeChoice.SYSTEM, store.settings.first().theme)
    }

    @Test
    fun theTokenStaysOutOfLogs() {
        val settings = Settings("http://parts.lan/".toHttpUrl(), "pt_secret", ThemeChoice.SYSTEM)
        assertFalse("pt_secret" in settings.toString())
    }
}
