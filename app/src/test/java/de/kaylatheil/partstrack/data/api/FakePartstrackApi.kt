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
 * A [PartstrackApi] for ViewModel tests: every call fails the test unless a
 * subclass overrides it, e.g.
 * `object : FakePartstrackApi() { override suspend fun serverInfo() = ServerInfo("1.4.0") }`.
 */
open class FakePartstrackApi : PartstrackApi {
    private fun unexpected(): Nothing = throw AssertionError("unexpected API call")

    override suspend fun serverInfo(): ServerInfo = unexpected()
    override suspend fun checkToken(): TokenInfo = unexpected()
    override suspend fun parts(query: String, tag: String?, location: String?, category: String?, lowOnly: Boolean): List<Part> =
        unexpected()
    override suspend fun part(id: Int): Part = unexpected()
    override suspend fun locations(): List<Location> = unexpected()
    override suspend fun location(id: Int): Location = unexpected()
    override suspend fun tags(): List<TagCount> = unexpected()
    override suspend fun categories(): List<String> = unexpected()
    override suspend fun history(partId: Int?, location: String?, limit: Int?): List<StockChange> = unexpected()
    override suspend fun addPart(fields: PartFields, stock: List<StockAmount>): Part = unexpected()
    override suspend fun updatePart(id: Int, fields: PartFields): Part = unexpected()
    override suspend fun deletePart(id: Int): Unit = unexpected()
    override suspend fun setStock(partId: Int, location: String, quantity: Int, note: String): Part = unexpected()
    override suspend fun adjustStock(partId: Int, location: String, delta: Int, note: String): Part = unexpected()
    override suspend fun moveStock(partId: Int, from: String, to: String, quantity: Int?, note: String): Part = unexpected()
    override suspend fun moveAllStock(partId: Int, to: String, note: String): Part = unexpected()
    override suspend fun addLocation(path: String, description: String): Location = unexpected()
    override suspend fun updateLocation(id: Int, name: String?, description: String?, parent: Patch<String?>): Location =
        unexpected()
    override suspend fun deleteLocation(id: Int): Unit = unexpected()
    override suspend fun addLink(partId: Int, url: String, kind: AttachmentKind?, download: Boolean): Attachment = unexpected()
    override suspend fun uploadAttachment(
        partId: Int,
        upload: Upload,
        kind: AttachmentKind?,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): Attachment = unexpected()
    override suspend fun downloadAttachment(partId: Int, attachmentId: Int): Attachment = unexpected()
    override suspend fun removeAttachment(partId: Int, attachmentId: Int): Unit = unexpected()
}
