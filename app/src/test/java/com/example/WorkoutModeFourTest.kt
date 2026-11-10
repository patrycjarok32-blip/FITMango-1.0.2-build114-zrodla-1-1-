package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutModeFourTest {
    @Test fun photoSuggestionsAreReviewableAndBounded() {
        val result = VideoPlanPhotoParser.parse("Walking Cardio\n15 min\nBrzuch i boczki\n20 min")
        assertEquals(2, result.size)
        assertEquals("Walking Cardio", result[0].title)
        assertEquals(15, result[0].minutes)
        assertEquals(20, result[1].minutes)
    }
    @Test fun challengeDuplicationClearsCompletionAndKeepsLinks() {
        val original = VideoChallenge("one", "Mój plan", 14, listOf(
            VideoWorkout("video", 1, "Cardio", 15, "Cardio", "https://youtu.be/example", 1234)))
        val duplicate = VideoChallengeStore.duplicate(listOf(original), "one").last()
        assertNotEquals(original.id, duplicate.id)
        assertEquals(0L, duplicate.workouts.first().completedAt)
        assertEquals(original.workouts.first().url, duplicate.workouts.first().url)
    }
    @Test fun weekCopyResetsCompletion() {
        val original = VideoChallenge("one", "Plan", 14, listOf(
            VideoWorkout("video", 2, "Cardio", 20, "Cardio", "", 1234)))
        val copied = VideoChallengeStore.duplicateWeek(listOf(original), "one", 1, 2).single()
        assertEquals(9, copied.workouts.last().day)
        assertEquals(0L, copied.workouts.last().completedAt)
    }
    @Test fun achievementsUseCompletedWorkoutsOnly() {
        val completed = VideoChallenge("a", "Plan", 7, listOf(
            VideoWorkout("w", 1, "Cardio", 15, "Cardio", "", 1234)))
        val result = WorkoutInsights.summary(SportsData(), emptyList(), listOf(completed))
        assertEquals(1, result.sessions)
        assertEquals(15, result.videoMinutes)
        assertTrue(result.badges.contains("🌟 Ukończone wyzwanie"))
    }
    @Test fun challengeAppearsInCalendarByStartDate() {
        val challenge = VideoChallenge("a", "Plan", 7, listOf(
            VideoWorkout("v", 2, "Walking Cardio", 15, "Cardio", "")), "2026-11-09")
        val calendar = WorkoutInsights.calendar(java.time.LocalDate.parse("2026-11-09"),
            SportsData(), emptyList(), listOf(challenge))
        assertEquals(1, calendar[1].videosPlanned)
        assertEquals(0, calendar[0].videosPlanned)
    }
    @Test fun ocrMatchesAdjacentDuration() {
        val result = VideoPlanPhotoParser.parse("Trening nóg\\n35 min\\nJoga\\n15 min")
        assertEquals(35, result[0].minutes)
        assertEquals(15, result[1].minutes)
    }
    @Test fun lowRecoveryPreventsWeightIncrease() {
        val data = SportsData(recovery = listOf(RecoveryCheck("2026-11-10", 1, 5)),
            sets = listOf(SportSet("a", "2026-11-09", "Przysiad", 10, 40.0, 1000L)))
        val advice = WorkoutInsights.coachAdvice(data, "Przysiad", 10,
            java.time.LocalDate.parse("2026-11-10"))
        assertTrue(advice.recommendation.contains("Regeneracja"))
        assertTrue(advice.weightKg!! < 40.0)
    }
    @Test fun missingRecoveryDoesNotPretendToKnow() {
        val advice = WorkoutInsights.coachAdvice(SportsData(), "Przysiad", 10)
        assertFalse(advice.dataAvailable)
    }
    @Test fun invalidTutorialLinkRejected() {
        try {
            ExerciseVideoLinks.validate("""{"Squat":"https://example.com/video"}""")
            fail("Non-YouTube URL must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
