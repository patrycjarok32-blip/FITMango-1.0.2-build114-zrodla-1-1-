package com.example.viewmodel

import com.example.data.MealPlanEntity

data class PortionMealAssignment(val date: String, val portions: Float)

object MealQueueSupport {
    fun eaten(meals: List<MealPlanEntity>, date: String) = meals.filter { it.isCompleted && it.completedDate == date }
    fun remaining(meals: List<MealPlanEntity>, date: String) = meals.filterNot { it.isCompleted && it.completedDate == date }

    fun validatePortions(prepared: Float, rows: List<PortionMealAssignment>) {
        require(prepared.isFinite() && prepared > 0 && prepared <= 1000) { "Podaj liczbę przygotowanych porcji." }
        require(rows.size in 1..14 && rows.map { it.date }.distinct().size == rows.size) { "Wybierz dni bez powtórzeń." }
        require(rows.all { MealPlannerSupport.parsePlanDate(it.date) != null && it.portions.isFinite() && it.portions > 0 && it.portions <= 1000 }) { "Sprawdź daty i liczbę porcji." }
        require(rows.sumOf { it.portions.toDouble() } <= prepared + 0.001) { "Przypisano więcej porcji niż przygotowano." }
    }
}
