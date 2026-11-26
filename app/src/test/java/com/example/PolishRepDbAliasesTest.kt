package com.example
import com.example.ui.components.CuratedExercisePhotos
import org.junit.Assert.*
import org.junit.Test
class PolishRepDbAliasesTest {
    @Test fun mappedPolishNamesHaveOfflineFrames() {
        listOf("Przysiad", "Suwnica na nogi", "Wykroki", "Prostowanie nóg", "Uginanie nóg", "Hip thrust", "Martwy ciąg rumuński", "Odwodzenie nóg", "Glute bridge", "Wiosłowanie hantlami", "Ściąganie drążka", "Wyciskanie na ławce", "Pompki", "Wyciskanie hantli", "Unoszenie bokiem", "Uginanie na biceps", "Prostowanie na triceps", "Przysiad goblet", "Przysiad bułgarski", "Przysiad sumo", "Przysiad Smitha", "Step-up", "Wykroki chodzone", "Zakroki", "Wspięcia na palce stojąc", "Wspięcia na palce siedząc", "Odwodzenie nogi z gumą", "Martwy ciąg klasyczny", "Martwy ciąg sumo", "Wiosłowanie sztangą", "Wiosłowanie na maszynie", "Wiosłowanie na wyciągu siedząc", "Pullover na wyciągu", "Podciąganie z asystą", "Wyciskanie hantli na ławce", "Wyciskanie na skosie dodatnim", "Rozpiętki z hantlami", "Rozpiętki na maszynie", "Rozpiętki na bramie", "Odwrotne rozpiętki", "Reverse pec deck", "Uginanie młotkowe", "Uginanie na modlitewniku", "Uginanie na wyciągu", "Uginanie koncentracyjne", "Francuskie wyciskanie", "Prostowanie tricepsa nad głową", "Dipy na poręczach", "Spięcia brzucha na wyciągu", "Unoszenie kolan w zwisie", "Farmer walk", "Thruster z hantlami", "Wiosłowanie gumą", "Mostek biodrowy na jednej nodze", "Glute bridge na jednej nodze").forEach {
            assertNotNull(it, CuratedExercisePhotos.illustrationUrl(it, 1))
        }
    }
    @Test fun ambiguousHipExtensionIsNotGuessed() {
        assertNull(CuratedExercisePhotos.illustrationUrl("Wyprost biodra (na stronę)", 1))
    }
}
