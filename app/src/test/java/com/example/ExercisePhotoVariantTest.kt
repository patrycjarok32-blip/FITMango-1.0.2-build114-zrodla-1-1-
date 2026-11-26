package com.example
import com.example.ui.components.CuratedExercisePhotos
import org.junit.Assert.*
import org.junit.Test
class ExercisePhotoVariantTest {
    @Test fun cableAndBandAreDistinct() {
        val cable = CuratedExercisePhotos.url("Kickback na wyciągu", 1)
        val band = CuratedExercisePhotos.url("Wyprost biodra z gumą", 1)
        assertNotNull(cable)
        assertNotNull(band)
        assertNotEquals(cable, band)
        assertNotEquals(cable, CuratedExercisePhotos.url("Kickback na wyciągu", 2))
    }
    @Test fun unspecifiedHipExtensionDoesNotGuessEquipment() {
        assertNull(CuratedExercisePhotos.url("Wyprost biodra (na stronę)", 1))
    }
}
