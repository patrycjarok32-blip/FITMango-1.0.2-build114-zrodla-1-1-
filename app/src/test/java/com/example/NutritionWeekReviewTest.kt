package com.example

import com.example.data.UserProfileEntity
import com.example.data.WeightLogEntity
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NutritionWeekReviewTest {
    private val today = LocalDate.parse("2026-10-25")
    private val profile = UserProfileEntity(targetProtein = 120, targetCalories = 1900)
    private fun day(offset: Long, kcal: Int = 1800, complete: Boolean = true) = HabitDay(
        today.minusDays(offset).toString(), kcal, 100, 200, 60, 2000, 1800, 400, 2500,
        listOf("Posiłek • $kcal kcal"), emptyList(), complete)
    private fun approval(d: HabitDay) = EnergyConfirmation(d.date, WeeklyEnergySupport.fingerprint(d), 2200.0, acceptsPartial = true)
    private fun review(days: List<HabitDay>, approvals: List<EnergyConfirmation> = days.map(::approval), p: UserProfileEntity? = profile) =
        NutritionWeekSupport.review(p, days, approvals, today.minusDays(7), today.minusDays(1), today)

    @Test fun sevenApprovedEstimatedDaysAreCompleteInEveryFoodAverage() {
        val days = (1L..7L).map { day(it, 1800, false) }
        val r = review(days)
        assertEquals(7, r.approved.size); assertEquals(7, r.estimatedDays)
        assertEquals(1800.0, r.meanCalories!!, .001)
        assertTrue(r.strengths.first().contains("pełny zapis"))
        val effects = EffectsSupport.analyze(emptyList(), emptyList(), days, days.map(::approval), profile,
            SportsData(), EffectsSettings(), today, 7)
        assertEquals(7, effects.completeDays); assertEquals(7, effects.confirmedDays)
        assertEquals(1800.0, effects.meanCalories!!, .001)
        assertEquals(100.0, effects.meanProtein!!, .001)
        assertNull(effects.proposedDelta)
    }

    @Test fun unapprovedStaleTodayFutureAndDuplicateDaysCannotInflateResults() {
        val accepted = day(1)
        val stale = day(2)
        val days = listOf(accepted, accepted, stale.copy(calories = 9000), day(3), day(0), day(-1))
        val r = review(days, listOf(approval(accepted), approval(stale), approval(day(0)), approval(day(-1))))
        assertEquals(listOf(accepted.date), r.approved.map { it.date })
        assertEquals(1800.0, r.meanCalories!!, .001)
    }

    @Test fun previousWeekHasSeparateCountsAndMissingDaysAreNotZeros() {
        val r = review(listOf(day(1, 2000), day(8, 1600), day(9, 1800), day(15, 9999)))
        assertEquals(1, r.approved.size); assertEquals(2, r.previous.size)
        assertEquals(1700.0, r.previousMeanCalories!!, .001)
        assertEquals(2000.0, r.meanCalories!!, .001)
        assertTrue(r.observations.any { it.contains("różną liczbę") })
        val empty = review(emptyList())
        assertNull(empty.meanCalories); assertNull(empty.meanProtein); assertNull(empty.previousMeanCalories)
        assertNull(empty.produceMean)
    }

    @Test fun historicalCalorieGoalsAndCurrentProteinTargetAreKeptDistinct() {
        val a = day(1, 1800).copy(targetCalories = 1600, protein = 120)
        val b = day(2, 2000).copy(targetCalories = 2200, protein = 100)
        val r = review(listOf(a, b))
        assertEquals(0.0, r.calorieDifference!!, .001)
        assertEquals(1, r.proteinDaysAtTarget!!)
        assertNull(review(listOf(a), p = null).proteinTarget)
        assertNull(review(listOf(a.copy(targetCalories = 0))).calorieDifference)
    }

    @Test fun produceGramsAreNotInventedAndDoNotInvalidateApprovedFood() {
        val measured = day(1).copy(produce = listOf(ProduceEntry("a", "Marchew", 450f, "vegetable", true)))
        val unknown = day(2).copy(produce = listOf(ProduceEntry("b", "Jabłko", null, "fruit", true)))
        val absent = day(3)
        val r = review(listOf(measured, unknown, absent))
        assertEquals(3, r.approved.size); assertEquals(1, r.produceMeasuredDays)
        assertEquals(450.0, r.produceMean!!, .001); assertEquals(1, r.produceDaysAtGoal)
        assertEquals(2000.0, r.drinksMean!!, .001)
        assertTrue(r.observations.any { it.contains("nie podważają zatwierdzenia") })
    }

    @Test fun estimatedApprovedDaysDoNotUnlockAutomaticCalorieCorrection() {
        val days = (1L..29L).map { day(it, 1800, false) }
        val weights = (0L..29L).map { WeightLogEntity(id = it + 1, date = today.minusDays(it).toString(), weightKg = 70f) }
        val r = EffectsSupport.analyze(weights, emptyList(), days, days.map(::approval), profile,
            SportsData(), EffectsSettings(), today, 30)
        assertEquals(29, r.completeDays)
        assertEquals(1800.0, r.meanCalories!!, .001)
        assertNull(r.proposedDelta); assertNull(r.referenceIntake)
    }
}
