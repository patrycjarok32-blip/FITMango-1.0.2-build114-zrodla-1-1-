package com.example.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

sealed interface HealthConnectAvailability {
    data object Available : HealthConnectAvailability
    data object NotSupported : HealthConnectAvailability
    data object ProviderUpdateRequired : HealthConnectAvailability
}

data class HealthWeightEntry(
    val recordId: String,
    val weightKg: Float,
    val time: Instant,
    val dateString: String
)

data class HealthExerciseEntry(
    val title: String,
    val caloriesBurnedKcal: Double?,
    val durationMinutes: Long,
    val startTime: Instant
)

data class HealthSyncResult(
    val stepsToday: Long? = null,
    val totalCaloriesBurnedKcal: Double? = null,
    val activeCaloriesBurnedKcal: Double? = null,
    val latestWeightKg: Float? = null,
    val latestWeightTime: Instant? = null,
    val latestWeightRecordId: String? = null,
    val weightHistory: List<HealthWeightEntry> = emptyList(),
    val sleepDurationMinutes: Long? = null,
    val sleepStartTime: Instant? = null,
    val sleepEndTime: Instant? = null,
    val sleepReadState: String = "UNKNOWN",
    val sleepReadMessage: String = "",
    val exerciseSessions: List<HealthExerciseEntry> = emptyList(),
    val lastSyncTimeMillis: Long = System.currentTimeMillis(),
    val isConnected: Boolean = true,
    val statusMessage: String = "Synchronizacja powiodła się"
)

interface HealthConnectReader {
    suspend fun checkAvailability(): HealthConnectAvailability
    suspend fun getGrantedPermissions(): Set<String>
    suspend fun syncAllHealthData(): HealthSyncResult
    suspend fun readDailyEnergy(start: LocalDate, end: LocalDate): List<com.example.viewmodel.DailyEnergy> = emptyList()
}

class HealthConnectManager(private val context: Context) : HealthConnectReader {

