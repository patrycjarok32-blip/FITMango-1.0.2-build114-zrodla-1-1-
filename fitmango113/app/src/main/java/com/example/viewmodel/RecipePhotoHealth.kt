package com.example.viewmodel

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** A failed image remains recoverable without replacing a user's own photograph. */
object RecipePhotoHealth {
    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun failed(context: Context, url: String, value: Boolean) {
        val edit = context.applicationContext.getSharedPreferences("RecipePhotoHealth", 0).edit()
        if (value) edit.putBoolean(key(url), true) else edit.remove(key(url))
        edit.apply()
    }
    fun needsRepair(context: Context, url: String): Boolean {
        if (url.isBlank()) return true
        val local = when {
            url.startsWith("/") -> File(url)
            url.startsWith("file://") && !url.startsWith("file:///android_asset/") ->
                runCatching { File(java.net.URI(url)) }.getOrNull()
            else -> null
        }
        return local?.let { !it.isFile || it.length() == 0L } == true ||
            context.applicationContext.getSharedPreferences("RecipePhotoHealth", 0).getBoolean(key(url), false)
    }
}
