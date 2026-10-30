package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.health.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomaticHealthSyncTest {
    private class Reader : HealthConnectReader {
        val calls = AtomicInteger()
        var permissions = setOf("android.permission.health.READ_STEPS", "android.permission.health.READ_WEIGHT")
        override suspend fun checkAvailability() = HealthConnectAvailability.Available
        override suspend fun getGrantedPermissions() = permissions
        override suspend fun syncAllHealthData(): HealthSyncResult {
            calls.incrementAndGet()
            val entry = HealthWeightEntry("auto-weight", 64.5f, Instant.now(), java.time.LocalDate.now().toString())
            return HealthSyncResult(stepsToday = 1500, latestWeightKg = 64.5f,
                weightHistory = listOf(entry, entry), lastSyncTimeMillis = System.currentTimeMillis())
        }
    }

    @Test fun simultaneousAutomaticRequestsSyncOnceAndPreserveManualWeight() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("fitmango_health_sync", Context.MODE_PRIVATE).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val repository = DietaRepository(db.dietaDao())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            repository.saveUserProfile(UserProfileEntity(id = 1, currentWeightKg = 70f))
            repository.saveHealthConnectSync(HealthConnectSyncEntity(id = 1, isConnected = true, lastSyncTimeMillis = 1L, latestWeightKg = 65.0))
            val reader = Reader()
            val a = HealthConnectCoordinator(repository, app, scope, MutableStateFlow(repository.userProfile.first())).apply { readerOverride = reader }
            val b = HealthConnectCoordinator(repository, app, scope, MutableStateFlow(repository.userProfile.first())).apply { readerOverride = reader }
            coroutineScope { listOf(async { a.syncAutomatically() }, async { b.syncAutomatically() }).awaitAll() }
            assertEquals(1, reader.calls.get())
            assertEquals(1500L, repository.healthConnectSync.first()!!.stepsToday)
            assertEquals(70f, repository.userProfile.first()!!.currentWeightKg, 0.001f)
            assertEquals(1, repository.allBodyMeasurements.first().size)
        } finally { scope.coroutineContext[Job]?.cancelAndJoin(); db.close() }
    }

    @Test fun disconnectedDisabledOrRevokedConnectionsNeverReadAutomatically() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("fitmango_health_sync", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val repository = DietaRepository(db.dietaDao())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reader = Reader()
            val coordinator = HealthConnectCoordinator(repository, app, scope, MutableStateFlow<UserProfileEntity?>(null)).apply { readerOverride = reader }
            coordinator.syncAutomatically()
            repository.saveHealthConnectSync(HealthConnectSyncEntity(id = 1, isConnected = false))
            coordinator.syncAutomatically()
            repository.saveHealthConnectSync(HealthConnectSyncEntity(id = 1, isConnected = true))
            prefs.edit().putBoolean("automatic", false).commit()
            coordinator.syncAutomatically()
            prefs.edit().putBoolean("automatic", true).commit()
            reader.permissions = emptySet()
            coordinator.syncAutomatically()
            assertEquals(0, reader.calls.get())
        } finally { scope.coroutineContext[Job]?.cancelAndJoin(); db.close(); prefs.edit().clear().commit() }
    }
}
