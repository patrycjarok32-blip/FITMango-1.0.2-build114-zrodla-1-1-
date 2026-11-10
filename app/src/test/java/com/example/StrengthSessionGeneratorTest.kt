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
    @Test fun restDayHasNoExercises() {
        assertTrue(StrengthSessionGenerator.exercises("Odpoczynek", "beginner", "home").isEmpty())
    }
}