    suspend fun readScaleMeasurements(): Pair<List<com.example.viewmodel.ScaleMeasurement>, List<String>> = kotlinx.coroutines.withTimeout(45_000) {
        val client = healthConnectClient ?: error("Health Connect nie jest dostępny.")
        val granted = getGrantedPermissions()
        require(SCALE_PERMISSIONS.any { it in granted }) { "Zezwól na odczyt pomiarów składu ciała w Health Connect." }
        val now = Instant.now()
        val result = mutableListOf<com.example.viewmodel.ScaleMeasurement>()
        val failures = mutableListOf<String>()
        suspend fun <T : Record> read(type: kotlin.reflect.KClass<T>, key: String, time: (T) -> Instant, value: (T) -> Double) {
            if (HealthPermission.getReadPermission(type) !in granted) return
            try {
                val records = WorkoutReadSupport.readPages { token ->
                    val page = client.readRecords(ReadRecordsRequest(recordType = type,
                        timeRangeFilter = TimeRangeFilter.between(now.minus(30, ChronoUnit.DAYS), now), pageToken = token, pageSize = 100))
                    page.records to page.pageToken
                }
                records.filter { time(it) <= now }.forEach { r ->
                    if (key == "weight" && r.metadata.id in com.example.viewmodel.MeasurementRemovalSupport.hidden(context)) return@forEach
                    val origin = r.metadata.dataOrigin.packageName.ifBlank { "Health Connect" }
                    val row = com.example.viewmodel.ScaleMeasurement("$key|$origin|" + r.metadata.id.ifBlank { time(r).toString() },
                        time(r).atZone(ZoneId.systemDefault()).toLocalDate().toString(), time(r).toEpochMilli(), origin,
                        mapOf(key to value(r)), "health")
                    if (runCatching { com.example.viewmodel.ScaleMeasurementsSupport.validate(row) }.isSuccess) result.add(row)
                    else failures.add("Pominięto nieprawidłowy pomiar: $key")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures.add("Nie odczytano: ${com.example.viewmodel.ScaleMeasurementsSupport.metrics[key]?.label}") }
        }
        read(WeightRecord::class, "weight", { it.time }, { it.weight.inKilograms })
        read(BodyFatRecord::class, "fat", { it.time }, { it.percentage.value })
        read(LeanBodyMassRecord::class, "lean", { it.time }, { it.mass.inKilograms })
        read(BoneMassRecord::class, "bone", { it.time }, { it.mass.inKilograms })
        read(BodyWaterMassRecord::class, "waterKg", { it.time }, { it.mass.inKilograms })
        read(BasalMetabolicRateRecord::class, "bmr", { it.time }, { it.basalMetabolicRate.inKilocaloriesPerDay })
        result to failures.distinct()
    }

    /** Daily steps use the aggregate API (source priorities), sleep uses one source per night.
     * https://developer.android.com/health-and-fitness/health-connect/aggregate-data */
    suspend fun readActivityDays(): List<com.example.viewmodel.ActivityDay> = kotlinx.coroutines.withTimeout(45_000) {
        val client = healthConnectClient ?: error("Health Connect nie jest dostępny.")
        val granted = getGrantedPermissions()
        val stepsAllowed = HealthPermission.getReadPermission(StepsRecord::class) in granted
        val sleepAllowed = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
        require(stepsAllowed || sleepAllowed) { "Zezwól na odczyt kroków lub snu w Health Connect." }
        val now = Instant.now(); val zone = ZoneId.systemDefault(); val today = now.atZone(zone).toLocalDate()
        val from = today.minusDays(14)
        val windows = if (!sleepAllowed) emptyList() else {
            val records = SleepReadSupport.readPages { token ->
                val page = client.readRecords(ReadRecordsRequest(recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.minusDays(2).atStartOfDay(zone).toInstant(), now),
                    pageToken = token, pageSize = 100))
                page.records to page.pageToken
            }
            val asleepTypes = setOf(SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT,
                SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM)
            val awakeTypes = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
            records.filter { it.endTime <= now }.map { r -> SleepWindow(r.startTime, r.endTime, r.metadata.dataOrigin.packageName,
                r.stages.filter { it.stage in asleepTypes }.map { SleepInterval(it.startTime, it.endTime) },
                r.stages.filter { it.stage in awakeTypes }.map { SleepInterval(it.startTime, it.endTime) }) }
        }
        (0L..14L).map { offset ->
            val day = from.plusDays(offset); val start = day.atStartOfDay(zone).toInstant()
            val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), now)
            val steps = if (stepsAllowed && end > start) client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL] else null
            val sameWakeDate = windows.filter { it.end.atZone(zone).toLocalDate() == day }
            val sleep = SleepReadSupport.summarize(sameWakeDate, end, zone)
            val sleepSource = sleep?.source.orEmpty()
            com.example.viewmodel.ActivityDay(day.toString(), steps, sleep?.minutes, sleep?.estimated ?: false,
                (if (stepsAllowed) "Kroki: Health Connect (priorytety źródeł); sen: " else "Kroki: brak uprawnienia; sen: ") + sleepSource.ifBlank { "brak odczytu" }, now.toEpochMilli())
        }
    }

    suspend fun readWorkoutHistory(): List<com.example.viewmodel.SportSession> = kotlinx.coroutines.withTimeout(45_000) {
        val client = healthConnectClient ?: error("Health Connect nie jest dostępny na tym urządzeniu.")
        val granted = getGrantedPermissions()
        require(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            "Zezwól na odczyt treningów w Health Connect."
        }
        val now = Instant.now()
        val records = WorkoutReadSupport.readPages { token ->
            val page = client.readRecords(ReadRecordsRequest(recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(30, ChronoUnit.DAYS), now), pageToken = token, pageSize = 100))
            page.records to page.pageToken
        }
        records.filter { it.endTime <= now && it.endTime > it.startTime }
            .distinctBy { it.metadata.dataOrigin.packageName + "|" + it.metadata.id.ifBlank { "${it.startTime}|${it.endTime}|${it.exerciseType}" } }
            .sortedByDescending { it.startTime }.map { record ->
                suspend fun active(): Double? {
                    if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) !in granted) return null
                    return try { client.aggregate(AggregateRequest(metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime),
                        dataOriginFilter = setOf(record.metadata.dataOrigin)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }
                suspend fun distance(): Double? {
                    if (HealthPermission.getReadPermission(DistanceRecord::class) !in granted) return null
                    return try { client.aggregate(AggregateRequest(metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime),
                        dataOriginFilter = setOf(record.metadata.dataOrigin)))[DistanceRecord.DISTANCE_TOTAL]?.inKilometers }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }
                val type = when (record.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Spacer"
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Bieganie"
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Rower"
                    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Trening siłowy"
                    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Joga"
                    else -> "Trening"
                }
                com.example.viewmodel.SportSession(record.metadata.dataOrigin.packageName + "|" + record.metadata.id.ifBlank { "${record.startTime}|${record.endTime}|${record.exerciseType}" },
                    record.title?.takeIf { it.isNotBlank() }?.take(200) ?: type, record.startTime.toString(), record.endTime.toString(),
                    record.metadata.dataOrigin.packageName, active()?.takeIf { it.isFinite() && it >= 0 }, distance()?.takeIf { it.isFinite() && it >= 0 }, record.exerciseType)
            }
    }

    companion object {
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

        val SCALE_PERMISSIONS = setOf(WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class,
            BoneMassRecord::class, BodyWaterMassRecord::class, BasalMetabolicRateRecord::class).map { HealthPermission.getReadPermission(it) }.toSet()

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )

        fun createHealthConnectSettingsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            } else {
                val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                if (intent.resolveActivity(context.packageManager) != null) {
                    intent
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER_PACKAGE_NAME&url=healthconnect%3A%2F%2Fonboarding"))
                }
            }
        }
    }

    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            if (getAvailabilityStatus() == HealthConnectAvailability.Available) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun getAvailabilityStatus(): HealthConnectAvailability {
        return try {
            val status = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)
            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.ProviderUpdateRequired
                else -> HealthConnectAvailability.NotSupported
            }
        } catch (_: Throwable) {
            HealthConnectAvailability.NotSupported
        }
    }

    override suspend fun checkAvailability(): HealthConnectAvailability = getAvailabilityStatus()

    override suspend fun getGrantedPermissions(): Set<String> {
        val client = healthConnectClient ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    @OptIn(androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi::class)
    fun supportsBackgroundRead(): Boolean = try {
        healthConnectClient?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (_: Exception) { false }

    suspend fun canReadInBackground(): Boolean = supportsBackgroundRead() &&
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in getGrantedPermissions()

    fun hasAllPermissions(granted: Set<String>): Boolean {
        return REQUIRED_PERMISSIONS.all { it in granted }
    }

    fun hasAnyPermission(granted: Set<String>): Boolean {
        return REQUIRED_PERMISSIONS.any { it in granted }
    }

    override suspend fun readDailyEnergy(start: LocalDate, end: LocalDate): List<com.example.viewmodel.DailyEnergy> {
        require(!end.isBefore(start) && ChronoUnit.DAYS.between(start, end) <= 6)
        val client = healthConnectClient ?: error("Health Connect nie jest dostępny.")
        require(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in getGrantedPermissions()) {
            "Zezwól na odczyt całkowitych spalonych kalorii w Health Connect."
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now()
        return (0..ChronoUnit.DAYS.between(start, end).toInt()).mapNotNull { offset ->
            val day = start.plusDays(offset.toLong())
            if (day > today) null else {
                val from = day.atStartOfDay(zone).toInstant()
                val until = if (day == today) now else day.plusDays(1).atStartOfDay(zone).toInstant()
                val value = if (until > from) client.aggregate(AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(from, until)
                ))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories else null
                com.example.viewmodel.DailyEnergy(day.toString(), value, day < today, now.toEpochMilli())
            }
        }
    }

    override suspend fun syncAllHealthData(): HealthSyncResult {
        val client = healthConnectClient
            ?: return HealthSyncResult(
                isConnected = false,
                statusMessage = "Health Connect nie jest dostępny na tym urządzeniu."
            )

        val granted = getGrantedPermissions()
        if (!hasAnyPermission(granted)) {
            return HealthSyncResult(
                isConnected = false,
                statusMessage = "Brak przyznanych uprawnień do Health Connect."
            )
        }

        var failedReads = 0
        val zoneId = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        val now = Instant.now()
        val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)

        var stepsToday: Long? = null
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            try {
                val response = client.aggregate(AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfToday, now)))
                stepsToday = response[StepsRecord.COUNT_TOTAL] ?: 0L
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failedReads++ }
        }

        var totalCaloriesBurned: Double? = null
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
            try {
                val response = client.aggregate(AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfToday, now)))
                totalCaloriesBurned = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failedReads++ }
        }

        var activeCaloriesBurned: Double? = null
        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
            try {
                val response = client.aggregate(AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfToday, now)))
                activeCaloriesBurned = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failedReads++ }
        }

        val exerciseList = mutableListOf<HealthExerciseEntry>()
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            try {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfToday, now)
                    )
                )
                for (record in response.records) {
                    val durationMin = java.time.Duration.between(record.startTime, record.endTime).toMinutes().coerceAtLeast(1)
                    val title = record.title?.takeIf { it.isNotBlank() } ?: "Trening"
                    exerciseList.add(
                        HealthExerciseEntry(
                            title = title,
                            caloriesBurnedKcal = null,
                            durationMinutes = durationMin,
                            startTime = record.startTime
                        )
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failedReads++ }
        }

        var latestWeightKg: Float? = null
        var latestWeightTime: Instant? = null
        var latestWeightRecordId: String? = null
        val weightHistory = mutableListOf<HealthWeightEntry>()

        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
            try {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(thirtyDaysAgo, now)
                    )
                )
                val sortedRecords = response.records.sortedByDescending { it.time }
                val latest = sortedRecords.firstOrNull()
                if (latest != null) {
                    latestWeightKg = WeightPrecisionSupport.truncateKg(latest.weight.inKilograms)
                    latestWeightTime = latest.time
                    latestWeightRecordId = latest.metadata.id
                }
                for (rec in sortedRecords) {
                    val kg = WeightPrecisionSupport.truncateKg(rec.weight.inKilograms)
                    val localDate = rec.time.atZone(zoneId).toLocalDate()
                    val dateStr = String.format("%04d-%02d-%02d", localDate.year, localDate.monthValue, localDate.dayOfMonth)
                    weightHistory.add(
                        HealthWeightEntry(
                            recordId = rec.metadata.id,
                            weightKg = kg,
                            time = rec.time,
                            dateString = dateStr
                        )
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failedReads++ }
        }

        var sleepDurationMin: Long? = null
        var sleepStart: Instant? = null
        var sleepEnd: Instant? = null

        var sleepState = "NO_PERMISSION"
        var sleepMessage = "Zezwól FITMango na odczyt snu w Health Connect."
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            try {
                val from = LocalDate.now(zoneId).minusDays(7).atStartOfDay(zoneId).toInstant()
                val records = kotlinx.coroutines.withTimeout(30_000) {
                    SleepReadSupport.readPages { token ->
                        val page = client.readRecords(ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(from, now), pageToken = token, pageSize = 100))
                        page.records to page.pageToken
                    }
                }
                val asleepTypes = setOf(SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT,
                    SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM)
                val awakeTypes = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
                val summary = SleepReadSupport.summarize(records.map { record ->
                    SleepWindow(record.startTime, record.endTime, record.metadata.dataOrigin.packageName,
                        record.stages.filter { it.stage in asleepTypes }.map { SleepInterval(it.startTime, it.endTime) },
                        record.stages.filter { it.stage in awakeTypes }.map { SleepInterval(it.startTime, it.endTime) })
                }, now, zoneId)
                if (summary != null) {
                    sleepDurationMin = summary.minutes; sleepStart = summary.start; sleepEnd = summary.end
                    sleepState = "AVAILABLE"
                    val date = summary.end.atZone(zoneId).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    sleepMessage = "Sen z $date" + if (summary.estimated) " • czas sesji, bez pełnych faz snu" else " • według faz snu"
                } else {
                    sleepState = "EMPTY"
                    sleepMessage = "Brak zapisów snu w Health Connect z ostatnich 7 dni. Sprawdź udostępnianie snu w aplikacji zegarka."
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                failedReads++; sleepState = "READ_ERROR"
                sleepMessage = "Odczyt snu trwał zbyt długo. Ponów synchronizację; poprzedni wynik może być nieaktualny."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                failedReads++; sleepState = "READ_ERROR"
                sleepMessage = "Nie udało się odczytać snu. Ponów synchronizację i sprawdź uprawnienia w Health Connect."
            }
        }

        if (failedReads > 0 && failedReads >= REQUIRED_PERMISSIONS.count { it in granted }) {
            throw java.io.IOException("Nie udało się odczytać danych Health Connect. Zachowano poprzednie dane.")
        }
        return HealthSyncResult(
            stepsToday = stepsToday,
            totalCaloriesBurnedKcal = totalCaloriesBurned,
            activeCaloriesBurnedKcal = activeCaloriesBurned,
            latestWeightKg = latestWeightKg,
            latestWeightTime = latestWeightTime,
            latestWeightRecordId = latestWeightRecordId,
            weightHistory = weightHistory,
            sleepDurationMinutes = sleepDurationMin,
            sleepStartTime = sleepStart,
            sleepEndTime = sleepEnd,
            sleepReadState = sleepState,
            sleepReadMessage = sleepMessage,
            exerciseSessions = exerciseList,
            lastSyncTimeMillis = System.currentTimeMillis(),
            isConnected = true,
            statusMessage = if (failedReads == 0) "Synchronizacja powiodła się" else "Część danych niedostępna. Zachowano poprzednie odczyty."
        )
    }
}
