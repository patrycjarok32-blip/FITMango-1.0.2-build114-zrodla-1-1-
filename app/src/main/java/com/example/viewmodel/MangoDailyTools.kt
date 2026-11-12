package com.example.viewmodel

import com.example.data.RecipeEntity
import com.example.data.ShoppingItemEntity
import java.time.LocalDate
import kotlin.math.abs

/** Read-only suggestions; existing records, backup keys and user preferences are untouched. */
object MangoDailyTools {
    fun mealAlternatives(
        original: RecipeEntity,
        recipes: List<RecipeEntity>,
        maxResults: Int = 3
    ): List<RecipeEntity> = recipes.asSequence()
        .filter { it.id != original.id && it.calories > 0 && original.calories > 0 }
        .filter { it.category.equals(original.category, ignoreCase = true) }
        .filter { abs(it.calories - original.calories) <= (original.calories * 0.25).coerceAtLeast(80.0) }
        .sortedWith(compareBy<RecipeEntity> { abs(it.calories - original.calories) }
            .thenBy { abs(it.protein - original.protein) }
            .thenBy { it.title })
        .take(maxResults.coerceIn(0, 10)).toList()

    fun quickWorkout(exercises: List<PlannedExercise>, minutes: Int = 20): List<PlannedExercise> {
        val available = minutes.coerceIn(5, 60) * 60
        var seconds = 0
        val selected = mutableListOf<PlannedExercise>()
        for (exercise in exercises) {
            val perSet = 45 + exercise.restSeconds.coerceIn(0, 180)
            val count = ((available - seconds) / perSet).coerceAtMost(exercise.sets)
            if (count <= 0) break
            selected += exercise.copy(sets = count)
            seconds += count * perSet
        }
        return selected
    }

    fun rewardPoints(finishedWorkouts: Int, medals: Int): Int =
        (finishedWorkouts.coerceAtLeast(0).toLong() * 10 +
            medals.coerceAtLeast(0).toLong() * 50).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun nextAction(
        date: LocalDate,
        plans: List<SportPlan>,
        waterMl: Int,
        caloriesRemaining: Int?
    ): String {
        val plan = plans.firstOrNull { it.date == date.toString() && !it.rest }
        return when {
            plan != null -> "Dziś w planie: ${plan.title}. Możesz też wybrać krótszą sesję."
            waterMl < 1000 -> "Sprawdź nawodnienie i uzupełnij wodę według swoich potrzeb."
            caloriesRemaining != null && caloriesRemaining > 0 ->
                "Sprawdź najbliższy posiłek w jadłospisie."
            else -> "Zajrzyj do planu dnia i zapisz wykonane aktywności."
        }
    }

    /** Only exact duplicates with the same unit and quantity are collapsed. */
    fun uniqueShoppingItems(items: List<ShoppingItemEntity>): List<ShoppingItemEntity> =
        items.distinctBy { listOf(it.weekStart, it.itemName.trim().lowercase(),
            it.amount.trim().lowercase(), it.isChecked.toString()) }

    fun weightChange(firstKg: Float, lastKg: Float): Float? =
        if (firstKg.isFinite() && lastKg.isFinite() && firstKg > 0f && lastKg > 0f)
            lastKg - firstKg else null
}
