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

// What the stock and move dialogs send, and how their input is checked
// before it is: the server checks again and has the last word.

package de.kaylatheil.partstrack.ui.part

import de.kaylatheil.partstrack.data.api.StockEntry
import de.kaylatheil.partstrack.ui.common.normalizeLocationPath

enum class StockMode { TAKE, ADD, SET }

/** A stock change, ready to send. Locations are normalized full paths. */
sealed interface StockRequest {
    data class Adjust(val location: String, val delta: Int, val note: String = "") : StockRequest
    data class Set(val location: String, val quantity: Int, val note: String = "") : StockRequest

    /** [quantity] null: everything at [from]. */
    data class Move(val from: String, val to: String, val quantity: Int?, val note: String = "") : StockRequest
}

/** Why a dialog's input can't be sent yet, and which field it is about. */
sealed interface StockProblem {
    /** No location yet: nothing to say, Save just stays off. */
    data object NoLocation : StockProblem

    /** None of the part is at the location it should be taken or moved from. */
    data object NotStoredThere : StockProblem

    /** Moving to where it already is. */
    data object SameLocation : StockProblem

    /** No amount yet: nothing to say either. */
    data object NoAmount : StockProblem

    /** Not a whole number, or 0 where that changes nothing. */
    data object BadAmount : StockProblem

    /** More than there is to take or move. */
    data class TooMany(val available: Int) : StockProblem
}

sealed interface StockFormResult {
    data class Ready(val request: StockRequest) : StockFormResult
    data class Invalid(val problem: StockProblem) : StockFormResult
}

/** What the part has at [location] (a normalized path), or null if it isn't stored there. */
fun List<StockEntry>.quantityAt(location: String): Int? = firstOrNull { it.location == location }?.quantity

/** A whole number of at least [min] (leading zeros and spaces allowed), or null. */
private fun parseAmount(text: String, min: Int): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || !trimmed.all { it in '0'..'9' }) return null
    return trimmed.toIntOrNull()?.takeIf { it >= min }
}

/** The stock dialog: take, add or set an amount at a location. */
fun stockRequest(mode: StockMode, location: String, amount: String, note: String, stock: List<StockEntry>): StockFormResult {
    val path = normalizeLocationPath(location)
    val there = stock.quantityAt(path)
    fun invalid(problem: StockProblem) = StockFormResult.Invalid(problem)
    if (path.isEmpty()) return invalid(StockProblem.NoLocation)
    if (mode == StockMode.TAKE && (there == null || there == 0)) return invalid(StockProblem.NotStoredThere)
    if (amount.isBlank()) return invalid(StockProblem.NoAmount)
    val value = parseAmount(amount, min = if (mode == StockMode.SET) 0 else 1) ?: return invalid(StockProblem.BadAmount)
    val cleanNote = note.trim()
    val request = when (mode) {
        StockMode.TAKE -> {
            if (value > there!!) return invalid(StockProblem.TooMany(there))
            StockRequest.Adjust(path, -value, cleanNote)
        }
        StockMode.ADD -> StockRequest.Adjust(path, value, cleanNote)
        StockMode.SET -> StockRequest.Set(path, value, cleanNote)
    }
    return StockFormResult.Ready(request)
}

/** The move dialog: [amount] blank moves everything at [from]. */
fun moveRequest(from: String, to: String, amount: String, note: String, stock: List<StockEntry>): StockFormResult {
    val target = normalizeLocationPath(to)
    val there = stock.quantityAt(from)
    fun invalid(problem: StockProblem) = StockFormResult.Invalid(problem)
    if (there == null || there == 0) return invalid(StockProblem.NotStoredThere)
    if (target.isEmpty()) return invalid(StockProblem.NoLocation)
    if (target == from) return invalid(StockProblem.SameLocation)
    val quantity = if (amount.isBlank()) {
        null
    } else {
        val value = parseAmount(amount, min = 1) ?: return invalid(StockProblem.BadAmount)
        if (value > there) return invalid(StockProblem.TooMany(there))
        value
    }
    return StockFormResult.Ready(StockRequest.Move(from, target, quantity, note.trim()))
}
