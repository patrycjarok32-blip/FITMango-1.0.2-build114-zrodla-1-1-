package com.example.viewmodel

import kotlin.math.roundToInt

/** Imported positive values take precedence. Unknown quantities are never treated as zero. */
object ImportedNutritionSupport {
    fun needsEnrichment(recipe: ImportedRecipe): Boolean =
        recipe.calories <= 0 || recipe.protein <= 0 || recipe.carbs <= 0 || recipe.fat <= 0

    fun hasUsableNutrition(recipe: ImportedRecipe): Boolean =
        recipe.calories > 0 && (recipe.protein > 0 || recipe.carbs > 0 || recipe.fat > 0)

    fun merge(original: ImportedRecipe, estimated: ImportedRecipe): ImportedRecipe = original.copy(
        calories = original.calories.takeIf { it > 0 } ?: estimated.calories,
        protein = original.protein.takeIf { it > 0 } ?: estimated.protein,
        carbs = original.carbs.takeIf { it > 0 } ?: estimated.carbs,
        fat = original.fat.takeIf { it > 0 } ?: estimated.fat,
        nutritionAvailable = true, nutritionIsEstimated = true
    )

    /** Independent totals: missing macro must not discard an otherwise complete calorie sum. */
    fun availableFromLocalIngredients(recipe: ImportedRecipe, structured: List<com.example.data.RecipeIngredientEntity> = emptyList()): ScaledMealNutrition? {
        val ingredients = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, structured)
            .filterNot { IngredientSubstitutionSupport.isIgnoredSeasoning(it.name) }
        if (ingredients.isEmpty()) return null
        val servings = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:porcj.*|servings?.*)?$")
            .matchEntire(recipe.servings ?: "1")?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0 } ?: return null
        val items = ingredients.map { ingredient ->
            val qty = ingredient.baseQuantity?.takeIf { it.isFinite() && it > 0 }
            val mass = qty?.let { IngredientSubstitutionSupport.quantityInGramsOrMl(ingredient.name, it, ingredient.unit, structured) }
                ?.takeIf { it.isFinite() && it > 0 }
            mass to IngredientSubstitutionSupport.lookupKnownIngredientNutrition(ingredient.name, structured)
        }
        fun sum(select: (IngredientSubstitutionSupport.KnownMacroProfile) -> Float?): Int? {
            var total = 0f
            for ((mass, profile) in items) {
                val value = profile?.let(select)?.takeIf { it.isFinite() && it >= 0f } ?: return null
                if (mass == null) return null
                total += value * mass / 100f / servings
            }
            return total.takeIf { it.isFinite() && it <= Int.MAX_VALUE }?.roundToInt()
        }
        val kcal = sum { it.caloriesPer100g }; val protein = sum { it.proteinPer100g }
        val carbs = sum { it.carbsPer100g }; val fat = sum { it.fatPer100g }
        if (listOf(kcal, protein, carbs, fat).all { it == null }) return null
        return ScaledMealNutrition(kcal, protein, carbs, fat, isPartial = listOf(kcal, protein, carbs, fat).any { it == null })
    }
    fun fromLocalIngredients(recipe: ImportedRecipe, structured: List<com.example.data.RecipeIngredientEntity> = emptyList()): ImportedRecipe? {
        val totals = availableFromLocalIngredients(recipe, structured) ?: return null
        if (totals.isPartial) return null
        return recipe.copy(calories = totals.calories ?: return null, protein = totals.protein ?: return null,
            carbs = totals.carbs ?: return null, fat = totals.fat ?: return null, nutritionAvailable = true, nutritionIsEstimated = true)
    }
}

class ImportedNutritionException(message: String) : IllegalStateException(message)
