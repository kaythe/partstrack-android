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

package de.kaylatheil.partstrack.data.labels

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The codes as partstrack/labels.py writes them: part_url() and location_url(). */
class LabelLinkTest {
    private val server = "http://parts.lan:8080/".toHttpUrl()

    private fun parse(text: String) = LabelLink.parse(text, server)

    @Test
    fun part() {
        assertEquals(LabelLink.Part(12), parse("http://parts.lan:8080/parts/12"))
        assertEquals(LabelLink.Part(12), parse("  http://PARTS.lan:8080/parts/12/\n")) // host names ignore case
    }

    @Test
    fun location() {
        // labels.py: quote("/" + path, safe="/")
        assertEquals(LabelLink.Location("Cabinet/Drawer 3"), parse("http://parts.lan:8080/?location=/Cabinet/Drawer%203"))
        assertEquals(LabelLink.Location("Schrank/Fach ä"), parse("http://parts.lan:8080/?location=/Schrank/Fach%20%C3%A4"))
        assertEquals(LabelLink.Location("A&B/C=D"), parse("http://parts.lan:8080/?location=/A%26B/C%3DD"))
        assertEquals(LabelLink.Location("Shelf"), parse("http://parts.lan:8080/?location=Shelf"))
    }

    @Test
    fun serverBehindAPath() {
        val proxied = "https://example.com/partstrack/".toHttpUrl()
        assertEquals(LabelLink.Part(3), LabelLink.parse("https://example.com/partstrack/parts/3", proxied))
        assertEquals(LabelLink.Location("Shelf"), LabelLink.parse("https://example.com/partstrack/?location=/Shelf", proxied))
        assertEquals(
            LabelLink.OtherServer("https://example.com/"),
            LabelLink.parse("https://example.com/parts/3", proxied),
        )
    }

    @Test
    fun otherServers() {
        assertEquals(LabelLink.OtherServer("http://parts.lan:8081/"), parse("http://parts.lan:8081/parts/12"))
        assertEquals(LabelLink.OtherServer("http://other.lan:8080/"), parse("http://other.lan:8080/?location=/Shelf"))
        assertEquals(LabelLink.OtherServer("https://parts.lan:8080/"), parse("https://parts.lan:8080/parts/12"))
        assertEquals(LabelLink.OtherServer("http://parts.lan/"), parse("http://parts.lan/parts/12")) // port 80
    }

    @Test
    fun notLabels() {
        for (text in listOf(
            "hello",
            "pt_abc", // a token: not for the parts list
            "http://parts.lan:8080/",
            "http://parts.lan:8080/parts",
            "http://parts.lan:8080/parts/x",
            "http://parts.lan:8080/parts/0",
            "http://parts.lan:8080/parts/12/edit",
            "http://parts.lan:8080/?location=/",
            "https://www.example.com/datasheet.pdf",
            "WIFI:S:lab;T:WPA;P:secret;;",
        )) {
            assertEquals(text, LabelLink.Unknown(text), parse(text))
        }
    }

    @Test
    fun tokens() {
        assertEquals("pt_Ab-9_x", LabelLink.token(" pt_Ab-9_x\n"))
        assertNull(LabelLink.token("pt_"))
        assertNull(LabelLink.token("pt_with space"))
        assertNull(LabelLink.token("http://parts.lan:8080/parts/12"))
        assertNull(LabelLink.token(""))
    }
}
