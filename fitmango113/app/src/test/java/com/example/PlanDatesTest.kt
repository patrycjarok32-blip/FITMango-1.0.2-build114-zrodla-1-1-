package com.example

import com.example.data.MealPlanEntity
import com.example.viewmodel.MealPlannerSupport
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlanDatesTest {
    @Test fun futureWeeksRemainSeparateAndCrossYearBoundaries() {
        val sunday = LocalDate.parse("2026-10-11")
        val nextMonday = sunday.plusDays(1)
        assertEquals("2026-10-12", MealPlannerSupport.dateForDay("Poniedziałek", nextMonday).toString())
        assertEquals(nextMonday, MealPlannerSupport.dateForDay(nextMonday.toString(), sunday))
        assertFalse(MealPlannerSupport.matchesDay("2026-10-12", "2026-10-19"))
        assertTrue(MealPlannerSupport.matchesDay("2026-10-12", "2026-10-12"))
        assertEquals(LocalDate.parse("2027-01-04"), MealPlannerSupport.dateForDay("Poniedziałek", LocalDate.parse("2027-01-06")))
    }
    @Test fun datedCompletionDoesNotAppearInAnotherWeek() {
        val date = LocalDate.parse("2026-10-12")
        val meal = MealPlanEntity(dayOfWeek = date.toString(), mealType = "Obiad", recipeTitle = "Danie", calories = 450,
            isCompleted = true, completedDate = date.toString())
        assertEquals(450, MealPlannerSupport.consumedCaloriesForDate(listOf(meal), date))
        assertEquals(0, MealPlannerSupport.consumedCaloriesForDate(listOf(meal), date.plusWeeks(1)))
        assertTrue(MealPlannerSupport.isPlanDay(date.toString()))
        assertTrue(MealPlannerSupport.isPlanDay("Poniedziałek"))
        assertFalse(MealPlannerSupport.isPlanDay("2026-02-30"))
    }
}
