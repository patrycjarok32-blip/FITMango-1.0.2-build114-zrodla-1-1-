package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Regression coverage for the profile's reuse of the existing sports_v1 schema. */
class SportProfileCompatibilityTest {
    @Test fun existingPlansSetsAndRecoverySurviveRoundTrip() {
        val day = LocalDate.now().toString()
        val original = SportsData(
            sets = listOf(SportSet("set-1", day, "Przysiad", 8, 40.0, 100L)),
            plans = listOf(SportPlan(day, "Legs", false)),
            recovery = listOf(RecoveryCheck(day, 3, 4)),
            history = emptyList()
        )
        val restored = SportsSupport.decode(SportsSupport.encode(original))
        assertEquals(original, restored)
        assertEquals(40.0, restored.sets.single().kg, 0.0)
        assertEquals("Legs", restored.plans.single().title)
    }

    @Test fun legacyEmptySportProfileRemainsReadable() {
        val restored = SportsSupport.decode("{}")
        assertTrue(restored.sets.isEmpty())
        assertTrue(restored.plans.isEmpty())
        assertTrue(restored.recovery.isEmpty())
    }

    @Test fun duplicateSetIdentifiersAreRejectedRatherThanSilentlyMerged() {
        val day = LocalDate.now().toString()
        val set = SportSet("same", day, "Przysiad", 8, 40.0, 100L)
        val raw = SportsSupport.encode(SportsData(sets = listOf(set, set)))
        assertTrue(runCatching { SportsSupport.decode(raw) }.isFailure)
    }
}
