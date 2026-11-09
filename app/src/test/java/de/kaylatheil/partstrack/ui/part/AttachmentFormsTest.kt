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

import de.kaylatheil.partstrack.data.api.Attachment
import de.kaylatheil.partstrack.data.api.AttachmentKind
import de.kaylatheil.partstrack.data.api.HttpPartstrackApiTest
import de.kaylatheil.partstrack.data.api.Part
import de.kaylatheil.partstrack.data.api.PartstrackJson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentFormsTest {
    @Test
    fun links() {
        assertEquals("https://www.example.com/rc0603.pdf", linkOrNull("  https://www.example.com/rc0603.pdf "))
        assertEquals("http://10.0.2.2:8765/x", linkOrNull("http://10.0.2.2:8765/x"))
        assertEquals("ftp://ftp.example.com/a.pdf", linkOrNull("ftp://ftp.example.com/a.pdf"))
        assertEquals("HTTPS://EXAMPLE.COM/", linkOrNull("HTTPS://EXAMPLE.COM/"))
        for (bad in listOf("", "example.com/a.pdf", "file:///sdcard/a.pdf", "https://", "ftp://", "https://exa mple.com", "mailto:a@b.c")) {
            assertNull(bad, linkOrNull(bad))
        }
    }

    @Test
    fun photoNames() {
        assertEquals("photo_2026-11-09_08-40-12.jpg", photoFileName(LocalDateTime.of(2026, 11, 9, 8, 40, 12)))
    }

    @Test
    fun kinds() {
        assertEquals(AttachmentKind.IMAGE, kindForUpload("image/png"))
        assertNull(kindForUpload("application/pdf")) // the server goes by the name
        assertNull(kindForUpload(null))
    }

    @Test
    fun withAttachment() {
        val part: Part = PartstrackJson.decodeFromString(Part.serializer(), HttpPartstrackApiTest.fixture("part"))
        val new = Attachment(9, "image", "photo.jpg", true, "http://x/attachments/9", null, "")
        assertEquals(part.attachments + new, part.withAttachment(new).attachments)
        val stored = part.attachments[0].copy(stored = true, url = "http://x/attachments/2")
        assertEquals(listOf(stored) + part.attachments.drop(1), part.withAttachment(stored).attachments) // in its place
    }
}
