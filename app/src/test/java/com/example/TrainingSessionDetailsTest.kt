package com.example

import com.example.viewmodel.SportPlan
import com.example.viewmodel.PlannedExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSessionDetailsTest {
    @Test fun legacyPlansKeepCompatibleDefaults() {
        val old = SportPlan("2026-11-13", "Long Run", false)
        assertEquals("Siłowy", old.category)
        assertEquals("", old.description)
        assertNull(old.targetDistanceKm)
        assertNull(old.targetMinutes)
        assertTrue(old.exercises.isEmpty())
    }

    @Test fun longRunGoalsCanBeEditedWithoutChangingPlanIdentity() {
        val old = SportPlan("2026-11-13", "Long Run", false)
        val updated = old.copy(category = "Cardio", targetDistanceKm = 9.0,
            targetMinutes = 60, distanceRange = "8–10 km",
            description = "Spokojne tempo")
        assertEquals(old.date, updated.date)
        assertEquals(old.exercises, updated.exercises)
        assertEquals("Cardio", updated.category)
        assertEquals(9.0, updated.targetDistanceKm!!, 0.001)
        assertEquals(60, updated.targetMinutes)
        assertEquals("8–10 km", updated.distanceRange)
        assertEquals("Spokojne tempo", updated.description)
    }
}
