package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BodyMeasurementEntity
import com.example.data.DietaRepository
import com.example.data.UserProfileEntity
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectReader
import com.example.health.HealthExerciseEntry
import com.example.health.HealthSyncResult
import com.example.health.HealthWeightEntry
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProfileScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h2400dp")
class HealthConnectVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Room resumes these jobs on Main. Pump Robolectric's paused looper while
    // waiting, rather than treating one idle() call as completion of DB writes.
    private suspend fun awaitMainJob(job: kotlinx.coroutines.Job) {
        kotlinx.coroutines.withTimeout(10_000L) {
            while (!job.isCompleted) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(10L)
            }
            job.join()
        }
        assertFalse("Health Connect operation was cancelled before completion", job.isCancelled)
    }

    class FakeHealthConnectReader(
        var availability: HealthConnectAvailability = HealthConnectAvailability.Available,
        var grantedPermissions: Set<String> = setOf("android.permission.health.READ_STEPS", "android.permission.health.READ_WEIGHT"),
        var syncResultToReturn: HealthSyncResult = HealthSyncResult(
            stepsToday = 8450L,
            totalCaloriesBurnedKcal = 620.0,
            activeCaloriesBurnedKcal = 350.0,
            latestWeightKg = 64.5f,
            latestWeightTime = Instant.ofEpochMilli(1700000000000L),
            latestWeightRecordId = "weight_rec_001",
            weightHistory = listOf(
                HealthWeightEntry(
                    recordId = "weight_rec_001",
                    weightKg = 64.5f,
                    time = Instant.ofEpochMilli(1700000000000L),
                    dateString = "2026-10-06"
                ),
                HealthWeightEntry(
                    recordId = "weight_rec_002",
                    weightKg = 65.0f,
                    time = Instant.ofEpochMilli(1699900000000L),
                    dateString = "2026-10-05"
                )
            ),
            sleepDurationMinutes = 450L,
            exerciseSessions = listOf(
                HealthExerciseEntry(
                    title = "Bieganie poranne",
                    caloriesBurnedKcal = 320.0,
                    durationMinutes = 40L,
                    startTime = Instant.ofEpochMilli(1700000000000L)
                )
            ),
            lastSyncTimeMillis = 1700000000000L,
            isConnected = true,
            statusMessage = "Synchronizacja powiodła się"
        )
    ) : HealthConnectReader {
        override suspend fun checkAvailability(): HealthConnectAvailability = availability
        override suspend fun getGrantedPermissions(): Set<String> = grantedPermissions
        override suspend fun syncAllHealthData(): HealthSyncResult = syncResultToReturn
    }

    @Test
    fun testAvailabilityMessages_andPolishFormatting() {
        val availMsg = HealthConnectSupport.formatAvailabilityMessage(HealthConnectAvailability.Available)
        assertTrue(availMsg.contains("dostępny"))

        val updateMsg = HealthConnectSupport.formatAvailabilityMessage(HealthConnectAvailability.ProviderUpdateRequired)
        assertTrue(updateMsg.contains("wymaga aktualizacji"))

        val notSuppMsg = HealthConnectSupport.formatAvailabilityMessage(HealthConnectAvailability.NotSupported)
        assertTrue(notSuppMsg.contains("nie jest obsługiwany"))

        // Formatting of metrics
        assertEquals("8 450", HealthConnectSupport.formatStepsDisplay(8450L))
        assertEquals("Brak danych", HealthConnectSupport.formatStepsDisplay(null))

        val calFormatted = HealthConnectSupport.formatCaloriesBurnedDisplay(620.0, 350.0)
        assertTrue(calFormatted.contains("620 kcal"))
        assertTrue(calFormatted.contains("350 kcal aktywnych"))
        assertEquals("Brak danych", HealthConnectSupport.formatCaloriesBurnedDisplay(null, null))

        assertEquals("64.50 kg", HealthConnectSupport.formatWeightDisplay(64.5f))
        assertEquals("Brak danych", HealthConnectSupport.formatWeightDisplay(null))

        assertEquals("7h 30min", HealthConnectSupport.formatSleepDurationDisplay(450L))
        assertEquals("45 min", HealthConnectSupport.formatSleepDurationDisplay(45L))
        assertEquals("Brak danych", HealthConnectSupport.formatSleepDurationDisplay(null))
    }

    @Test
    fun testDuplicateMeasurementDetection() {
        val existing = listOf(
            BodyMeasurementEntity(
                id = 1,
                date = "2026-10-01",
                weightKg = 65f,
                waistCm = 70f,
                hipsCm = 95f,
                chestCm = 85f,
                armsCm = 28f,
                thighsCm = 55f,
                source = "MANUAL",
                healthRecordId = null,
                timestamp = 1699000000000L
            ),
            BodyMeasurementEntity(
                id = 2,
                date = "2026-10-02",
                weightKg = 64.8f,
                waistCm = 0f,
                hipsCm = 0f,
                chestCm = 0f,
                armsCm = 0f,
                thighsCm = 0f,
                source = "HEALTH_CONNECT",
                healthRecordId = "rec_existing_123",
                timestamp = 1699100000000L
            )
        )

        // 1. Same record ID -> duplicate
        assertTrue(
            HealthConnectSupport.isDuplicateMeasurement(
                existingMeasurements = existing,
                recordId = "rec_existing_123",
                dateString = "2026-10-02",
                timestampMillis = 1699100000000L
            )
        )

        // 2. Different ID but timestamp within 60 seconds of existing -> duplicate
        assertTrue(
            HealthConnectSupport.isDuplicateMeasurement(
                existingMeasurements = existing,
                recordId = "rec_other",
                dateString = "2026-10-02",
                timestampMillis = 1699100010000L // 10s difference
            )
        )

        // 3. New date, new ID, different timestamp -> not duplicate
        assertFalse(
            HealthConnectSupport.isDuplicateMeasurement(
                existingMeasurements = existing,
                recordId = "rec_new_456",
                dateString = "2026-10-06",
                timestampMillis = 1700000000000L
            )
        )
    }

    @Test
    fun testHealthConnectSync_persistenceAndDeduplication(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        val repository = DietaRepository(dao)

        // Insert initial manual measurement
        repository.insertBodyMeasurement(
            BodyMeasurementEntity(
                id = 10,
                date = "2026-10-01",
                weightKg = 66.0f,
                waistCm = 72f,
                hipsCm = 96f,
                chestCm = 88f,
                armsCm = 29f,
                thighsCm = 56f,
                source = "MANUAL",
                healthRecordId = null,
                timestamp = 1698000000000L
            )
        )

        repository.saveUserProfile(
            UserProfileEntity(
                id = 1,
                name = "Kasia",
                age = 29,
                heightCm = 167f,
                currentWeightKg = 66.0f,
                targetCalories = 2000
            )
        )

        val viewModel = DietaViewModel(app)
        val fakeReader = FakeHealthConnectReader()
        viewModel.healthConnect.readerOverride = fakeReader

        // Initial status check
        viewModel.healthConnect.checkStatus()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Perform synchronization
        val (syncSuccess, syncMsg) = viewModel.healthConnect.syncDirect(force = true)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        println("HEALTH_CONNECT_TEST_SYNC_RESULT=success:$syncSuccess, msg:'$syncMsg', statusMsg:'${viewModel.healthConnect.statusMessage.value}'")
        assertTrue("Sync failed: $syncMsg", syncSuccess)
        assertEquals("Synchronizacja powiodła się.", syncMsg)

        // Verify HealthConnectSyncEntity in DB
        val syncEntity = repository.healthConnectSync.first()
        assertNotNull(syncEntity)
        assertTrue(syncEntity!!.isConnected)
        assertEquals(8450L, syncEntity.stepsToday)
        assertEquals(620.0, syncEntity.totalCaloriesBurned, 0.01)
        assertEquals(350.0, syncEntity.activeCaloriesBurned, 0.01)
        assertEquals(64.5, syncEntity.latestWeightKg, 0.01)
        assertEquals(450L, syncEntity.sleepDurationMinutes)

        // Verify body measurements table contains synced items without overwriting the manual entry
        val measurements = repository.allBodyMeasurements.first()
        assertTrue(measurements.size >= 2)

        val manualEntry = measurements.find { it.id == 10L }
        assertNotNull(manualEntry)
        assertEquals("MANUAL", manualEntry!!.source)
        assertEquals(66.0f, manualEntry.weightKg)
        assertEquals(72f, manualEntry.waistCm)

        val syncedEntry = measurements.find { it.healthRecordId == "weight_rec_001" }
        assertNotNull(syncedEntry)
        assertEquals("HEALTH_CONNECT", syncedEntry!!.source)
        assertEquals(64.5f, syncedEntry.weightKg)
        assertEquals("2026-10-06", syncedEntry.date)

        // Perform second sync with same data -> should not duplicate entries
        val beforeCount = repository.allBodyMeasurements.first().size
        var secondSyncResult: Pair<Boolean, String>? = null
        awaitMainJob(viewModel.healthConnect.sync(force = true) { success, message ->
            secondSyncResult = success to message
        })
        assertEquals("Second sync must complete successfully", true to "Synchronizacja powiodła się.", secondSyncResult)
        val afterCount = repository.allBodyMeasurements.first().size
        assertEquals(beforeCount, afterCount)

        // Test Disconnect flow: should mark disconnected, preserve local measurements
        awaitMainJob(viewModel.healthConnect.disconnect())
        val syncEntityAfterDisconnect = repository.healthConnectSync.first()

        assertNotNull(syncEntityAfterDisconnect)
        assertFalse("Completed disconnect must be persisted", syncEntityAfterDisconnect!!.isConnected)

        val measurementsAfterDisconnect = repository.allBodyMeasurements.first()
        assertEquals(afterCount, measurementsAfterDisconnect.size)
    }

    @Test
    fun testComposeUi_ProfileScreen(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        val fakeReader = FakeHealthConnectReader()
        viewModel.healthConnect.readerOverride = fakeReader

        // Sync first so data exists
        viewModel.healthConnect.sync(force = true)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.setContent {
            ProfileScreen(viewModel = viewModel)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithTag("profile_tab_profile").performClick()
        composeTestRule.onNodeWithTag("health_connect_status_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("profile_content")
            .performScrollToNode(hasTestTag("profile_tile_health"))
        composeTestRule.onNodeWithTag("profile_tile_health").performClick()
        composeTestRule.onNodeWithTag("health_connect_section_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_connect_status_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_connect_last_sync_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_connect_sync_now_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_connect_manage_permissions_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_connect_disconnect_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testComposeUi_DashboardScreen(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DietaViewModel(app)
        val fakeReader = FakeHealthConnectReader()
        viewModel.healthConnect.readerOverride = fakeReader

        // Sync first so data exists
        viewModel.healthConnect.sync(force = true)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.setContent {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToAiCoach = {},
                onNavigateToRecipes = {},
                onNavigateToMealPlan = {},
                onNavigateToBarcodeScanner = {},
                onNavigateToMeasurements = {},
                onNavigateToDocuments = {}
            )
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithTag("compact_start_health").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("dashboard_health_connect_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_steps_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_calories_burned_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_weight_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_sleep_text").performScrollTo().assertIsDisplayed()
    }
}
