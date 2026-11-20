package org.suraksha.kavach.family

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.suraksha.core.PassphraseHash
import org.suraksha.kavach.data.SecretStore

class FamilyCode(private val secrets: SecretStore) {
    suspend fun configured(): Boolean = withContext(Dispatchers.IO) {
        secrets.read(KEY)?.let { it.fill(0); true } ?: false
    }
    suspend fun save(input: String): Boolean = withContext(Dispatchers.Default) {
        if (!PassphraseHash.valid(input)) return@withContext false
        val record = PassphraseHash.create(input).toByteArray()
        try { withContext(Dispatchers.IO) { secrets.write(KEY, record) }; true } finally { record.fill(0) }
    }
    suspend fun matches(input: String): Boolean = withContext(Dispatchers.Default) {
        val record = withContext(Dispatchers.IO) { secrets.read(KEY) } ?: return@withContext false
        try { PassphraseHash.matches(input, record.toString(Charsets.UTF_8)) } finally { record.fill(0) }
    }
    companion object { private const val KEY = "family_code_hash_v1" }
}
