package com.example.viewmodel

import com.example.data.MealPlanEntity
import com.example.data.RecipeEntity
import java.time.LocalDate
import kotlin.math.abs

/** Generates only references to recipes already present in the user's local database. */
object LocalFourMealPlanner {
    val SLOTS = listOf("Śniadanie", "II śniadanie", "Obiad", "Kolacja")
    private val shares = listOf(0.25, 0.15, 0.35, 0.25)

    fun generate(start: LocalDate, days: Int, recipes: List<RecipeEntity>,
        existing: List<MealPlanEntity>, targetCalories: Int): List<MealPlanEntity> {
        require(days in setOf(1, 3, 7))
        require(recipes.isNotEmpty()) { "Baza przepisów jest pusta. Dodaj przepisy przed generowaniem jadłospisu." }
        val available = recipes.filter { it.id > 0L && it.title.isNotBlank() && it.calories > 0 }
        require(available.isNotEmpty()) { "Brak przepisów z podaną kalorycznością. Uzupełnij kalorie w bazie przepisów." }
        val added = mutableListOf<MealPlanEntity>()
        val used = mutableMapOf<Long, Int>()
        for (offset in 0 until days) {
            val date = start.plusDays(offset.toLong()).toString()
            val scheduled = existing.filter { MealPlannerSupport.matchesDay(it.dayOfWeek, date) } +
                added.filter { it.dayOfWeek == date }
            for ((index, slot) in SLOTS.withIndex()) {
                if (scheduled.any { MealPlannerSupport.normalizeMealType(it.mealType) == slot }) continue
                val preferred = available.filter { matchesSlot(it.category, slot) }
                val pool = if (preferred.isNotEmpty()) preferred else available
                val ideal = (targetCalories.coerceAtLeast(1) * shares[index])
                val recipe = pool.minWithOrNull(compareBy<RecipeEntity> {
                    // Prefer recipes not used on this day; reuse across days only when necessary.
                    if (scheduled.any { meal -> meal.recipeId == it.id }) 1 else 0
                }.thenBy { used[it.id] ?: 0 }.thenBy { abs(it.calories - ideal) }.thenBy { it.id })!!
                added += MealPlanEntity(dayOfWeek = date, mealType = slot, recipeId = recipe.id,
                    recipeTitle = recipe.title, servings = 1f, calories = recipe.calories)
                used[recipe.id] = (used[recipe.id] ?: 0) + 1
            }
        }
        return added
    }

    private fun matchesSlot(category: String, slot: String): Boolean {
        val value = category.lowercase().trim()
        return when (slot) {
            "Śniadanie" -> value.contains("breakfast") || value.contains("śniadan") || value.contains("sniadan")
            "II śniadanie" -> value.contains("snack") || value.contains("przekąsk") ||
                value.contains("przekask") || value.contains("second breakfast") || value.contains("ii śniad")
            "Obiad" -> value.contains("lunch") || value.contains("obiad")
            "Kolacja" -> value.contains("dinner") || value.contains("kolac") || value.contains("supper")
            else -> false
        }
    }
}
