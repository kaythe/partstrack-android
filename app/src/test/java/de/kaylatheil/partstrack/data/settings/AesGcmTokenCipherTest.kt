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

import java.security.KeyStoreException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cipher with an ordinary AES key in place of the Keystore's (which only exists on a device). */
class AesGcmTokenCipherTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmTokenCipher { key }
    private val token = "pt_" + "x".repeat(43)

    @Test
    fun roundTrip() {
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
        assertEquals("pt_äöü ✓", cipher.decrypt(cipher.encrypt("pt_äöü ✓")))
    }

    @Test
    fun format() {
        val stored = cipher.encrypt(token)
        assertTrue(stored.startsWith("v1:"))
        assertFalse(token in stored)
        val bytes = Base64.getDecoder().decode(stored.removePrefix("v1:"))
        assertEquals(12 + token.length + 16, bytes.size) // IV, ciphertext, tag
    }

    @Test
    fun everyEncryptionDiffers() {
        // A new IV each time, so equal tokens don't look equal when stored.
        assertNotEquals(cipher.encrypt(token), cipher.encrypt(token))
    }

    @Test
    fun anotherKeyCannotDecrypt() {
        assertNull(AesGcmTokenCipher { newKey() }.decrypt(cipher.encrypt(token)))
    }

    @Test
    fun changedDataIsRefused() {
        val bytes = Base64.getDecoder().decode(cipher.encrypt(token).removePrefix("v1:"))
        bytes[20] = (bytes[20].toInt() xor 1).toByte()
        assertNull(cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun notOurs() {
        assertNull(cipher.decrypt(token)) // e.g. stored without encryption
        assertNull(cipher.decrypt("v1:"))
        assertNull(cipher.decrypt("v1:not base64!"))
        assertNull(cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(ByteArray(10))))
        assertNull(cipher.decrypt("v2:" + cipher.encrypt(token).removePrefix("v1:")))
    }

    @Test
    fun missingKeyStoreIsNotACrash() {
        // If the Keystore can't give a key, decrypting reports "unreadable".
        val broken = AesGcmTokenCipher { throw KeyStoreException("gone") }
        assertNull(broken.decrypt(cipher.encrypt(token)))
    }
}
