package com.example.viewmodel

import com.example.data.HealthConnectSyncEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class CurrentActivity(val steps: Long?, val sleepMinutes: Long?, val calories: Double?, val readAt: Long,
    val sleepLabel: String = "Sen (ostatni odczyt)")

/** All current-day cards resolve the same snapshots; corrections may legitimately lower a value. */
object CurrentActivitySupport {
    fun resolve(health: HealthConnectSyncEntity?, data: SportsData, today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()): CurrentActivity {
        val fresh = health?.takeIf { it.isConnected && it.lastSyncTimeMillis > 0 &&
            Instant.ofEpochMilli(it.lastSyncTimeMillis).atZone(zone).toLocalDate() == today }
        val day = data.daily.find { it.date == today.toString() }
        val preferDay = day != null && (fresh == null || day.readAt > fresh.lastSyncTimeMillis)
        val steps = if (preferDay) day?.steps ?: fresh?.stepsToday else fresh?.stepsToday ?: day?.steps
        val sleep = if (preferDay) day?.sleepMinutes ?: fresh?.sleepDurationMinutes?.takeIf { it > 0 }
            else fresh?.sleepDurationMinutes?.takeIf { it > 0 } ?: day?.sleepMinutes
        val sleepFromDay = day?.sleepMinutes != null && (preferDay || fresh?.sleepDurationMinutes?.takeIf { it > 0 } == null)
        val calories = fresh?.totalCaloriesBurned?.takeIf { it.isFinite() && it > 0 }
            ?: fresh?.activeCaloriesBurned?.takeIf { it.isFinite() && it > 0 }
        return CurrentActivity(steps, sleep, calories, maxOf(fresh?.lastSyncTimeMillis ?: 0, day?.readAt ?: 0),
            if (sleepFromDay) "Sen po pobudce dziś" else "Sen (ostatni odczyt)")
    }
    fun calories(value: Double?): Int? = value?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE }?.roundToInt()
}
