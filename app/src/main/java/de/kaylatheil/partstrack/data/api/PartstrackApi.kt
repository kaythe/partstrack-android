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

package de.kaylatheil.partstrack.data.api

/**
 * The JSON API of partstrack-web (`/api`). Every function throws a
 * [PartstrackException] if it fails: [ApiException] for an error answer,
 * [NetworkException] if the server can't be reached, and
 * [UnexpectedResponseException] for an answer that isn't partstrack's.
 *
 * Locations are full paths as the API returns them ("Cabinet/Drawer 3"),
 * always meant exactly. Changes answer with the changed object, so callers
 * can show it without loading it again.
 */
interface PartstrackApi {
    /** `GET /api`: the server's version. Also tells whether the address is a partstrack server. */
    suspend fun serverInfo(): ServerInfo

    /** `GET /api/token`: checks the token (401 if it isn't valid). */
    suspend fun checkToken(): TokenInfo

    /**
     * `GET /api/parts`. [query] is the search text with the server's attribute
     * filters (`resistance>=10k`); [location] includes its sublocations.
     */
    suspend fun parts(
        query: String = "",
        tag: String? = null,
        location: String? = null,
        category: String? = null,
        lowOnly: Boolean = false,
    ): List<Part>

    suspend fun part(id: Int): Part

    /** All locations, parents before their children. */
    suspend fun locations(): List<Location>

    suspend fun location(id: Int): Location

    suspend fun tags(): List<TagCount>

    suspend fun categories(): List<String>

    /** Stock changes, newest first; [limit] 0 means all, null the server's default (50). */
    suspend fun history(partId: Int? = null, location: String? = null, limit: Int? = null): List<StockChange>

    /** Adds a part (its name is required) with optional initial [stock], all or nothing. */
    suspend fun addPart(fields: PartFields, stock: List<StockAmount> = emptyList()): Part

    /** Changes the fields given; the rest stays as it is. */
    suspend fun updatePart(id: Int, fields: PartFields): Part

    /** Deletes a part with its stock, history and attachments. */
    suspend fun deletePart(id: Int)

    /** Sets the amount at [location] to [quantity]. */
    suspend fun setStock(partId: Int, location: String, quantity: Int, note: String = ""): Part

    /** Adds [delta] at [location], or takes it if negative. Safe if others change stock at the same time. */
    suspend fun adjustStock(partId: Int, location: String, delta: Int, note: String = ""): Part

    /** Moves [quantity] (null: all there) from one location to another. */
    suspend fun moveStock(partId: Int, from: String, to: String, quantity: Int? = null, note: String = ""): Part

    /** Moves all of the part's stock, from every location, to [to]. */
    suspend fun moveAllStock(partId: Int, to: String, note: String = ""): Part

    /** Adds a location; missing parents are created too. */
    suspend fun addLocation(path: String, description: String = ""): Location

    /** Renames, describes or moves a location; `parent = Patch.Set(null)` moves it to the top level. */
    suspend fun updateLocation(
        id: Int,
        name: String? = null,
        description: String? = null,
        parent: Patch<String?> = Patch.Keep,
    ): Location

    /** Deletes an empty location. */
    suspend fun deleteLocation(id: Int)

    /**
     * Attaches a link. With [download], the server stores a copy of the file
     * it points to; if that fails, the link is still added, and the answer's
     * [Attachment.downloadError] says why. [kind] null: from the name.
     */
    suspend fun addLink(partId: Int, url: String, kind: AttachmentKind? = null, download: Boolean = true): Attachment

    /**
     * Uploads a file. [onProgress] gets the bytes sent so far and the total
     * (from [Upload.length]); it is called on a network thread. Cancelling the
     * coroutine cancels the upload.
     */
    suspend fun uploadAttachment(
        partId: Int,
        upload: Upload,
        kind: AttachmentKind? = null,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): Attachment

    /** Tries again to store a copy of the file a link points to. */
    suspend fun downloadAttachment(partId: Int, attachmentId: Int): Attachment

    suspend fun removeAttachment(partId: Int, attachmentId: Int)
}
