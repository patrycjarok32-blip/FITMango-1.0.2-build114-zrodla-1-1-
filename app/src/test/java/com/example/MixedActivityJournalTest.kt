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
