package com.example.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureKeyStorage {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "MangoFitGeminiKeyAlias"
    private const val PREFS_NAME = "MangoFitSecurePrefs"
    private const val ENCRYPTED_KEY_PREF = "encrypted_gemini_api_key"
    private const val IV_PREF = "gemini_api_key_iv"

    private var appContext: Context? = null

    // Isolated test-only hook
    private var testStoredKey: String? = null
    private var isTestingMode = false

    fun setTestingMode(enabled: Boolean) {
        isTestingMode = enabled
        if (!enabled) {
            testStoredKey = null
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun localOnly(): Boolean = appContext?.getSharedPreferences("FITMangoCostControl", Context.MODE_PRIVATE)?.getBoolean("localOnly", true) ?: true
    fun setLocalOnly(enabled: Boolean) {
        appContext?.getSharedPreferences("FITMangoCostControl", Context.MODE_PRIVATE)?.edit()?.putBoolean("localOnly", enabled)?.apply()
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun saveKey(key: String) {
        if (isTestingMode) {
            testStoredKey = key.takeIf { it.isNotBlank() }
            return
        }

        val context = appContext ?: throw IllegalStateException("SecureKeyStorage is not initialized")
        if (key.isBlank()) {
            deleteKey()
            return
        }

        try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(key.toByteArray(Charsets.UTF_8))

            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(ENCRYPTED_KEY_PREF, encryptedBase64)
                .putString(IV_PREF, ivBase64)
                .apply()
        } catch (e: Exception) {
            throw RuntimeException("Android Keystore encryption failed: ${e.message}", e)
        }
    }

    fun getKey(): String? {
        if (isTestingMode) {
            return testStoredKey
        }

        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null) ?: return null
        val ivBase64 = prefs.getString(IV_PREF, null) ?: return null

        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteKey() {
        if (isTestingMode) {
            testStoredKey = null
            return
        }

        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(ENCRYPTED_KEY_PREF)
            .remove(IV_PREF)
            .apply()
    }

    fun hasKey(): Boolean {
        val key = getKey()
        return !key.isNullOrBlank()
    }
}
