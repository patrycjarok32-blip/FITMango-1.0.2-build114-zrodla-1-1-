package com.example

import com.example.data.RecipeEntity
import com.example.viewmodel.RecipeFilterSupport
import org.junit.Assert.*
import org.junit.Test

class IndexedRecipeSearchTest {
    @Test fun indexedSearchPreservesAccentsAliasesCategoriesFavoritesAndTimeFiltersForALargeLibrary() {
        val recipes = (1L..1000L).map { id -> RecipeEntity(id, if (id % 2L == 0L) "Łosoś z ryżem $id" else "Owsianka $id",
            "Czas: ${if (id % 3L == 0L) 10 else 25} min", "Jabłko, ryż", "Podaj", 300, 20, 30, 10,
            if (id % 2L == 0L) "Lunch/Dinner" else "Breakfast", isFavorite = id % 5L == 0L) }
        val index = recipes.map { RecipeFilterSupport.index(it, RecipeFilterSupport.providedMinutes(it), it.id % 7L == 0L, it.id % 3L == 0L) }
        val cases = listOf(
            listOf("", "Wszystkie", "Wszystkie czasy", "false"),
            listOf("losos ryzem", "Obiad", "Do 30 minut", "false"),
            listOf("ŚNIADANIE", "Śniadanie", "Do 15 minut", "false"),
            listOf("jabłko", "Kolacja", "Wszystkie czasy", "true"),
            listOf("nieistniejące", "Wszystkie", "Wszystkie czasy", "false"),
            listOf("", "Wszystkie", "Bez gotowania", "false"),
            listOf("", "Wszystkie", "Do pracy", "true"),
            listOf("", "Wszystkie", "Brak czasu", "false"))
        for ((query, category, preparation, favorite) in cases) {
            val favorites = favorite.toBoolean()
            val expected = recipes.filter { RecipeFilterSupport.matches(it, query, category, preparation,
                RecipeFilterSupport.providedMinutes(it), it.id % 7L == 0L, it.id % 3L == 0L, favorites) }
            assertEquals(expected.map { it.id }, RecipeFilterSupport.filter(index, query, category, preparation, favorites).map { it.id })
        }
    }
}
