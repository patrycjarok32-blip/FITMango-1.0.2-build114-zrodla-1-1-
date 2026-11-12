package com.example

import com.example.viewmodel.MangoExerciseAlternatives
import org.junit.Assert.*
import org.junit.Test

class MangoExerciseAlternativesTest {
    @Test fun alternativesNeverSuggestUnrelatedMuscleGroups() {
        val options = listOf("Glute bridge", "Hip thrust", "Biceps curl", "Lat pulldown")
        assertEquals(listOf("Hip thrust"),
            MangoExerciseAlternatives.candidates("Glute bridge", options, false))
    }
    @Test fun unknownExerciseDoesNotGetRandomReplacement() {
        assertTrue(MangoExerciseAlternatives.candidates("Unrecognized workout",
            listOf("Hip thrust", "Biceps curl"), false).isEmpty())
    }
    @Test fun homeFilterExcludesMachineAlternatives() {
        val options = listOf("Squat", "Leg press", "Przysiad z hantlami")
        val home = MangoExerciseAlternatives.candidates("Squat", options, true)
        assertFalse(home.contains("Leg press"))
        assertTrue(home.contains("Przysiad z hantlami"))
    }
}
