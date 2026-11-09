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

package de.kaylatheil.partstrack.ui.locations

import de.kaylatheil.partstrack.data.api.Location
import de.kaylatheil.partstrack.data.api.Patch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFormsTest {
    private val drawer = Location(2, "Drawer 1", "Cabinet/Drawer 1", "Cabinet", "Resistors", 1, 97)
    private val top = Location(1, "Cabinet", "Cabinet", null, "", 0, 0)

    private fun ready(result: LocationFormResult) = (result as LocationFormResult.Ready).request

    @Test
    fun newAtTheTop() {
        assertEquals(LocationRequest.Add("Shelf", ""), ready(newLocation(" Shelf ", "", "")))
    }

    @Test
    fun newInside() {
        assertEquals(
            LocationRequest.Add("Cabinet/Drawer 4", "Capacitors"),
            ready(newLocation("Drawer 4", " /Cabinet/ ", " Capacitors ")),
        )
        // Parents that don't exist yet are the server's to create.
        assertEquals(LocationRequest.Add("Lab/Shelf 2/Box", ""), ready(newLocation("Box", "Lab / Shelf 2", "")))
    }

    @Test
    fun names() {
        assertEquals(LocationFormResult.Invalid(LocationProblem.NameMissing), newLocation("  ", "Cabinet", ""))
        assertEquals(LocationFormResult.Invalid(LocationProblem.NameHasSlash), newLocation("Drawer/3", "", ""))
    }

    @Test
    fun unchanged() {
        assertEquals(LocationFormResult.Unchanged, locationChanges(drawer, "Drawer 1", "Cabinet", "Resistors"))
        assertEquals(LocationFormResult.Unchanged, locationChanges(drawer, " Drawer 1 ", "/Cabinet/", " Resistors "))
        assertEquals(LocationFormResult.Unchanged, locationChanges(top, "Cabinet", "", ""))
    }

    @Test
    fun onlyWhatChangedIsSent() {
        assertEquals(LocationRequest.Change(2, name = "Drawer A"), ready(locationChanges(drawer, "Drawer A", "Cabinet", "Resistors")))
        assertEquals(LocationRequest.Change(2, description = ""), ready(locationChanges(drawer, "Drawer 1", "Cabinet", "")))
    }

    @Test
    fun moving() {
        assertEquals(
            LocationRequest.Change(2, parent = Patch.Set("Shelf")),
            ready(locationChanges(drawer, "Drawer 1", "Shelf", "Resistors")),
        )
        assertEquals(
            LocationRequest.Change(2, parent = Patch.Set(null)), // to the top level
            ready(locationChanges(drawer, "Drawer 1", " ", "Resistors")),
        )
        assertEquals(
            LocationRequest.Change(1, parent = Patch.Set("Room")),
            ready(locationChanges(top, "Cabinet", "Room", "")),
        )
    }

    @Test
    fun notIntoItself() {
        assertEquals(LocationFormResult.Invalid(LocationProblem.ParentInside), locationChanges(top, "Cabinet", "Cabinet", ""))
        assertEquals(LocationFormResult.Invalid(LocationProblem.ParentInside), locationChanges(top, "Cabinet", "Cabinet/Drawer 1", ""))
        // A sibling whose name starts the same is fine.
        assertEquals(
            LocationRequest.Change(1, parent = Patch.Set("Cabinet 2")),
            ready(locationChanges(top, "Cabinet", "Cabinet 2", "")),
        )
    }

    @Test
    fun within() {
        assertTrue(isWithin("Cabinet", top))
        assertTrue(isWithin("Cabinet/Drawer 1/Box", top))
        assertFalse(isWithin("Cabinets", top))
        assertFalse(isWithin("Shelf/Cabinet", top))
    }

    @Test
    fun depth() {
        assertEquals(0, top.depth)
        assertEquals(1, drawer.depth)
    }

    private val tree = listOf(
        top,
        drawer,
        Location(4, "Box", "Cabinet/Drawer 1/Box", "Cabinet/Drawer 1", "", 0, 0),
        Location(5, "Cabinet 2", "Cabinet 2", null, "", 0, 0),
    )

    @Test
    fun parents() {
        assertEquals(setOf("Cabinet", "Cabinet/Drawer 1"), parentPaths(tree))
    }

    @Test
    fun collapsing() {
        fun shown(vararg collapsed: String) = visibleLocations(tree, collapsed.toSet()).map { it.path }
        assertEquals(tree.map { it.path }, shown())
        // A collapsed location still shows; everything inside it, at any depth, doesn't.
        assertEquals(listOf("Cabinet", "Cabinet 2"), shown("Cabinet"))
        assertEquals(listOf("Cabinet", "Cabinet/Drawer 1", "Cabinet 2"), shown("Cabinet/Drawer 1"))
        assertEquals(listOf("Cabinet", "Cabinet 2"), shown("Cabinet", "Cabinet/Drawer 1"))
    }
}
