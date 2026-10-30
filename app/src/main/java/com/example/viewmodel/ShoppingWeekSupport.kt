package com.example.viewmodel

import com.example.data.MealPlanEntity
import com.example.data.ShoppingItemEntity
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object ShoppingWeekSupport {
    fun start(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun weekOf(meal: MealPlanEntity): String? = LegacyPlanDates.knownDate(meal)?.let { start(it).toString() }
    fun requireWeek(value: String): String {
        val date = LocalDate.parse(value)
        require(start(date) == date) { "Początek tygodnia musi być poniedziałkiem" }
        return date.toString()
    }
    /** Exact Monday–Sunday ranges retain their existing database key. */
    fun rangeKey(from: LocalDate, to: LocalDate): String {
        require(!to.isBefore(from)) { "Data do nie może być wcześniejsza niż data od." }
        return if (start(from) == from && to == from.plusDays(6)) from.toString()
            else "$from..$to"
    }
    fun bounds(key: String): Pair<LocalDate, LocalDate> {
        val parts = key.split("..")
        require(parts.size in 1..2)
        val from = LocalDate.parse(parts[0])
        val to = if (parts.size == 1) LocalDate.parse(requireWeek(key)).plusDays(6) else LocalDate.parse(parts[1])
        require(!to.isBefore(from)) { "Data do nie może być wcześniejsza niż data od." }
        return from to to
    }
    fun requireListKey(key: String): String = bounds(key).let { rangeKey(it.first, it.second) }
    fun includes(key: String, meal: MealPlanEntity): Boolean {
        val date = LegacyPlanDates.knownDate(meal) ?: return false
        val (from, to) = bounds(key)
        return !date.isBefore(from) && !date.isAfter(to)
    }
    fun visible(items: List<ShoppingItemEntity>, week: String, category: String): List<ShoppingItemEntity> =
        items.filter { it.weekStart == week && (category == "Wszystkie" || it.category.equals(category, true)) }
            .sortedWith(compareBy<ShoppingItemEntity> { it.isChecked }.thenBy { it.id })
}
