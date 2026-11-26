package com.example
import com.example.viewmodel.MealPlannerSupport
import com.example.viewmodel.UniversalNutritionRepair
import org.junit.Assert.*
import org.junit.Test
class ImportedPrefixNutritionRepairTest {
    @Test fun importedPrefixIsSeparated() {
        val item = MealPlannerSupport.parseSingleIngredientLine("(0.2 x Opakowanie) Rukola Ryneczek Lidla")
        assertEquals("Rukola Ryneczek Lidla", item.name)
        assertEquals(0.2f, item.baseQuantity!!, 0.001f)
        assertEquals("opakowanie", item.unit)
    }
    @Test fun gramsAndDecimalCommaArePreserved() {
        val items = MealPlannerSupport.parseRecipeIngredients("(0,25 g) Pieprz czarny; (10g) Szczypiorek")
        assertEquals(2, items.size)
        assertEquals(0.25f, items.first().baseQuantity!!, 0.001f)
        assertEquals("Szczypiorek", items.last().name)
        assertTrue(UniversalNutritionRepair.sameName("(10g) Szczypiorek", "Szczypiorek"))
        assertFalse(UniversalNutritionRepair.sameName("Mozzarella light Pilos", "Mozzarella"))
    }
}
