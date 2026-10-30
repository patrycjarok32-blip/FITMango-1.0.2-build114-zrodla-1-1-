package com.example.viewmodel

import com.example.data.RecipeEntity

object RecipeFilterSupport {
    val preparationOptions = listOf("Wszystkie czasy", "Do 15 minut", "Do 30 minut", "Bez gotowania", "Do pracy", "Brak czasu")

    /** Read provided durations, never infer total time from one cooking step. */
    fun durationMinutes(raw: String): Int? {
        val text = RecipeCategories.normalized(raw)
        if (text.isBlank()) return null
        if (text.startsWith("pt")) {
            return runCatching { java.time.Duration.parse(raw.trim().uppercase(java.util.Locale.ROOT)).toMinutes() }
                .getOrNull()?.takeIf { it in 0L..1440L }?.toInt()
        }
        val hour = Regex("(\\d+)\\s*(?:godzin\\w*|godz\\.?|hours?\\b|hrs?\\b|h\\b)").find(text)
        val minute = Regex("(\\d+)(?:\\s*[-–]\\s*(\\d+))?\\s*(?:minut\\w*|minutes?\\b|min\\b)").find(text)
        if (hour == null && minute == null) return null
        val hours = if (hour == null) 0L else hour.groupValues[1].toLongOrNull() ?: return null
        val minutes = if (minute == null) 0L else minute.groupValues[2].ifBlank { minute.groupValues[1] }.toLongOrNull() ?: return null
        if (hours !in 0L..24L || minutes !in 0L..1440L) return null
        return (hours * 60 + minutes).takeIf { it in 0L..1440L }?.toInt()
    }

    fun providedMinutes(recipe: RecipeEntity): Int? {
        val labelled = Regex("(?im)^\\s*(?:czas calkowity|laczny czas|total time|ready in|czas przygotowania|preparation time|czas)\\s*[:=–-]?\\s*(.+)$")
        val lines = (recipe.description + "\n" + recipe.instructions).lines().map(RecipeCategories::normalized)
        val totalLabel = Regex("^(?:czas calkowity|laczny czas|total time|ready in)\\s*[:=–-]?\\s*(.+)$")
        for (line in lines) {
            val match = totalLabel.find(line) ?: continue
            durationMinutes(match.groupValues[1])?.let { return it }
        }
        for (line in lines) {
            val match = labelled.find(line) ?: continue
            durationMinutes(match.groupValues[1])?.let { return it }
        }
        // Descriptions may contain legacy "15 min" metadata. Do not scan individual instruction steps.
        return durationMinutes(recipe.description)
    }

    fun matches(recipe: RecipeEntity, query: String, category: String, preparation: String,
        minutes: Int?, noCook: Boolean, work: Boolean, favoritesOnly: Boolean = false): Boolean {
        if (favoritesOnly && !recipe.isFavorite || !RecipeCategories.matches(recipe.category, category)) return false
        val timeMatches = when (preparation) {
            "Do 15 minut" -> minutes != null && minutes <= 15
            "Do 30 minut" -> minutes != null && minutes <= 30
            "Bez gotowania" -> noCook
            "Do pracy" -> work
            "Brak czasu" -> minutes == null
            else -> true
        }
        if (!timeMatches) return false
        val searchable = searchable(recipe)
        return RecipeCategories.normalized(query).split(' ').filter { it.isNotBlank() }.all { it in searchable }
    }
    fun searchable(recipe: RecipeEntity): String {
        val categories = RecipeCategories.categories(recipe.category)
        val aliases = categories.joinToString(" ") {
            when (it) {
                "Śniadanie" -> "sniadanie sniadania breakfast brunch"
                "Obiad" -> "obiad obiady lunch main course"
                "Kolacja" -> "kolacja kolacje dinner supper"
                "Przekąska" -> "przekaska przekaski deser desery snack dessert"
                else -> "inne"
            }
        }
        return RecipeCategories.normalized(listOf(recipe.title, recipe.ingredients, recipe.category,
            recipe.description, recipe.source, aliases).joinToString(" "))
    }

    data class Indexed(val recipe: RecipeEntity, val text: String, val categories: Set<String>,
        val minutes: Int?, val noCook: Boolean, val work: Boolean)
    fun index(recipe: RecipeEntity, minutes: Int?, noCook: Boolean, work: Boolean) =
        Indexed(recipe, searchable(recipe), RecipeCategories.categories(recipe.category), minutes, noCook, work)

    fun filter(index: List<Indexed>, query: String, category: String, preparation: String, favoritesOnly: Boolean): List<RecipeEntity> {
        val words = RecipeCategories.normalized(query).split(' ').filter { it.isNotBlank() }
        val selected = if (category == "Wszystkie") null else RecipeCategories.canonical(category)
        return index.asSequence().filter { entry ->
            (!favoritesOnly || entry.recipe.isFavorite) && (selected == null || selected in entry.categories) &&
                (when (preparation) {
                    "Do 15 minut" -> entry.minutes != null && entry.minutes <= 15
                    "Do 30 minut" -> entry.minutes != null && entry.minutes <= 30
                    "Bez gotowania" -> entry.noCook
                    "Do pracy" -> entry.work
                    "Brak czasu" -> entry.minutes == null
                    else -> true
                }) && words.all { it in entry.text }
        }.map { it.recipe }.toList()
    }

}
