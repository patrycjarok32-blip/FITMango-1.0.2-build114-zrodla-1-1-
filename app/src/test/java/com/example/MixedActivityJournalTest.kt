package com.example

import com.example.viewmodel.ActivityRecoverySupport
import com.example.viewmodel.SportsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MixedActivityJournalTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-13T23:00:00Z")

    @Test fun multipleCategoriesCanShareOneDay() {
        val strength = ActivityRecoverySupport.manualSession("2026-11-13", "08:00", 45,
            "Siłowy", 1, now, zone)
        val cardio = ActivityRecoverySupport.manualSession("2026-11-13", "09:00", 20,
            "Cardio", 2, now, zone)
        val mobility = ActivityRecoverySupport.manualSession("2026-11-13", "09:30", 15,
            "Mobilność i stretching", 3, now, zone)
        assertEquals(3, ActivityRecoverySupport.unique(listOf(strength, cardio, mobility)).size)
    }

    @Test fun copyCreatesIndependentManualEntryOnAnotherDay() {
        val original = ActivityRecoverySupport.manualSession("2026-11-11", "08:00", 45,
            "Siłowy", 1, now, zone)
        val copied = ActivityRecoverySupport.copyManual(original,
            java.time.LocalDate.parse("2026-11-12"), now, zone)
        assertTrue(copied.id != original.id)
        assertEquals(original.title, copied.title)
        assertEquals("2026-11-12", copied.start.substring(0, 10))
    }

    @Test fun copyingHealthConnectEntryIsForbidden() {
        val original = ActivityRecoverySupport.manualSession("2026-11-11", "08:00", 45,
            "Cardio", 2, now, zone).copy(id = "health|123", source = "Health Connect")
        assertTrue(runCatching {
            ActivityRecoverySupport.copyManual(original, java.time.LocalDate.parse("2026-11-12"), now, zone)
        }.isFailure)
    }

    @Test fun daySummaryKeepsMixedSessions() {
        val a = ActivityRecoverySupport.manualSession("2026-11-13", "08:00", 45, "Siłowy", 1, now, zone)
        val b = ActivityRecoverySupport.manualSession("2026-11-13", "09:00", 20, "Cardio", 2, now, zone)
        val data = SportsData(history = listOf(a, b))
        assertEquals(2, ActivityRecoverySupport.daySessions(data, java.time.LocalDate.parse("2026-11-13"), zone).size)
        assertEquals(65L, ActivityRecoverySupport.dayMinutes(data, java.time.LocalDate.parse("2026-11-13"), zone))
    }

    @Test fun importedDuplicateDoesNotReplaceDistinctManualActivity() {
        val strength = ActivityRecoverySupport.manualSession("2026-11-13", "08:00", 45,
            "Siłowy", 1, now, zone)
        val cardio = ActivityRecoverySupport.manualSession("2026-11-13", "09:00", 20,
            "Cardio", 2, now, zone)
        val merged = ActivityRecoverySupport.mergeImported(
            SportsData(history = listOf(strength, cardio)), emptyList(), null, now.toEpochMilli(), "")
        assertEquals(2, merged.history.size)
        assertTrue(merged.history.all { ActivityRecoverySupport.manual(it) })
    }
}
