package com.example.viewmodel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials stay encrypted on the phone and are excluded from backups. */
class RecipePhotoSettings(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("RecipePhotoSettings", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    fun blocked(provider: String) = prefs.getLong("blocked_$provider", 0) > System.currentTimeMillis()
    fun block(provider: String, millis: Long) {
        prefs.edit().putLong("blocked_$provider", System.currentTimeMillis() + millis).commit()
    }
    fun status(message: String) { prefs.edit().putString("status", message).apply() }
    fun token(provider: String): String = try {
        val bytes = prefs.getString("token_$provider", null)
        val iv = prefs.getString("iv_$provider", null)
        if (bytes == null || iv == null) "" else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(bytes, Base64.NO_WRAP)), Charsets.UTF_8)
        }
    } catch (_: Exception) { "" }
    fun saveToken(provider: String, value: String) {
        val editor = prefs.edit().remove("blocked_$provider")
        if (value.isBlank()) editor.remove("token_$provider").remove("iv_$provider").commit()
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            editor.putString("token_$provider", Base64.encodeToString(cipher.doFinal(value.trim().toByteArray()), Base64.NO_WRAP))
                .putString("iv_$provider", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit()
        }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("FITMangoPhotoTokens", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("FITMangoPhotoTokens", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
