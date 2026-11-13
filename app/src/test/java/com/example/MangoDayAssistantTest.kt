package com.example

import com.example.viewmodel.MangoDayAssistant
import org.junit.Assert.*
import org.junit.Test

class MangoDayAssistantTest {
    @Test fun missingGoalDoesNotInventCalorieAdvice() {
        assertTrue(MangoDayAssistant.message(200, 0, 0, false).contains("Uzupełnij cel"))
    }
    @Test fun trainingSuggestionUsesActualRemainingCalories() {
        assertTrue(MangoDayAssistant.message(1250, 1700, 300, true).contains("450 kcal"))
    }
    @Test fun completedGoalTakesPriorityOverTraining() {
        assertTrue(MangoDayAssistant.message(1800, 1700, 0, true).contains("osiągnięty"))
    }
    @Test fun zeroWaterIsNotMistakenForHydrationRecord() {
        assertTrue(MangoDayAssistant.message(500, 1700, 0, false).contains("Nie zapisano"))
    }
}
