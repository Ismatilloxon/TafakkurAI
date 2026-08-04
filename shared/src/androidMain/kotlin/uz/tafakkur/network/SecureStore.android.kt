package uz.tafakkur.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecureStore] backed by an AES-GCM key that lives in the AndroidKeyStore, with the ciphertext
 * parked in a private SharedPreferences file.
 *
 * Not `EncryptedSharedPreferences`: androidx.security-crypto is deprecated, and using the
 * Keystore directly keeps the key in hardware-backed storage (StrongBox/TEE where available)
 * without adding a dependency to `shared/build.gradle.kts` — a file the host implant keeps
 * rewriting, so the fewer reasons to touch it, the better.
 */
actual class SecureStore(context: Context) : KeyValueStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("tafakkur.secure", Context.MODE_PRIVATE)

    actual override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            // Layout: [12-byte GCM IV][ciphertext+tag].
            val iv = blob.copyOfRange(0, IV_BYTES)
            val body = blob.copyOfRange(IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(body).decodeToString()
        }.getOrElse {
            // Key invalidated (device credential removed, app restored to a new device …).
            // Treat it as "no session" rather than crashing the launch path.
            prefs.edit().remove(key).apply()
            null
        }
    }

    actual override fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(value.encodeToByteArray())
        val blob = cipher.iv + body
        prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // No setUserAuthenticationRequired: the token has to be readable on a cold
                // launch before the user interacts with anything.
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "uz.tafakkur.auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
