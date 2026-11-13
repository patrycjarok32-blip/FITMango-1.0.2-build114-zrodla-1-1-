package com.example

import com.example.viewmodel.SportPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
    @Test fun sessionDetailsSurviveBackupRoundTrip() {
        val original = com.example.viewmodel.SportsData(plans = listOf(
            SportPlan("2026-11-13", "Long Run", false,
                category = "Cardio", description = "Spokojne tempo",
                targetDistanceKm = 9.0, targetMinutes = 60, distanceRange = "8–10 km")))
        val restored = com.example.viewmodel.SportsSupport.decode(
            com.example.viewmodel.SportsSupport.encode(original))
        assertEquals(original.plans, restored.plans)
    }

    @Test fun legacyBackupWithoutNewFieldsStillLoads() {
        val raw = com.example.viewmodel.SportsSupport.encode(
            com.example.viewmodel.SportsData(plans = listOf(SportPlan("2026-11-13", "PULL", false))))
        val json = org.json.JSONObject(raw)
        val oldPlan = json.getJSONArray("plans").getJSONObject(0)
        listOf("category", "description", "targetDistanceKm", "targetMinutes", "distanceRange")
            .forEach { oldPlan.remove(it) }
        val restored = com.example.viewmodel.SportsSupport.decode(json.toString())
        assertEquals("Siłowy", restored.plans.single().category)
        assertNull(restored.plans.single().targetDistanceKm)
    }
    @Test fun severalSessionsOnOneDaySurviveBackup() {
        val day = "2026-11-13"
        val plans = listOf(
            SportPlan(day, "PULL", false, sessionId = "strength-1"),
            SportPlan(day, "Long Run", false, category = "Cardio",
                targetDistanceKm = 9.0, targetMinutes = 60, sessionId = "run-1"),
            SportPlan(day, "Stretching", false, category = "Mobilność i stretching",
                targetMinutes = 20, sessionId = "stretch-1"))
        val data = com.example.viewmodel.SportsData(plans = plans)
        val restored = com.example.viewmodel.SportsSupport.decode(
            com.example.viewmodel.SportsSupport.encode(data))
        assertEquals(3, restored.plans.count { it.date == day })
        assertEquals(plans, restored.plans)
        assertEquals(3, restored.plans.map { it.sessionId }.distinct().size)
    }

}
