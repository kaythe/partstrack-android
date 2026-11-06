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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * The app's settings. [serverUrl] is null until the app has been set up;
 * without a [token] the app only reads. [tokenUnreadable]: a token is stored
 * but can't be decrypted (its key in the Keystore is gone), so it has to be
 * entered again.
 */
data class Settings(
    val serverUrl: HttpUrl?,
    val token: String?,
    val theme: ThemeChoice,
    val tokenUnreadable: Boolean = false,
) {
    val canWrite: Boolean get() = token != null

    // Keep the token out of logs and crash reports.
    override fun toString() =
        "Settings(serverUrl=$serverUrl, token=${if (token == null) "none" else "***"}, theme=$theme, " +
            "tokenUnreadable=$tokenUnreadable)"
}

/** Settings in a DataStore, with the token encrypted by [cipher]. */
class SettingsStore(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val settings: Flow<Settings> = store.data
        .map { prefs ->
            val stored = prefs[TOKEN]
            val token = stored?.let(cipher::decrypt)
            Settings(
                serverUrl = prefs[SERVER_URL]?.toHttpUrlOrNull(),
                token = token,
                theme = prefs[THEME]?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.SYSTEM,
                tokenUnreadable = stored != null && token == null,
            )
        }
        .distinctUntilChanged()
        .flowOn(ioDispatcher) // decrypting uses the Keystore

    /** Saves the server and the token (null or blank: none, so the app only reads). */
    suspend fun saveConnection(serverUrl: HttpUrl, token: String?) {
        val clean = token?.trim()?.takeIf { it.isNotEmpty() }
        val encrypted = clean?.let { withContext(ioDispatcher) { cipher.encrypt(it) } }
        store.edit { prefs ->
            prefs[SERVER_URL] = serverUrl.toString()
            if (encrypted == null) prefs.remove(TOKEN) else prefs[TOKEN] = encrypted
        }
    }

    suspend fun setTheme(theme: ThemeChoice) {
        store.edit { it[THEME] = theme.name }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
        val THEME = stringPreferencesKey("theme")
    }
}
