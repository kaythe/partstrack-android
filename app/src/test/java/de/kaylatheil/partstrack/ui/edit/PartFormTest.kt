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

package de.kaylatheil.partstrack.ui.edit

import de.kaylatheil.partstrack.data.api.HttpPartstrackApiTest
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartFields
import de.kaylatheil.partstrack.data.api.PartstrackJson
import de.kaylatheil.partstrack.data.api.Patch
import de.kaylatheil.partstrack.data.api.StockAmount
import org.junit.Assert.assertEquals
import org.junit.Test

class PartFormTest {
    private val part: Part = PartstrackJson.decodeFromString(Part.serializer(), HttpPartstrackApiTest.fixture("part"))

    private fun problems(result: PartFormResult) = (result as PartFormResult.Invalid).problems

    private fun ready(result: PartFormResult) = result as PartFormResult.Ready

    // -- a new part -----------------------------------------------------------

    @Test
    fun onlyANameIsNeeded() {
        val result = ready(PartForm(name = "  Mystery part ").toNewPart())
        assertEquals(PartFields(name = "Mystery part"), result.fields)
        assertEquals(emptyList<StockAmount>(), result.stock)
    }

    @Test
    fun everythingFilledIn() {
        val form = PartForm(
            name = "10k resistor",
            description = " Thick film ",
            manufacturer = "Yageo",
            mpn = "RC0603FR-0710KL",
            category = "Resistor",
            packageName = "0603",
            notes = "line 1\nline 2",
            tags = listOf("smd"),
            tagInput = "resistor, SMD ,",
            attributes = listOf(AttributeRow(" resistance ", " 10k "), AttributeRow("", ""), AttributeRow("tolerance", "")),
            minQuantity = "50",
            location = " Cabinet / Drawer 1 ",
            quantity = "100",
        )
        val result = ready(form.toNewPart())
        assertEquals(
            PartFields(
                name = "10k resistor",
                description = "Thick film",
                manufacturer = "Yageo",
                mpn = "RC0603FR-0710KL",
                category = "Resistor",
                packageName = "0603",
                notes = "line 1\nline 2",
                tags = listOf("smd", "resistor"), // the typed tag counts; "SMD" is there already
                attributes = mapOf("resistance" to "10k", "tolerance" to ""), // an empty row is left out
                minQuantity = Patch.Set(50),
            ),
            result.fields,
        )
        assertEquals(listOf(StockAmount("Cabinet/Drawer 1", 100)), result.stock)
    }

    @Test
    fun name() {
        assertEquals(listOf(FormProblem.NameMissing), problems(PartForm(name = "  ").toNewPart()))
        assertEquals(listOf(FormProblem.NameStartsWithHash), problems(PartForm(name = " #12").toNewPart()))
        ready(PartForm(name = "Part #12").toNewPart())
    }

    @Test
    fun minimumQuantity() {
        assertEquals(Patch.Set(0), ready(PartForm(name = "x", minQuantity = "0").toNewPart()).fields.minQuantity)
        for (bad in listOf("-1", "1.5", "ten", "99999999999")) {
            assertEquals(bad, listOf(FormProblem.BadMinQuantity), problems(PartForm(name = "x", minQuantity = bad).toNewPart()))
        }
    }

    @Test
    fun initialStock() {
        assertEquals(listOf(FormProblem.QuantityMissing), problems(PartForm(name = "x", location = "Shelf").toNewPart()))
        assertEquals(listOf(FormProblem.LocationMissing), problems(PartForm(name = "x", quantity = "5").toNewPart()))
        assertEquals(listOf(FormProblem.BadQuantity), problems(PartForm(name = "x", location = "Shelf", quantity = "-5").toNewPart()))
        assertEquals(listOf(StockAmount("Shelf", 0)), ready(PartForm(name = "x", location = "/Shelf/", quantity = "0").toNewPart()).stock)
    }

    @Test
    fun attributes() {
        val form = PartForm(
            name = "x",
            attributes = listOf(
                AttributeRow("Resistance", "10k"),
                AttributeRow("", "1%"),
                AttributeRow("resistance", "20k"), // the same name, as the server compares names
                AttributeRow("Widerstand", "10k"),
                AttributeRow("WIDERSTAND", "10k"),
            ),
        )
        assertEquals(
            listOf(FormProblem.AttributeKeyMissing(1), FormProblem.AttributeKeyRepeated(2), FormProblem.AttributeKeyRepeated(4)),
            problems(form.toNewPart()),
        )
        // Only ASCII letters ignore case, as in SQLite: these are two names.
        ready(PartForm(name = "x", attributes = listOf(AttributeRow("Größe", "1"), AttributeRow("GRÖSSE", "2"))).toNewPart())
    }

