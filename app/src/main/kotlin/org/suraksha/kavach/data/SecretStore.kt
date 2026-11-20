package org.suraksha.kavach.data

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only ciphertext and IVs are written to preferences. The AES key stays in Android Keystore. */
@SuppressLint("UseKtx") // The synchronous commit result is checked before using an encryption key.
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("sealed_secrets", Context.MODE_PRIVATE)
    private val alias = context.packageName + ".local-secrets.v1"

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    @Synchronized fun read(name: String): ByteArray? {
        val stored = prefs.getString(name, null) ?: return null
        val parts = stored.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
    }

    @Synchronized fun write(name: String, value: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val ciphertext = Base64.encodeToString(cipher.doFinal(value), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        check(prefs.edit().putString(name, "$iv:$ciphertext").commit())
    }

    @Synchronized fun databaseKey(): ByteArray = read("database_key") ?: ByteArray(32).also {
        SecureRandom().nextBytes(it)
        write("database_key", it)
    }

    @Synchronized fun remove(name: String) { check(prefs.edit().remove(name).commit()) }
}
