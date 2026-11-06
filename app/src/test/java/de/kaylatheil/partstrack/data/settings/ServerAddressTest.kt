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

package de.kaylatheil.partstrack.data.settings

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    private fun parsed(text: String) = ServerAddress.parse(text)?.toString()

    @Test
    fun completeAddresses() {
        assertEquals("http://parts.lan:8080/", parsed("http://parts.lan:8080/"))
        assertEquals("https://parts.example.com/", parsed("https://parts.example.com"))
        assertEquals("http://192.168.1.20:8080/", parsed("  http://192.168.1.20:8080  "))
        assertEquals("http://[fd00::1]:8080/", parsed("http://[fd00::1]:8080"))
    }

    @Test
    fun withoutSchemeIsHttp() {
        assertEquals("http://parts.lan:8080/", parsed("parts.lan:8080"))
        assertEquals("http://10.0.2.2:8080/", parsed("10.0.2.2:8080"))
        assertEquals("http://parts/", parsed("parts"))
    }

    @Test
    fun pathsEndWithASlash() {
        // Behind a reverse proxy, partstrack may live under a path.
        assertEquals("https://example.com/partstrack/", parsed("https://example.com/partstrack"))
        assertEquals("https://example.com/partstrack/", parsed("https://example.com/partstrack/"))
    }

    @Test
    fun pagesOfTheWebInterfaceAreNotTheServer() {
        // Query and fragment are dropped; a page's path is kept (it might be a proxy path).
        assertEquals("http://parts.lan:8080/", parsed("http://parts.lan:8080/?q=resistor#top"))
    }

    @Test
    fun notAddresses() {
        assertNull(parsed(""))
        assertNull(parsed("   "))
        assertNull(parsed("parts lan"))
        assertNull(parsed("ftp://parts.lan/"))
        assertNull(parsed("http://"))
        assertNull(parsed("http://:8080/"))
    }

    @Test
    fun plainHttp() {
        assertTrue(ServerAddress.isPlainHttp("http://parts.lan:8080/".toHttpUrl()))
        assertFalse(ServerAddress.isPlainHttp("https://parts.lan/".toHttpUrl()))
    }
}
