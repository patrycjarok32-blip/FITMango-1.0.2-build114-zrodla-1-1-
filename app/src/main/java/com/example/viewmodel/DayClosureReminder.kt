package com.example.viewmodel

import java.time.LocalDate

/**
 * Determines whether the app should offer to close yesterday's food diary.
 * This never approves a day without an explicit user action.
 */
object DayClosureReminder {
    data class Prompt(val date: String, val calories: Int, val targetCalories: Int)

    fun pendingYesterday(
        today: LocalDate,
        days: List<HabitDay>,
        confirmations: List<EnergyConfirmation>
    ): Prompt? {
        val yesterday = today.minusDays(1).toString()
        val day = days.firstOrNull { it.date == yesterday && it.meals.isNotEmpty() } ?: return null
        if (WeeklyEnergySupport.isFoodConfirmed(day, confirmations.firstOrNull { it.date == yesterday })) return null
        return Prompt(day.date, day.calories, day.targetCalories)
    }
}
