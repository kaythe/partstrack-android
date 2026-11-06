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

import java.io.IOException

/** Everything [PartstrackApi] throws, apart from cancellation. */
sealed class PartstrackException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The server answered with an error status. [serverMessage] is the server's
 * `error` text, meant to be shown to the user as it is; it is null when the
 * answer had none (e.g. an error page from a proxy).
 */
class ApiException(val status: Int, val serverMessage: String?) :
    PartstrackException(serverMessage ?: "the server answered with HTTP $status") {

    /** The token is missing, wrong or revoked. */
    val isUnauthorized: Boolean get() = status == 401

    /** The server shows a read-only copy, so changes can't be saved. */
    val isReadOnly: Boolean get() = status == 409

    val isNotFound: Boolean get() = status == 404
}

/** The server couldn't be reached, or the connection failed or timed out. */
class NetworkException(cause: IOException) :
    PartstrackException("can't reach the server: ${cause.message ?: cause.javaClass.simpleName}", cause)

/** The server answered, but not with what partstrack answers (perhaps it isn't partstrack). */
class UnexpectedResponseException(message: String, cause: Throwable? = null) : PartstrackException(message, cause)