    @Test
    fun allProblemsAtOnce() {
        val form = PartForm(name = "", minQuantity = "x", location = "Shelf", attributes = listOf(AttributeRow("", "v")))
        assertEquals(
            listOf(FormProblem.NameMissing, FormProblem.BadMinQuantity, FormProblem.AttributeKeyMissing(0), FormProblem.QuantityMissing),
            problems(form.toNewPart()),
        )
    }

    // -- tags -------------------------------------------------------------------

    @Test
    fun addingTags() {
        assertEquals(listOf("smd", "resistor"), addTags(listOf("smd"), " resistor "))
        assertEquals(listOf("smd", "a", "b"), addTags(listOf("smd"), "a,, b ,SMD,a"))
        assertEquals(listOf("smd"), addTags(listOf("smd"), " , "))
        assertEquals(listOf("Größe", "GRÖSSE"), addTags(listOf("Größe"), "GRÖSSE"))
    }

    // -- changing a part ------------------------------------------------------

    @Test
    fun theFormStartsWithThePart() {
        val form = PartForm.of(part)
        assertEquals("10k resistor", form.name)
        assertEquals("0603", form.packageName)
        assertEquals(listOf("resistor", "smd"), form.tags)
        assertEquals(listOf(AttributeRow("resistance", "10k"), AttributeRow("tolerance", "1%")), form.attributes)
        assertEquals("50", form.minQuantity)
        assertEquals(PartFormResult.Unchanged, form.toChanges(part))
    }

    @Test
    fun onlyWhatChangedIsSent() {
        val form = PartForm.of(part).copy(description = "Thin film", notes = " ")
        assertEquals(PartFields(description = "Thin film"), ready(form.toChanges(part)).fields)
    }

    @Test
    fun spacesAloneAreNoChange() {
        assertEquals(PartFormResult.Unchanged, PartForm.of(part).copy(name = " 10k resistor ").toChanges(part))
    }

    @Test
    fun clearingAField() {
        val form = PartForm.of(part).copy(manufacturer = "", minQuantity = "")
        assertEquals(PartFields(manufacturer = "", minQuantity = Patch.Set(null)), ready(form.toChanges(part)).fields)
    }

    @Test
    fun tagsAreSentWhole() {
        // The server replaces all tags with the list sent.
        val removed = PartForm.of(part).copy(tags = listOf("smd"))
        assertEquals(PartFields(tags = listOf("smd")), ready(removed.toChanges(part)).fields)
        val typed = PartForm.of(part).copy(tagInput = "0603")
        assertEquals(PartFields(tags = listOf("resistor", "smd", "0603")), ready(typed.toChanges(part)).fields)
        val allRemoved = PartForm.of(part).copy(tags = emptyList())
        assertEquals(PartFields(tags = emptyList()), ready(allRemoved.toChanges(part)).fields)
    }

    @Test
    fun tagsInAnotherCaseAreNoChange() {
        // The server keeps the spelling it has.
        assertEquals(PartFormResult.Unchanged, PartForm.of(part).copy(tags = listOf("SMD", "Resistor")).toChanges(part))
    }

    @Test
    fun attributesAreSentWhole() {
        val form = PartForm.of(part).copy(attributes = listOf(AttributeRow("resistance", "10k"), AttributeRow("power", "0.1W")))
        assertEquals(
            PartFields(attributes = mapOf("resistance" to "10k", "power" to "0.1W")),
            ready(form.toChanges(part)).fields,
        )
        // The order of the rows doesn't matter, but a name's spelling does (the server takes it).
        val reordered = PartForm.of(part).copy(attributes = PartForm.of(part).attributes.reversed())
        assertEquals(PartFormResult.Unchanged, reordered.toChanges(part))
        val respelled = PartForm.of(part).copy(attributes = listOf(AttributeRow("Resistance", "10k"), AttributeRow("tolerance", "1%")))
        assertEquals(
            PartFields(attributes = mapOf("Resistance" to "10k", "tolerance" to "1%")),
            ready(respelled.toChanges(part)).fields,
        )
    }

    @Test
    fun anEditIsCheckedToo() {
        assertEquals(listOf(FormProblem.NameMissing), problems(PartForm.of(part).copy(name = "").toChanges(part)))
    }
}
