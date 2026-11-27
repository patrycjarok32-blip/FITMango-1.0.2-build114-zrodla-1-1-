package com.example

import com.example.ui.components.CuratedExercisePhotos
import com.example.ui.components.ExerciseMovementGuide
import org.junit.Assert.*
import org.junit.Test

class Sport71MediaRegressionTest {
    @Test fun knownExerciseHasTechniqueSteps() {
        assertEquals(3, ExerciseMovementGuide.steps("Hip thrust").size)
        assertEquals(3, ExerciseMovementGuide.steps("Squat").size)
    }

    @Test fun unknownExerciseDoesNotReceiveUnrelatedPhoto() {
        assertNull(CuratedExercisePhotos.url("unknown-exercise-71", 1))
    }

    @Test fun techniqueStepsAreAvailableWithoutNetwork() {
        assertTrue(ExerciseMovementGuide.steps("Hip thrust").all {
            it.title.isNotBlank() && it.instruction.isNotBlank()
        })
    }
}
