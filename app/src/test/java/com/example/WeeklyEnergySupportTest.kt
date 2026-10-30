package com.example

import com.example.viewmodel.*
import com.example.data.WeightLogEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeeklyEnergySupportTest {
    @Test fun partialDayNeedsExplicitAcceptanceAndEditsStillInvalidateIt() {
        val d = day("2026-10-08").copy(nutritionComplete = false)
        assertTrue(analyze(listOf(d), listOf(energy(d)), listOf(confirmation(d))).includedDates.isEmpty())
        val c = confirmation(d).copy(acceptsPartial = true)
        assertEquals(listOf(d.date), analyze(listOf(d), listOf(energy(d)), listOf(c)).includedDates)
        assertTrue(analyze(listOf(d.copy(calories = d.calories + 10)), listOf(energy(d)), listOf(c)).includedDates.isEmpty())
    }
    private val today = LocalDate.parse("2026-10-09")
    private fun day(date: String, calories: Int = 1800) = HabitDay(date, calories, 100, 150, 50, 2000, 1800, 400, 2500, listOf("Posiłki $calories"), emptyList())
    private fun energy(d: HabitDay, total: Double? = 2200.0, full: Boolean = true) = DailyEnergy(d.date, total, full, 1L)
    private fun confirmation(d: HabitDay, total: Double = 2200.0) = EnergyConfirmation(d.date, WeeklyEnergySupport.fingerprint(d), total)
    private fun analyze(days: List<HabitDay>, energy: List<DailyEnergy>, c: List<EnergyConfirmation>) = WeeklyEnergySupport.analyze(days, energy, c, today.minusDays(6), today, today)

    @Test fun missingUnconfirmedCurrentAndPartialDaysAreExcluded() {
        val complete = day("2026-10-08")
        val current = day("2026-10-09")
        val missing = day("2026-10-07")
        val unconfirmed = day("2026-10-06")
        val partial = day("2026-10-05")
        val result = analyze(listOf(complete, current, missing, unconfirmed, partial),
            listOf(energy(complete), energy(current), energy(missing, null), energy(unconfirmed), energy(partial, full = false)),
            listOf(confirmation(complete), confirmation(current), confirmation(missing), confirmation(partial)))
        assertEquals(listOf(complete.date), result.includedDates)
        assertEquals(400.0, result.deficitKcal, 0.001)
        assertEquals(-400.0 / 7700, result.energyEquivalentKg, 0.00001)
    }
    @Test fun changingFoodInvalidatesApprovalButRefreshingBurningDoesNot() {
        val d = day("2026-10-08")
        assertTrue(analyze(listOf(d.copy(nutritionComplete = false)), listOf(energy(d)), listOf(confirmation(d))).includedDates.isEmpty())
        assertTrue(analyze(listOf(d.copy(calories = 1700)), listOf(energy(d)), listOf(confirmation(d))).includedDates.isEmpty())
        assertEquals(500.0, analyze(listOf(d), listOf(energy(d, 2300.0)), listOf(confirmation(d))).deficitKcal, 0.001)
    }
    @Test fun noExtrapolationNoDuplicatesAndSurplusHasPositiveEquivalent() {
        val d = day("2026-10-08", 2400)
        val r = analyze(listOf(d, d), listOf(energy(d)), listOf(confirmation(d)))
        assertEquals(1, r.includedDates.size); assertEquals(2400, r.eatenKcal)
        assertEquals(-200.0, r.deficitKcal, 0.001); assertTrue(r.energyEquivalentKg > 0)
    }
    @Test fun outsideWindowAndEmptyFoodCannotCreateDeficit() {
        val d = day("2026-10-01")
        val empty = day("2026-10-08").copy(meals = emptyList(), calories = 0)
        assertTrue(analyze(listOf(d, empty), listOf(energy(d), energy(empty)), listOf(confirmation(d), confirmation(empty))).includedDates.isEmpty())
    }
    @Test fun realWeightChangeNeedsTwoDistinctDatesAndUsesLatestMeasurementPerDay() {
        val a = WeightLogEntity(id = 1, date = "2026-10-03", weightKg = 61f)
        assertNull(WeeklyEnergySupport.weightChange(listOf(a, a.copy(id = 2, weightKg = 62f)), today.minusDays(6), today))
        val b = a.copy(id = 3, date = "2026-10-08", weightKg = 60.5f)
        assertEquals(-0.5f, WeeklyEnergySupport.weightChange(listOf(a, b), today.minusDays(6), today)!!, 0.001f)
    }
    @Test fun maintenanceIgnoresReductionGoalAndFoodTarget() {
        val profile = com.example.data.UserProfileEntity(age = 30, heightCm = 165f, currentWeightKg = 60f,
            gender = "Kobieta", activityLevel = "Umiarkowana aktywność", targetCalories = 1200)
        val expected = (600.0 + 1031.25 - 150 - 161) * 1.375
        assertEquals(expected, WeeklyEnergySupport.maintenanceCalories(profile)!!, 0.001)
        assertEquals(expected, WeeklyEnergySupport.maintenanceCalories(profile.copy(goal = "Masa", targetCalories = 3000))!!, 0.001)
        assertNull(WeeklyEnergySupport.maintenanceCalories(profile.copy(age = 0)))
        assertNull(WeeklyEnergySupport.maintenanceCalories(profile.copy(activityLevel = "")))
    }
    @Test fun foodApprovalSurvivesChangingExpenditureBasisAndProfile() {
        val d = day("2026-10-08")
        val c = confirmation(d).copy(source = "profile")
        fun result(total: Double, source: String) = WeeklyEnergySupport.analyze(listOf(d), listOf(energy(d, total)),
            listOf(c), today.minusDays(6), today, today, source)
        assertEquals(400.0, result(2200.0, "profile").deficitKcal, 0.001)
        assertEquals(listOf(d.date), result(2200.0, "health").includedDates)
        assertEquals(500.0, result(2300.0, "profile").deficitKcal, 0.001)
    }
}
