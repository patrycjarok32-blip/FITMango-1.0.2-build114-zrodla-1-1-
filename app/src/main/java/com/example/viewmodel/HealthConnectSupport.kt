package com.example.viewmodel

import android.app.Application
import android.content.Context
import com.example.health.HealthSyncScheduler
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import com.example.data.BodyMeasurementEntity
import com.example.data.DietaRepository
import com.example.data.HealthConnectSyncEntity
import com.example.data.UserProfileEntity
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.health.HealthConnectReader
import com.example.health.HealthSyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object HealthConnectSupport {

    fun formatAvailabilityMessage(availability: HealthConnectAvailability): String {
        return when (availability) {
            HealthConnectAvailability.Available -> "Health Connect jest dostępny i gotowy do połączenia."
            HealthConnectAvailability.ProviderUpdateRequired -> "Usługa Health Connect wymaga aktualizacji ze sklepu Google Play."
            HealthConnectAvailability.NotSupported -> "Health Connect nie jest obsługiwany na tym urządzeniu."
        }
    }

    fun formatConnectionStatus(isConnected: Boolean, lastSyncTimeMillis: Long): String {
        return if (isConnected) {
            if (lastSyncTimeMillis > 0) {
                val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastSyncTimeMillis))
                "Połączono (ostatnia synchronizacja: $timeStr)"
            } else {
                "Połączono z Health Connect"
            }
        } else {
            "Niepołączono z Health Connect"
        }
    }

    fun formatLastSyncTime(lastSyncTimeMillis: Long): String {
        return if (lastSyncTimeMillis > 0) {
            SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()).format(Date(lastSyncTimeMillis))
        } else {
            "Brak wcześniejszych synchronizacji"
        }
    }

    fun formatStepsDisplay(steps: Long?): String {
        return if (steps != null && steps >= 0) {
            String.format(Locale.getDefault(), "%,d", steps).replace(',', ' ')
        } else {
            "Brak danych"
        }
    }

    fun formatCaloriesBurnedDisplay(totalCalories: Double?, activeCalories: Double?): String {
        return when {
            totalCalories != null && totalCalories > 0 -> {
                val totalInt = totalCalories.roundToInt()
                if (activeCalories != null && activeCalories > 0) {
                    val activeInt = activeCalories.roundToInt()
                    "$totalInt kcal ($activeInt kcal aktywnych)"
                } else {
                    "$totalInt kcal"
                }
            }
            activeCalories != null && activeCalories > 0 -> {
                "${activeCalories.roundToInt()} kcal (aktywne)"
            }
            else -> "Brak danych"
        }
    }

    fun formatWeightDisplay(weightKg: Float?): String {
        return if (weightKg != null && weightKg > 0f) {
            String.format(Locale.getDefault(), "%.2f kg", com.example.health.WeightPrecisionSupport.truncateKg(weightKg))
        } else {
            "Brak danych"
        }
    }

    fun formatSleepDurationDisplay(minutes: Long?): String {
        return if (minutes != null && minutes > 0) {
            val hours = minutes / 60
            val remMin = minutes % 60
            if (hours > 0) {
                "${hours}h ${remMin}min"
            } else {
                "${remMin} min"
            }
        } else {
            "Brak danych"
        }
    }

    /**
     * Determines whether a Health Connect weight entry is already present in existing measurements.
     * Checks record identifier first, then timestamp (within 60s), then exact date match with HEALTH_CONNECT source.
     */
   /**
 * Rozpoznaje duplikaty pomiarów wagi.
 * Najpierw sprawdza identyfikator rekordu, a następnie
 * porównuje czas i wartość wyłącznie dla Health Connect.
 */
fun isDuplicateMeasurement(
    existingMeasurements: List<BodyMeasurementEntity>,
    recordId: String,
    dateString: String,
    timestampMillis: Long,
    weightKg: Float
): Boolean {
    return existingMeasurements.any { measurement ->
        val sameRecordId =
            recordId.isNotBlank() &&
                !measurement.healthRecordId.isNullOrBlank() &&
                measurement.healthRecordId == recordId

        val sameHealthConnectMeasurement =
            measurement.source == "HEALTH_CONNECT" &&
                measurement.timestamp > 0 &&
                timestampMillis > 0 &&
                kotlin.math.abs(measurement.timestamp - timestampMillis) < 60_000 &&
                kotlin.math.abs(measurement.weightKg - weightKg) < 0.01f

        sameRecordId || sameHealthConnectMeasurement
    }
}
        }
    }
}

