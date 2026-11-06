package com.jarvis.assistant

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockPin {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun create(pin: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return salt.toHex() to derive(pin, salt).toHex()
    }

    fun verify(pin: String, saltHex: String, expectedHex: String): Boolean {
        if (saltHex.isBlank() || expectedHex.isBlank()) return false
        return runCatching {
            val salt = saltHex.hexToBytes()
            val expected = expectedHex.hexToBytes()
            MessageDigest.isEqual(expected, derive(pin, salt))
        }.getOrDefault(false)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
