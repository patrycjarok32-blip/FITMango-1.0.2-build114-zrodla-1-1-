package com.example.viewmodel

import android.content.Context
import com.example.data.*
import org.json.JSONObject

data class NutritionAttention(val name: String, val meals: Int, val quantitiesMissing: Boolean, val barcode: String = "")
object NutritionAttentionSupport {
    const val KEY = "known_product_nutrition_v1"
    @Volatile private var known = emptyMap<String, IngredientSubstitutionSupport.KnownMacroProfile>()
    fun load(context: Context) { known = decode(context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "{}").orEmpty()) }
    fun decode(raw: String): Map<String, IngredientSubstitutionSupport.KnownMacroProfile> {
        require(raw.length <= 1_000_000)
        val json = JSONObject(raw); require(json.length() <= 1000)
        return json.keys().asSequence().associateWith { key ->
            val values = json.getJSONArray(key); require(values.length() == 4)
            val n = (0..3).map { values.getDouble(it).toFloat() }; require(n.all { it.isFinite() && it >= 0 && it <= 10000 })
            IngredientSubstitutionSupport.KnownMacroProfile(n[0], n[1], n[2], n[3])
        }
    }
    fun profile(name: String): IngredientSubstitutionSupport.KnownMacroProfile? {
        known[IngredientSubstitutionSupport.normalizeIngredientKey(name)]?.let { return it }
        val alias = ImportedIngredientReferences.find(name)?.key ?: return null
        return known.entries.firstOrNull { ImportedIngredientReferences.find(it.key)?.key == alias }?.value
    }
    fun save(context: Context, name: String, candidate: SubstituteCandidate) {
        val n = listOf(candidate.caloriesPer100g, candidate.proteinPer100g, candidate.carbsPer100g, candidate.fatPer100g)
        require(n.all { it != null && it.isFinite() && it >= 0 && it <= 10000 }) { "Uzupełnij kalorie oraz pełne makro na 100 g/ml." }
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        val json = JSONObject(prefs.getString(KEY, "{}").orEmpty())
        json.put(IngredientSubstitutionSupport.normalizeIngredientKey(name), org.json.JSONArray(n))
        val next = decode(json.toString())
        check(prefs.edit().putString(KEY, json.toString()).commit()) { "Nie udało się zapisać produktu." }
        known = next
    }
    fun collect(meals: List<MealPlanEntity>, recipes: List<RecipeEntity>, rows: List<RecipeIngredientEntity>): List<NutritionAttention> {
        val missing = meals.flatMap { meal ->
            val recipe = recipes.find { it.id == meal.recipeId }
            val items = IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, rows)
            val structured = rows.filter { it.recipeId == recipe?.id }
            items.filter { MealConvenienceSupport.hasMissingNutrition(it, structured) }.map { meal.id to it }
        }
        return missing.groupBy { IngredientSubstitutionSupport.normalizeIngredientKey(it.second.ingredientName) + "|" + it.second.substitution?.barcode.orEmpty().trim() }.values.map { group ->
            NutritionAttention(group.first().second.ingredientName, group.map { it.first }.distinct().size,
                group.any { it.second.quantityForMeal == null || it.second.quantityForMeal!! <= 0f }, group.first().second.substitution?.barcode.orEmpty().trim())
        }.sortedBy { it.name.lowercase() }
    }
}
