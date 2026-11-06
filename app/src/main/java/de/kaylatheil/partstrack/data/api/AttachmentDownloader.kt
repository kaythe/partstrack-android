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

import java.io.File
import java.io.IOException
import java.net.URLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.buffer
import okio.sink

/** A stored attachment, downloaded to open it in another app. */
data class DownloadedFile(val file: File, val mediaType: String)

/**
 * Downloads attachments the server stores (`/attachments/<id>`) into
 * [folder] (a folder in the app's cache, shared with other apps through a
 * FileProvider), one subfolder per attachment, replacing an earlier copy.
 */
class AttachmentDownloader(
    private val client: OkHttpClient,
    private val folder: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun download(attachment: Attachment): DownloadedFile {
        val request = Request.Builder().url(attachment.url).build()
        val response = try {
            client.newCall(request).executeAsync()
        } catch (e: IOException) {
            throw NetworkException(e)
        }
        return withContext(ioDispatcher) {
            response.use {
                if (!it.isSuccessful) throw ApiException(it.code, null)
                val target = File(File(folder, attachment.id.toString()), safeName(attachment.name))
                try {
                    target.parentFile?.mkdirs()
                    target.sink().buffer().use { sink -> sink.writeAll(it.body.source()) }
                } catch (e: IOException) {
                    target.delete()
                    throw NetworkException(e)
                }
                val fromServer = it.body.contentType()?.let { type -> "${type.type}/${type.subtype}" }
                val mediaType = fromServer?.takeUnless { type -> type == "application/octet-stream" }
                    ?: URLConnection.guessContentTypeFromName(target.name)
                    ?: fromServer
                    ?: "application/octet-stream"
                DownloadedFile(target, mediaType)
            }
        }
    }

    companion object {
        /** A bare file name from the server's [name], so it can't point outside its folder. */
        fun safeName(name: String): String {
            val bare = name.substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it !in "<>:\"|?*" }
                .trim()
            return bare.takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "attachment"
        }
    }
}
