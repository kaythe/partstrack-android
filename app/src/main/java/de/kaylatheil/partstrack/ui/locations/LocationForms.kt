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

// What the location dialogs send, and how their input is checked before it
// is. The server checks again: e.g. only it knows whether a name is taken
// in that place. Missing parents are created by the server, both for a new
// location and when one is moved.

package de.kaylatheil.partstrack.ui.locations

import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Patch
import de.kaylatheil.partstrack.ui.common.normalizeLocationPath

sealed interface LocationProblem {
    /** No name yet: nothing to say, Save just stays off. */
    data object NameMissing : LocationProblem

    /** '/' separates the names in a path, so a name can't contain it. */
    data object NameHasSlash : LocationProblem

    /** Moving a location into itself or one of its sublocations. */
    data object ParentInside : LocationProblem
}

sealed interface LocationRequest {
    /** [path]: the new location's full path, parents included. */
    data class Add(val path: String, val description: String) : LocationRequest

    /** Only what changed: null (or [Patch.Keep]) leaves it as it is; a parent of null is the top level. */
    data class Change(
        val id: Int,
        val name: String? = null,
        val description: String? = null,
        val parent: Patch<String?> = Patch.Keep,
    ) : LocationRequest
}

sealed interface LocationFormResult {
    data class Ready(val request: LocationRequest) : LocationFormResult

    /** An edit that changes nothing. */
    data object Unchanged : LocationFormResult

    data class Invalid(val problem: LocationProblem) : LocationFormResult
}

private fun nameProblem(name: String): LocationProblem? = when {
    name.isEmpty() -> LocationProblem.NameMissing
    '/' in name -> LocationProblem.NameHasSlash
    else -> null
}

/** A new location called [name] in [parent] (a path, empty for the top level). */
fun newLocation(name: String, parent: String, description: String): LocationFormResult {
    val cleanName = name.trim()
    nameProblem(cleanName)?.let { return LocationFormResult.Invalid(it) }
    val parentPath = normalizeLocationPath(parent)
    val path = if (parentPath.isEmpty()) cleanName else "$parentPath/$cleanName"
    return LocationFormResult.Ready(LocationRequest.Add(path, description.trim()))
}

/** Whether [path] is [location] itself or somewhere inside it. */
fun isWithin(path: String, location: Location): Boolean = path == location.path || path.startsWith(location.path + "/")

/** Renames, describes or moves [location]; [parent] empty is the top level. */
fun locationChanges(location: Location, name: String, parent: String, description: String): LocationFormResult {
    val cleanName = name.trim()
    nameProblem(cleanName)?.let { return LocationFormResult.Invalid(it) }
    val parentPath = normalizeLocationPath(parent)
    if (parentPath.isNotEmpty() && isWithin(parentPath, location)) return LocationFormResult.Invalid(LocationProblem.ParentInside)
    val cleanDescription = description.trim()
    val request = LocationRequest.Change(
        id = location.id,
        name = cleanName.takeIf { it != location.name },
        description = cleanDescription.takeIf { it != location.description },
        parent = if (parentPath == (location.parent ?: "")) Patch.Keep else Patch.Set(parentPath.ifEmpty { null }),
    )
    return if (request == LocationRequest.Change(location.id)) LocationFormResult.Unchanged else LocationFormResult.Ready(request)
}

/** How deep [location] is: 0 at the top level. */
val Location.depth: Int get() = path.count { it == '/' }

/** The paths of the locations that have sublocations, which can be collapsed. */
fun parentPaths(locations: List<Location>): Set<String> = locations.mapNotNull { it.parent }.toSet()

/**
 * [locations] (in tree order) without those inside a collapsed location: a
 * collapsed location itself still shows, its sublocations at any depth don't.
 */
fun visibleLocations(locations: List<Location>, collapsed: Set<String>): List<Location> {
    if (collapsed.isEmpty()) return locations
    return locations.filter { location -> collapsed.none { location.path.startsWith("$it/") } }
}
