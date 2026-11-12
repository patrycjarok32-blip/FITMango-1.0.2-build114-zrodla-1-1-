package com.example

import com.example.viewmodel.MangoDailyTools
import com.example.viewmodel.PlannedExercise
import com.example.viewmodel.SportPlan
import com.example.data.RecipeEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MangoDailyToolsTest {
    private fun recipe(id: Long, kcal: Int, protein: Int = 20, category: String = "Lunch") =
        RecipeEntity(id = id, title = "Danie $id", description = "", ingredients = "",
            instructions = "", calories = kcal, protein = protein, carbs = 30, fat = 10,
            category = category)

    @Test fun swapsKeepMealTypeAndPreferClosestEnergy() {
        val original = recipe(1, 500)
        val result = MangoDailyTools.mealAlternatives(original, listOf(
            original, recipe(2, 505), recipe(3, 490), recipe(4, 900),
            recipe(5, 500, category = "Breakfast")))
        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test fun twentyMinuteSessionNeverExceedsEstimatedTime() {
        val exercises = listOf(PlannedExercise("A", 4, 10, 60),
            PlannedExercise("B", 4, 10, 60), PlannedExercise("C", 4, 10, 60),
            PlannedExercise("D", 4, 10, 60))
        val selected = MangoDailyTools.quickWorkout(exercises, 20)
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.sumOf { it.sets * (45 + it.restSeconds) } <= 1200)
        assertEquals(4, exercises[0].sets) // original plan stays unchanged
    }

    @Test fun pointsAndCoachAreDeterministic() {
        assertEquals(120, MangoDailyTools.rewardPoints(7, 1))
        assertEquals(0, MangoDailyTools.rewardPoints(-1, -3))
        val date = LocalDate.of(2026, 11, 12)
        assertTrue(MangoDailyTools.nextAction(date,
            listOf(SportPlan(date.toString(), "Trening A", false)), 1500, 400)
            .contains("Trening A"))
    }

    @Test fun weightTrendRejectsInvalidValues() {
        assertNull(MangoDailyTools.weightChange(0f, 70f))
        assertEquals(-1.5f, MangoDailyTools.weightChange(71.5f, 70f)!!, 0.001f)
    }
}
