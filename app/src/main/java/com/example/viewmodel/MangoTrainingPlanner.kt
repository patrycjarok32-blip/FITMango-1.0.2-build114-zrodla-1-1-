package com.example.viewmodel

import java.time.LocalDate

/** Optional suggestions; never overwrites a user's plan without explicit confirmation. */
object MangoTrainingPlanner {
    data class Template(val title: String, val category: String, val minutes: Int,
        val distanceKm: Double? = null, val range: String = "")
    val templates = listOf(
        Template("PUSH", "Siłowy", 45),
        Template("PULL", "Siłowy", 45),
        Template("LEGS", "Siłowy", 50),
        Template("Easy Run", "Cardio", 30, 4.0, "3–5 km"),
        Template("Long Run", "Cardio", 60, 9.0, "8–10 km"),
        Template("Full Body Stretch", "Mobilność i stretching", 20),
        Template("Wzmacnianie", "Wzmacnianie", 30))
    fun suggest(start: LocalDate, days: Int, minutes: Int, existing: List<SportPlan>): List<SportPlan> {
        require(days in 1..7 && minutes in 10..180)
        val monday = SportsSupport.week(start)
        val occupied = existing.map { it.date }.toSet()
        val order = when (days) {
            1 -> listOf(0)
            2 -> listOf(0, 3)
            3 -> listOf(0, 2, 4)
            4 -> listOf(0, 1, 3, 5)
            5 -> listOf(0, 1, 2, 4, 5)
            6 -> listOf(0, 1, 2, 3, 4, 5)
            else -> (0..6).toList()
        }
        val sequence = listOf(templates[0], templates[3], templates[1], templates[5],
            templates[2], templates[4], templates[6])
        return order.mapIndexedNotNull { index, day ->
            val date = monday.plusDays(day.toLong()).toString()
            if (date in occupied) null else {
                val template = sequence[index]
                SportPlan(date, template.title, false, category = template.category,
                    targetMinutes = minOf(minutes, template.minutes),
                    targetDistanceKm = template.distanceKm, distanceRange = template.range)
            }
        }
    }
}
