package com.example

import com.example.data.HealthConnectSyncEntity
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CurrentActivitySupportTest {
    private val today = LocalDate.parse("2026-10-26")
    private val zone = ZoneId.of("Europe/London")
    private val now = Instant.parse("2026-10-26T14:45:00Z").toEpochMilli()
    private fun health() = HealthConnectSyncEntity(isConnected = true, lastSyncTimeMillis = now,
        stepsToday = 2272, sleepDurationMinutes = 396, totalCaloriesBurned = 877.6)
    @Test fun newestHealthSnapshotReplacesTheOlderSportsReadForAllCurrentCards() {
        val data = SportsData(daily = listOf(ActivityDay(today.toString(), 97, 324, readAt = now - 60_000)))
        val current = CurrentActivitySupport.resolve(health(), data, today, zone)
        assertEquals(2272L, current.steps); assertEquals(396L, current.sleepMinutes)
        assertEquals(878, CurrentActivitySupport.calories(current.calories))
    }
    @Test fun newerSportsCorrectionsMayDecreaseValuesAndMissingFieldsDoNotEraseReadableValues() {
        val data = SportsData(daily = listOf(ActivityDay(today.toString(), 90, 300, readAt = now + 60_000)))
        val corrected = CurrentActivitySupport.resolve(health(), data, today, zone)
        assertEquals(90L, corrected.steps); assertEquals(300L, corrected.sleepMinutes)
        val partial = data.copy(daily = listOf(data.daily.single().copy(steps = null)))
        assertEquals(2272L, CurrentActivitySupport.resolve(health(), partial, today, zone).steps)
    }
    @Test fun yesterdayAndDisconnectedSnapshotsCannotBecomeTodaysValuesAndZeroStepsAreValid() {
        val stale = health().copy(lastSyncTimeMillis = now - 86_400_000)
        assertNull(CurrentActivitySupport.resolve(stale, SportsData(), today, zone).steps)
        assertNull(CurrentActivitySupport.resolve(stale, SportsData(), today, zone).calories)
        assertNull(CurrentActivitySupport.resolve(health().copy(isConnected = false), SportsData(), today, zone).steps)
        val empty = CurrentActivitySupport.resolve(health().copy(stepsToday = 0, sleepDurationMinutes = 0), SportsData(), today, zone)
        assertEquals(0L, empty.steps); assertNull(empty.sleepMinutes)
    }
    @Test fun roundingIsIdenticalForTheSummaryAndDetailsAndInvalidCaloriesStayUnknown() {
        assertEquals(878, CurrentActivitySupport.calories(877.6))
        assertEquals(877, CurrentActivitySupport.calories(877.4))
        assertNull(CurrentActivitySupport.calories(Double.NaN)); assertNull(CurrentActivitySupport.calories(-1.0))
    }
}
