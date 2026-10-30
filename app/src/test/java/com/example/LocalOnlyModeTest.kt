package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalOnlyModeTest {
    @Test fun defaultModeBlocksGeminiEvenWithProvidedKey(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("FITMangoCostControl", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SecureKeyStorage.init(context)
        try {
            assertTrue(SecureKeyStorage.localOnly())
            assertTrue(callGemini("test", overrideApiKey = "test-key").exceptionOrNull()?.message.orEmpty().contains("Tryb lokalny"))
            assertTrue(diagnoseGemini("test-key").exceptionOrNull()?.message.orEmpty().contains("Tryb lokalny"))
            SecureKeyStorage.setLocalOnly(false)
            assertFalse(SecureKeyStorage.localOnly())
        } finally { prefs.edit().clear().commit() }
    }
}
