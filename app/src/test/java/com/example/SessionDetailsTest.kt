package com.example

import com.example.viewmodel.SportPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDetailsTest {
    @Test fun legacyPlansKeepDefaultOptionalDetails() {
        val legacy = SportPlan("2026-11-13", "PULL", false)
        assertEquals("Siłowy", legacy.category)
        assertEquals("", legacy.description)
        assertNull(legacy.targetDistanceKm)
        assertNull(legacy.targetMinutes)
        assertTrue(legacy.exercises.isEmpty())
    }

    @Test fun longRunTemplateFieldsCanBeSavedWithoutChangingExercises() {
        val existing = SportPlan("2026-11-13", "Long Run", false)
        val updated = existing.copy(
            category = "Cardio", distanceRange = "8–10 km",
            targetDistanceKm = 9.0, targetMinutes = 60, description = "")
        assertEquals("Cardio", updated.category)
        assertEquals("8–10 km", updated.distanceRange)
        assertEquals(9.0, updated.targetDistanceKm!!, 0.001)
        assertEquals(60, updated.targetMinutes)
        assertEquals(existing.exercises, updated.exercises)
    }
}
