package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class NutritionLabelSupportTest {
    @Test fun polishLabelReadsKcalAndTotalMacrosOnly() {
        val values = NutritionLabelSupport.parse("Wartość energetyczna 840 kJ / 200 kcal\nTłuszcz 6,5 g\nw tym kwasy tłuszczowe nasycone 2 g\nWęglowodany 20 g\nw tym cukry 4 g\nBiałko 15 g")
        assertEquals(200f, values.calories!!, 0.001f)
        assertEquals(6.5f, values.fat!!, 0.001f)
        assertEquals(20f, values.carbs!!, 0.001f)
        assertEquals(15f, values.protein!!, 0.001f)
    }
    @Test fun multipleColumnsAndMissingValuesStayUnknown() {
        val values = NutritionLabelSupport.parse("Energy 840 kJ\nProtein 10 g 3 g\nCarbohydrate 0 g\nFat 2 g 0.6 g")
        assertNull(values.calories); assertNull(values.protein); assertNull(values.fat)
        assertEquals(0f, values.carbs!!, 0.001f)
    }
    @Test fun portionLabelNormalizesThenScalesActualAmount() {
        val item = NutritionLabelSupport.product("Jogurt", "60", "3", "6", "2", "50", "g")!!
        assertEquals(120f, item.caloriesPer100g!!, 0.001f)
        val recipe = ScannedProductMealSupport.recipe(item, 150f, "g")
        assertEquals(180, recipe.calories); assertEquals(9, recipe.protein)
        assertEquals(18, recipe.carbs); assertEquals(6, recipe.fat)
        assertEquals("Etykieta produktu", recipe.source)
        assertFalse(recipe.description.contains("Open Food Facts"))
        assertEquals(150f, ScannedProductMealSupport.ingredient(item, 150f, "g").quantity, 0.001f)
    }
    @Test fun invalidOrIncompleteInputCannotBeAdded() {
        assertNull(NutritionLabelSupport.product("X", "100", "", "2", "3", "100", "g"))
        assertNull(NutritionLabelSupport.product("X", "NaN", "1", "2", "3", "100", "g"))
        assertNull(NutritionLabelSupport.product("X", "100", "1", "2", "3", "0", "g"))
        assertNull(NutritionLabelSupport.product("X", "100", "101", "2", "3", "100", "g"))
        assertNotNull(NutritionLabelSupport.product("Woda", "0", "0", "0", "0", "100", "ml"))
    }
}
