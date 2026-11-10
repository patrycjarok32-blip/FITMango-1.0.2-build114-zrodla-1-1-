package com.example.ui.components

import org.junit.Assert.*
import org.junit.Test

class StrengthSessionGeneratorTest {
    @Test fun lowerBodyGetsExercises() {
        val exercises = StrengthSessionGenerator.exercises("Nogi i pośladki", "beginner", "gym")
        assertTrue(exercises.any { it.name == "Hip thrust" })
        assertTrue(exercises.all { it.sets == 2 && it.reps == 10 })
    }
    @Test fun upperBodyDiffersFromLowerBody() {
        val upper = StrengthSessionGenerator.exercises("Góra ciała", "intermediate", "gym")
        val lower = StrengthSessionGenerator.exercises("Nogi", "intermediate", "gym")
        assertNotEquals(upper.map { it.name }, lower.map { it.name })
    }
    @Test fun homePlansOnlyUseHomeCompatibleEquipment() {
        val exercises = StrengthSessionGenerator.exercises("Push", "beginner", "home")
        val library = StrengthExerciseLibrary.exercises.associateBy { it.name }
        assertTrue(exercises.isNotEmpty())
        assertTrue(exercises.all { library[it.name]?.equipment in listOf("Dom", "Oba") })
    }
    @Test fun allGeneratedMovementsExistInLibrary() {
        for (title in listOf("Push", "Pull", "Nogi", "Pośladki", "Góra ciała", "Full Body")) {
            for (place in listOf("gym", "home", "mixed")) {
                val names = StrengthSessionGenerator.exercises(title, "intermediate", place).map { it.name }
                assertTrue(names.all { n -> StrengthExerciseLibrary.exercises.any { it.name == n } })
                assertEquals(names.size, names.distinct().size)
            }
        }
    }
    @Test fun restDayHasNoExercises() {
        assertTrue(StrengthSessionGenerator.exercises("Odpoczynek", "beginner", "home").isEmpty())
    }
}
