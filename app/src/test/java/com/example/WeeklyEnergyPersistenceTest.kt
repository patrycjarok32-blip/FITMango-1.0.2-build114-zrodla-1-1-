package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeeklyEnergyPersistenceTest {
    @Test fun foodApprovalSurvivesBackupRestoreAndTemporaryMissingEnergy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE).edit().clear().commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val diary = WeeklyEnergyDiary(context, scope)
            val d = HabitDay("2026-10-08", 1800, 100, 150, 50, 2000, 1800, 400, 2500, listOf("Posiłki"), emptyList())
            diary.saveEnergy(listOf(DailyEnergy(d.date, 2200.0, true, 1)))
            diary.confirm(d, LocalDate.parse("2026-10-09"))
            val backup = HabitsDiary.export(context)
            HabitsDiary.validate(backup)
            diary.reopen(d.date); assertTrue(diary.confirmations.value.isEmpty())
            HabitsDiary.restore(context, backup)
            val restored = WeeklyEnergyDiary(context, scope)
            assertEquals(1, restored.confirmations.value.size)
            assertEquals(2200.0, restored.energy.value.single().totalKcal!!, 0.001)
            restored.saveEnergy(listOf(DailyEnergy(d.date, null, true, 2)))
            assertTrue(WeeklyEnergySupport.isFoodConfirmed(d, restored.confirmations.value.single()))
            assertTrue(WeeklyEnergySupport.analyze(listOf(d), restored.energy.value, restored.confirmations.value,
                LocalDate.parse(d.date), LocalDate.parse(d.date), LocalDate.parse("2026-10-09")).includedDates.isEmpty())
            restored.saveEnergy(listOf(DailyEnergy(d.date, 2300.0, true, 3)))
            val reopened = WeeklyEnergyDiary(context, scope)
            assertTrue(WeeklyEnergySupport.isFoodConfirmed(d, reopened.confirmations.value.single()))
            assertEquals(500.0, WeeklyEnergySupport.analyze(listOf(d), reopened.energy.value, reopened.confirmations.value,
                LocalDate.parse(d.date), LocalDate.parse(d.date), LocalDate.parse("2026-10-09")).deficitKcal, 0.001)
            reopened.reopen(d.date)
            assertFalse(WeeklyEnergySupport.isFoodConfirmed(d, reopened.confirmations.value.firstOrNull()))
        } finally { scope.cancel() }
    }
    @Test fun profileConfirmationPersistsWithoutOverwritingHealthEnergy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FITMangoHabits", Context.MODE_PRIVATE).edit().clear().commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val diary = WeeklyEnergyDiary(context, scope)
            val d = HabitDay("2026-10-08", 1800, 100, 150, 50, 2000, 1800, 400, 2500, listOf("Posiłki"), emptyList())
            diary.saveEnergy(listOf(DailyEnergy(d.date, 1638.0, true, 1)))
            diary.confirm(d, LocalDate.parse("2026-10-09"), 2200.0)
            val backup = HabitsDiary.export(context)
            HabitsDiary.validate(backup)
            diary.reopen(d.date)
            HabitsDiary.restore(context, backup)
            val restored = WeeklyEnergyDiary(context, scope)
            assertEquals("profile", restored.confirmations.value.single().source)
            assertEquals(2200.0, restored.confirmations.value.single().burnedKcal, 0.001)
            assertEquals(1638.0, restored.energy.value.single().totalKcal!!, 0.001)
        } finally { scope.cancel() }
    }
}
