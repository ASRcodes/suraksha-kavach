package org.suraksha.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PassphraseHash {
    const val ITERATIONS = 210_000
    private fun normalized(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ")
    fun valid(input: String): Boolean {
        if (input.length > 256) return false
        val text = normalized(input)
        return text.codePointCount(0, text.length) in 4..64 && text.none { it.isISOControl() }
    }
    fun create(input: String): String {
        require(valid(input))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = derive(input, salt)
        return try { "v1:$ITERATIONS:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(key) }
        finally { key.fill(0) }
    }
    fun matches(input: String, record: String): Boolean {
        if (!valid(input) || record.length > 200) return false
        val parts = record.split(':')
        if (parts.size != 4 || parts[0] != "v1" || parts[1] != ITERATIONS.toString()) return false
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            if (salt.size != 16 || expected.size != 32) return false
            val actual = derive(input, salt)
            try { MessageDigest.isEqual(expected, actual) } finally { expected.fill(0); actual.fill(0) }
        } catch (_: IllegalArgumentException) { false }
    }
    private fun derive(input: String, salt: ByteArray): ByteArray {
        val chars = normalized(input).toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }
}
