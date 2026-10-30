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
class SportsFeaturesTest {
    @Test fun journalUsesExactDatesPreviousSessionAndCountsDaysOnlyOnce() {
        val today = LocalDate.now(); val previous = today.minusDays(1).toString()
        val sets = listOf(SportSet("one", previous, "Przysiad", 10, 25.0, 100), SportSet("two", previous, "Przysiad", 8, 30.0, 200),
            SportSet("three", today.toString(), "Przysiad", 6, 35.0, 300), SportSet("four", today.toString(), "Wiosłowanie", 10, 20.0, 400))
        val data = SportsData(sets = sets, plans = listOf(SportPlan(today.plusDays(1).toString(), "Siłownia", false)))
        assertEquals(listOf("one", "two"), SportsSupport.previous(data, "przysiad", today.toString()).map { it.id })
        // Archived manual sets stay in backups but no longer count as watch activity.
        assertEquals(0, SportsSupport.activeDays(data, today))
        val session = SportSession("watch|1", "Spacer", today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString(),
            today.atStartOfDay(java.time.ZoneId.systemDefault()).plusHours(1).toInstant().toString(), "zepp")
        val second = session.copy(id = "watch|2")
        assertEquals(1, SportsSupport.activeDays(data.copy(history = listOf(session, second)), today))
        assertEquals(0, SportsSupport.activeDays(data.copy(history = listOf(session.copy(
            start = today.plusWeeks(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString(),
            end = today.plusWeeks(1).atStartOfDay(java.time.ZoneId.systemDefault()).plusHours(1).toInstant().toString()))), today))
        assertEquals(SportsSupport.week(today).plusDays(7), SportsSupport.week(today.plusWeeks(1)))
    }
    @Test fun diaryAndPlanSurvivePreferenceBackupRestoreIncludingOlderEmptyBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val date = LocalDate.now().toString()
        val data = SportsData(sets = listOf(SportSet("id", date, "Pompki", 12, 0.0, 123)), plans = listOf(SportPlan(date, "Odpoczynek", true)), goal = 4)
        SportsSupport.write(context, data)
        val backup = UsabilityPreferences.export(context)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(context, backup)
        assertEquals(data, SportsSupport.read(context))
        UsabilityPreferences.restore(context, "{}")
        assertEquals(SportsData(), SportsSupport.read(context))
        assertTrue(runCatching { SportsSupport.decode(SportsSupport.encode(data.copy(sets = data.sets.map { it.copy(kg = -1.0) }))) }.isFailure)
    }
    @Test fun restTimerSupportsPauseResumeAndExpiryWithoutRepeatingFrames() {
        val timer = RestTimer.start(90, 1000)
        assertEquals(90L, timer.seconds(1000))
        val paused = timer.pause(31000)
        assertEquals(60L, paused.seconds(100000))
        val resumed = paused.resume(100000)
        assertEquals(1L, resumed.seconds(159999))
        assertEquals(0L, resumed.seconds(160000))
        assertTrue(runCatching { RestTimer.start(0, 1) }.isFailure)
    }
    @Test fun importedSessionsRoundTripWithoutTurningMissingMetricsIntoZero() {
        val session = SportSession("origin|id", "Rower", "2026-10-12T10:00:00Z", "2026-10-12T11:00:00Z", "com.example.zepp", null, 18.2)
        val restored = SportsSupport.decode(SportsSupport.encode(SportsData(history = listOf(session))))
        assertEquals(session, restored.history.single())
        assertNull(restored.history.single().activeKcal)
        assertTrue(runCatching { SportsSupport.decode(SportsSupport.encode(restored.copy(history = listOf(session, session)))) }.isFailure)
    }
}
