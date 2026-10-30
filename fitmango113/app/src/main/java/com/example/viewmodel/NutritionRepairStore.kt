package com.example.viewmodel

import android.content.Context
import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Numeric updates use the existing Room schema; quantities and valid values never change. */
data class NutritionRepairSaveResult(val changed: Boolean, val complete: Boolean = true)
class NutritionRepairStore(private val context: Context, private val repository: DietaRepository,
    private val database: AppDatabase = AppDatabase.getDatabase(context)) {
    suspend fun save(target: NutritionRepairTarget, incoming: SubstituteCandidate): NutritionRepairSaveResult = withContext(Dispatchers.IO) {
        require(UniversalNutritionRepair.usable(incoming)) { "Sprawdź kalorie i makro na 100 g/ml. Dane muszą pochodzić z poprawnego profilu produktu." }
        require(UniversalNutritionRepair.matches(target, incoming)) { "Nazwa, kod lub jednostka nie odpowiada wybranemu produktowi." }
        PersonalDataGate.mutex.withLock {
            val result = database.withTransaction {
                val recipes = repository.allRecipes.first()
                val rows = repository.allRecipeIngredients.first()
                val meals = repository.allMealPlans.first()
                val affectedRecipes = mutableSetOf<Long>()
                val affectedMeals = mutableSetOf<Long>()
                var changed = false
                var complete = true
                if (target.barcode.isBlank()) for (recipe in recipes) {
                    val structured = rows.filter { it.recipeId == recipe.id }
                    for (item in MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, structured).distinctBy { UniversalNutritionRepair.identity(it.name) + "|" + UniversalNutritionRepair.basis(it.unit) }) {
                        if (!UniversalNutritionRepair.sameName(item.name, target.name) || UniversalNutritionRepair.basis(item.unit) != target.basis) continue
                        val old = structured.find { UniversalNutritionRepair.sameName(it.foodName, item.name) }
                        val effective = IngredientSubstitutionSupport.lookupKnownIngredientNutrition(item.name, structured.filter { UniversalNutritionRepair.sameName(it.foodName, item.name) || ImportedIngredientReferences.sameFood(it.foodName, item.name) })
                        val original = SubstituteCandidate(item.name, caloriesPer100g = old?.caloriesPer100g?.takeIf { UniversalNutritionRepair.known(it) } ?: effective?.caloriesPer100g,
                            proteinPer100g = old?.proteinPer100g?.takeIf { UniversalNutritionRepair.known(it) } ?: effective?.proteinPer100g,
                            carbsPer100g = old?.carbsPer100g?.takeIf { UniversalNutritionRepair.known(it) } ?: effective?.carbsPer100g,
                            fatPer100g = old?.fatPer100g?.takeIf { UniversalNutritionRepair.known(it) } ?: effective?.fatPer100g, defaultUnit = target.basis)
                        val merged = UniversalNutritionRepair.merge(original, incoming)
                        complete = complete && !UniversalNutritionRepair.missing(merged)
                        if (UniversalNutritionRepair.values(original) == UniversalNutritionRepair.values(merged)) continue
                        val base = old ?: RecipeIngredientEntity(recipeId = recipe.id, foodName = item.name,
                            quantity = item.baseQuantity?.takeIf { it.isFinite() && it > 0f } ?: 0f, unit = item.unit,
                            grams = if (item.unit.trim().lowercase() in listOf("g", "kg", "mg", "ml", "l", "gram", "gramy", "gramow", "gramów"))
                                item.baseQuantity?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(item.name, it, item.unit, structured) }?.takeIf { it.isFinite() && it > 0f } ?: 0f else 0f,
                            caloriesPer100g = -1f, proteinPer100g = -1f, carbsPer100g = -1f, fatPer100g = -1f)
                        repository.insertIngredient(base.copy(caloriesPer100g = merged.caloriesPer100g ?: -1f,
                            proteinPer100g = merged.proteinPer100g ?: -1f, carbsPer100g = merged.carbsPer100g ?: -1f, fatPer100g = merged.fatPer100g ?: -1f))
                        affectedRecipes += recipe.id; changed = true
                    }
                }
                for (meal in meals) {
                    val recipe = recipes.find { it.id == meal.recipeId }
                    for (item in IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(recipe, meal, rows)) {
                        val sub = item.substitution ?: continue
                        if (!UniversalNutritionRepair.sameName(sub.replacementName, target.name) || sub.barcode.trim() != target.barcode.trim() || UniversalNutritionRepair.basis(sub.unit) != target.basis) continue
                        val original = SubstituteCandidate(sub.replacementName, barcode = sub.barcode, caloriesPer100g = sub.caloriesPer100g,
                            proteinPer100g = sub.proteinPer100g, carbsPer100g = sub.carbsPer100g, fatPer100g = sub.fatPer100g, defaultUnit = sub.unit)
                        val merged = UniversalNutritionRepair.merge(original, incoming)
                        complete = complete && !UniversalNutritionRepair.missing(merged)
                        if (UniversalNutritionRepair.values(original) == UniversalNutritionRepair.values(merged)) continue
                        repository.insertIngredient(IngredientSubstitutionSupport.encodeMealSubstitutionEntity(existingId = sub.entityId,
                            mealPlanId = meal.id, originalIngredientName = sub.originalIngredientName, originalBaseQuantity = sub.originalBaseQuantity,
                            originalUnit = sub.originalUnit, candidate = merged.copy(name = sub.replacementName, barcode = sub.barcode,
                                brand = sub.brand, imageUrl = sub.imageUrl), baseQuantityPerServing = sub.baseQuantityPerServing, unit = sub.unit))
                        affectedMeals += meal.id; changed = true
                    }
                }
                if (changed) {
                    val updatedRows = repository.allRecipeIngredients.first()
                    for (meal in meals.filter { it.recipeId in affectedRecipes || it.id in affectedMeals }) {
                        val nutrition = IngredientSubstitutionSupport.calculateMealNutritionWithSubstitutions(meal, recipes.find { it.id == meal.recipeId }, updatedRows)
                        nutrition.calories?.let { calories -> if (calories != meal.calories) repository.updateMealPlan(meal.copy(calories = calories)) }
                    }
                }
                NutritionRepairSaveResult(changed, complete)
            }
            if (result.changed) NutritionRepairSources.record(context, target, incoming)
            result
        }
    }
}

