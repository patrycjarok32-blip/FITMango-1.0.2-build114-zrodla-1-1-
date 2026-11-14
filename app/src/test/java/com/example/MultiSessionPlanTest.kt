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
    @Test fun multipleSessionsOnSameDayPassValidation() {
        val date = "2026-11-13"
        val data = SportsData(plans = listOf(
            SportPlan(date, "PULL", false),
            SportPlan(date, "Run", false, category = "Cardio", sessionId = "run-1"),
            SportPlan(date, "Stretch", false, category = "Mobilność i stretching", sessionId = "stretch-1")))
        assertEquals(3, SportsSupport.decode(SportsSupport.encode(data)).plans.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSessionIdsAreRejected() {
        val date = "2026-11-13"
        val data = SportsData(plans = listOf(
            SportPlan(date, "Run", false, sessionId = "same-id"),
            SportPlan(date, "Stretch", false, sessionId = "same-id")))
        SportsSupport.decode(SportsSupport.encode(data))
    }

    @Test fun matchingRunCompletesOnlyTheCorrespondingPlan() {
        val day = "2026-11-13"
        val run = SportPlan(day, "Long Run", false, category = "Cardio",
            targetDistanceKm = 8.0, targetMinutes = 60, sessionId = "run")
        val strength = SportPlan(day, "PULL", false, sessionId = "pull")
        val activity = com.example.viewmodel.SportSession("huawei-run", "Bieganie",
            "2026-11-13T20:49:00Z", "2026-11-13T21:47:00Z", "huawei",
            372.0, 8.01)
        val data = SportsData(plans = listOf(run, strength), history = listOf(activity))
        assertEquals(activity.id, com.example.viewmodel.PlannedActivityMatching.match(run, data)?.id)
        assertEquals(null, com.example.viewmodel.PlannedActivityMatching.match(strength, data))
        assertEquals("✓ Wykonany", com.example.viewmodel.MangoWorkoutInsights.plannedStatus(run, data, emptyList()))
        assertEquals("◷ Zaplanowany", com.example.viewmodel.MangoWorkoutInsights.plannedStatus(strength, data, emptyList()))
    }
    @Test fun ambiguousRunsRequireConfirmation() {
        val day = "2026-11-13"
        val plan = SportPlan(day, "Long Run", false, category = "Cardio", sessionId = "run")
        val activities = listOf("a", "b").map { id ->
            com.example.viewmodel.SportSession(id, "Bieganie", "2026-11-13T10:00:00Z",
                "2026-11-13T10:45:00Z", "health")
        }
        val data = SportsData(plans = listOf(plan), history = activities)
        assertEquals(null, com.example.viewmodel.PlannedActivityMatching.match(plan, data))
        assertEquals("a", com.example.viewmodel.PlannedActivityMatching.match(
            plan, data.copy(linkedActivities = mapOf("run" to "a")))?.id)
    }
    @Test fun linkedActivitiesSurviveSportsBackup() {
        val data = SportsData(linkedActivities = mapOf("run" to "health-123"))
        assertEquals(data.linkedActivities,
            SportsSupport.decode(SportsSupport.encode(data)).linkedActivities)
    }
    @Test fun strengthCompletionUsesSessionIdRatherThanOnlyDate() {
        val date = "2026-11-13"
        val planA = SportPlan(date, "PULL", false, sessionId = "pull-a")
        val planB = SportPlan(date, "PULL", false, sessionId = "pull-b")
        val workout = com.example.viewmodel.WorkoutSession("w1", date, "PULL",
            1000L, emptyList(), ended = 2000L, plannedSessionId = "pull-a")
        assertEquals("✓ Wykonany", com.example.viewmodel.MangoWorkoutInsights.plannedStatus(
            planA, SportsData(), listOf(workout)))
        assertEquals("◷ Zaplanowany", com.example.viewmodel.MangoWorkoutInsights.plannedStatus(
            planB, SportsData(), listOf(workout)))
    }

    @Test fun trainerDoesNotSuggestProgressionWithoutHistory() {
        assertTrue(com.example.viewmodel.MangoWorkoutInsights.trainingSuggestion(
            "Squat", emptyList()).contains("Brak danych"))
    }
    @Test fun olderStrengthWorkoutsDefaultToNoPlannedId() {
        val legacy = com.example.viewmodel.WorkoutSession(
            "old", "2026-11-13", "PULL", 1000L, emptyList())
        assertEquals("", legacy.plannedSessionId)
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
