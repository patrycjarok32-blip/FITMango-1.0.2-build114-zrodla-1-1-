package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShoppingRangeTest {
    @get:Rule val compose = createComposeRule()
    private fun date(value: String) = LocalDate.parse(value)
    @Test fun inclusiveRangeCrossesWeeksAndYearsAndRetainsOldWeekKeys() {
        val key = ShoppingWeekSupport.rangeKey(date("2026-12-30"), date("2027-01-05"))
        fun meal(day: String) = MealPlanEntity(dayOfWeek = day, mealType = "Obiad", recipeTitle = "Test", calories = 0)
        for (day in listOf("2026-12-30", "2027-01-01", "2027-01-05")) assertTrue(ShoppingWeekSupport.includes(key, meal(day)))
        for (day in listOf("2026-12-29", "2027-01-06", "Poniedziałek")) assertFalse(ShoppingWeekSupport.includes(key, meal(day)))
        assertEquals("2026-10-19", ShoppingWeekSupport.rangeKey(date("2026-10-19"), date("2026-10-25")))
        val single = ShoppingWeekSupport.rangeKey(date("2026-10-23"), date("2026-10-23"))
        assertTrue(ShoppingWeekSupport.includes(single, meal("2026-10-23")))
        assertFalse(ShoppingWeekSupport.includes(single, meal("2026-10-24")))
        assertTrue(runCatching { ShoppingWeekSupport.rangeKey(date("2026-10-24"), date("2026-10-23")) }.isFailure)
    }
    @Test fun generatorUsesOnlySelectedDaysAndKeepsManualAndOwnedRowsAcrossRegeneration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        val key = ShoppingWeekSupport.rangeKey(date("2026-12-30"), date("2027-01-05"))
        runBlocking {
            val recipe = dao.insertRecipe(RecipeEntity(title = "Ryż zakres", description = "", ingredients = "Ryż — 100 g", instructions = "Ugotuj", calories = 350, protein = 7, carbs = 78, fat = 1, category = "Obiad"))
            for (day in listOf("2026-12-29", "2026-12-30", "2027-01-05", "2027-01-06"))
                dao.insertMealPlan(MealPlanEntity(dayOfWeek = day, mealType = "Obiad", recipeId = recipe, recipeTitle = "Ryż zakres", calories = 350))
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "Ręczny", category = "Inne", weekStart = key, isManual = true))
        }
        val vm = DietaViewModel(app)
        compose.setContent { Text("Zakres") }
        fun rows() = runBlocking { dao.getAllShoppingItems().first() }.filter { it.weekStart == key }
        fun waitFor(check: () -> Boolean) = compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); check() }
        vm.generateShoppingList(key)
        waitFor { rows().any { !it.isManual } }
        val generated = rows().single { !it.isManual }
        assertEquals("200 g", generated.amount)
        vm.toggleShoppingItem(generated)
        waitFor { rows().any { it.id == generated.id && it.isChecked } }
        vm.generateShoppingList(key)
        vm.addShoppingItem("Ręczny drugi", "Inne", key)
        waitFor { rows().any { it.itemName == "Ręczny drugi" } }
        assertTrue(rows().single { it.id == generated.id }.isChecked)
        assertEquals(2, rows().count { it.isManual })
        assertEquals("200 g", rows().single { !it.isManual }.amount)
    }
}
