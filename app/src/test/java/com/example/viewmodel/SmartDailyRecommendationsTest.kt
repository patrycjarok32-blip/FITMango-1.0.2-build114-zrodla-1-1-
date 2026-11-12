package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class SmartDailyRecommendationsTest {
    @Test fun recommendsRemainingTargets() {
        val result = SmartDailyRecommendations.evaluate(SmartDailyInput(
            calorieTarget = 1700, consumedCalories = 1200,
            proteinTargetGrams = 110, consumedProteinGrams = 80,
            waterConsumedMl = 1200, waterTargetMl = 2000
        ))
        assertEquals(listOf("calorie_balance", "protein_balance", "water"), result.map { it.id })
        assertTrue(result.first().explanation.contains("500 kcal"))
        assertTrue(result[1].explanation.contains("30 g"))
    }

    @Test fun missingNutritionDoesNotInventCaloriesOrProtein() {
        val result = SmartDailyRecommendations.evaluate(SmartDailyInput(
            calorieTarget = 1700, consumedCalories = null,
            proteinTargetGrams = 110, consumedProteinGrams = null,
            waterConsumedMl = null, waterTargetMl = 2000, nutritionIsPartial = true
        ))
        assertEquals(listOf("check_nutrition"), result.map { it.id })
    }

    @Test fun invalidTargetsDoNotCreateRecommendations() {
        val result = SmartDailyRecommendations.evaluate(SmartDailyInput(
            calorieTarget = 0, consumedCalories = 100,
            proteinTargetGrams = -1, consumedProteinGrams = 20,
            waterConsumedMl = -10, waterTargetMl = 2000
        ))
        assertTrue(result.isEmpty())
    }

    @Test fun completedTargetsProduceNoReminders() {
        val result = SmartDailyRecommendations.evaluate(SmartDailyInput(
            calorieTarget = 1700, consumedCalories = 1700,
            proteinTargetGrams = 100, consumedProteinGrams = 110,
            waterConsumedMl = 2000, waterTargetMl = 2000
        ))
        assertTrue(result.isEmpty())
    }
}
