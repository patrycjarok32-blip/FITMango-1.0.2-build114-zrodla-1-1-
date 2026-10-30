package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReductionPlanTest {
    private val plan = ReductionPlan("2026-10-26", listOf(ReductionStage(1800, 3), ReductionStage(1700, 3), ReductionStage(1600, 3)))
    @Test fun threeWeekStagesHaveNoGapOrDoubleCountOnCorrectionDay() {
        val p = ReductionPlanSupport.periods(plan)
        assertEquals(LocalDate.parse("2026-11-16"), p[0].endExclusive)
        assertTrue(p[0].contains(LocalDate.parse("2026-11-15")))
        assertEquals(1L, p[0].daysRemaining(LocalDate.parse("2026-11-15")))
        assertFalse(p[0].contains(LocalDate.parse("2026-11-16")))
        assertTrue(p[1].contains(LocalDate.parse("2026-11-16")))
        assertEquals(LocalDate.parse("2026-12-07"), p[2].start)
        assertEquals(LocalDate.parse("2026-12-28"), p[2].endExclusive)
        assertEquals(listOf(1, 4, 7), p.map { it.firstWeek })
        assertTrue(p.none { it.contains(LocalDate.parse("2026-10-25")) })
        assertTrue(p.none { it.contains(LocalDate.parse("2026-12-28")) })
    }
    @Test fun customDurationsCrossLeapDayAndKeepEqualCalorieStagesSeparate() {
        val p = ReductionPlanSupport.periods(ReductionPlan("2028-02-22", listOf(ReductionStage(1800, 1), ReductionStage(1800, 2))))
        assertEquals(LocalDate.parse("2028-02-29"), p[1].start)
        assertEquals(LocalDate.parse("2028-03-14"), p[1].endExclusive)
        assertEquals(14L, p[1].daysRemaining(p[1].start))
    }
    @Test fun invalidPlanCannotBeSavedOrRestored() {
        listOf(plan.copy(start = "2026-02-30"), plan.copy(stages = emptyList()),
            plan.copy(stages = listOf(ReductionStage(0, 3))), plan.copy(stages = listOf(ReductionStage(1800, 0))),
            plan.copy(stages = listOf(ReductionStage(1800, 53))), plan.copy(stages = List(13) { ReductionStage(1800, 3) }))
            .forEach { assertTrue(runCatching { ReductionPlanSupport.validate(it) }.isFailure) }
        assertTrue(runCatching { EffectsSupport.decode(EffectsSupport.encode(EffectsSettings(reductionPlan = plan.copy(stages = emptyList())))) }.isFailure)
    }
    @Test fun planSurvivesExistingBackupAndOldSettingsStillLoadWithoutOne() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = EffectsSettings(lastAdjustment = "2026-10-20", reductionPlan = plan)
        EffectsSupport.save(context, settings)
        val backup = UsabilityPreferences.export(context)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(context, backup)
        assertEquals(settings, EffectsSupport.read(context))
        assertNull(EffectsSupport.decode("{}").reductionPlan)
    }
}
