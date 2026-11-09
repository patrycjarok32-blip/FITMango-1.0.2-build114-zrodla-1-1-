package com.example.ui.components

import com.example.viewmodel.SportSet
import org.junit.Assert.*
import org.junit.Test

class StrengthExerciseLibraryTest {
    @Test fun libraryHasDistinctExerciseNames() {
        assertTrue(StrengthExerciseLibrary.exercises.size >= 15)
        assertEquals(StrengthExerciseLibrary.exercises.size,
            StrengthExerciseLibrary.exercises.map { it.name }.distinct().size)
    }

    @Test fun progressionRequiresCompletedTargetReps() {
        val sets = listOf(
            SportSet("1", "2026-11-02", "Hip thrust", 10, 40.0, 1),
            SportSet("2", "2026-11-02", "Hip thrust", 10, 40.0, 2)
        )
        assertEquals(42.5, StrengthExerciseLibrary.suggestKg(sets, 10)!!, 0.001)
        assertNull(StrengthExerciseLibrary.suggestKg(sets, 12))
        assertNull(StrengthExerciseLibrary.suggestKg(sets.take(1), 10))
    }
}
