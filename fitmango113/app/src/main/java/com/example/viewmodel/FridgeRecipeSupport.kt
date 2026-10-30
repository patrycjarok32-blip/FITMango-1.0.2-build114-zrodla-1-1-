package com.example.viewmodel

import com.example.data.RecipeEntity
import java.text.Normalizer
import java.util.Locale

data class FridgeRecipeMatch(val recipe: RecipeEntity, val available: List<String>, val missing: List<String>) {
    val coverage: Float get() = available.size.toFloat() / (available.size + missing.size).coerceAtLeast(1)
}

/** Matches names only; quantities, freshness and dietary suitability must still be checked. */
object FridgeRecipeSupport {
    private fun key(raw: String): String {
        val name = MealPlannerSupport.parseSingleIngredientLine(raw).name
        return Normalizer.normalize(name.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }
    private val aliases = listOf(
        listOf("jajko", "jajka", "jajek", "egg", "eggs"),
        listOf("ryz", "ryzu", "rice"), listOf("kurczak", "kurczaka", "chicken"),
        listOf("mleko", "mleka", "milk"), listOf("pomidor", "pomidory", "pomidorow", "tomato", "tomatoes"),
        listOf("ogorek", "ogorki", "ogorka", "cucumber"), listOf("ziemniak", "ziemniaki", "ziemniakow", "potato", "potatoes"),
        listOf("banan", "banany", "banana"), listOf("jablko", "jablka", "apple", "apples"),
        listOf("cebula", "cebuli", "onion"), listOf("marchew", "marchewka", "carrot"),
        listOf("jogurt", "jogurtu", "yogurt"), listOf("szpinak", "szpinaku", "spinach")
    )
    private fun canonicalWord(word: String) = aliases.firstOrNull { word in it }?.first() ?: word
    private fun tokens(text: String) = text.split(' ').filter { it.isNotBlank() }.map(::canonicalWord)

    fun matches(ingredient: String, stored: String): Boolean {
        val required = tokens(key(ingredient)); val have = tokens(key(stored))
        if (required.isEmpty() || have.isEmpty()) return false
        if (required == have) return true
        // A specific product must not be replaced with a generic one (e.g. oat milk with milk).
        val generic = setOf("z", "ze", "i", "the", "of", "a")
        return required.filter { it !in generic }.all { it in have }
    }

    fun suggestions(recipes: List<RecipeEntity>, contents: String): List<String> {
        val query = contents.split(',', ';', '\n').last().trim()
        if (query.length < 2) return emptyList()
        val queryKey = key(query)
        val queryTokens = tokens(queryKey)
        val stored = contents.split(',', ';', '\n').dropLast(1)
        return recipes.asSequence().flatMap { MealPlannerSupport.parseRecipeIngredients(it.ingredients).asSequence() }
            .map { it.name }.filter { it.isNotBlank() }.distinct()
            .filter { name -> (key(name).contains(queryKey) || queryTokens.all { it in tokens(key(name)) }) &&
                stored.none { matches(name, it) } }
            .sortedBy { it.length }.take(6).toList()
    }

    fun completeSuggestion(contents: String, suggestion: String): String {
        val existing = contents.split(',', ';', '\n').dropLast(1).map { it.trim() }.filter { it.isNotBlank() }
        return (existing + suggestion).joinToString(", ") + ", "
    }

    fun find(recipes: List<RecipeEntity>, contents: String): List<FridgeRecipeMatch> {
        val stored = contents.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }
        if (stored.isEmpty()) return emptyList()
        return recipes.mapNotNull { recipe ->
            val names = MealPlannerSupport.parseRecipeIngredients(recipe.ingredients).map { it.name }.filter { it.isNotBlank() }.distinct()
            if (names.isEmpty()) return@mapNotNull null
            val (available, missing) = names.partition { required -> stored.any { matches(required, it) } }
            if (available.isEmpty()) null else FridgeRecipeMatch(recipe, available, missing)
        }.sortedWith(compareByDescending<FridgeRecipeMatch> { it.coverage }.thenByDescending { it.available.size }.thenBy { it.missing.size }.thenBy { it.recipe.title })
    }
}
