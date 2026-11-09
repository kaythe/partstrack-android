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

// Small rules for adding attachments: which links the server takes, what a
// photo is called, and which kind an upload gets.

package de.kaylatheil.partstrack.ui.part

import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.AttachmentKind
import de.kaylatheil.partstrack.data.api.Part
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * [text] as a link the server takes (its is_url: http://, https:// or
 * ftp:// with a host), without spaces around it; null if it isn't one.
 */
fun linkOrNull(text: String): String? {
    val link = text.trim()
    if (link.any { it.isWhitespace() }) return null
    val scheme = link.substringBefore("://", "").lowercase()
    val ok = when (scheme) {
        "http", "https" -> link.toHttpUrlOrNull()?.host?.isNotEmpty() == true
        // OkHttp doesn't read ftp:// links; a host after the scheme is enough.
        "ftp" -> link.substringAfter("://").substringBefore('/').isNotEmpty()
        else -> false
    }
    return link.takeIf { ok }
}

private val PHOTO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

/** What a photo taken [at] is called on the server: e.g. photo_2026-11-09_08-40-12.jpg. */
fun photoFileName(at: LocalDateTime): String = "photo_${PHOTO_TIME.format(at)}.jpg"

/** The kind of a chosen file: a picture for image types; otherwise (null) the server goes by the name. */
fun kindForUpload(mediaType: String?): AttachmentKind? =
    if (mediaType?.startsWith("image/") == true) AttachmentKind.IMAGE else null

/** The part with [attachment] added, or put in place of the one with its id. */
fun Part.withAttachment(attachment: Attachment): Part =
    if (attachments.any { it.id == attachment.id }) {
        copy(attachments = attachments.map { if (it.id == attachment.id) attachment else it })
    } else {
        copy(attachments = attachments + attachment)
    }
