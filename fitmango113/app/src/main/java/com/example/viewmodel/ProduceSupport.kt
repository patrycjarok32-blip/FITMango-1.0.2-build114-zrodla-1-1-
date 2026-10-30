package com.example.viewmodel

import com.example.data.*
import java.text.Normalizer

data class ProduceEntry(val key: String, val name: String, val grams: Float?, val category: String, val automatic: Boolean, val portionScale: Float = 1f, val reviewed: Boolean = false)
data class HabitDay(val date: String, val calories: Int, val protein: Int, val carbs: Int, val fat: Int,
    val drinksMl: Int, val targetCalories: Int, val produceGoal: Int, val waterGoal: Int,
    val meals: List<String>, val produce: List<ProduceEntry>, val nutritionComplete: Boolean = true) {
    val vegetableGrams get() = produce.filter { it.category == "vegetable" }.sumOf { (it.grams ?: 0f).toDouble() }.toInt()
    val fruitGrams get() = produce.filter { it.category == "fruit" }.sumOf { (it.grams ?: 0f).toDouble() }.toInt()
    val produceGrams get() = vegetableGrams + fruitGrams
}
object ProduceSupport {
    fun normalize(name: String): String = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace("ł", "l")
    fun category(name: String): String {
        val n = normalize(name)
        if (listOf("sok", "juice", "dzem", "jam", "zupa", "soup", "sos", "sauce", "ciasto", "cake", "mleko", "milk", "jogurt", "yogurt", "skyr", "serek", "cheese", "smak", "flavour", "platki", "flakes", "granola", "herbata", "tea", "ketchup", "salatka", "omlet", "pancake", "nalesnik", "smoothie", "shake").any { Regex("(^|[^a-z])" + it).containsMatchIn(n) }) return "other"
        val fruit = listOf("jabl", "apple", "banan", "orange", "pomarancz", "mandaryn", "mango", "gruszk", "pear", "truskaw", "strawber", "malin", "raspber", "borow", "blueber", "kiwi", "ananas", "pineapple", "brzoskw", "peach", "sliwk", "plum", "winogron", "grape", "granat", "pomegranate", "melon", "arbuz", "watermelon", "cytryn", "lemon", "awokad", "avocado")
        val veg = listOf("marchew", "carrot", "pomidor", "tomato", "ogorek", "cucumber", "brokul", "broccoli", "szpinak", "spinach", "salat", "lettuce", "papryk", "pepper", "cukini", "zucchini", "courgette", "kapust", "cabbage", "kalafior", "cauliflower", "burak", "beet", "cebula", "cebuli", "onion", "leek", "seler", "celery", "baklazan", "aubergine", "eggplant", "rzodk", "radish", "szczaw", "sorrel", "warzywa", "vegetables")
        // English pear must be a complete word: pearl barley is a cereal.
        val isFruit = fruit.any {
            val pattern = if (it == "pear") "(^|[^a-z])pears?($|[^a-z])" else "(^|[^a-z])" + it
            Regex(pattern).containsMatchIn(n)
        }
        return when { isFruit -> "fruit"; veg.any { Regex("(^|[^a-z])" + it).containsMatchIn(n) } -> "vegetable"; else -> "other" }
    }
    fun grams(quantity: Float?, unit: String): Float? {
        if (quantity == null || !quantity.isFinite() || quantity <= 0) return null
        val result = when (unit.trim().lowercase()) { "g", "gram", "gramy", "gramow", "gramów" -> quantity; "kg" -> quantity * 1000; else -> null }
        return result?.takeIf { it.isFinite() && it <= 100000f }
    }
    fun entries(meals: List<MealPlanEntity>, recipes: List<RecipeEntity>, ingredients: List<RecipeIngredientEntity>): List<ProduceEntry> {
        val byId = recipes.associateBy { it.id }
        return meals.flatMap { meal ->
            IngredientSubstitutionSupport.resolveMealIngredientsWithSubstitutions(byId[meal.recipeId], meal, ingredients).map { i ->
                ProduceEntry("${meal.id}:${normalize(i.originalIngredientName)}:${normalize(i.ingredientName)}", i.ingredientName,
                    grams(i.quantityForMeal, i.unit), category(i.ingredientName), true, MealPlannerSupport.safeServings(meal.servings))
            }
        }
    }
}
