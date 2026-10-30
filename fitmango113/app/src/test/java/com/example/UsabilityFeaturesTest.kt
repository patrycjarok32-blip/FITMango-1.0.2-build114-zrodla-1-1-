package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.*
import com.example.backup.BackupStatusStore
import com.example.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UsabilityFeaturesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() {
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        context.getSharedPreferences("FITMangoBackupStatus", 0).edit().clear().commit()
    }
    @Test fun productsAndDashboardSurviveRecreationAndBackupRestore() {
        val store = UsabilityPreferences(context)
        val product = SubstituteCandidate(name = "Jogurt", barcode = "123", caloriesPer100g = 80f)
        store.rememberProduct(product); store.toggleFavorite(product)
        store.rememberProduct(product.copy(caloriesPer100g = 90f))
        val previousOrder = store.order()
        store.move("week", -1); store.show("ai", false)
        val movedOrder = store.order()
        assertEquals(previousOrder.indexOf("week") - 1, movedOrder.indexOf("week"))
        assertEquals(1, store.recent().size)
        assertEquals(90f, store.favorites().single().caloriesPer100g!!, 0.01f)
        val saved = UsabilityPreferences.export(context)
        context.getSharedPreferences("FITMangoUsability", 0).edit().clear().commit()
        UsabilityPreferences.restore(context, saved)
        val restored = UsabilityPreferences(context)
        assertTrue(restored.hidden("ai"))
        assertEquals(store.cards.size, restored.order().distinct().size)
        assertEquals(movedOrder, restored.order())
        assertTrue(restored.isFavorite(product))
    }
    @Test fun newCheckinAppearsNearCaloriesWithoutReorderingExistingCards() {
        val original = listOf("week", "calories", "water", "ai")
        context.getSharedPreferences("FITMangoUsability", 0).edit().putString("order", original.joinToString(",")).commit()
        val store = UsabilityPreferences(context)
        assertEquals(store.order().indexOf("calories") + 1, store.order().indexOf("checkin"))
        assertEquals(original, store.order().filter { it in original })
        store.move("checkin", 1)
        val saved = store.order()
        assertEquals(saved, UsabilityPreferences(context).order())
    }
    @Test fun previewUsesSameIngredientCalculationAsSavedOverride() {
        val recipe = RecipeEntity(id = 10, title = "Ryż", description = "", ingredients = "Ryż — 100 g", instructions = "", calories = 350, protein = 7, carbs = 79, fat = 1, category = "Obiad")
        val meal = MealPlanEntity(id = 20, dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = 10, recipeTitle = "Ryż", servings = 2f, calories = 700)
        val rows = listOf(RecipeIngredientEntity(recipeId = 10, foodName = "Ryż", quantity = 100f, grams = 100f, caloriesPer100g = 350f, proteinPer100g = 7f, carbsPer100g = 79f, fatPer100g = 1f))
        val replacement = SubstituteCandidate(name = "Własny produkt", caloriesPer100g = 200f, proteinPer100g = 10f, carbsPer100g = 20f, fatPer100g = 8f)
        val preview = MealChangePreview.calculate(meal, recipe, rows, "Ryż", replacement, 50f, "g")
        assertEquals(100, preview.calories); assertEquals(5, preview.protein); assertEquals(10, preview.carbs); assertEquals(4, preview.fat)
        assertFalse(preview.isPartial)
    }
    @Test fun preparationTagsAndBackupDatesPersist() {
        val store = UsabilityPreferences(context)
        store.setRecipe(10, 12, true, true)
        val other = UsabilityPreferences(context)
        assertTrue(other.tag(10, "nocook")); assertTrue(other.tag(10, "work"))
        val status = BackupStatusStore(context)
        assertFalse(status.fingerprint(1)); assertTrue(status.fingerprint(2))
        status.savedAutomatic(); assertEquals(1, status.state.value.changes)
        status.exported(); assertEquals(0, status.state.value.changes)
        assertTrue(BackupStatusStore(context).state.value.lastExport > 0)
    }
}
