package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MeasurementRemovalTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deletingOneDuplicatePreservesTheOtherAndLastDeletionRemovesTheCorrespondingWeightLog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        val manual = BodyMeasurementEntity(id = 5001, date = "18.10.2026", weightKg = 61.5f)
        val health = manual.copy(id = 5002, date = "2026-10-18", source = "HEALTH_CONNECT", healthRecordId = "renpho-123")
        runBlocking {
            dao.saveUserProfile(UserProfileEntity())
            dao.insertBodyMeasurement(manual); dao.insertBodyMeasurement(health)
            dao.insertWeightLog(WeightLogEntity(id = 5001, date = manual.date, weightKg = 61.5f))
            dao.insertWeightLog(WeightLogEntity(id = 5002, date = manual.date, weightKg = 62f))
        }
        val vm = DietaViewModel(app)
        compose.setContent { Text("Test usuwania") }
        var done = false; var error: String? = null
        vm.deleteBodyMeasurement(manual.id) { error = it; done = true }
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); done }
        assertNull(error)
        assertTrue(runBlocking { dao.getAllBodyMeasurementsDirect() }.any { it.id == health.id })
        assertTrue(runBlocking { dao.getAllWeightLogs().first() }.any { it.id == 5001L })
        done = false
        vm.deleteBodyMeasurement(health.id) { error = it; done = true }
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); done }
        assertNull(error)
        assertFalse(runBlocking { dao.getAllBodyMeasurementsDirect() }.any { it.id == health.id })
        val logs = runBlocking { dao.getAllWeightLogs().first() }
        assertFalse(logs.any { it.id == 5001L }); assertTrue(logs.any { it.id == 5002L })
        assertTrue("renpho-123" in MeasurementRemovalSupport.hidden(app))
        val backup = UsabilityPreferences.export(app)
        app.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(app, backup)
        assertTrue("renpho-123" in MeasurementRemovalSupport.hidden(app))
        val imported = ScaleMeasurement("weight|renpho|renpho-123", "2026-10-18", 1, "renpho", mapOf("weight" to 61.5), "health")
        assertTrue(MeasurementRemovalSupport.hiddenScale(imported, MeasurementRemovalSupport.hidden(app)))
        assertFalse(MeasurementRemovalSupport.hiddenScale(imported.copy(id = "weight|renpho|other"), MeasurementRemovalSupport.hidden(app)))
    }
}
