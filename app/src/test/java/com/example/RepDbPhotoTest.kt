package com.example
import com.example.ui.components.CuratedExercisePhotos
import org.junit.Assert.*
import org.junit.Test
class RepDbPhotoTest {
    @Test fun missingPhotoUsesIllustration() {
        assertTrue(CuratedExercisePhotos.url("Hack squat", 1)!!.endsWith("hack-squat-start.webp"))
        assertTrue(CuratedExercisePhotos.url("Hack squat", 2)!!.endsWith("hack-squat-peak.webp"))
    }
    @Test fun existingPhotoRetained() {
        assertTrue(CuratedExercisePhotos.url("Kickback na wyciągu", 1)!!.endsWith(".jpg"))
        assertNotNull(CuratedExercisePhotos.illustrationUrl("Kickback na wyciągu", 1))
        assertNull(CuratedExercisePhotos.illustrationUrl("Wyprost biodra (na stronę)", 1))
    }
}
