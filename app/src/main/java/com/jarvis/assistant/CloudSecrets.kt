package com.jarvis.assistant

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps an API key out of plain sight, using AES/GCM with a key that lives in the
 * Android Keystore and never leaves the device.
 *
 * If the Keystore is unavailable (a JVM test, for instance) the value is stored as it
 * is and [isProtected] says so.
 */
object SecretBox {

    private const val Alias = "jarvis_secret_box"
    private const val Sep = ":"

    fun write(prefs: SharedPreferences, name: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        prefs.edit().putString(name, encrypt(value) ?: value).apply()
    }

    fun read(prefs: SharedPreferences, name: String): String {
        val raw = prefs.getString(name, "").orEmpty()
        if (raw.isEmpty()) return ""
        return decrypt(raw) ?: raw
    }

    /** True when what is on disk is encrypted rather than plain. */
    fun isProtected(prefs: SharedPreferences, name: String): Boolean {
        val raw = prefs.getString(name, "").orEmpty()
        return raw.isNotEmpty() && decrypt(raw) != null
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(Alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                Alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        "$iv$Sep$body"
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val parts = stored.split(Sep)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, key(), spec)
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrNull()
}
