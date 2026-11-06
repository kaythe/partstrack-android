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

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Server addresses as people type them. */
object ServerAddress {
    /**
     * The address of a partstrack server from what was typed, or null if it
     * isn't one. Without a scheme it is `http://` (partstrack-web serves plain
     * HTTP); the path always ends with `/`, and a query or fragment is dropped.
     * `parts.lan:8080` becomes `http://parts.lan:8080/`.
     */
    fun parse(text: String): HttpUrl? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null
        val path = if (url.encodedPath.endsWith("/")) url.encodedPath else url.encodedPath + "/"
        return url.newBuilder().encodedPath(path).query(null).fragment(null).build()
    }

    /** Whether the token would travel unencrypted to [url]. */
    fun isPlainHttp(url: HttpUrl): Boolean = !url.isHttps
}
