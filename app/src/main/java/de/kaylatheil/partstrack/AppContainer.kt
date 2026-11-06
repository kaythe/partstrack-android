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

package de.kaylatheil.partstrack

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import de.kaylatheil.partstrack.data.api.HttpPartstrackApi
import de.kaylatheil.partstrack.data.api.PartstrackApi
import de.kaylatheil.partstrack.data.settings.AesGcmTokenCipher
import de.kaylatheil.partstrack.data.settings.KeystoreTokenKey
import de.kaylatheil.partstrack.data.settings.SettingsStore
import okhttp3.HttpUrl

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Builds an API client for a server address and token (null: read only). */
typealias ApiFactory = (serverUrl: HttpUrl, token: String?) -> PartstrackApi

/** The objects the whole app shares, built once (dependency injection by hand). */
class AppContainer(context: Context) {
    /** One client for the API and, later, images: it holds the connection pool. */
    val httpClient = HttpPartstrackApi.httpClient()

    val settingsStore = SettingsStore(context.settingsDataStore, AesGcmTokenCipher(KeystoreTokenKey::get))

    val apiFactory: ApiFactory = { serverUrl, token -> HttpPartstrackApi(serverUrl, httpClient, { token }) }
}

class PartstrackApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
