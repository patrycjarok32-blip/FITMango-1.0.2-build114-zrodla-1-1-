package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import com.example.ui.components.EnergyPlanPreview
import com.example.ui.theme.MojaDietaAiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnergyProfileIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val p = UserProfileEntity(age = 35, heightCm = 175f, currentWeightKg = 75f, gender = "Mężczyzna", goal = "Utrzymanie")
    @Test fun previewHasNoSideEffectsAndConfirmedSaveRejectsAStaleProfile() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        runBlocking { dao.saveUserProfile(p) }
        val vm = DietaViewModel(app)
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); vm.userProfile.value == p }
        val draft = p.copy(currentWeightKg = 76f, goal = "Redukcja")
        val settings = EnergyProfileSettings(averageSteps = 9000, sessions = 3, minutes = 60)
        val plan = EnergyCalculatorSupport.plan(draft, settings)
        compose.setContent { MojaDietaAiTheme { EnergyPlanPreview(plan) } }
        compose.onNodeWithText("Cel jedzenia: ${plan.calories} kcal").assertExists()
        assertEquals(p, runBlocking { dao.getUserProfile().first() })
        assertNull(EnergyCalculatorSupport.read(app))
        var finished = false
        var error: String? = null
        vm.saveCalculatedEnergyProfile(draft, settings, p) { error = it; finished = true }
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); finished }
        assertNull(error)
        assertEquals(plan.calories, runBlocking { dao.getUserProfile().first() }!!.targetCalories)
        assertEquals(settings, EnergyCalculatorSupport.read(app))
        assertTrue(runBlocking { dao.getAllWeightLogs().first() }.any { it.date == LocalDate.now().toString() && it.weightKg == 76f })
        finished = false
        vm.saveCalculatedEnergyProfile(p, settings, p) { error = it; finished = true }
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); finished }
        assertNotNull(error)
        assertEquals(plan.calories, runBlocking { dao.getUserProfile().first() }!!.targetCalories)
    }
    @Test fun failedRoomWriteRollsBackActivitySettingsAndIndividualModeKeepsExistingGoals() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("FITMangoUsability", 0)
        prefs.edit().clear().commit()
        val db = AppDatabase.getDatabase(app)
        val dao = db.dietaDao()
        runBlocking { dao.saveUserProfile(p) }
        val vm = DietaViewModel(app)
        compose.setContent { Text("Test zapisu") }
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); vm.userProfile.value == p }
        val previous = EnergyProfileSettings(detailed = false)
        prefs.edit().putString(EnergyCalculatorSupport.KEY, EnergyCalculatorSupport.encode(previous)).commit()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER energy_test_reject BEFORE INSERT ON user_profile BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        var done = false
        var error: String? = null
        try {
            vm.saveCalculatedEnergyProfile(p, EnergyProfileSettings(averageSteps = 9000), p) { error = it; done = true }
            compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); done }
            assertNotNull(error)
            assertEquals(previous, EnergyCalculatorSupport.read(app))
            assertEquals(p, runBlocking { dao.getUserProfile().first() })
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS energy_test_reject") }
        done = false
        vm.saveIndividualEnergyMode(previous.copy(individualPlan = true)) { error = it; done = true }
        compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); done }
        assertNull(error)
        assertEquals(p, runBlocking { dao.getUserProfile().first() })
        assertNull(WeeklyEnergySupport.maintenanceCalories(p, EnergyCalculatorSupport.read(app)))
    }
}
