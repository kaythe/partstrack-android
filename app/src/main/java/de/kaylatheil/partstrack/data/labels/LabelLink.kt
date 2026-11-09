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

package de.kaylatheil.partstrack.data.labels

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What the QR code on a partstrack label says (partstrack/labels.py):
 * `<server>/parts/<id>` for a part, `<server>/?location=/<path>` (the path
 * URL-encoded) for the parts at a location.
 */
sealed interface LabelLink {
    data class Part(val id: Int) : LabelLink

    /** A location's full path, without the leading '/'. */
    data class Location(val path: String) : LabelLink

    /** A partstrack label, but for the server at [server] (scheme, host, port and path), not the one set up. */
    data class OtherServer(val server: String) : LabelLink

    /** Not a partstrack label: [text] is what the code says. */
    data class Unknown(val text: String) : LabelLink

    companion object {
        private val PART = Regex("parts/(\\d+)/?")

        /**
         * Reads [text] as a label of the server at [server] (as set up, so its
         * path ends with '/'). Labels count only if scheme, host, port and the
         * server's path match: the same ids mean other parts on another server.
         */
        fun parse(text: String, server: HttpUrl): LabelLink {
            val trimmed = text.trim()
            val url = trimmed.toHttpUrlOrNull() ?: return Unknown(trimmed)
            // Where the label's server would be: the part of the path before what labels add.
            val path = url.encodedPath
            val part = PART.find(path)?.takeIf { it.range.last == path.lastIndex }
            val location = url.queryParameter("location")
            val labelBase = when {
                part != null -> path.substring(0, part.range.first)
                location != null -> path
                else -> return Unknown(trimmed)
            }
            val sameServer = url.scheme == server.scheme && url.host == server.host && url.port == server.port &&
                labelBase == server.encodedPath
            if (!sameServer) {
                return OtherServer(url.newBuilder().encodedPath(labelBase).query(null).fragment(null).build().toString())
            }
            if (part != null) {
                val id = part.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return Unknown(trimmed)
                return Part(id)
            }
            val locationPath = location!!.split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")
            return if (locationPath.isEmpty()) Unknown(trimmed) else Location(locationPath)
        }

        /** An API token from a QR code (the CLI's 'partstrack token add' shows one), or null if it isn't one. */
        fun token(text: String): String? = text.trim().takeIf { it.startsWith("pt_") && it.length > 3 && it.none(Char::isWhitespace) }
    }
}
