package com.example

import com.example.viewmodel.MangoTrainingPlanner
import com.example.viewmodel.SportPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MangoTrainingPlannerTest {
    private val monday = LocalDate.parse("2026-11-09")
    @Test fun threeDayPlanUsesDistinctDaysAndCategories() {
        val result = MangoTrainingPlanner.suggest(monday, 3, 60, emptyList())
        assertEquals(3, result.size)
        assertEquals(3, result.map { it.date }.distinct().size)
        assertTrue(result.any { it.category == "Cardio" })
    }
    @Test fun existingSessionsAreNeverOverwritten() {
        val existing = listOf(SportPlan("2026-11-09", "My custom workout", false))
        val suggestions = MangoTrainingPlanner.suggest(monday, 3, 45, existing)
        assertEquals(2, suggestions.size)
        assertTrue(suggestions.none { it.date == existing.first().date })
    }
    @Test fun durationRespectsUserLimit() {
        assertTrue(MangoTrainingPlanner.suggest(monday, 7, 20, emptyList())
            .all { (it.targetMinutes ?: 0) <= 20 })
    }
    @Test fun invalidPreferencesAreRejected() {
        assertTrue(runCatching { MangoTrainingPlanner.suggest(monday, 8, 45, emptyList()) }.isFailure)
    }
}
