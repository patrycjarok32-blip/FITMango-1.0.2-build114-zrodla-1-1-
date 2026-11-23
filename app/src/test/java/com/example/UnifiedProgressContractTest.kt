package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.DietitianControlSettings
import com.example.viewmodel.DietitianControlStore
import com.example.viewmodel.ActivityRecoverySupport
import com.example.viewmodel.SportSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression checks for opt-in dietitian controls and deduplicated activity sources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UnifiedProgressContractTest {
    @Test fun userConsentModesPersistAndNeverSilentlyChange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modes = listOf("approve", "manual", "off")
        modes.forEach { mode ->
            val settings = DietitianControlSettings(mode, false)
            DietitianControlStore.save(context, settings)
            assertEquals(settings, DietitianControlStore.read(context))
        }
        DietitianControlStore.save(context, DietitianControlSettings())
    }

    @Test fun invalidAdviceModeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DietitianControlSettings("automatic", true)
        }
    }

    @Test fun duplicateWatchActivitiesAreNotCountedAsSeparateSessions() {
        val item = SportSession("watch|123", "Bieg", "2026-11-21T09:00:00Z",
            "2026-11-21T09:45:00Z", "Health Connect")
        assertEquals(1, ActivityRecoverySupport.unique(listOf(item, item)).size)
    }
}
