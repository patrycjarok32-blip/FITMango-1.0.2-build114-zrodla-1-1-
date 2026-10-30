package com.example.viewmodel

import android.content.Context
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

data class SavedMealEntry(val meal: MealPlanEntity, val overrides: List<RecipeIngredientEntity>)
data class SavedMealSet(val id: String, val name: String, val entries: List<SavedMealEntry>)
data class BatchMealPortion(val day: String, val grams: Float)

object MealConvenienceSupport {
    const val BATCH_WEIGHT = "__FITMANGO_BATCH_PORTION_WEIGHT_V1__"
    fun batchPortionWeight(mealId: Long, ingredients: List<RecipeIngredientEntity>): Float? = ingredients
        .firstOrNull { it.recipeId == -mealId && it.foodName == BATCH_WEIGHT }?.grams
        ?.takeIf { it.isFinite() && it > 0 }
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter<List<SavedMealSet>>(Types.newParameterizedType(List::class.java, SavedMealSet::class.java))
    private fun prefs(context: Context) = context.getSharedPreferences("SavedMealSets", Context.MODE_PRIVATE)
    fun read(context: Context): List<SavedMealSet> = decode(export(context))
    fun export(context: Context): String = prefs(context).getString("sets", "[]") ?: "[]"
    fun decode(json: String): List<SavedMealSet> {
        require(json.length <= 2_000_000) { "Zestawy przekraczają limit." }
        val sets = if (json.isBlank()) emptyList() else adapter.fromJson(json) ?: error("Nieprawidłowe zestawy.")
        require(sets.size <= 100 && sets.map { it.id }.distinct().size == sets.size)
        sets.forEach { set ->
            require(set.id.isNotBlank() && set.name.isNotBlank() && set.name.length <= 80 && set.entries.size in 1..30)
            set.entries.forEach { entry ->
                require(entry.meal.servings.isFinite() && entry.meal.servings > 0 && entry.meal.servings <= 1000)
                require(entry.meal.recipeId > 0 && entry.meal.recipeTitle.isNotBlank())
                require(MealPlannerSupport.isPlanDay(entry.meal.dayOfWeek))
                require(MealPlannerSupport.MEAL_CATEGORIES.any { it.canonicalName == entry.meal.mealType })
                require(entry.overrides.size <= 200)
            }
        }
        return sets
    }
    fun restore(context: Context, json: String) { write(context, decode(json)) }
    fun write(context: Context, sets: List<SavedMealSet>) {
        val json = adapter.toJson(sets); decode(json)
        check(prefs(context).edit().putString("sets", json).commit()) { "Nie udało się zapisać zestawu." }
    }
    fun snapshot(name: String, meals: List<MealPlanEntity>, ingredients: List<RecipeIngredientEntity>): SavedMealSet {
        require(name.trim().length in 1..80 && meals.size in 1..30) { "Podaj nazwę i wybierz posiłki (maks. 30)." }
        return SavedMealSet(UUID.randomUUID().toString(), name.trim(), meals.map { meal ->
            SavedMealEntry(QuickUseSupport.freshCopy(meal, meal.dayOfWeek, meal.mealType),
                ingredients.filter { it.recipeId == -meal.id }.map { it.copy(id = 0) })
        })
    }
    fun batchServings(total: Float, portions: Float, assignments: List<BatchMealPortion>): List<Float> {
        val portionWeight = QuickUseSupport.portionWeight(total, portions)
        require(assignments.size in 1..7 && assignments.map { it.day }.distinct().size == assignments.size) { "Wybierz dni bez powtórzeń." }
        require(assignments.all { row -> MealPlannerSupport.isPlanDay(row.day) && row.grams.isFinite() && row.grams > 0 }) { "Sprawdź dni i gramaturę." }
        require(assignments.sumOf { it.grams.toDouble() } <= total.toDouble() + 0.01) { "Porcje przekraczają wagę całego dania." }
        return assignments.map { it.grams / portionWeight }
    }
    fun missingIngredients(items: List<ResolvedMealIngredientItem>, structured: List<RecipeIngredientEntity>): List<ResolvedMealIngredientItem> = items.filter { item ->
        if (IngredientSubstitutionSupport.isIgnoredSeasoning(item.ingredientName)) return@filter false
        val grams = item.quantityForMeal?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(item.ingredientName, it, item.unit, structured) }
        grams == null || !grams.isFinite() || grams <= 0 || hasMissingNutrition(item, structured)
    }
    fun hasMissingNutrition(item: ResolvedMealIngredientItem, structured: List<RecipeIngredientEntity>): Boolean {
        if (IngredientSubstitutionSupport.isIgnoredSeasoning(item.ingredientName)) return false
        val profile = if (item.substitution != null) {
            val sub = item.substitution
            listOf(sub.caloriesPer100g, sub.proteinPer100g, sub.carbsPer100g, sub.fatPer100g)
        } else IngredientSubstitutionSupport.lookupKnownIngredientNutrition(item.ingredientName, structured)?.let {
            listOf(it.caloriesPer100g, it.proteinPer100g, it.carbsPer100g, it.fatPer100g)
        }
        return profile == null || profile.any { it == null || !it.isFinite() || it < 0 }
    }
}
