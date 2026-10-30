package com.example.viewmodel

import com.example.data.*

object QuickUseSupport {
    fun weeklyDays(days: List<HabitDay>, end: java.time.LocalDate): List<HabitDay> {
        val start = end.minusDays(6).toString()
        return days.filter { it.date >= start && it.date <= end.toString() }
            .distinctBy { it.date }.sortedBy { it.date }
    }
    const val PRODUCT_SOURCE = "Własny produkt • 100 g/ml"
    private val weightPattern = Regex("(?m)^Waga gotowego dania: ([0-9.]+) g; porcji: ([0-9.]+)$")
    fun cookedWeight(recipe: RecipeEntity): Pair<Float, Float>? = weightPattern.find(recipe.description)?.let {
        val total = it.groupValues[1].toFloatOrNull()
        val portions = it.groupValues[2].toFloatOrNull()
        if (total != null && portions != null && total.isFinite() && total in 1f..100000f && portions.isFinite() && portions in 0.1f..1000f) total to portions else null
    }
    fun portionWeight(total: Float, portions: Float): Float {
        require(total.isFinite() && total in 1f..100000f && portions.isFinite() && portions in 0.1f..1000f) { "Sprawdź wagę całości i liczbę porcji." }
        return total / portions
    }
    fun withCookedWeight(recipe: RecipeEntity, total: Float, portions: Float): RecipeEntity {
        portionWeight(total, portions)
        return recipe.copy(description = weightPattern.replace(recipe.description, "").trimEnd() +
            "\nWaga gotowego dania: $total g; porcji: $portions")
    }
    fun productRecipe(product: SubstituteCandidate): RecipeEntity {
        require(product.defaultUnit in listOf("g", "ml"))
        require(product.name.isNotBlank())
        require(listOf(product.caloriesPer100g, product.proteinPer100g, product.carbsPer100g, product.fatPer100g)
            .all { it != null && it.isFinite() && it >= 0 }) { "Uzupełnij kalorie i pełne makro przed zapisem produktu." }
        return ScannedProductMealSupport.recipe(product, 100f, product.defaultUnit).copy(source = PRODUCT_SOURCE,
            instructions = "Własny produkt na 100 ${product.defaultUnit}: ${product.caloriesPer100g}; ${product.proteinPer100g}; ${product.carbsPer100g}; ${product.fatPer100g}.",
            description = "Porcje: 1\nWłasny produkt. 1 porcja = 100 ${product.defaultUnit}. Wartości z etykiety lub bazy, sprawdź przed użyciem.")
    }
    fun product(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>): SubstituteCandidate? {
        if (recipe.source != PRODUCT_SOURCE) return null
        val i = ingredients.firstOrNull { it.recipeId == recipe.id } ?: return null
        return SubstituteCandidate(name = recipe.title, imageUrl = recipe.imageUrl, caloriesPer100g = i.caloriesPer100g,
            proteinPer100g = i.proteinPer100g, carbsPer100g = i.carbsPer100g, fatPer100g = i.fatPer100g,
            defaultUnit = i.unit, source = SubstituteOptionMode.LOCAL_DATABASE)
    }
    fun freshCopy(meal: MealPlanEntity, day: String, type: String) = meal.copy(id = 0,
        dayOfWeek = MealPlannerSupport.normalizeDayOfWeek(day), mealType = MealPlannerSupport.normalizeMealType(type),
        isCompleted = false, completedDate = null)
}
