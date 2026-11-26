package com.example

import com.example.ui.components.CuratedExercisePhotos
import org.junit.Assert.*
import org.junit.Test

class CuratedExercisePhotoMatchingTest {
    @Test fun normalizesCaseAndWhitespace() {
        assertEquals(CuratedExercisePhotos.url("Hip thrust", 1), CuratedExercisePhotos.url(" HIP   THRUST ", 1))
        assertNotNull(CuratedExercisePhotos.url(" HIP   THRUST ", 1))
    }
    @Test fun unknownVariantDoesNotGetUnrelatedPhoto() {
        assertNull(CuratedExercisePhotos.url("Wyprost biodra (na stronę)", 1))
        assertNull(CuratedExercisePhotos.url("Hip thrust", 9))
    }
}
