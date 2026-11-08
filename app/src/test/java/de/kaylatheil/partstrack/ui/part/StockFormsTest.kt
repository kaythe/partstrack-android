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

package de.kaylatheil.partstrack.ui.part

import de.kaylatheil.partstrack.data.api.StockEntry
import de.kaylatheil.partstrack.ui.part.StockFormResult.Invalid
import de.kaylatheil.partstrack.ui.part.StockFormResult.Ready
import org.junit.Assert.assertEquals
import org.junit.Test

class StockFormsTest {
    private val stock = listOf(StockEntry("Cabinet/Drawer 1", 97), StockEntry("Shelf", 5), StockEntry("Box", 0))

    private fun change(mode: StockMode, location: String, amount: String, note: String = "") =
        stockRequest(mode, location, amount, note, stock)

    @Test
    fun take() {
        assertEquals(Ready(StockRequest.Adjust("Cabinet/Drawer 1", -3)), change(StockMode.TAKE, "Cabinet/Drawer 1", "3"))
        assertEquals(Ready(StockRequest.Adjust("Shelf", -5, "for the prototype")), change(StockMode.TAKE, " Shelf ", " 5", " for the prototype "))
        assertEquals(Invalid(StockProblem.TooMany(5)), change(StockMode.TAKE, "Shelf", "6"))
        assertEquals(Invalid(StockProblem.NotStoredThere), change(StockMode.TAKE, "Cabinet/Drawer 2", "1"))
        assertEquals(Invalid(StockProblem.NotStoredThere), change(StockMode.TAKE, "Box", "1"))
        assertEquals(Invalid(StockProblem.BadAmount), change(StockMode.TAKE, "Shelf", "0"))
    }

    @Test
    fun add() {
        assertEquals(Ready(StockRequest.Adjust("Cabinet/Drawer 1", 10)), change(StockMode.ADD, "Cabinet/Drawer 1", "10"))
        // A new location: the server creates it.
        assertEquals(Ready(StockRequest.Adjust("Cabinet/Drawer 9", 10)), change(StockMode.ADD, "/Cabinet / Drawer 9/", "010"))
        assertEquals(Invalid(StockProblem.BadAmount), change(StockMode.ADD, "Shelf", "0"))
    }

    @Test
    fun set() {
        assertEquals(Ready(StockRequest.Set("Shelf", 0)), change(StockMode.SET, "Shelf", "0"))
        assertEquals(Ready(StockRequest.Set("Shelf", 120, "counted")), change(StockMode.SET, "Shelf", "120", "counted"))
        assertEquals(Ready(StockRequest.Set("Elsewhere", 4)), change(StockMode.SET, "Elsewhere", "4"))
    }

    @Test
    fun incompleteOrWrongInput() {
        assertEquals(Invalid(StockProblem.NoLocation), change(StockMode.ADD, " / ", "1"))
        assertEquals(Invalid(StockProblem.NoAmount), change(StockMode.ADD, "Shelf", " "))
        for (amount in listOf("-1", "1.5", "2e3", "x", "99999999999")) {
            assertEquals(amount, Invalid(StockProblem.BadAmount), change(StockMode.ADD, "Shelf", amount))
        }
    }

    @Test
    fun move() {
        fun move(from: String, to: String, amount: String, note: String = "") = moveRequest(from, to, amount, note, stock)
        assertEquals(Ready(StockRequest.Move("Shelf", "Cabinet/Drawer 1", null)), move("Shelf", "Cabinet/Drawer 1", ""))
        assertEquals(Ready(StockRequest.Move("Shelf", "Cabinet/Drawer 7", 2, "sorted")), move("Shelf", " Cabinet/ Drawer 7 ", "2", "sorted "))
        assertEquals(Invalid(StockProblem.TooMany(5)), move("Shelf", "Box", "6"))
        assertEquals(Invalid(StockProblem.SameLocation), move("Shelf", "/Shelf", ""))
        assertEquals(Invalid(StockProblem.NoLocation), move("Shelf", "", "1"))
        assertEquals(Invalid(StockProblem.NotStoredThere), move("Nowhere", "Shelf", ""))
        assertEquals(Invalid(StockProblem.NotStoredThere), move("Box", "Shelf", ""))
        assertEquals(Invalid(StockProblem.BadAmount), move("Shelf", "Box", "0"))
    }
}
