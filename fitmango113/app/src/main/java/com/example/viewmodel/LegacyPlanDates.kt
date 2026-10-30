package com.example.viewmodel

import com.example.data.MealPlanEntity
import java.time.LocalDate

object LegacyPlanDates {
    fun knownDate(meal: MealPlanEntity): LocalDate? = MealPlannerSupport.parsePlanDate(meal.dayOfWeek)
        ?: meal.completedDate?.let { MealPlannerSupport.parsePlanDate(it) }

    fun needsWeek(meal: MealPlanEntity): Boolean = knownDate(meal) == null && MealPlannerSupport.isPlanDay(meal.dayOfWeek)

    fun assign(meal: MealPlanEntity, referenceWeek: LocalDate): MealPlanEntity {
        val date = knownDate(meal) ?: MealPlannerSupport.dateForDay(meal.dayOfWeek, referenceWeek)
            ?: error("Nieprawidłowy dzień posiłku")
        return meal.copy(dayOfWeek = date.toString())
    }

    fun newEntry(meal: MealPlanEntity, today: LocalDate = LocalDate.now()): MealPlanEntity = assign(meal, today)
}
