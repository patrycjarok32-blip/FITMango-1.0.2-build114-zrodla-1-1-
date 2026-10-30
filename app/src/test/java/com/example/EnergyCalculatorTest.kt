package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnergyCalculatorTest {
    private val profile = UserProfileEntity(age = 35, heightCm = 175f, currentWeightKg = 75f, gender = "Mężczyzna", goal = "Utrzymanie")
    @Test fun legacyEstimateIsPreservedUntilDetailedSettingsAreSaved() {
        val expected = (750 + 6.25 * 175 - 5 * 35 + 5) * 1.375
        assertEquals(expected, WeeklyEnergySupport.maintenanceCalories(profile)!!, .001)
        assertEquals(expected, WeeklyEnergySupport.maintenanceCalories(profile.copy(targetCalories = 1200, goal = "Redukcja"))!!, .001)
    }
    @Test fun allThreeGoalsShareMaintenanceAndMacrosFitTheirTarget() {
        val settings = EnergyProfileSettings(work = "mixed", averageSteps = 8000, sessions = 3, minutes = 60)
        val plans = listOf("Redukcja", "Utrzymanie", "Budowanie masy").map { EnergyCalculatorSupport.plan(profile.copy(goal = it), settings) }
        assertEquals(plans[0].base.maintenance, plans[2].base.maintenance, .001)
        assertTrue(plans[0].calories < plans[1].calories)
        assertTrue(plans[2].calories > plans[1].calories)
        assertEquals(plans[0].base.maintenance * .85, plans[0].calories.toDouble(), 1.0)
        plans.forEach { p -> assertTrue(p.carbs >= 0); assertTrue(p.calories - p.protein * 4 - p.carbs * 4 - p.fat * 9 in 0..3) }
    }
    @Test fun overlappingMovementIsNotAddedTwiceAndMoreTrainingIsVisible() {
        val movement = EnergyProfileSettings(work = "standing", averageSteps = 8000, sessions = 0)
        val sameMovement = movement.copy(work = "desk")
        assertEquals(EnergyCalculatorSupport.base(profile, movement)!!.maintenance, EnergyCalculatorSupport.base(profile, sameMovement)!!.maintenance, .001)
        val running = movement.copy(trainingType = "walkrun", sessions = 4, minutes = 60, trainingStepsIncluded = true)
        assertEquals(EnergyCalculatorSupport.base(profile, movement)!!.maintenance, EnergyCalculatorSupport.base(profile, running)!!.maintenance, .001)
        assertTrue(EnergyCalculatorSupport.base(profile, running.copy(trainingStepsIncluded = false))!!.maintenance > EnergyCalculatorSupport.base(profile, running)!!.maintenance)
        assertTrue(EnergyCalculatorSupport.base(profile, movement.copy(sessions = 4, minutes = 60))!!.maintenance > EnergyCalculatorSupport.base(profile, movement)!!.maintenance)
        // Without a step estimate there is no recorded overlap to subtract.
        assertTrue(EnergyCalculatorSupport.base(profile, running.copy(averageSteps = null))!!.pal > EnergyCalculatorSupport.base(profile, movement.copy(averageSteps = null))!!.pal)
    }
    @Test fun invalidDataAreNotSilentlyReplacedAndIndividualModeDoesNotGenerateCalories() {
        assertNull(EnergyCalculatorSupport.base(profile.copy(age = 0), EnergyProfileSettings()))
        assertNull(EnergyCalculatorSupport.base(profile.copy(currentWeightKg = Float.NaN), EnergyProfileSettings()))
        assertTrue(runCatching { EnergyCalculatorSupport.plan(profile, EnergyProfileSettings(individualPlan = true)) }.isFailure)
        assertTrue(runCatching { EnergyCalculatorSupport.plan(profile, EnergyProfileSettings(averageSteps = -1)) }.isFailure)
        assertTrue(runCatching { EnergyCalculatorSupport.plan(profile.copy(goal = "Redukcja", currentWeightKg = 45f), EnergyProfileSettings()) }.isFailure)
    }
    @Test fun settingsRoundTripAndBackUpTogetherWithTheExistingUsabilityData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        prefs.edit().clear().commit()
        assertNull(EnergyCalculatorSupport.read(context))
        val settings = EnergyProfileSettings(work = "physical", averageSteps = 11000, trainingType = "cycle", sessions = 2, minutes = 90, surplusPercent = 8)
        prefs.edit().putString(EnergyCalculatorSupport.KEY, EnergyCalculatorSupport.encode(settings)).commit()
        val backup = UsabilityPreferences.export(context)
        UsabilityPreferences.validate(backup)
        prefs.edit().clear().commit()
        UsabilityPreferences.restore(context, backup)
        assertEquals(settings, EnergyCalculatorSupport.read(context))
    }
    @Test fun effectsAndWeeklySummaryUseIdenticalMaintenanceAndOldWeightDatesRemainUsable() {
        val today = LocalDate.parse("2026-10-14")
        val details = EnergyProfileSettings(averageSteps = 9000, sessions = 3, minutes = 60)
        val weights = listOf(WeightLogEntity(id = 1, date = "20.09.2026", weightKg = 75f), WeightLogEntity(id = 2, date = "13.10.2026", weightKg = 74f))
        val report = EffectsSupport.analyze(weights, emptyList(), emptyList(), emptyList(), profile, SportsData(), EffectsSettings(), today, 30, details)
        assertEquals(WeeklyEnergySupport.maintenanceCalories(profile, details)!!, report.maintenance!!, .001)
        assertEquals(listOf("2026-09-20", "2026-10-13"), report.points.map { it.date })
        assertNull(report.proposedDelta)
    }
}
