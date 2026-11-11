package com.example

import com.example.viewmodel.UniversalWorkoutImporter
import org.junit.Assert.*
import org.junit.Test

class UniversalWorkoutImporterTest {
    @Test fun parsesMultipleDaysAndExercises() {
        val text = """
            PUSH
            Wyciskanie hantli 4 x 10
            Unoszenie bokiem 3 × 12
            PULL
            Wiosłowanie hantlami 4 x 8-10
        """.trimIndent()
        val result = UniversalWorkoutImporter.parse(text)
        assertEquals(2, result.size)
        assertEquals("PUSH", result[0].title)
        assertEquals(2, result[0].exercises.size)
        assertEquals(4, result[0].exercises[0].sets)
        assertEquals(10, result[0].exercises[0].reps)
        assertEquals(8, result[1].exercises[0].reps)
    }

    @Test fun rejectsUnclearOrOutOfRangeRows() {
        val result = UniversalWorkoutImporter.parse("PUSH\nNieczytelne ćwiczenie\nPrzysiad 99 x 10\nHip thrust 3 x 12")
        assertEquals(1, result.size)
        assertEquals(1, result[0].exercises.size)
        assertEquals("Hip thrust", result[0].exercises[0].name)
    }
}
