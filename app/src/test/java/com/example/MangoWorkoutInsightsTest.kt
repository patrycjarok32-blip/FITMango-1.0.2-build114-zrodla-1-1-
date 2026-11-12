package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MangoWorkoutInsightsTest {
    private fun session(id: String, day: String, ended: Long, kg: Double) =
        WorkoutSession(id, day, "PUSH", 1000L, listOf(
            WorkoutEntry("Floor press", 1, 10, kg, true),
            WorkoutEntry("Floor press", 2, 8, kg, true)), ended, 6)
    @Test fun premiumSummaryUsesActiveTimeAndPriorVolume() {
        val old = session("old", "2026-11-10", 61000L, 10.0)
        val current = session("new", "2026-11-12", 121000L, 12.0)
        val report = MangoWorkoutInsights.summary(current, listOf(old, current))
        assertEquals(2L, report.activeMinutes)
        assertEquals(216.0, report.volumeKg, 0.001)
        assertEquals(180.0, report.previousVolumeKg!!, 0.001)
        assertEquals(1, report.personalRecords)
    }
    @Test fun calendarDistinguishesMissedAndActiveAndCompleted() {
        val day = LocalDate.parse("2026-11-10")
        val plan = SportPlan(day.toString(), "PUSH", false,
            listOf(PlannedExercise("Floor press", 3, 10)))
        assertEquals("○ Pominięty", MangoWorkoutInsights.calendarStatus(
            day, day.plusDays(2), listOf(plan), emptyList()))
        assertEquals("▶ Rozpoczęty", MangoWorkoutInsights.calendarStatus(
            day, day.plusDays(2), listOf(plan), listOf(session("a", day.toString(), 0L, 10.0))))
        assertEquals("✓ Ukończony (1)", MangoWorkoutInsights.calendarStatus(
            day, day.plusDays(2), listOf(plan), listOf(session("a", day.toString(), 61000L, 10.0))))
    }
    @Test fun quickPlanDoesNotMutateOriginal() {
        val plan = SportPlan("2026-11-12", "PUSH", false,
            listOf(PlannedExercise("Floor press", 5, 10, 60),
                PlannedExercise("Squat", 5, 10, 60)))
        val quick = MangoWorkoutInsights.quickPlan(plan, 15)
        assertTrue(quick.exercises.sumOf { it.sets } <= 10)
        assertEquals(5, plan.exercises.first().sets)
        assertTrue(quick.title.contains("15 min"))
        assertEquals(2, quick.exercises.size)
    }
    @Test fun historyExcludesIncompleteSessions() {
        val completed = session("a", "2026-11-10", 61000L, 10.0)
        val active = session("b", "2026-11-12", 0L, 15.0)
        assertEquals(1, MangoWorkoutInsights.exerciseHistory(
            "floor press", listOf(completed, active)).size)
    }
    @Test fun pausedMinutesAreNotCountedInWeeklySummary() {
        val paused = WorkoutSessionStore.finish(
            WorkoutSessionStore.pause(session("p", "2026-11-12", 0L, 12.0), 31000L),
            631000L, 6)
        assertEquals(0L, WorkoutSessionStore.activeDurationMillis(paused) / 60000L)
    }
    @Test fun alternativesRespectHomeEquipmentAndMuscleGroup() {
        val library = listOf("Glute bridge", "Hip thrust", "Cable kickback", "Bench press")
        val home = MangoExerciseAlternatives.candidates("Glute bridge", library, true)
        assertTrue(home.contains("Hip thrust"))
        assertFalse(home.contains("Cable kickback"))
        assertFalse(home.contains("Bench press"))
        val gym = MangoExerciseAlternatives.candidates("Glute bridge", library, false)
        assertEquals("Cable kickback", gym.first())
    }

}
