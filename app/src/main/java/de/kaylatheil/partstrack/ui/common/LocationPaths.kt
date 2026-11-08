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

package de.kaylatheil.partstrack.ui.common

/**
 * A location path as typed, in the form the API uses: names separated by
 * '/', without spaces around them and without a leading or trailing '/'
 * (" /Cabinet / Drawer 3/" is "Cabinet/Drawer 3"). Empty if nothing is left.
 */
fun normalizeLocationPath(text: String): String =
    text.split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")

/**
 * Suggestions for a location field: the paths in [paths] that contain
 * [text] (ignoring case and the spaces [normalizeLocationPath] removes), in
 * their order; all of them for an empty text.
 */
fun matchingLocations(paths: List<String>, text: String): List<String> {
    val wanted = normalizeLocationPath(text)
    if (wanted.isEmpty()) return paths
    return paths.filter { it.contains(wanted, ignoreCase = true) }
}
