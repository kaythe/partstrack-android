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

// What the partstrack API answers with, as the server's api.py writes it.
// Fields the app doesn't know are ignored, so a newer server still works.
// Locations are full paths without a leading '/' ("Cabinet/Drawer 3"), and
// times are UTC as "YYYY-MM-DD HH:MM:SS" (see parseServerTime).

package de.kaylatheil.partstrack.data.api

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api`: the server's version and what it offers. */
@Serializable
data class ServerInfo(
    val version: String,
    val endpoints: Map<String, String> = emptyMap(),
)

/** `GET /api/token`: the token sent is valid; [readOnly] if the server shows a read-only copy. */
@Serializable
data class TokenInfo(
    val id: Int,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_only") val readOnly: Boolean,
)

@Serializable
data class Part(
    val id: Int,
    val name: String,
    val description: String,
    val manufacturer: String,
    val mpn: String,
    val category: String,
    @SerialName("package") val packageName: String,
    val notes: String,
    @SerialName("min_quantity") val minQuantity: Int?,
    @SerialName("total_quantity") val totalQuantity: Int,
    /** The total is below [minQuantity]. */
    val low: Boolean,
    val stock: List<StockEntry>,
    val tags: List<String>,
    val attributes: Map<String, String>,
    val attachments: List<Attachment>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("web_url") val webUrl: String,
)

@Serializable
data class StockEntry(
    val location: String,
    val quantity: Int,
)

@Serializable
data class Attachment(
    val id: Int,
    /** "datasheet", "image" or "other" (see [AttachmentKind]). */
    val kind: String,
    val name: String,
    /** A file kept by the server (at [url]), rather than only a link. */
    val stored: Boolean,
    /** Where to show or open it: the server's `/attachments/<id>`, or the link itself. */
    val url: String,
    /** For a downloaded link: where the file came from. */
    @SerialName("source_url") val sourceUrl: String?,
    @SerialName("added_at") val addedAt: String,
    /** Only when adding a link: why its file couldn't be downloaded (the link is kept). */
    @SerialName("download_error") val downloadError: String? = null,
) {
    val isImage: Boolean get() = kind == AttachmentKind.IMAGE.value
}

@Serializable
data class Location(
    val id: Int,
    val name: String,
    val path: String,
    /** The parent's path, or null at the top level. */
    val parent: String?,
    val description: String,
    /** Parts stored directly here (not in sublocations). */
    @SerialName("part_count") val partCount: Int,
    @SerialName("total_quantity") val totalQuantity: Int,
)

@Serializable
data class TagCount(
    val name: String,
    val parts: Int,
)

/** One entry of the stock history. */
@Serializable
data class StockChange(
    val id: Int,
    val at: String,
    @SerialName("part_id") val partId: Int,
    @SerialName("part_name") val partName: String,
    val location: String,
    val delta: Int,
    /** The amount at [location] after the change. */
    val quantity: Int,
    /** "initial", "set", "adjust" or "move". */
    val action: String,
    val note: String,
)

private val SERVER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** A time from the server ("2026-11-06 10:24:09", UTC), or null if it isn't one. */
fun parseServerTime(text: String): Instant? =
    try {
        LocalDateTime.parse(text, SERVER_TIME).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        null
    }
