package com.example

import com.example.viewmodel.*
import com.example.data.*
import com.example.reminders.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersonalProgressTest {
    private val today = LocalDate.parse("2026-10-22")
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private fun food(date: String, kcal: Int, complete: Boolean = true) = HabitDay(date, kcal, 100, 180, 50, 2000, 1800, 400, 2000,
        listOf("Owsianka • $kcal kcal"), emptyList(), complete)
    private fun walk(id: String, date: String, minutes: Int, source: String = "zepp") = SportSession(id, "Spacer", "${date}T10:00:00Z",
        Instant.parse("${date}T10:00:00Z").plusSeconds(minutes * 60L).toString(), source,
        activityType = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING)

    @Test fun recordsUseAvailableCompletedSessionsAndDoNotInventMissingDistance() {
        val a = walk("a", "2026-10-19", 45)
        val data = SportsData(history = listOf(a, a.copy(id = "manual|copy", source = "Wpis ręczny"), walk("b", "2026-10-20", 60), walk("future", "2026-10-23", 120),
            walk("bike", "2026-10-20", 40).copy(title = "Rower", activityType = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING, distanceKm = null)))
        val records = PersonalProgressSupport.records(data, now, zone)
        assertEquals(60.0, records.single { it.title == "Najdłuższy spacer" }.value, .001)
        assertEquals(2.0, records.single { it.title == "Najwięcej aktywnych dni" }.value, .001)
        assertFalse(records.any { it.unit == "km" })
        assertTrue(PersonalProgressSupport.records(SportsData(), now, zone).isEmpty())
    }
    @Test fun comparisonUsesTwoCompletedMondaySundayWeeksAndApprovedFoodOnly() {
        val older = food("2026-10-06", 1600)
        val newer = food("2026-10-13", 1800, false)
        val ignored = food("2026-10-14", 500)
        val current = food("2026-10-21", 9998)
        val approvals = listOf(EnergyConfirmation(older.date, WeeklyEnergySupport.fingerprint(older), 2000.0),
            EnergyConfirmation(newer.date, WeeklyEnergySupport.fingerprint(newer), 2000.0, acceptsPartial = true))
        val data = SportsData(history = listOf(walk("old", "2026-10-06", 30), walk("new", "2026-10-13", 45)),
            daily = listOf(ActivityDay("2026-10-13", sleepMinutes = 420)))
        val comparison = PersonalProgressSupport.compare(data, listOf(older, newer, ignored, current), approvals,
            listOf(WeightLogEntity(id = 1, date = "13.10.2026", weightKg = 62f), WeightLogEntity(id = 2, date = "2026-10-13", weightKg = 61f)), today, now, zone)
        assertEquals(LocalDate.parse("2026-10-05"), comparison.older.from)
        assertEquals(LocalDate.parse("2026-10-18"), comparison.newer.until)
        assertEquals(1600.0, comparison.older.calories!!, .001)
        assertEquals(1800.0, comparison.newer.calories!!, .001)
        assertEquals(1, comparison.newer.approvedDays)
        assertEquals(420.0, comparison.newer.sleepMinutes!!, .001)
        assertEquals(1, comparison.newer.sleepDays)
        assertNull(comparison.older.sleepMinutes)
        assertEquals(61.0, comparison.newer.weight!!, .001)
        assertEquals(1, comparison.newer.weightDays)
    }
    @Test fun quickCheckinReplacesTodayInBothStoresAndPreservesSleepAndOtherDays() {
        val old = today.minusDays(1).toString()
        val sports = SportsData(recovery = listOf(RecoveryCheck(today.toString(), 1, 5), RecoveryCheck(old, 4, 1)))
        val effects = EffectsSettings(lastAdjustment = old, wellbeing = listOf(WellbeingDay(today.toString(), 3, 1, 7.5), WellbeingDay(old, 2, 4, 8.0)))
        val update = PersonalProgressSupport.checkin(sports, effects, today, 4, 2, 1)
        assertEquals(2, update.first.recovery.size); assertEquals(2, update.second.wellbeing.size)
        assertEquals(4, update.first.recovery.first { it.date == today.toString() }.energy)
        assertEquals(4, update.second.wellbeing.first { it.date == today.toString() }.energy)
        assertEquals(1, update.second.wellbeing.first { it.date == today.toString() }.hunger)
        assertEquals(7.5, update.second.wellbeing.first { it.date == today.toString() }.sleepHours!!, .001)
        assertEquals(old, update.second.lastAdjustment)
        assertTrue(runCatching { PersonalProgressSupport.checkin(sports, effects, today, 0, 2, 1) }.isFailure)
        assertEquals(update.first, SportsSupport.decode(SportsSupport.encode(update.first)))
        assertEquals(update.second, EffectsSupport.decode(EffectsSupport.encode(update.second)))
    }
    @Test fun personalReminderDaysAndQuietHoursDoNotReplayOrEnableOtherAlerts() {
        val settings = ReminderSettings(weight = true, weightDay = 4, weightTime = "09:00", activity = true, activityTime = "17:00",
            weekly = true, weeklyDay = 1, weeklyTime = "19:00")
        assertEquals(mapOf("weight" to "2026-10-22:0"), ReminderSupport.due(settings, LocalDateTime.parse("2026-10-22T09:15")))
        assertFalse(ReminderSupport.due(settings, LocalDateTime.parse("2026-10-21T09:15")).containsKey("weight"))
        assertTrue(ReminderSupport.due(settings, LocalDateTime.parse("2026-10-19T19:15")).containsKey("weekly"))
        assertFalse(ReminderSupport.due(settings, LocalDateTime.parse("2026-10-20T19:15")).containsKey("weekly"))
        assertTrue(ReminderSupport.due(settings.copy(activityTime = "23:00"), LocalDateTime.parse("2026-10-22T23:10")).isEmpty())
        assertEquals(settings, ReminderSupport.decode(ReminderSupport.encode(settings)))
        assertFalse(ReminderSupport.decode("{\"water\":true}").weight)
        assertTrue(runCatching { ReminderSupport.validate(settings.copy(weeklyDay = 0)) }.isFailure)
    }
    @Test fun aiEvidenceContainsActualPromptDatesSourcesAndApprovedEstimates() {
        val d = food("2026-10-20", 1700, false)
        val confirmations = listOf(EnergyConfirmation(d.date, WeeklyEnergySupport.fingerprint(d), 2000.0, acceptsPartial = true))
        val data = SportsData(history = listOf(walk("a", "2026-10-20", 30)), daily = listOf(ActivityDay("2026-10-20", 5000, 420, sources = "zepp")))
        val profile = UserProfileEntity(goal = "Redukcja", targetCalories = 1800)
        val report = EffectsSupport.analyze(emptyList(), emptyList(), listOf(d), confirmations, profile, data, EffectsSettings(), today, 7)
        val prompt = ProgressAiContext.build(profile, listOf(d), confirmations, data, emptyList(), null, report, today, 7)
        val evidence = AiEvidenceSupport.describe(prompt)
        assertTrue(evidence.contains("2026-10-15 – 2026-10-21"))
        assertTrue(evidence.contains("2026-10-20: 1700 kcal • zatwierdzony"))
        assertTrue(evidence.contains("zepp")); assertTrue(evidence.contains("5000")); assertTrue(evidence.contains("420 min"))
        assertFalse(evidence.contains("9998"))
        assertTrue(evidence.contains("Kalorie treningów nie są dodawane drugi raz"))
    }
}
