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

// What the app sends. The server refuses unknown fields, so request bodies
// are built field by field here, with only the fields that are set.

package de.kaylatheil.partstrack.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Source

/** A field in a change that may also be set to null: [Keep] leaves it out of the request. */
sealed interface Patch<out T> {
    data object Keep : Patch<Nothing>
    data class Set<out T>(val value: T) : Patch<T>
}

enum class AttachmentKind(val value: String) {
    DATASHEET("datasheet"),
    IMAGE("image"),
    OTHER("other"),
}

/**
 * A part's fields for adding or changing it; null (or [Patch.Keep]) leaves a
 * field out. When changing a part, [tags] and [attributes] replace all of
 * them, and `minQuantity = Patch.Set(null)` removes the minimum.
 */
data class PartFields(
    val name: String? = null,
    val description: String? = null,
    val manufacturer: String? = null,
    val mpn: String? = null,
    val category: String? = null,
    val packageName: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
    val attributes: Map<String, String>? = null,
    val minQuantity: Patch<Int?> = Patch.Keep,
) {
    internal fun JsonObjectBuilder.putFields() {
        putIfSet("name", name)
        putIfSet("description", description)
        putIfSet("manufacturer", manufacturer)
        putIfSet("mpn", mpn)
        putIfSet("category", category)
        putIfSet("package", packageName)
        putIfSet("notes", notes)
        tags?.let { put("tags", JsonArray(it.map(::JsonPrimitive))) }
        attributes?.let { put("attributes", JsonObject(it.mapValues { (_, v) -> JsonPrimitive(v) })) }
        if (minQuantity is Patch.Set) put("min_quantity", minQuantity.value)
    }
}

/** Stock for a new part: [quantity] at [location] (a full path, created if missing). */
data class StockAmount(val location: String, val quantity: Int)

/**
 * A file to upload. [open] is called for each attempt to send it and must
 * return a new source each time; [length] is its size in bytes, or -1 if
 * unknown (then there is no progress to show).
 */
class Upload(
    val fileName: String,
    val mediaType: String?,
    val length: Long,
    val open: () -> Source,
)

internal fun partBody(fields: PartFields, stock: List<StockAmount>?): JsonObject = buildJsonObject {
    with(fields) { putFields() }
    if (!stock.isNullOrEmpty()) {
        put("stock", JsonArray(stock.map { buildJsonObject { put("location", it.location); put("quantity", it.quantity) } }))
    }
}

internal fun JsonObjectBuilder.putIfSet(key: String, value: String?) {
    if (value != null) put(key, value)
}

/** Notes are optional: an empty one is left out. */
internal fun JsonObjectBuilder.putNote(note: String) {
    if (note.isNotEmpty()) put("note", note)
}

internal fun JsonObjectBuilder.putPatch(key: String, value: Patch<String?>) {
    if (value is Patch.Set) put(key, value.value?.let(::JsonPrimitive) ?: JsonNull)
}
