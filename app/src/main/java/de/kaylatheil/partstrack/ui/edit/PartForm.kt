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

// The add/edit form's input, how it is checked, and what it sends. The
// checks follow the server's (store.py), which checks again and has the
// last word: e.g. only the server knows whether a name is taken.

package de.kaylatheil.partstrack.ui.edit

import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartFields
import de.kaylatheil.partstrack.data.api.Patch
import de.kaylatheil.partstrack.data.api.StockAmount
import de.kaylatheil.partstrack.ui.common.normalizeLocationPath

data class AttributeRow(val key: String = "", val value: String = "")

/** Everything in the form, as typed. [location] and [quantity] are only for a new part. */
data class PartForm(
    val name: String = "",
    val description: String = "",
    val manufacturer: String = "",
    val mpn: String = "",
    val category: String = "",
    val packageName: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    /** A tag being typed; it counts when saving, as if it had been added. */
    val tagInput: String = "",
    val attributes: List<AttributeRow> = emptyList(),
    val minQuantity: String = "",
    val location: String = "",
    val quantity: String = "",
) {
    /** The tags, with the one being typed. */
    val allTags: List<String> get() = addTags(tags, tagInput)

    companion object {
        fun of(part: Part) = PartForm(
            name = part.name,
            description = part.description,
            manufacturer = part.manufacturer,
            mpn = part.mpn,
            category = part.category,
            packageName = part.packageName,
            notes = part.notes,
            tags = part.tags,
            attributes = part.attributes.map { (key, value) -> AttributeRow(key, value) },
            minQuantity = part.minQuantity?.toString() ?: "",
        )
    }
}

/** Why the form can't be saved, and where. */
sealed interface FormProblem {
    data object NameMissing : FormProblem

    /** The server reads "#12" as a part id. */
    data object NameStartsWithHash : FormProblem

    data object BadMinQuantity : FormProblem

    /** An amount for the initial stock, but no location. */
    data object LocationMissing : FormProblem

    /** A location for the initial stock, but no amount. */
    data object QuantityMissing : FormProblem

    data object BadQuantity : FormProblem

    /** The attribute in row [row] has a value but no name. */
    data class AttributeKeyMissing(val row: Int) : FormProblem

    /** The attribute in row [row] has the name of an earlier row (names ignore case). */
    data class AttributeKeyRepeated(val row: Int) : FormProblem
}

sealed interface PartFormResult {
    /** Ready to send: the fields (only those that changed, for an edit) and a new part's stock. */
    data class Ready(val fields: PartFields, val stock: List<StockAmount>) : PartFormResult

    /** An edit that changes nothing: there is nothing to send. */
    data object Unchanged : PartFormResult

    data class Invalid(val problems: List<FormProblem>) : PartFormResult
}

/**
 * Compares as the server's names do (SQLite's NOCASE): ASCII letters
 * ignore case, other letters don't.
 */
internal fun nocase(text: String): String = buildString(text.length) {
    for (c in text) append(if (c in 'A'..'Z') c + ('a' - 'A') else c)
}

/**
 * [tags] with those in [text] added: commas separate several, spaces
 * around them don't count, and a tag already there (ignoring case) isn't
 * added again.
 */
fun addTags(tags: List<String>, text: String): List<String> {
    val result = tags.toMutableList()
    val seen = tags.map(::nocase).toMutableSet()
    for (tag in text.split(',').map { it.trim() }) {
        if (tag.isNotEmpty() && seen.add(nocase(tag))) result += tag
    }
    return result
}

/** A whole number of at least 0 (spaces around it allowed), or null. */
private fun parseCount(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || !trimmed.all { it in '0'..'9' }) return null
    return trimmed.toIntOrNull()
}

/** The problems of [form] that don't depend on whether it adds or changes a part. */
private fun commonProblems(form: PartForm): MutableList<FormProblem> {
    val problems = mutableListOf<FormProblem>()
    val name = form.name.trim()
    when {
        name.isEmpty() -> problems += FormProblem.NameMissing
        name.startsWith('#') -> problems += FormProblem.NameStartsWithHash
    }
    if (form.minQuantity.isNotBlank() && parseCount(form.minQuantity) == null) problems += FormProblem.BadMinQuantity
    val keys = mutableSetOf<String>()
    form.attributes.forEachIndexed { row, attribute ->
        val key = attribute.key.trim()
        when {
            key.isEmpty() && attribute.value.isNotBlank() -> problems += FormProblem.AttributeKeyMissing(row)
            key.isNotEmpty() && !keys.add(nocase(key)) -> problems += FormProblem.AttributeKeyRepeated(row)
        }
    }
    return problems
}

/** The attributes to send: rows without a name (and so without a value) are left out. */
private fun attributeMap(form: PartForm): Map<String, String> =
    form.attributes.filter { it.key.isNotBlank() }.associate { it.key.trim() to it.value.trim() }

private fun minQuantity(form: PartForm): Int? = form.minQuantity.takeIf { it.isNotBlank() }?.let(::parseCount)

/** A new part: everything filled in, and the initial stock if a location is given. */
fun PartForm.toNewPart(): PartFormResult {
    val problems = commonProblems(this)
    val location = normalizeLocationPath(location)
    val quantity = parseCount(this.quantity)
    when {
        location.isEmpty() && this.quantity.isNotBlank() -> problems += FormProblem.LocationMissing
        location.isNotEmpty() && this.quantity.isBlank() -> problems += FormProblem.QuantityMissing
        this.quantity.isNotBlank() && quantity == null -> problems += FormProblem.BadQuantity
    }
    if (problems.isNotEmpty()) return PartFormResult.Invalid(problems)
    fun text(value: String) = value.trim().ifEmpty { null }
    val fields = PartFields(
        name = name.trim(),
        description = text(description),
        manufacturer = text(manufacturer),
        mpn = text(mpn),
        category = text(category),
        packageName = text(packageName),
        notes = text(notes),
        tags = allTags.ifEmpty { null },
        attributes = attributeMap(this).ifEmpty { null },
        minQuantity = minQuantity(this)?.let { Patch.Set(it) } ?: Patch.Keep,
    )
    val stock = if (location.isEmpty()) emptyList() else listOf(StockAmount(location, quantity!!))
    return PartFormResult.Ready(fields, stock)
}

/**
 * A change to [original]: only the fields that differ from it, so a change
 * made meanwhile by someone else to another field stays.
 */
fun PartForm.toChanges(original: Part): PartFormResult {
    val problems = commonProblems(this)
    if (problems.isNotEmpty()) return PartFormResult.Invalid(problems)
    fun changed(value: String, before: String) = value.trim().takeIf { it != before }
    val tags = allTags
    val attributes = attributeMap(this)
    // The server keeps a tag's spelling when it is given again in another case,
    // but takes an attribute name's new spelling: so only the latter is a change.
    val sameTags = tags.map(::nocase).toSet() == original.tags.map(::nocase).toSet()
    val sameAttributes = attributes == original.attributes
    val min = minQuantity(this)
    val fields = PartFields(
        name = changed(name, original.name),
        description = changed(description, original.description),
        manufacturer = changed(manufacturer, original.manufacturer),
        mpn = changed(mpn, original.mpn),
        category = changed(category, original.category),
        packageName = changed(packageName, original.packageName),
        notes = changed(notes, original.notes),
        tags = tags.takeUnless { sameTags },
        attributes = attributes.takeUnless { sameAttributes },
        minQuantity = if (min == original.minQuantity) Patch.Keep else Patch.Set(min),
    )
    return if (fields == PartFields()) PartFormResult.Unchanged else PartFormResult.Ready(fields, emptyList())
}
