package com.example

import com.example.viewmodel.MangoAiDailyContext
import com.example.viewmodel.SmartMealOption
import com.example.viewmodel.SmartMealSwapSupport
import org.junit.Assert.*
import org.junit.Test

class UnifiedMangoAiAndSwapTest {
    @Test fun dailyContextUsesActualValuesAndMarksMissingHealthData() {
        val context = MangoAiDailyContext.build(
            "2026-11-16", 1700,
            listOf(Triple("Owsianka", 350, true)),
            listOf("PULL", "Bieg"), null
        )
        assertTrue(context.contains("1700"))
        assertTrue(context.contains("Owsianka: 350 kcal; zjedzony"))
        assertTrue(context.contains("PULL"))
        assertTrue(context.contains("Bieg"))
        assertTrue(context.contains("brak potwierdzonej synchronizacji"))
        assertTrue(context.contains("nie zakładaj wartości"))
    }

    @Test fun contextBoundsUntrustedTitlesAndLargeLists() {
        val context = MangoAiDailyContext.build(
            "2026-11-16", null,
            List(30) { Triple("Meal\\n" + "X".repeat(200), 100, false) },
            List(30) { "Training\\n" + "Y".repeat(200) }, 1L
        )
        assertTrue(context.contains("Posiłki (30)"))
        assertTrue(context.contains("Treningi (30)"))
        assertFalse(context.contains("X".repeat(150)))
        assertFalse(context.contains("Y".repeat(150)))
    }

    @Test fun suggestedSwapsExcludeSameRecipeAndMissingCalories() {
        val current = SmartMealOption(1, "Lunch", 500, 25)
        val options = listOf(
            current,
            SmartMealOption(2, "Protein", 510, 35),
            SmartMealOption(3, "Unknown", null, 40)
        )
        val results = SmartMealSwapSupport.suggest(current, options)
        assertEquals(1, results.size)
        assertEquals(2L, results.first().recipe.recipeId)
    }
}