class HealthConnectCoordinator(
    private val repository: DietaRepository,
    private val application: Application,
    private val scope: CoroutineScope,
    private val userProfileFlow: StateFlow<UserProfileEntity?>
) {
    val healthConnectSync: StateFlow<HealthConnectSyncEntity?> = repository.healthConnectSync
        .stateIn(scope, SharingStarted.Eagerly, null)

    val weeklyEnergy = WeeklyEnergyDiary(application, scope)
    private val _weeklyEnergyLoading = MutableStateFlow(false)
    val weeklyEnergyLoading = _weeklyEnergyLoading.asStateFlow()
    private val _weeklyEnergyMessage = MutableStateFlow<String?>(null)
    val weeklyEnergyMessage = _weeklyEnergyMessage.asStateFlow()
    private var weeklyReadJob: Job? = null
    fun loadWeeklyEnergy(start: java.time.LocalDate, end: java.time.LocalDate) {
        val previousRead = weeklyReadJob
        weeklyReadJob = scope.launch {
            previousRead?.cancelAndJoin()
            _weeklyEnergyLoading.value = true; _weeklyEnergyMessage.value = null
            try {
                val reader = readerOverride ?: manager
                if (reader.checkAvailability() != HealthConnectAvailability.Available) error("Połącz Health Connect w Profilu, aby pobrać spalanie.")
                val rows = reader.readDailyEnergy(start, end)
                weeklyEnergy.saveEnergy(rows)
                if (rows.none { it.totalKcal != null }) _weeklyEnergyMessage.value = "Brak danych całkowitego spalania dla tego tygodnia."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _weeklyEnergyMessage.value = e.message ?: "Nie udało się odświeżyć spalania. Zachowano wcześniejsze odczyty." }
            finally { _weeklyEnergyLoading.value = false }
        }
    }

    val manager by lazy { HealthConnectManager(application) }
    var readerOverride: HealthConnectReader? = null

    private val _availability = MutableStateFlow<HealthConnectAvailability>(HealthConnectAvailability.NotSupported)
    val availability: StateFlow<HealthConnectAvailability> = _availability.asStateFlow()

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Niepołączono z Health Connect")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    companion object {
        // Shared by foreground coordinators and the periodic worker.
        internal val syncMutex = Mutex()
        const val AUTO_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
    private val syncPrefs = application.getSharedPreferences("fitmango_health_sync", Context.MODE_PRIVATE)
    private val _sleepReadMessage = MutableStateFlow(syncPrefs.getString("sleepMessage", "").orEmpty())
    val sleepReadMessage: StateFlow<String> = _sleepReadMessage.asStateFlow()
    private val _automaticSync = MutableStateFlow(syncPrefs.getBoolean("automatic", true))
    val automaticSync: StateFlow<Boolean> = _automaticSync.asStateFlow()

    fun setAutomaticSync(enabled: Boolean) {
        syncPrefs.edit().putBoolean("automatic", enabled).apply()
        _automaticSync.value = enabled
        if (!enabled) HealthSyncScheduler.cancel(application)
        else scope.launch { syncAutomatically() }
    }

    fun requestBackgroundRead(onLaunchPermissions: (Set<String>) -> Unit) {
        scope.launch {
            if (!manager.supportsBackgroundRead()) {
                _statusMessage.value = "Ten telefon nie obsługuje odczytu w tle. Dane odświeżamy automatycznie podczas używania aplikacji."
            } else {
                onLaunchPermissions(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND))
            }
        }
    }

    fun onBackgroundPermissionsResult() {
        scope.launch {
            val granted = manager.getGrantedPermissions()
            _grantedPermissions.value = granted
            if (manager.canReadInBackground() && syncPrefs.getBoolean("automatic", true)) {
                HealthSyncScheduler.schedule(application)
                _statusMessage.value = "Automatyczna synchronizacja w tle włączona."
                syncAutomatically()
            } else {
                _statusMessage.value = "Brak zgody na odczyt w tle. Automatyczne odświeżanie działa podczas używania aplikacji."
            }
        }
    }

    suspend fun syncAutomatically() {
        if (!syncPrefs.getBoolean("automatic", true) || repository.healthConnectSync.first()?.isConnected != true) return
        val reader = readerOverride ?: manager
        if (reader.checkAvailability() != HealthConnectAvailability.Available ||
            reader.getGrantedPermissions().none { it in HealthConnectManager.REQUIRED_PERMISSIONS }) return
        if (readerOverride == null && manager.canReadInBackground()) HealthSyncScheduler.schedule(application)
        syncDirect(automatic = true)
    }


    fun checkStatus() {
        scope.launch {
            val reader = readerOverride ?: manager
            val avail = reader.checkAvailability()
            _availability.value = avail
            val granted = reader.getGrantedPermissions()
            _grantedPermissions.value = granted
            val currentSync = healthConnectSync.value
            _statusMessage.value = when {
                avail is HealthConnectAvailability.NotSupported ->
                    "Health Connect nie jest obsługiwany na tym urządzeniu."
                avail is HealthConnectAvailability.ProviderUpdateRequired ->
                    "Usługa Health Connect wymaga aktualizacji ze sklepu Google Play."
                currentSync?.isConnected == true ->
                    HealthConnectSupport.formatConnectionStatus(true, currentSync.lastSyncTimeMillis)
                granted.isNotEmpty() ->
                    "Połączono z Health Connect"
                else ->
                    "Health Connect jest dostępny. Kliknij \"Połącz z Health Connect\"."
            }
        }
    }

    fun requestConnect(onLaunchPermissions: (Set<String>) -> Unit) {
        scope.launch {
            val reader = readerOverride ?: manager
            val avail = reader.checkAvailability()
            _availability.value = avail
            when (avail) {
                is HealthConnectAvailability.NotSupported -> {
                    _statusMessage.value = "Health Connect nie jest obsługiwany na tym urządzeniu."
                }
                is HealthConnectAvailability.ProviderUpdateRequired -> {
                    _statusMessage.value = "Usługa Health Connect wymaga aktualizacji ze sklepu Google Play."
                }
                is HealthConnectAvailability.Available -> {
                    syncPrefs.edit().putBoolean("automatic", true).apply()
                    _automaticSync.value = true
                    onLaunchPermissions(HealthConnectManager.REQUIRED_PERMISSIONS)
                }
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        _grantedPermissions.value = granted
        if (granted.isNotEmpty()) {
            _statusMessage.value = "Uprawnienia przyznane. Trwa synchronizacja..."
            sync(force = true) { success, _ ->
                if (success && readerOverride == null && syncPrefs.getBoolean("automatic", true)) {
                    scope.launch { if (manager.canReadInBackground()) HealthSyncScheduler.schedule(application) }
                }
            }
        } else {
            _statusMessage.value = "Brak przyznanych uprawnień do Health Connect."
        }
    }

    fun sync(force: Boolean = false, onComplete: ((Boolean, String) -> Unit)? = null): Job {
        return scope.launch {
            val (success, msg) = syncDirect(force)
            onComplete?.invoke(success, msg)
        }
    }

    suspend fun syncDirect(force: Boolean = false, automatic: Boolean = false): Pair<Boolean, String> {
        syncMutex.lock()
        _isSyncing.value = true
        return try {
            val previousSync = repository.healthConnectSync.first()
            if (automatic && (!syncPrefs.getBoolean("automatic", true) || previousSync?.isConnected != true)) return Pair(true, "Automatyczna synchronizacja wyłączona.")
            if (automatic && System.currentTimeMillis() - (previousSync?.lastSyncTimeMillis ?: 0L) in 0 until AUTO_SYNC_INTERVAL_MS) return Pair(true, "Dane są aktualne.")
            val reader = readerOverride ?: manager
            val syncResult = reader.syncAllHealthData()

            if (syncResult.isConnected) {
                val today = java.time.LocalDate.now()
                val previousIsToday = previousSync?.lastSyncTimeMillis?.let {
                    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
                } == true
                val syncEntity = HealthConnectSyncEntity(
                    id = 1,
                    isConnected = true,
                    lastSyncTimeMillis = syncResult.lastSyncTimeMillis,
                    stepsToday = syncResult.stepsToday ?: (if (previousIsToday) previousSync?.stepsToday else null) ?: 0L,
                    totalCaloriesBurned = syncResult.totalCaloriesBurnedKcal ?: (if (previousIsToday) previousSync?.totalCaloriesBurned else null) ?: 0.0,
                    activeCaloriesBurned = syncResult.activeCaloriesBurnedKcal ?: (if (previousIsToday) previousSync?.activeCaloriesBurned else null) ?: 0.0,
                    latestWeightKg = syncResult.latestWeightKg?.toDouble() ?: previousSync?.latestWeightKg ?: 0.0,
                    sleepDurationMinutes = if (syncResult.sleepReadState in setOf("NO_PERMISSION", "EMPTY")) 0L else
                        syncResult.sleepDurationMinutes ?: (if (previousIsToday) previousSync?.sleepDurationMinutes else null) ?: 0L,
                    syncStatusMessage = syncResult.statusMessage
                )
                repository.saveHealthConnectSync(syncEntity)
                if (syncResult.sleepReadMessage.isNotBlank()) {
                    syncPrefs.edit().putString("sleepMessage", syncResult.sleepReadMessage).apply()
                    _sleepReadMessage.value = syncResult.sleepReadMessage
                }

                // Synchronize weight measurements without duplicates
                com.example.data.PersonalDataGate.mutex.withLock {
                val existingMeasurements = repository.getAllBodyMeasurementsDirect().toMutableList()
                for (wEntry in syncResult.weightHistory) {
                    if (wEntry.recordId in MeasurementRemovalSupport.hidden(application)) continue
                    val isDup = HealthConnectSupport.isDuplicateMeasurement(
                        existingMeasurements = existingMeasurements,
                        recordId = wEntry.recordId,
                        dateString = wEntry.dateString,
                        timestampMillis = wEntry.time.toEpochMilli()
                    )
                    if (!isDup) {
                        val measurement = BodyMeasurementEntity(
                                date = wEntry.dateString,
                                weightKg = wEntry.weightKg,
                                waistCm = 0f,
                                hipsCm = 0f,
                                chestCm = 0f,
                                armsCm = 0f,
                                thighsCm = 0f,
                                source = "HEALTH_CONNECT",
                                healthRecordId = wEntry.recordId,
                                timestamp = wEntry.time.toEpochMilli()
                            )
                        repository.insertBodyMeasurement(measurement)
                        existingMeasurements.add(measurement)
                    }
                }
                }

                // Update user profile weight if new weight is provided
                syncResult.latestWeightKg?.let { newWeight ->
                    val currentProfile = repository.userProfile.first()
                    if (currentProfile != null && (currentProfile.currentWeightKg <= 0f || force || (automatic && previousSync?.latestWeightKg?.let { kotlin.math.abs(currentProfile.currentWeightKg - it.toFloat()) < 0.01f } == true))) {
                        repository.saveUserProfile(currentProfile.copy(currentWeightKg = newWeight))
                    }
                }

                if (readerOverride == null && ScaleAutoSyncSupport.enabled(application)) {
                    try { ScaleAutoSyncSupport.sync(application) }
                    catch (_: kotlinx.coroutines.TimeoutCancellationException) { /* A slow scale provider must not cancel activity synchronization. */ }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Keep the completed activity sync and previous scale values. */ }
                }

                val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(syncResult.lastSyncTimeMillis))
                _statusMessage.value = "Połączono (ostatnia synchronizacja: $timeStr)" + if (syncResult.statusMessage != "Synchronizacja powiodła się") " • ${syncResult.statusMessage}" else ""
                Pair(true, "Synchronizacja powiodła się.")
            } else {
                _statusMessage.value = syncResult.statusMessage
                Pair(false, syncResult.statusMessage)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val errMsg = "Błąd synchronizacji: ${e.message ?: "Nieznany błąd"}"
            _statusMessage.value = errMsg
            Pair(false, errMsg)
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }

    fun disconnect(onComplete: (() -> Unit)? = null): Job {
        syncPrefs.edit().putBoolean("automatic", false).apply()
        _automaticSync.value = false
        HealthSyncScheduler.cancel(application)
        return scope.launch {
            syncMutex.lock()
            try {
            val current = repository.healthConnectSync.first()
            if (current != null) {
                repository.saveHealthConnectSync(current.copy(isConnected = false, syncStatusMessage = "Rozłączono"))
            } else {
                repository.saveHealthConnectSync(HealthConnectSyncEntity(id = 1, isConnected = false, syncStatusMessage = "Rozłączono"))
            }
            _grantedPermissions.value = emptySet()
            syncPrefs.edit().remove("sleepMessage").apply()
            _sleepReadMessage.value = ""
            _statusMessage.value = "Odłączono od Health Connect. Lokalne pomiary zostały zachowane."
            onComplete?.invoke()
            } finally { syncMutex.unlock() }
        }
    }

    fun refreshDashboard() { scope.launch { syncAutomatically() } }
}
