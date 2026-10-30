package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActivityRecoveryTest {
    private val zone = ZoneId.of("Europe/London")
    private val day = LocalDate.parse("2026-10-20")
    private val now = Instant.parse("2026-10-21T08:00:00Z")
    private fun session(id: String, start: String = "2026-10-20T10:00:00Z", end: String = "2026-10-20T11:00:00Z", type: Int = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING) =
        SportSession(id, "Spacer", start, end, "watch", 150.0, 3.0, type)

    @Test fun duplicateAcrossSourcesIsExcludedAndOverlappingTimeIsNotAddedTwice() {
        val original = session("zepp|1")
        val copy = session("manual|2").copy(source = "Wpis ręczny", activeKcal = null)
        val overlapping = session("watch|3", "2026-10-20T10:30:00Z", "2026-10-20T11:30:00Z", 56).copy(title = "Rower")
        val data = SportsData(history = listOf(copy, original, overlapping))
        assertEquals(1, ActivityRecoverySupport.duplicateCount(data.history))
        assertFalse(ActivityRecoverySupport.unique(data.history).any { it.id == copy.id })
        val summary = ActivityRecoverySupport.summary(data, day, day, now, zone)
        assertEquals(90L, summary.minutes)
        assertEquals(2, summary.sessions)
        assertEquals(1, summary.activeDays)
        assertEquals(1, summary.walks)
        assertEquals(listOf(150.0, 150.0), ActivityRecoverySupport.unique(data.history).map { it.activeKcal })
    }
    @Test fun differentActivitiesAtSameTimeStayDistinctAndFutureSessionsAreNotCounted() {
        val a = session("a")
        val b = session("b", type = 56).copy(title = "Rower")
        val future = session("c", "2026-10-21T09:00:00Z", "2026-10-21T10:00:00Z")
        assertEquals(2, ActivityRecoverySupport.unique(listOf(a, b)).size)
        val summary = ActivityRecoverySupport.summary(SportsData(history = listOf(a, b, future)), day, day.plusDays(1), now, zone)
        assertEquals(60L, summary.minutes)
        assertEquals(2, summary.sessions)
    }
    @Test fun midnightAndDstRespectLocalDatesAndClippedTime() {
        val cross = session("cross", "2026-10-24T22:30:00Z", "2026-10-25T02:30:00Z")
        val data = SportsData(history = listOf(cross))
        val end = Instant.parse("2026-10-26T12:00:00Z")
        assertEquals(30L, ActivityRecoverySupport.summary(data, LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-24"), end, zone).minutes)
        assertEquals(210L, ActivityRecoverySupport.summary(data, LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-25"), end, zone).minutes)
        assertEquals(2, ActivityRecoverySupport.summary(data, LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-25"), end, zone).activeDays)
    }
    @Test fun refreshPreservesManualEntriesAndFailuresPreservePriorReads() {
        val manual = session("manual|a")
        val old = session("old")
        val metric = ActivityDay(day.toString(), 5000, 420, readAt = 10)
        val data = SportsData(history = listOf(old, manual), daily = listOf(metric), updated = 10, healthUpdated = 10,
            recovery = listOf(RecoveryCheck(day.toString(), 2, 4)))
        val failed = ActivityRecoverySupport.mergeImported(data, null, null, 100, "offline")
        assertEquals(data.history, failed.history); assertEquals(10L, failed.updated)
        assertEquals(data.daily, failed.daily); assertEquals(10L, failed.healthUpdated)
        val refreshed = ActivityRecoverySupport.mergeImported(data, listOf(session("new")), emptyList(), 100, "")
        assertEquals(setOf("manual|a", "new"), refreshed.history.map { it.id }.toSet())
        assertEquals(data.recovery, refreshed.recovery)
        assertEquals(100L, refreshed.updated)
    }
    @Test fun manualActivityRequiresPastEndAndIsNotAnEstimatedCalorieEntry() {
        val entry = ActivityRecoverySupport.manualSession("2026-10-20", "12:00", 30, "Spacer", 79, now, zone)
        assertTrue(ActivityRecoverySupport.manual(entry)); assertNull(entry.activeKcal); assertNull(entry.distanceKm)
        assertEquals(30L, java.time.Duration.between(Instant.parse(entry.start), Instant.parse(entry.end)).toMinutes())
        assertTrue(runCatching { ActivityRecoverySupport.manualSession("2026-10-21", "09:00", 30, "Spacer", 79, now, zone) }.isFailure)
        assertTrue(runCatching { ActivityRecoverySupport.manualSession("2026-10-20", "12:00", 0, "Spacer", 79, now, zone) }.isFailure)
        assertTrue(runCatching { ActivityRecoverySupport.manualSession("2026-03-29", "01:30", 30, "Spacer", 79, now, zone) }.isFailure)
        val a = session("short1", "2026-10-20T12:00:00Z", "2026-10-20T12:01:00Z")
        val b = session("short2", "2026-10-20T12:01:00Z", "2026-10-20T12:02:00Z")
        assertFalse(ActivityRecoverySupport.duplicate(a, b))
    }
    @Test fun newDataRoundTripsAndOldBackupsHaveCompatibleDefaults() {
        val data = SportsData(history = listOf(session("a")), goalMode = "minutes", minuteGoal = 200,
            daily = listOf(ActivityDay(day.toString(), null, 430, true, "zepp", 10)),
            recovery = listOf(RecoveryCheck(day.toString(), 2, 4)), healthUpdated = 10)
        val restored = SportsSupport.decode(SportsSupport.encode(data))
        assertEquals(data, restored)
        assertNull(restored.daily.single().steps)
        assertEquals(SportsData(), SportsSupport.decode("{}"))
        assertTrue(runCatching { SportsSupport.decode(SportsSupport.encode(data.copy(minuteGoal = 0))) }.isFailure)
        assertTrue(runCatching { SportsSupport.decode(SportsSupport.encode(data.copy(recovery = data.recovery + data.recovery))) }.isFailure)
        assertTrue(runCatching { SportsSupport.decode(SportsSupport.encode(data.copy(daily = listOf(data.daily.single().copy(steps = -1))))) }.isFailure)
    }
    @Test fun adviceNeedsSeveralCheckinsAndNeverCountsMissingSleepAsZero() {
        val notes = (0L..2L).map { RecoveryCheck(day.minusDays(it).toString(), 2, 4) }
        val data = SportsData(recovery = notes)
        assertTrue(ActivityRecoverySupport.recovery(data, day).contains("3 wpisach"))
        assertTrue(ActivityRecoverySupport.recovery(data, day).contains("brak odczytanych nocy"))
        assertFalse(ActivityRecoverySupport.recovery(data.copy(recovery = notes.take(1)), day).contains("spokojniejszy"))
        val asleep = data.copy(daily = listOf(ActivityDay(day.toString(), sleepMinutes = 420), ActivityDay(day.minusDays(1).toString())))
        assertTrue(ActivityRecoverySupport.recovery(asleep, day).contains("z 1 odczytanych nocy"))
        assertEquals(150, ActivityRecoverySupport.progress(SportsData(goalMode = "minutes"), ActivitySummary(50, 1, 1, 1)).second)
    }
}
