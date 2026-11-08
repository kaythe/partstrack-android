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

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPathsTest {
    @Test
    fun normalize() {
        assertEquals("Cabinet/Drawer 3", normalizeLocationPath("Cabinet/Drawer 3"))
        assertEquals("Cabinet/Drawer 3", normalizeLocationPath(" /Cabinet / Drawer 3/ "))
        assertEquals("Cabinet/Drawer 3", normalizeLocationPath("Cabinet//Drawer 3"))
        assertEquals("Shelf", normalizeLocationPath("Shelf"))
        assertEquals("", normalizeLocationPath(" / "))
        assertEquals("", normalizeLocationPath(""))
    }

    @Test
    fun spacesInsideNamesStay() {
        assertEquals("Big  cabinet/Drawer 3", normalizeLocationPath("Big  cabinet/Drawer 3"))
    }

    @Test
    fun matching() {
        val paths = listOf("Cabinet", "Cabinet/Drawer 1", "Cabinet/Drawer 2", "Shelf")
        assertEquals(paths, matchingLocations(paths, ""))
        assertEquals(paths, matchingLocations(paths, " / "))
        assertEquals(listOf("Cabinet/Drawer 1", "Cabinet/Drawer 2"), matchingLocations(paths, "drawer"))
        assertEquals(listOf("Cabinet/Drawer 1"), matchingLocations(paths, "cabinet / drawer 1"))
        assertEquals(emptyList<String>(), matchingLocations(paths, "Box"))
    }
}
