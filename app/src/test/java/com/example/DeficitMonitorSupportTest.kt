package com.example

import com.example.viewmodel.DeficitMonitorSupport
import com.example.viewmodel.WeightPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DeficitMonitorSupportTest {
    private val today = LocalDate.parse("2026-11-17")

    @Test fun missingFoodIsNotInterpretedAsZeroOrSuccessfulDeficit() {
        val result = DeficitMonitorSupport.evaluate(emptyList(), emptyList(), emptyList(), today)
        assertEquals("Za mało danych do oceny trendu wagi", result.status)
        assertNull(result.averageCalories)
        assertNull(result.weightChange)
        assertEquals(0, result.approvedDays)
        assertTrue(result.observations.any { it.contains("nie uznajemy za zero") })
    }

    @Test fun weightChangeWithoutConfirmedFoodDoesNotProveDeficit() {
        val first = (0..6).map { WeightPoint(today.minusDays(28L - it).toString(), 80.0) }
        val last = (0..6).map { WeightPoint(today.minusDays(7L - it).toString(), 79.0) }
        val result = DeficitMonitorSupport.evaluate(emptyList(), emptyList(), first + last, today)
        assertEquals("Waga spada • bilans wymaga weryfikacji", result.status)
        assertNotNull(result.weightChange)
        assertEquals("Dziennik: niepełny (0/28 dni)", result.foodStatus)
        assertEquals("Ograniczona", result.confidence)
        assertEquals(14, result.weightReadings)
    }
}
