package com.example

import com.example.viewmodel.SportPlan
import com.example.viewmodel.SportsData
import com.example.viewmodel.SportsSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiSessionPlanTest {
    @Test fun pullRunAndStretchCanCoexistAndSurviveBackup() {
        val date = "2026-11-13"
        val plans = listOf(
            SportPlan(date, "PULL", false),
            SportPlan(date, "Long Run", false, category = "Cardio", targetDistanceKm = 9.0,
                targetMinutes = 60, sessionId = "run-1"),
            SportPlan(date, "Stretching", false, category = "Mobilność i stretching",
                targetMinutes = 20, sessionId = "stretch-1"))
        val restored = SportsSupport.decode(SportsSupport.encode(SportsData(plans = plans)))
        assertEquals(3, restored.plans.count { it.date == date })
        assertEquals(plans, restored.plans)
        assertTrue(restored.history.isEmpty())
    }
    @Test fun removingOneSessionDoesNotRemoveTheOthers() {
        val plans = listOf(
            SportPlan("2026-11-13", "PULL", false),
            SportPlan("2026-11-13", "Long Run", false, sessionId = "run-1"),
            SportPlan("2026-11-13", "Stretching", false, sessionId = "stretch-1"))
        val remaining = plans.filterNot { it.sessionId == "run-1" }
        assertEquals(listOf("PULL", "Stretching"), remaining.map { it.title })
    }
}
