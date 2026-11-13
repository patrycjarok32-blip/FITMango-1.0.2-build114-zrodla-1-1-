package com.example

import com.example.viewmodel.WorkoutEntry
import com.example.viewmodel.WorkoutSession
import com.example.viewmodel.WorkoutSessionStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryPersistenceTest {
    private fun sample(id: String, kg: Double) = WorkoutSession(
        id, "2026-11-12", "Trening", 1000L,
        listOf(WorkoutEntry("Squat", 1, 8, kg, true)), 61000L, 6)

    @Test fun exportAndRestoreRoundTripPreservesData() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        val original = sample("original", 40.0)
        val updated = sample("updated", 45.0)
        WorkoutSessionStore.write(context, listOf(original))
        val exported = WorkoutSessionStore.exportJson(context)
        WorkoutSessionStore.validateRaw(exported)
        WorkoutSessionStore.write(context, listOf(updated))
        assertEquals("updated", WorkoutSessionStore.read(context).single().id)
        assertEquals("original", WorkoutSessionStore.restorePoint(context)!!.single().id)
        assertEquals(1, WorkoutSessionStore.restorePrevious(context))
        assertEquals(original, WorkoutSessionStore.read(context).single())
        assertEquals(updated, WorkoutSessionStore.restorePoint(context)!!.single())
        assertEquals(exported, WorkoutSessionStore.exportJson(context))
    }

    @Test fun corruptExportIsRejectedWithoutOverwritingHistory() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        WorkoutSessionStore.write(context, listOf(sample("safe", 30.0)))
        assertThrows(Exception::class.java) {
            WorkoutSessionStore.validateRaw("[{\"id\":\"invalid\"}]")
        }
        assertEquals("safe", WorkoutSessionStore.read(context).single().id)
    }
    @Test fun importedJsonRestoresHistoryAndKeepsPreviousVersion() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        val first = sample("first", 20.0)
        val second = sample("second", 30.0)
        WorkoutSessionStore.write(context, listOf(first))
        val exported = WorkoutSessionStore.exportJson(context)
        WorkoutSessionStore.write(context, listOf(second))
        assertEquals(1, WorkoutSessionStore.importJson(context, exported))
        assertEquals(first, WorkoutSessionStore.read(context).single())
        assertEquals(second, WorkoutSessionStore.restorePoint(context)!!.single())
    }

    @Test fun invalidImportNeverOverwritesExistingSessions() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        WorkoutSessionStore.write(context, listOf(sample("safe", 25.0)))
        assertThrows(Exception::class.java) {
            WorkoutSessionStore.importJson(context, "{invalid json}")
        }
        assertEquals("safe", WorkoutSessionStore.read(context).single().id)
    }

}
