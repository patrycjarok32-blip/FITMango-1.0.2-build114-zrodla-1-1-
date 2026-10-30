package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.*
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EffectsAnalysisTest {
    private val today = LocalDate.now()
    private fun day(date: String, complete: Boolean = true) = HabitDay(date, 1800, 110, 180, 60, 2000, 1800, 400, 2500, listOf("Posiłek"), emptyList(), complete)
    private fun analyze(profile: UserProfileEntity = UserProfileEntity(currentWeightKg = 70f, targetCalories = 2200), settings: EffectsSettings = EffectsSettings()): EffectsReport {
        val days = (1..29).map { day(today.minusDays(it.toLong()).toString()) }
        return EffectsSupport.analyze((0..29).map { WeightLogEntity(id = it + 1L, date = today.minusDays(it.toLong()).toString(), weightKg = 70f) }, emptyList(), days,
            days.map { EnergyConfirmation(it.date, WeeklyEnergySupport.fingerprint(it), 2200.0, "profile") }, profile, SportsData(), settings, today, 30)
    }
    @Test fun threeGoalsUseObservedTrendAndNeverCountExerciseCaloriesAgain() {
        val reduction = analyze()
        assertEquals(29, reduction.completeDays)
        assertEquals(0.0, reduction.trendKgWeek!!, 0.0001)
        assertEquals(-100, reduction.proposedDelta)
        assertEquals(1800.0, reduction.referenceIntake!!, .01)
        assertNull(analyze(UserProfileEntity(currentWeightKg = 70f, goal = "Utrzymanie")).proposedDelta)
        assertEquals(100, analyze(UserProfileEntity(currentWeightKg = 70f, targetCalories = 2200, goal = "Budowanie masy")).proposedDelta)
        assertNull(analyze(settings = EffectsSettings(lastAdjustment = today.minusDays(10).toString())).proposedDelta)
        assertNull(analyze(settings = EffectsSettings(goalChanged = today.minusDays(5).toString())).proposedDelta)
        assertNull(analyze(settings = EffectsSettings(wellbeing = (0..3).map { WellbeingDay(today.minusDays(it.toLong()).toString(), 5, 1, 6.0) })).proposedDelta)
    }
    @Test fun approvedEstimatesCountInAveragesButStaleAndTodayDoNotSupportCorrections() {
        val days = listOf(day(today.minusDays(1).toString(), false), day(today.minusDays(2).toString()), day(today.toString()))
        val confirmations = listOf(EnergyConfirmation(days[0].date, WeeklyEnergySupport.fingerprint(days[0]), 2000.0, acceptsPartial = true), EnergyConfirmation(days[1].date, "stale", 2000.0))
        val r = EffectsSupport.analyze(emptyList(), emptyList(), days, confirmations, UserProfileEntity(), SportsData(), EffectsSettings(), today, 30)
        assertEquals(2, r.loggedDays); assertEquals(1, r.confirmedDays); assertEquals(1, r.completeDays)
        assertEquals(1800.0, r.meanCalories!!, .001); assertNull(r.proposedDelta); assertEquals(1, r.partialDays)
    }
    @Test fun dailyDedupAndSparseMeasurementsPreserveGapsInsteadOfInventingWeights() {
        val points = listOf(WeightPoint(today.minusDays(8).toString(), 70.0), WeightPoint(today.minusDays(4).toString(), 69.5), WeightPoint(today.toString(), 69.0))
        assertEquals(-.875, EffectsSupport.trend(points)!!, .0001)
        assertNull(EffectsSupport.trend(points.takeLast(2)))
        assertEquals(2, EffectsSupport.smoothed(points).size)
    }
    @Test fun wellbeingAndMaintenanceBandTravelWithTheExistingBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = EffectsSettings(69.0, 71.0, listOf(WellbeingDay(today.toString(), 3, 4, 7.5)))
        EffectsSupport.save(context, settings)
        val raw = UsabilityPreferences.export(context)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(context, raw)
        assertEquals(settings, EffectsSupport.read(context))
        assertTrue(runCatching { EffectsSupport.decode(EffectsSupport.encode(settings.copy(high = 68.0))) }.isFailure)
    }
}
