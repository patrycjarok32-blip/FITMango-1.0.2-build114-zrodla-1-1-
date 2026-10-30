package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ai.SecureKeyStorage
import com.example.ai.callGemini
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureGeminiConfigTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        SecureKeyStorage.init(app)
        SecureKeyStorage.setTestingMode(true)
        SecureKeyStorage.deleteKey()
    }

    @Test
    fun testSecureKeyStorage_saveAndRetrieve() {
        assertFalse(SecureKeyStorage.hasKey())
        assertNull(SecureKeyStorage.getKey())

        val testKey = "AIzaSyTest12345Key"
        SecureKeyStorage.saveKey(testKey)

        assertTrue(SecureKeyStorage.hasKey())
        assertEquals(testKey, SecureKeyStorage.getKey())
    }

    @Test
    fun testSecureKeyStorage_deleteKey() {
        val testKey = "AIzaSyTestDeleteKey"
        SecureKeyStorage.saveKey(testKey)
        assertTrue(SecureKeyStorage.hasKey())

        SecureKeyStorage.deleteKey()
        assertFalse(SecureKeyStorage.hasKey())
        assertNull(SecureKeyStorage.getKey())
    }

    @Test
    fun testSecureKeyStorage_saveEmptyKey() {
        SecureKeyStorage.saveKey("  ")
        assertFalse(SecureKeyStorage.hasKey())
        assertNull(SecureKeyStorage.getKey())
    }

    @Test
    fun testCallGemini_missingKey_returnsExpectedError(): Unit = runBlocking {
        // Ensure no key is set
        SecureKeyStorage.deleteKey()
        
        // Invoke callGemini with no key and verify it fails with the expected Polish message
        val result = callGemini(prompt = "Hello", overrideApiKey = "")
        assertTrue(result.isFailure)
        val exceptionMessage = result.exceptionOrNull()?.message
        assertTrue(exceptionMessage?.contains("Klucz API Gemini nie jest skonfigurowany") == true)
    }
}
