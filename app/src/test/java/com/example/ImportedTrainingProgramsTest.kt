package com.example

import com.example.viewmodel.ImportedExerciseVideos
import com.example.viewmodel.ImportedTrainingPrograms
import org.junit.Assert.*
import org.junit.Test

class ImportedTrainingProgramsTest {
    @Test fun twoIndependentTemplatesHaveValidExercises() {
        val plans = ImportedTrainingPrograms.all
        assertEquals(setOf("home", "glutes"), plans.map { it.id }.toSet())
        assertEquals(3, plans.first { it.id == "home" }.days.size)
        assertEquals(4, plans.first { it.id == "glutes" }.days.size)
        plans.forEach { plan ->
            assertTrue(plan.days.isNotEmpty())
            plan.days.forEach { day ->
                assertTrue(day.exercises.isNotEmpty())
                assertTrue(day.exercises.size <= 30)
                day.exercises.forEach { exercise ->
                    assertTrue(exercise.name.length in 1..120)
                    assertTrue(exercise.sets in 1..12)
                    assertTrue(exercise.reps in 1..100)
                    assertEquals(60, exercise.restSeconds)
                }
            }
        }
    }

    @Test fun homeDocumentVideosUseYoutubeLinks() {
        val home = ImportedTrainingPrograms.all.first { it.id == "home" }
        val names = home.days.flatMap { it.exercises }.map { it.name }
        assertTrue(names.count { ImportedExerciseVideos.forExercise(it) != null } >= 15)
        assertNull(ImportedExerciseVideos.forExercise("unknown exercise"))
    }
}
