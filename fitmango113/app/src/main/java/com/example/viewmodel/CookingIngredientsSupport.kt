package com.example.viewmodel

import com.example.data.RecipeEntity
import com.example.data.RecipeIngredientEntity

data class CookingIngredient(val id: String, val name: String, val display: String, val originalName: String = name, val quantity: Float? = null, val unit: String = "")

object CookingIngredientsSupport {
    fun fromRecipe(recipe: RecipeEntity, rows: List<RecipeIngredientEntity> = emptyList()): List<CookingIngredient> =
        MealPlannerSupport.parseRecipeIngredients(recipe.ingredients, rows.filter { it.recipeId == recipe.id })
            .mapIndexed { index, item -> CookingIngredient(index.toString(), item.name,
                item.formatForServings(1f).let { if (item.baseQuantity == null) "$it • ilość według przepisu" else it }, quantity = item.baseQuantity, unit = item.unit) }

    fun fromMeal(items: List<ResolvedMealIngredientItem>): List<CookingIngredient> = items.mapIndexed { index, item ->
        CookingIngredient(index.toString(), item.ingredientName,
            item.displayLine.removePrefix("- ").let { if (item.quantityForMeal == null) "$it • ilość według przepisu" else it },
            item.originalIngredientName, item.quantityForMeal, item.unit)
    }

    // A conservative textual association; no invented per-step quantities or allocation.
    private val ignored = setOf("naturalny", "naturalna", "swiezy", "swieza", "mrozony", "mrozona", "nasiona", "nasion", "nasionami", "szt", "gram", "lyzka", "lyzki", "opcjonalnie", "lub", "bialy", "biala", "pelnoziarnista")
    private fun words(text: String) = ProduceSupport.normalize(text).split(Regex("[^a-z]+"))
        .filter { it.length >= 3 && it !in ignored }
    fun forStep(step: String, items: List<CookingIngredient>): List<CookingIngredient> {
        val text = words(step)
        return items.filter { item ->
            words(item.name + " " + item.originalName).any { token ->
                val stem = token.take(if (token.length <= 4 || token.startsWith("jaj")) 3 else 4)
                text.any { it.startsWith(stem) }
            } || (ProduceSupport.normalize(step).contains("przypraw") &&
                listOf("sol", "pieprz", "przypraw", "kurkum", "cynamon", "oregano", "bazyl").any { ProduceSupport.normalize(item.name).startsWith(it) })
        }
    }
    fun scaled(items: List<CookingIngredient>, factor: Float): List<CookingIngredient> {
        require(factor.isFinite() && factor > 0)
        return items.map { item -> item.quantity?.let { q -> item.copy(quantity = q * factor,
            display = "${item.name} — ${MealPlannerSupport.formatQuantityNumber(q * factor)} ${item.unit}".trim()) } ?: item }
    }
    fun displayForStep(step: String, item: CookingIngredient): String {
        val quantity = item.quantity ?: return item.display
        val normalized = ProduceSupport.normalize(step)
        val stems = words(item.name + " " + item.originalName).map { it.take(if (it.length <= 4 || it.startsWith("jaj")) 3 else 4) }.distinct()
        val half = stems.any { stem -> Regex("(?:1/2|½|polowe|polowa)\\s+" + Regex.escape(stem) + "[a-z]*\\b").containsMatchIn(normalized) }
        return if (half) "${item.name} — ${MealPlannerSupport.formatQuantityNumber(quantity / 2f)} ${item.unit} (½ ilości)".trim() else item.display
    }

}