/** Metadata is part of the existing backed-up preferences; numeric data stay in Room. */
object NutritionRepairSources {
    const val KEY = "nutrition_repair_sources_v1"
    fun validate(raw: String) {
        require(raw.length <= 5_000_000)
        val json = JSONObject(raw); require(json.length() <= 10000)
        json.keys().forEach { key ->
            require(key.length <= 500)
            val row = json.getJSONObject(key)
            require(row.getString("name").length <= 180 && row.getString("source").length <= 500)
            require(row.getString("basis") in listOf("g", "ml") && row.getLong("time") >= 0L)
        }
    }
    fun record(context: Context, target: NutritionRepairTarget, candidate: SubstituteCandidate) {
        val prefs = context.getSharedPreferences("FITMangoUsability", 0)
        val json = runCatching { JSONObject(prefs.getString(KEY, "{}").orEmpty()) }.getOrDefault(JSONObject())
        val origin = (if (candidate.source == SubstituteOptionMode.OPEN_FOOD_FACTS) "Open Food Facts" else "Zapisane dane lokalne lub potwierdzone ręcznie") +
                " • " + candidate.name.take(160) + " • " + candidate.brand.take(160)
        json.put(target.key.take(500), JSONObject().put("name", target.name.take(180)).put("basis", target.basis)
            .put("source", origin).put("time", System.currentTimeMillis()))
        // Retain recent provenance, never truncate or discard numeric product records.
        if (json.length() > 10000) {
            val oldest = json.keys().asSequence().minByOrNull { json.getJSONObject(it).getLong("time") }
            if (oldest != null) json.remove(oldest)
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }
    fun history(context: Context): List<String> = runCatching {
        val raw = context.getSharedPreferences("FITMangoUsability", 0).getString(KEY, "{}").orEmpty()
        val json = JSONObject(raw)
        json.keys().asSequence().map { json.getJSONObject(it) }.sortedByDescending { it.getLong("time") }
            .take(20).map { "${it.getString("name")} • na 100 ${it.getString("basis")} • ${it.getString("source")}" }.toList()
    }.getOrDefault(emptyList())
}
