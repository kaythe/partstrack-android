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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the API token for storage, so it never lies in the settings file as it is. */
interface TokenCipher {
    /** Throws [GeneralSecurityException] or [ProviderException] if the key can't be used. */
    fun encrypt(token: String): String

    /** The token, or null if [stored] can't be decrypted (e.g. the key is gone). */
    fun decrypt(stored: String): String?
}

/**
 * AES-GCM with [key]. The stored form is `v1:` and the Base64 of the 12-byte
 * IV followed by the ciphertext and its 16-byte tag. The cipher picks a new
 * random IV for every encryption (the Android Keystore insists on that).
 */
class AesGcmTokenCipher(private val key: () -> SecretKey) : TokenCipher {

    override fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV size ${iv.size}" }
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)))
    }

    override fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return null
        return try {
            val data = Base64.getDecoder().decode(stored.substring(PREFIX.length))
            if (data.size < IV_BYTES + TAG_BITS / 8) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
            String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) { // not Base64
            null
        } catch (_: GeneralSecurityException) { // changed data, another key, or the key is gone
            null
        } catch (_: ProviderException) { // some devices' Keystores fail this way
            null
        }
    }

    private companion object {
        const val PREFIX = "v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** The token's key in the Android Keystore: it can't be read out of the device, only used. */
object KeystoreTokenKey {
    private const val ALIAS = "partstrack-api-token"
    private const val PROVIDER = "AndroidKeyStore"

    @Synchronized
    fun get(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
