package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupDiagnosticsTest {
    @Test fun reportRetainsCausesAndLocationsWithoutPrivateMessages() {
        val error = IllegalStateException("Private recipe and API key", IllegalArgumentException("secret"))
        val report = StartupDiagnostics.format(error)
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("StartupDiagnosticsTest"))
        assertFalse(report.contains("Private recipe"))
        assertFalse(report.contains("API key"))
        assertFalse(report.contains("secret"))
    }

    @Test fun localReportCanBeReadAndClearedWithoutTouchingUserSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        settings.edit().putString("unchanged", "keep").commit()
        StartupDiagnostics.save(context, IllegalStateException("never stored"))
        assertTrue(StartupDiagnostics.pending(context)!!.contains("IllegalStateException"))
        StartupDiagnostics.clear(context)
        assertNull(StartupDiagnostics.pending(context))
        assertEquals("keep", settings.getString("unchanged", null))
    }

    @Test fun nextLaunchShowsNativeReportWithoutCreatingDatabaseOrDietaViewModel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        StartupDiagnostics.save(context, IllegalStateException("private"))
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            val activity = controller.setup().get()
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val layout = content.getChildAt(0) as android.widget.LinearLayout
            assertTrue((layout.getChildAt(0) as android.widget.TextView).text.contains("raport uruchomienia"))
            // ComponentActivity itself creates SavedStateHandlesVM; the app's Room-backed VM must not start.
            assertFalse(activity.viewModelStore.keys().any {
                it.contains(com.example.viewmodel.DietaViewModel::class.java.name)
            })
            assertEquals("brak bazy", StartupDiagnostics.databaseVersion(context))
        } finally {
            controller.pause().stop().destroy()
            StartupDiagnostics.clear(context)
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
